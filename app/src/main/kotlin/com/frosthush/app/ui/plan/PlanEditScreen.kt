package com.frosthush.app.ui.plan

import androidx.compose.runtime.Composable
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 新建 / 编辑专注计划：
 * - 名称输入（必填）
 * - 开始/结束时间：时间选择器（24 小时制），结束可小于开始（跨天）
 * - 星期多选 + 工作日/周末/不重复快捷（不重复 = 空 weekdays，只执行一次）
 * - 绑定：切换「从应用集选择」或「直接选择应用」（复用 AppSelectScreen）
 * - 分段专注（中途休息）
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun PlanEditScreen(plan: FocusPlan?, onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> PlanEditScreenMiuix(plan, onBack)
        UiMode.Material -> PlanEditScreenMaterial(plan, onBack)
    }
}
