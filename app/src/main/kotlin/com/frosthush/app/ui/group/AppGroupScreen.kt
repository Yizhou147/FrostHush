package com.frosthush.app.ui.group

import androidx.compose.runtime.Composable
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 应用集管理页：
 * - 列表：集名、条目数、是否默认；非默认集右侧「设为默认」
 * - 右上角 + 新建、选择键进入多选删除（复用专注页多选交互模式）
 * - 删除默认集后黑名单自动回退为空集；引用该集的计划回退到默认集
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun AppGroupScreen(onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> AppGroupScreenMiuix(onBack = onBack)
        UiMode.Material -> AppGroupScreenMaterial(onBack = onBack)
    }
}
