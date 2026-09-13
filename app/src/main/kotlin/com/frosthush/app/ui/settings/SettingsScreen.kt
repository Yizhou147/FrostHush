package com.frosthush.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 设置页（一级）：界面风格 / 主题设置，以及「专注设置」「计划与可靠性」「数据」三个分组入口。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun SettingsScreen(
    onOpenTheme: () -> Unit,
    onOpenFocusSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onReplayWelcome: () -> Unit = {},
    onOpenUpdateSettings: () -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsScreenMiuix(
            onOpenTheme = onOpenTheme,
            onOpenFocusSettings = onOpenFocusSettings,
            onOpenDataSettings = onOpenDataSettings,
            onReplayWelcome = onReplayWelcome,
            onOpenUpdateSettings = onOpenUpdateSettings,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> SettingsScreenMaterial(
            onOpenTheme = onOpenTheme,
            onOpenFocusSettings = onOpenFocusSettings,
            onOpenDataSettings = onOpenDataSettings,
            onReplayWelcome = onReplayWelcome,
            onOpenUpdateSettings = onOpenUpdateSettings,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
