package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 计划与可靠性二级页 · material 版：
 * 开始前二次确认、强制冻结（范围选择 + 首次开启副作用警告）、计划可靠性检查、恢复被暂停应用。
 *
 * 业务逻辑与改造前的设置页一致，仅位置调整到本二级页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSettingsMaterial(onBack: () -> Unit) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_group_plan)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingCard(
                icon = Icons.Filled.Security,
                title = stringResource(R.string.settings_confirm_before_start),
                summary = stringResource(R.string.settings_confirm_before_start_summary),
                onClick = { SettingsStore.setConfirmBeforeStart(!confirmBeforeStart) },
                trailing = {
                    Switch(checked = confirmBeforeStart, onCheckedChange = { SettingsStore.setConfirmBeforeStart(it) })
                },
            )
            SettingCard(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.settings_force_freeze),
                summary = fallbackSummary,
                onClick = { showFallbackScopeDialog = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.VerifiedUser,
                title = stringResource(R.string.settings_plan_reliability),
                summary = stringResource(R.string.settings_plan_reliability_summary),
                onClick = { showReliabilityDialog = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.LockOpen,
                title = stringResource(R.string.settings_restore_suspended),
                summary = stringResource(R.string.settings_restore_suspended_summary),
                onClick = { checkSuspended() },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }

    if (showReliabilityDialog) {
        PlanReliabilityDialog(onDismiss = { showReliabilityDialog = false })
    }
    if (showFallbackScopeDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackScopeDialog = false },
            title = { Text(stringResource(R.string.settings_force_freeze_scope_title)) },
            text = {
                Column {
                    listOf(
                        SettingsStore.FALLBACK_OFF to stringResource(R.string.settings_force_freeze_scope_off),
                        SettingsStore.FALLBACK_CLONE_ONLY to stringResource(R.string.settings_force_freeze_scope_clone),
                        SettingsStore.FALLBACK_ALL to stringResource(R.string.settings_force_freeze_scope_all),
                    ).forEach { (mode, label) ->
                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
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
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFallbackScopeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showFallbackWarning) {
        AlertDialog(
            onDismissRequest = { showFallbackWarning = false },
            title = { Text(stringResource(R.string.settings_force_freeze_warning_title)) },
            text = { Text(stringResource(R.string.settings_force_freeze_warning_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showFallbackWarning = false
                    SettingsStore.setSuspendFallbackMode(pendingFallbackMode)
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFallbackWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.settings_restore_suspended)) },
            text = { Text(stringResource(R.string.settings_restore_suspended_confirm, restoreCount)) },
            confirmButton = {
                TextButton(onClick = {
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
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
