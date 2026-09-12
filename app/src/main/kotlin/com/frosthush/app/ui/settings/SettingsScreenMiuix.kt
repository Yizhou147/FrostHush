package com.frosthush.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页（一级）· miuix 版（HyperOS 设计语言）：
 * 界面风格 / 主题设置，以及「专注设置」「计划与可靠性」「数据」三个分组入口。
 *
 * 各分组内的设置项已搬入对应的二级页（FocusSettingsScreen / PlanSettingsScreen / DataSettingsScreen）。
 */
@Composable
fun SettingsScreenMiuix(
    onOpenTheme: () -> Unit,
    onOpenFocusSettings: () -> Unit,
    onOpenPlanSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    val themeMode by SettingsStore.themeMode
        .collectAsState(initial = SettingsStore.cache.themeMode)

    val themeLabel = when (themeMode) {
        SettingsStore.THEME_LIGHT -> stringResource(R.string.settings_theme_light)
        SettingsStore.THEME_DARK -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_system)
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.tab_settings),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding)
                .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp + bottomInnerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 界面：仅保留「主题设置」（界面风格选项已在主题设置二级页内）
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_page_title),
                    summary = stringResource(R.string.settings_theme_summary, themeLabel),
                    startAction = { SettingIcon(MiuixIcons.Theme) },
                    onClick = onOpenTheme,
                )
            }
            // 分组入口：专注设置、计划与可靠性、数据
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_group_focus),
                    summary = stringResource(R.string.settings_group_focus_summary),
                    startAction = { SettingIcon(MiuixIcons.Timer) },
                    onClick = onOpenFocusSettings,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_group_plan),
                    summary = stringResource(R.string.settings_group_plan_summary),
                    startAction = { SettingIcon(MiuixIcons.Info) },
                    onClick = onOpenPlanSettings,
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_group_data),
                    summary = stringResource(R.string.settings_group_data_summary),
                    startAction = { SettingIcon(MiuixIcons.Folder) },
                    onClick = onOpenDataSettings,
                )
                ArrowPreference(
                    title = stringResource(R.string.about_check_update),
                    summary = stringResource(R.string.settings_update_entry_summary),
                    startAction = { SettingIcon(MiuixIcons.Update) },
                    onClick = onOpenUpdateSettings,
                )
            }
        }
    }
}

/** 设置项图标（跟随正文色，HyperOS 用黑色图标而非主题蓝；危险项可传入 error 色） */
@Composable
internal fun SettingIcon(icon: ImageVector, tint: Color = MiuixTheme.colorScheme.onSurfaceContainer) {
    Icon(icon, contentDescription = null, tint = tint)
}

/** 单选对话框（OverlayDialog + RadioButtonPreference 选项列表）；internal 供各二级页复用 */
@Composable
internal fun MiuixChoiceDialog(
    show: Boolean,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            options.forEachIndexed { index, label ->
                RadioButtonPreference(
                    title = label,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    insideMargin = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 通用确认对话框（OverlayDialog：标题 + 说明 + 取消/确认）；internal 供各二级页复用 */
@Composable
internal fun MiuixConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.action_confirm),
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
