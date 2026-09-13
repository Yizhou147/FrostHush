package com.frosthush.app.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 专注页（首页）：
 * - 顶部累计专注时长条（点击进统计页，无记录时隐藏）
 * - 已选应用列表（导入/移除）
 * - FAB「开始专注」：时长选择 → 警告 → 开始
 * - 专注进行中：剩余时间 + 已暂停应用数，无任何退出入口
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun FocusScreen(
    onOpenStats: () -> Unit,
    onImport: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡（内容仍可从底栏下方穿过） */
    bottomInnerPadding: Dp = 0.dp,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> FocusScreenMiuix(
            onOpenStats = onOpenStats,
            onImport = onImport,
            onOpenGroups = onOpenGroups,
            onOpenSettings = onOpenSettings,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> FocusScreenMaterial(
            onOpenStats = onOpenStats,
            onImport = onImport,
            onOpenGroups = onOpenGroups,
            onOpenSettings = onOpenSettings,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
