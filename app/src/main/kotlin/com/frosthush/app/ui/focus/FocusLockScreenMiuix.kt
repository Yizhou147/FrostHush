package com.frosthush.app.ui.focus

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Choreographer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.util.DebugLog
import com.frosthush.app.util.StallWatch
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 专注模式的全屏锁定倒计时界面（与雹专注模式一致）：
 * 不透明背景遮挡下层内容、消费所有触摸、拦截返回键，不可打断。
 * 分段专注时仅在专注段显示（休息段由 AppRoot 隐藏锁屏）；休息段到点自动恢复下一段专注。
 * 倒计时以会话数据实时推演（session.phaseAt）为唯一数据源，每秒刷新剩余时间——FocusService
 * 发布的 phase 可能滞后（进程冻结/tick 停更/段边界），只作诊断参考，避免把界面定格在旧段；
 * 会话结束（会话被清理）时由 AppRoot 或本界面兜底解除锁定。
 *
 * 专注锁屏 · miuix 版（HyperOS 设计语言）：全屏沉浸页，无 Scaffold / 顶栏，
 * 铺满屏幕的背景容器 + 居中内容，自行处理系统栏 inset。
 * 内容对齐 HyperOS 系统时钟计时页：阶段标题 + 大圆环（蓝弧 = 本段剩余比例、平滑走动，
 * 环心等宽倒计时与「共 X 分钟」类说明，见 [FocusRingMiuix]）+ 已暂停应用数；环尺寸随屏幕自适应。
 */
