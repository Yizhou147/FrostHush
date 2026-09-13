package com.frosthush.app.ui

import androidx.compose.runtime.Composable
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 通用应用选择页（应用集编辑 / 计划直选复用，逻辑与导入页一致）：
 * 搜索（名称/包名/拼音）+ 分类筛选（用户/系统/双开应用）+ 勾选多应用，
 * 条目为 包名 或 包名@userId（分身）。确认时通过 onDone 回调返回选中条目。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 * 公开签名保持不变，供 PlanEditScreen 与 AppGroupScreen 调用。
 */
@Composable
fun AppSelectScreen(
    initial: Set<String>,
    onBack: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> AppSelectScreenMiuix(initial = initial, onBack = onBack, onDone = onDone)
        UiMode.Material -> AppSelectScreenMaterial(initial = initial, onBack = onBack, onDone = onDone)
    }
}
