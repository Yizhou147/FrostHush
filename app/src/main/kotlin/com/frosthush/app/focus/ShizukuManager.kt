package com.frosthush.app.focus

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<State> = _state

    private val binderReceivedListener = Shizuku.addBinderReceivedListenerSticky {
        refresh()
    }
    private val binderDeadListener = Shizuku.addBinderDeadListener {
        _state.value = State.NOT_CONNECTED
    }
    private val permissionListener = Shizuku.addRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQUEST_CODE) refresh()
    }

    init {
        refresh()
    }

    private fun currentState(): State = runCatching {
        when {
            Shizuku.isPreV11() || !Shizuku.pingBinder() -> State.NOT_CONNECTED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> State.UNAUTHORIZED
            else -> State.AUTHORIZED
        }
    }.getOrDefault(State.NOT_CONNECTED)

    fun refresh() {
        _state.value = currentState()
        // 授权恢复后，若有未完成的专注会话则自动重新暂停应用
        if (_state.value == State.AUTHORIZED) FocusManager.resumeSuspensionIfNeeded()
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