@Composable
fun FocusLockScreenMiuix(onFinished: () -> Unit) {
    val context = LocalContext.current
    // 首帧同步用会话数据推演（旧实现 remaining/段起止初值为 0，环形首帧必然从 0% 起步，
    // 随后才被第一次 refresh 纠正，配合补间就会看到「弧线从头缓慢扫到当前值」）。
    val initialSession = remember { FocusStore.activeSession() }
    val initialNow = remember { System.currentTimeMillis() }
    val initialPhase = remember { initialSession?.phaseAt(initialNow) }
    var remaining by remember { mutableLongStateOf(initialPhase?.remainingAt(initialNow) ?: 0L) }
    var pausedCount by remember { mutableIntStateOf(initialSession?.packages?.size ?: 0) }
    // 环形所需的本段起止与说明文案（refresh 内随倒计时每秒更新）
    var segmentStartMillis by remember { mutableLongStateOf(initialPhase?.segmentStart ?: 0L) }
    var segmentEndMillis by remember { mutableLongStateOf(initialPhase?.segmentEnd ?: 0L) }
    var isFocusPhase by remember { mutableStateOf(initialPhase?.isFocus ?: true) }
    var ringSubLabel by remember {
        mutableStateOf(
            if (initialSession != null && initialPhase != null) {
                focusRingSubLabel(context, !initialPhase.isFocus, initialSession, initialPhase.index)
            } else {
                ""
            }
        )
    }
    // 每次回到前台自增：环形据此无条件落位到当前进度（禁止进入时缓慢扫到当前值）
    var entryToken by remember { mutableIntStateOf(0) }
    val isRest = !isFocusPhase

    // 单次刷新：会话已被清理（服务已结束）时返回 false
    fun refresh(): Boolean {
        val session = FocusStore.activeSession() ?: return false
        val now = System.currentTimeMillis()
        // 倒计时以会话实时推演为唯一数据源：FocusService 的 phase 可能滞后（进程冻结/tick 停更/
        // 段边界），旧实现优先信任非空 phase，会把时间/弧线定格在旧段的末尾（含 00:00 的变体）。
        val phaseInfo = session.phaseAt(now)
        remaining = phaseInfo.remainingAt(now)
        pausedCount = session.packages.size
        segmentStartMillis = phaseInfo.segmentStart
        segmentEndMillis = phaseInfo.segmentEnd
        isFocusPhase = phaseInfo.isFocus
        ringSubLabel = focusRingSubLabel(context, !phaseInfo.isFocus, session, phaseInfo.index)
        // 诊断打点：仅记录服务 phase 缺失/滞后（正常不刷日志），便于区分服务 tick 停更与 UI 问题
        val servicePhase = FocusManager.phase.value
        if (servicePhase == null || servicePhase.index != phaseInfo.index) {
            DebugLog.d(
                "LockScreen", "phase 与服务推演不一致 service=${servicePhase?.index} " +
                    "session=${phaseInfo.index} now=$now"
            )
        }
        StallWatch.markTick()
        // 仅当已过本段结束 2 秒以上仍为 0 才算异常（正常到点瞬间 tick 未切换时也会短暂归 0）
        if (remaining <= 0L && now - phaseInfo.segmentEnd > 2000L) {
            DebugLog.d(
                "LockScreen", "异常：remaining=0 idx${phaseInfo.index} " +
                    "segEnd=${phaseInfo.segmentEnd} now=$now sessionEnd=${session.endMillis}"
            )
        }
        return true
    }

    // 卡死现场实证（2026-11-21）：进程 TOP、主线程空闲、SurfaceFlinger 每 16.6ms 收到新 buffer
    // （App 一直在以 60fps 重绘同一帧），但页面数值几秒~几分钟不更新；切一次配置变更（Activity
    // 重建）立刻恢复。即 vsync/绘制链路活着，死的是 Handler/delay 驱动的每秒刷新。
    // 因此倒计时改为双通道：① Choreographer 帧回调（与绘制同源，卡死时实测仍在跑）；
    // ② delay 循环（保留为第二通道）。两者都调用幂等的 refresh()，谁活着谁驱动界面。
    var healGeneration by remember { mutableIntStateOf(0) }

    DisposableEffect(healGeneration) {
        val choreographer = Choreographer.getInstance()
        var lastFrameNanos = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (frameTimeNanos - lastFrameNanos >= 500_000_000L) {
                    lastFrameNanos = frameTimeNanos
                    if (!refresh()) {
                        onFinished()
                        return
                    }
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { choreographer.removeFrameCallback(callback) }
    }

    LaunchedEffect(healGeneration) {
        while (refresh()) delay(1000L)
        onFinished()
    }

    // 停更看门狗：写入还在、子组合持续拿不到新值 → 先换一代通道（重建两条 ticker），
    // 仍停更则重建 Activity（等价于用户手动切一次深色模式，实测可恢复）。
    // 仅在会话进行中、亮屏、页面处于 RESUMED 时生效，避免息屏/后台误触发。
    val hostActivity = context.findActivity()
    DisposableEffect(hostActivity) {
        val mainHandler = Handler(Looper.getMainLooper())
        val attempts = intArrayOf(0)
        val thread = Thread {
            var staleSince = 0L
            while (true) {
                try {
                    Thread.sleep(3000L)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                val now = SystemClock.elapsedRealtime()
                val interactive = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.isInteractive == true
                val resumed = hostActivity?.lifecycle?.currentState
                    ?.isAtLeast(Lifecycle.State.RESUMED) == true
                if (!interactive || !resumed || FocusStore.activeSession() == null) {
                    staleSince = 0L
                    attempts[0] = 0
                    continue
                }
                val lastRender = StallWatch.lastRenderAt
                if (lastRender != 0L && now - lastRender > 12_000L) {
                    if (staleSince == 0L) {
                        staleSince = now
                        StallWatch.log(
                            context,
                            "STALL render 停更 ${now - lastRender}ms tickAt=${StallWatch.lastTickAt} " +
                                "attempt=${attempts[0]}",
                        )
                        if (attempts[0] < 2) {
                            attempts[0]++
                            mainHandler.post { healGeneration++ }
                        }
                    } else if (now - staleSince > 12_000L) {
                        StallWatch.log(context, "STALL 持续 24s，重建 Activity")
                        staleSince = 0L
                        mainHandler.post { hostActivity?.recreate() }
                    }
                } else {
                    if (staleSince != 0L) {
                        StallWatch.log(context, "RECOVER render 恢复（attempt=${attempts[0]}）")
                    }
                    staleSince = 0L
                    attempts[0] = 0
                }
            }
        }.apply {
            isDaemon = true
            name = "fh-stall-watch"
            start()
        }
        onDispose { thread.interrupt() }
    }

    // 回到前台（小窗切全屏、系统解冻后恢复）立即重算一次，并让环形直接落位：
    // 进程冻结期间 1 秒循环不执行，界面会停在冻结前那一帧（历史实测卡在 00:06）；
    // entryToken 变化保证即使数据早已更新，弧线也不会用 1s 补间从旧值扫到当前值。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh()
        entryToken++
    }

    // 专注期间拦截返回键，禁止退出
    BackHandler { }

    // 全屏沉浸容器：铺满屏幕并自绘不透明背景，内容整体居中
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            // 不透明背景遮住下层内容
            .background(MiuixTheme.colorScheme.background)
            // 消费所有触摸事件，阻止点击穿透到下层页面
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 环尺寸按可用空间收缩（横屏/小屏防溢出），上限 280dp、下限 160dp
        val ringSize = minOf(280.dp, maxWidth * 0.85f, maxHeight * 0.5f).coerceAtLeast(160.dp)
        // 停更看门狗的在渲染侧信号：只有子组合真的拿到新值才记一次
        val lastMarkedRemaining = remember { longArrayOf(Long.MIN_VALUE) }
        SideEffect {
            if (remaining != lastMarkedRemaining[0]) {
                lastMarkedRemaining[0] = remaining
                StallWatch.markRender()
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(if (isRest) R.string.focus_rest_title else R.string.focus_lock_title),
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(28.dp))
            // 大圆环：蓝弧 = 本段剩余比例、平滑走动，环心等宽倒计时 + 本段/整场说明
            FocusRingMiuix(
                remaining = remaining,
                segmentStartMillis = segmentStartMillis,
                segmentEndMillis = segmentEndMillis,
                subLabel = ringSubLabel,
                preferredRingSize = ringSize,
                entryToken = entryToken,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = context.resources.getQuantityString(R.plurals.focus_apps_paused, pausedCount, pausedCount),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** 找到承载当前 Compose 内容的 Activity（看门狗需要判断是否 RESUMED、必要时重建）。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
