package com.frosthush.app.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 专注计划页：
 * - 列表项：计划名、时间段（跨天显示次日）、星期徽标（执行日高亮）、绑定应用集名/直选数、启用 Switch
 * - 右上角 多选/新建、长按进入多选删除、多选模式下长按拖拽排序
 * - 点击项进入编辑页；空状态引导新建
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun PlanScreen(
    onNewPlan: () -> Unit,
    onEditPlan: (FocusPlan) -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> PlanScreenMiuix(onNewPlan, onEditPlan, bottomInnerPadding)
        UiMode.Material -> PlanScreenMaterial(onNewPlan, onEditPlan, bottomInnerPadding)
    }
}
