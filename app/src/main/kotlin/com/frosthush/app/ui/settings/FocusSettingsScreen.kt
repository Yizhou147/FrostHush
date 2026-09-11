package com.frosthush.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.theme.UiMode

/**
 * 专注设置二级页：默认专注时长 / 默认休息时长 / 计划开始前提醒 / 专注结束通知 / 小米超级岛。
 *
 * 与全项目一致的「双 UI」结构：按当前 [UiMode] 分派到 miuix 版或 material 版实现。
 */
@Composable
fun FocusSettingsScreen(onBack: () -> Unit) {
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)
    when (UiMode.fromValue(uiModeValue)) {
        UiMode.Miuix -> FocusSettingsMiuix(onBack = onBack)
        UiMode.Material -> FocusSettingsMaterial(onBack = onBack)
    }
}
