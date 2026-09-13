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
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置页（一级）· miuix 版（HyperOS 设计语言）：
 * 外观（主题设置 / 重新查看引导）、页面入口（专注设置 / 数据 / 检查更新）、
 * 冻结与可靠性（强制冻结 / 计划可靠性 / 恢复被暂停应用）、小米超级岛。
 */
@Composable
fun SettingsScreenMiuix(
    onOpenTheme: () -> Unit,
    onOpenFocusSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onReplayWelcome: () -> Unit = {},
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    val focusIsland by SettingsStore.focusIslandEnabled
        .collectAsState(initial = SettingsStore.cache.focusIslandEnabled)
    val suspendFallback by SettingsStore.suspendFallbackMode
        .collectAsState(initial = SettingsStore.cache.suspendFallbackMode)
    var showReliabilityDialog by remember { mutableStateOf(false) }
    // 强制冻结：范围选择对话框 + 首次开启的副作用警告
    var showFallbackScopeDialog by remember { mutableStateOf(false) }
    var showFallbackWarning by remember { mutableStateOf(false) }
    var pendingFallbackMode by remember { mutableStateOf(SettingsStore.FALLBACK_OFF) }
    // 恢复被暂停应用：检测到的仍暂停数量 + 确认对话框
    var restoreCount by remember { mutableStateOf(0) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** 检测是否有应用仍被暂停（Shizuku 崩溃等导致专注结束后未能解冻），有则弹确认 */
    fun checkSuspended() {
        scope.launch {
            val n = withContext(Dispatchers.Default) { FocusManager.suspendedEntries().size }
            if (n == 0) {
                Toast.makeText(context, context.getString(R.string.settings_restore_suspended_none), Toast.LENGTH_SHORT).show()
            } else {
                restoreCount = n
                showRestoreConfirm = true
            }
        }
    }

    // 强制冻结当前范围文案
    val fallbackSummary = when (suspendFallback) {
        SettingsStore.FALLBACK_CLONE_ONLY -> stringResource(R.string.settings_force_freeze_summary_clone)
        SettingsStore.FALLBACK_ALL -> stringResource(R.string.settings_force_freeze_summary_all)
        else -> stringResource(R.string.settings_force_freeze_summary_off)
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
            // 外观：主题设置
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_page_title),
                    summary = stringResource(R.string.settings_theme_summary),
                    startAction = { SettingIcon(MiuixIcons.Theme) },
                    onClick = onOpenTheme,
                )
            }
            // 专注：专注设置入口 + 小米超级岛
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_group_focus),
                    summary = stringResource(R.string.settings_group_focus_summary),
                    startAction = { SettingIcon(MiuixIcons.Timer) },
                    onClick = onOpenFocusSettings,
                )
                SwitchPreference(
                    checked = focusIsland,
                    onCheckedChange = { SettingsStore.setFocusIslandEnabled(it) },
                    title = stringResource(R.string.settings_focus_island),
                    summary = stringResource(R.string.settings_focus_island_summary),
                    startAction = { SettingIcon(MiuixIcons.ScreenMirroring) },
                )
            }
            // 冻结与可靠性（原「计划与可靠性」二级页拆出）
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_force_freeze),
                    summary = fallbackSummary,
                    startAction = { SettingIcon(MiuixIcons.Blocklist) },
                    onClick = { showFallbackScopeDialog = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_plan_reliability),
                    summary = stringResource(R.string.settings_plan_reliability_summary),
                    startAction = { SettingIcon(MiuixIcons.Info) },
                    onClick = { showReliabilityDialog = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_restore_suspended),
                    summary = stringResource(R.string.settings_restore_suspended_summary),
                    startAction = { SettingIcon(MiuixIcons.Unlock) },
                    onClick = { checkSuspended() },
                )
            }
            // 底部：数据 / 检查更新 / 重新查看引导
            Card {
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
                ArrowPreference(
                    title = stringResource(R.string.settings_replay_welcome),
                    summary = stringResource(R.string.settings_replay_welcome_summary),
                    startAction = { SettingIcon(MiuixIcons.Refresh) },
                    onClick = onReplayWelcome,
                )
            }
        }
    }

    // 强制冻结：范围选择
    MiuixChoiceDialog(
        show = showFallbackScopeDialog,
        title = stringResource(R.string.settings_force_freeze_scope_title),
        options = listOf(
            stringResource(R.string.settings_force_freeze_scope_off),
            stringResource(R.string.settings_force_freeze_scope_clone),
            stringResource(R.string.settings_force_freeze_scope_all),
        ),
        selectedIndex = when (suspendFallback) {
            SettingsStore.FALLBACK_CLONE_ONLY -> 1
            SettingsStore.FALLBACK_ALL -> 2
            else -> 0
        },
        onSelect = { index ->
            val mode = when (index) {
                1 -> SettingsStore.FALLBACK_CLONE_ONLY
                2 -> SettingsStore.FALLBACK_ALL
                else -> SettingsStore.FALLBACK_OFF
            }
            showFallbackScopeDialog = false
            if (mode == SettingsStore.FALLBACK_OFF) {
                SettingsStore.setSuspendFallbackMode(mode)
            } else if (suspendFallback != SettingsStore.FALLBACK_OFF) {
                // 已开启：直接切换范围
                SettingsStore.setSuspendFallbackMode(mode)
            } else {
                // 从关闭首次开启：先弹副作用警告，确认后才生效
                pendingFallbackMode = mode
                showFallbackWarning = true
            }
        },
        onDismiss = { showFallbackScopeDialog = false },
    )
    MiuixConfirmDialog(
        show = showFallbackWarning,
        title = stringResource(R.string.settings_force_freeze_warning_title),
        summary = stringResource(R.string.settings_force_freeze_warning_text),
        onConfirm = {
            showFallbackWarning = false
            SettingsStore.setSuspendFallbackMode(pendingFallbackMode)
        },
        onDismiss = { showFallbackWarning = false },
    )
    // 计划可靠性检查：按 UiMode 分派（miuix 为 OverlayDialog 版，material 为 AlertDialog 版）
    PlanReliabilityDialog(show = showReliabilityDialog, onDismiss = { showReliabilityDialog = false })
    MiuixConfirmDialog(
        show = showRestoreConfirm,
        title = stringResource(R.string.settings_restore_suspended),
        summary = stringResource(R.string.settings_restore_suspended_confirm, restoreCount),
        onConfirm = {
            showRestoreConfirm = false
            scope.launch {
                val restored = withContext(Dispatchers.Default) { FocusManager.restoreSuspendedApps() }
                Toast.makeText(
                    context,
                    if (restored > 0) {
                        context.getString(R.string.focus_suspended_restored, restored)
                    } else {
                        context.getString(R.string.focus_suspended_restore_failed)
                    },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        },
        onDismiss = { showRestoreConfirm = false },
    )
}

/** 设置项图标（跟随正文色，HyperOS 用黑色图标而非主题蓝；危险项可传入 error 色） */
@Composable
internal fun SettingIcon(icon: ImageVector, tint: Color = MiuixTheme.colorScheme.onSurfaceContainer) {
    Icon(
        icon,
        contentDescription = null,
        tint = tint,
        // 图标与右侧文字保持 HyperOS 间距（默认过近）
        modifier = Modifier.padding(end = 12.dp),
    )
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
