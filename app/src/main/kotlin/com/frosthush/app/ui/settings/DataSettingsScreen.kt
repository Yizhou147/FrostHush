package com.frosthush.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.theme.UiMode

/**
 * 数据二级页：导出专注统计 / 导出应用配置 / 导入应用配置 / 导出诊断日志 / 清空统计。
 *
 * 与全项目一致的「双 UI」结构：按当前 [UiMode] 分派到 miuix 版或 material 版实现。
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    onOpenConfigImport: (FocusStore.ConfigData) -> Unit,
) {
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)
    when (UiMode.fromValue(uiModeValue)) {
        UiMode.Miuix -> DataSettingsMiuix(onBack = onBack, onOpenConfigImport = onOpenConfigImport)
        UiMode.Material -> DataSettingsMaterial(onBack = onBack, onOpenConfigImport = onOpenConfigImport)
    }
}
