package com.frosthush.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.data.FocusStore
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 设置页：默认时长 / 通知与超级岛 / 主题与界面风格 / 二次确认 / 强制冻结与可靠性 /
 * 数据导出导入与清空统计。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun SettingsScreen(
    onOpenConfigImport: (FocusStore.ConfigData) -> Unit,
    onOpenTheme: () -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> SettingsScreenMiuix(onOpenConfigImport, onOpenTheme, bottomInnerPadding)
        UiMode.Material -> SettingsScreenMaterial(onOpenConfigImport, onOpenTheme, bottomInnerPadding)
    }
}
