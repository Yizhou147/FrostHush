package com.frosthush.app.ui.focus

import androidx.compose.runtime.Composable
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 「快速专注」确认流程（长按图标菜单的快捷方式 / 桌面小部件格子点击后弹出）。
 *
 * 与普通专注完全同一套流程，按用户要求保持「连着两个框」：
 * ① 确认框（现在开始 N 分钟专注？）→ ② 计划冲突预判框（若有）→ ③ 二次确认警告 → 开始专注。
 * 业务逻辑放在 [com.frosthush.app.focus.QuickFocus] 里，两版 UI 只负责渲染。
 */
@Composable
fun QuickFocusDialog(minutes: Int, onDismiss: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> QuickFocusDialogMiuix(minutes, onDismiss)
        UiMode.Material -> QuickFocusDialogMaterial(minutes, onDismiss)
    }
}

/**
 * 确认流程的步骤（两版 UI 共用同一组常量）：没有独立的「确认框」——
 * 快速专注直接复用普通专注那条「（有冲突则）冲突预判 → 二次确认」路径，全程只确认一次。
 */
/** 已有专注进行中（只提示，不走确认流程） */
internal const val STEP_ALREADY_FOCUSING = 0
internal const val STEP_CONFLICT = 2
internal const val STEP_WARNING = 3
