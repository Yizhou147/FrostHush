package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 计划与可靠性二级页 · miuix 版（HyperOS 设计语言）：
 * 开始前二次确认、强制冻结（范围选择 + 首次开启副作用警告）、计划可靠性检查、恢复被暂停应用。
 *
 * 业务逻辑与 material 版逐字一致（Flow 订阅 / setter / 暂停恢复检测 / Toast），
 * 仅组件 / 颜色 / 文字样式替换为 miuix；可靠性检查对话框沿用 material3 实现（与计划页共用）。
 */
@Composable
fun PlanSettingsMiuix(onBack: () -> Unit) {
    val context = LocalContext.current
    val confirmBeforeStart by SettingsStore.confirmBeforeStart
        .collectAsState(initial = SettingsStore.cache.confirmBeforeStart)
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
                title = stringResource(R.string.settings_group_plan),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
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
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 行为与可靠性：开始前二次确认、强制冻结、计划可靠性、恢复被暂停应用
            Card {
                SwitchPreference(
                    checked = confirmBeforeStart,
                    onCheckedChange = { SettingsStore.setConfirmBeforeStart(it) },
                    title = stringResource(R.string.settings_confirm_before_start),
                    summary = stringResource(R.string.settings_confirm_before_start_summary),
                    startAction = { SettingIcon(MiuixIcons.Lock) },
                )
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
        }

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
        // 计划可靠性检查沿用 material3 AlertDialog（与计划页共用同一实现）
        if (showReliabilityDialog) {
            PlanReliabilityDialog(onDismiss = { showReliabilityDialog = false })
        }
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
}
