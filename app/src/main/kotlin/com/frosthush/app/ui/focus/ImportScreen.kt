package com.frosthush.app.ui.focus

import androidx.compose.runtime.Composable
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 导入页：FilterChip 切换「手动导入 / 剪贴板导入」。
 * 手动导入：应用列表 + 搜索（名称/包名/拼音）+ 分类筛选（全部/用户/系统）+ 全选/清空 + 已选计数。
 * 剪贴板导入：解析剪贴板包名列表，默认全选。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun ImportScreen(onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ImportScreenMiuix(onBack = onBack)
        UiMode.Material -> ImportScreenMaterial(onBack = onBack)
    }
}
