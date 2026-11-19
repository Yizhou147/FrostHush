package com.frosthush.app.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import com.frosthush.app.util.DebugLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 连接与授权管理，三种状态：
 * 未连接服务 / 已连接未授权 / 已连接已授权。
 */
object ShizukuManager {
    const val REQUEST_CODE = 1024

    enum class State {
        /** 未连接服务（Shizuku 未运行） */
        NOT_CONNECTED,
        /** 已连接但未授权 */
        UNAUTHORIZED,
        /** 已连接且已授权 */
        AUTHORIZED,
    }

    // 初值先给 NOT_CONNECTED，真实状态由后台线程探测后回流：
    // 本 object 常常在 Compose 组合期（FocusScreen 读 state）首次初始化，而
    // Shizuku.checkSelfPermission() 是同步 binder 调用——Shizuku 本体在后台被冻结时
    // 它会一直阻塞到对方解冻（实测卡住数秒），那就是「界面出来了但点不动」的来源之一。
    private val _state = MutableStateFlow(State.NOT_CONNECTED)
    val state: StateFlow<State> = _state

    /** 探测进行中的去重标记：并发触发（init / binder 回调 / 页面 resume）只跑一次 */
    private val probing = AtomicBoolean(false)

    private val binderReceivedListener = Shizuku.addBinderReceivedListenerSticky {
        refreshAsync("binderReceived")
    }
    private val binderDeadListener = Shizuku.addBinderDeadListener {
        _state.value = State.NOT_CONNECTED
    }
    private val permissionListener = Shizuku.addRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQUEST_CODE) refreshAsync("permissionResult")
    }

    init {
        refreshAsync("init")
    }

    private fun currentState(): State = runCatching {
        when {
            Shizuku.isPreV11() || !Shizuku.pingBinder() -> State.NOT_CONNECTED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.UNAUTHORIZED
            else -> State.AUTHORIZED
        }
    }.getOrDefault(State.NOT_CONNECTED)

    /** 兼容旧调用点：状态探测一律异步，绝不在调用线程执行 IPC */
    fun refresh() = refreshAsync("refresh")

    /**
     * 后台探测 Shizuku 状态并回流到 [state]。
     * 调用方可以在任意线程（包括 UI 线程/组合期）调用，这里只投递一个后台线程。
     */
    fun refreshAsync(reason: String) {
        if (!probing.compareAndSet(false, true)) return
        Thread({
            try {
                val start = SystemClock.elapsedRealtime()
                val newState = currentState()
                _state.value = newState
                val cost = SystemClock.elapsedRealtime() - start
                if (cost > 200) {
                    DebugLog.d("Shizuku", "状态探测耗时 ${cost}ms reason=$reason state=$newState")
                }
                // 授权恢复后，若有未完成的专注会话则自动重新暂停应用
                if (newState == State.AUTHORIZED) FocusManager.resumeSuspensionIfNeeded()
            } finally {
                probing.set(false)
            }
        }, "shizuku-probe").apply { isDaemon = true }.start()
    }

    fun isReady(): Boolean = _state.value == State.AUTHORIZED

    fun requestPermission() {
        if (!Shizuku.isPreV11() && Shizuku.pingBinder()) {
            runCatching { Shizuku.requestPermission(REQUEST_CODE) }
        }
    }

    /** 打开 Shizuku 应用（未连接服务时的引导） */
    fun openShizukuApp(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } else false
    }.getOrDefault(false)
}
