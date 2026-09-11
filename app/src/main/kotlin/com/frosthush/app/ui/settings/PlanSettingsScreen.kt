package com.frosthush.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.theme.UiMode

/**
 * 计划与可靠性二级页：开始前二次确认 / 强制冻结（范围与副作用警告）/ 计划可靠性检查 / 恢复被暂停应用。
 *
 * 与全项目一致的「双 UI」结构：按当前 [UiMode] 分派到 miuix 版或 material 版实现。
 */
@Composable
fun PlanSettingsScreen(onBack: () -> Unit) {
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)
    when (UiMode.fromValue(uiModeValue)) {
        UiMode.Miuix -> PlanSettingsMiuix(onBack = onBack)
        UiMode.Material -> PlanSettingsMaterial(onBack = onBack)
    }
}
