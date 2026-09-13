package com.frosthush.app.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.util.DebugLog
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 专注模式的全屏锁定倒计时界面（与雹专注模式一致）：
 * 不透明背景遮挡下层内容、消费所有触摸、拦截返回键，不可打断。
 * 分段专注时仅在专注段显示（休息段由 AppRoot 隐藏锁屏）；休息段到点自动恢复下一段专注。
 * 倒计时以 FocusService 发布的 phase 为唯一数据源（与超级岛同源），每秒刷新剩余时间；
 * 会话结束（phase 变 null / 会话被清理）时由 AppRoot 或本界面兜底解除锁定。
 *
 * 专注锁屏 · miuix 版（HyperOS 设计语言）：全屏沉浸页，无 Scaffold / 顶栏，
 * 铺满屏幕的背景容器 + 居中内容，自行处理系统栏 inset。
 */
@Composable
fun FocusLockScreenMiuix(onFinished: () -> Unit) {
    val context = LocalContext.current
    // 阶段状态统一以 FocusService 发布的 phase 为准（与超级岛倒计时同源）：
    // FocusService 每秒更新，StateFlow 变化自动触发重组刷新，锁屏不会再因自身循环
    // 在进程冻结/墙钟跳变时被定格在旧值（如 00:00）；会话结束 phase 变 null 时由
    // AppRoot 解除锁屏，本界面仅负责展示剩余时间，阶段推进/结束交给 FocusService。
    val phase by FocusManager.phase.collectAsState()
    var remaining by remember { mutableLongStateOf(0L) }
    var pausedCount by remember { mutableIntStateOf(0) }
    val isRest = phase?.isFocus == false

    // 单次刷新：会话已被清理（服务已结束）时返回 false
    fun refresh(): Boolean {
        val session = FocusStore.activeSession() ?: return false
        val current = FocusManager.phase.value
        // 倒计时以会话数据为准实时计算，phase 只作为快读来源（可能为 null）：
        // phase==null（如上一会话结束后、本会话服务首 tick 前的窗口）时回退到
        // session.phaseAt 实时推演，避免落到「?: 0L」显示错误的 00:00。
        val phaseInfo = current ?: session.phaseAt(System.currentTimeMillis())
        remaining = phaseInfo.remainingAt(System.currentTimeMillis())
        pausedCount = session.packages.size
        // 诊断打点（抓 00:00 bug 现场）：仅异常状态记录，正常不刷日志——
        // 会话存在但 phase 为空 / remaining 归 0 时每秒留一条，配合 FocusService 的
        // tick 异常日志即可还原"锁屏 00:00"是 phase 停更还是 UI 层问题
        if (current == null) {
            // phase 为 null 现会回退到会话实时推演、不再显示 00:00；此打点仅用于观察该窗口是否高频出现
            DebugLog.d("LockScreen", "phase 为 null，回退 session 推演 sessionEnd=${session.endMillis}")
        } else if (remaining <= 0L) {
            // 仅当已过本段结束 2 秒以上仍为 0 才算异常（tick 停更/phase 未推进）：
            // 正常到点瞬间（tick 慢 <1s 未切换）remaining 也会短暂归 0，需排除误报
            val now = System.currentTimeMillis()
            if (now - current.segmentEnd > 2000L) {
                DebugLog.d(
                    "LockScreen", "异常：remaining=0 phase=idx${current.index} " +
                        "segEnd=${current.segmentEnd} now=$now sessionEnd=${session.endMillis}"
                )
            }
        }
        return true
    }

    // 每秒刷新剩余时间与暂停应用数；会话被清理（服务已结束）时退出锁屏
    LaunchedEffect(Unit) {
        while (refresh()) delay(1000L)
        onFinished()
    }

    // 回到前台（小窗切全屏、系统解冻后恢复）立即重算一次：进程被冻结期间上面的 1 秒循环
    // 不会执行，界面会停在冻结前那一帧（实测卡在 00:06），须在恢复瞬间纠正
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    // 专注期间拦截返回键，禁止退出
    BackHandler { }

    // 全屏沉浸容器：铺满屏幕并自绘不透明背景，内容整体居中
    Box(
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(if (isRest) R.string.focus_rest_title else R.string.focus_lock_title),
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(24.dp))
            // 大号倒计时：title1 放大，等宽字体保证数字跳动时不抖动
            Text(
                text = FocusManager.countdownText(remaining),
                style = MiuixTheme.textStyles.title1.copy(fontSize = 56.sp),
                color = MiuixTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = context.resources.getQuantityString(R.plurals.focus_apps_paused, pausedCount, pausedCount),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
