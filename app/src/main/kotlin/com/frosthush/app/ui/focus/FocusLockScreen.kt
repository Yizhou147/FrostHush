package com.frosthush.app.ui.focus

import androidx.compose.runtime.Composable
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 专注模式的全屏锁定倒计时界面（与雹专注模式一致）：
 * 不透明背景遮挡下层内容、消费所有触摸、拦截返回键，不可打断。
 * 分段专注时仅在专注段显示（休息段由 AppRoot 隐藏锁屏）；休息段到点自动恢复下一段专注。
 * 倒计时以 FocusService 发布的 phase 为唯一数据源（与超级岛同源），每秒刷新剩余时间；
 * 会话结束（phase 变 null / 会话被清理）时由 AppRoot 或本界面兜底解除锁定。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun FocusLockScreen(onFinished: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> FocusLockScreenMiuix(onFinished = onFinished)
        UiMode.Material -> FocusLockScreenMaterial(onFinished = onFinished)
    }
}
