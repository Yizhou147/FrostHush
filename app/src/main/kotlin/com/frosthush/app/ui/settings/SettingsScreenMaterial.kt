package com.frosthush.app.ui.settings

import android.app.AlarmManager
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.frosthush.app.focus.ShizukuManager
import rikka.shizuku.Shizuku

/**
 * 设置页（一级）· material 版：
 * 界面风格 / 主题设置，以及「专注设置」「计划与可靠性」「数据」三个分组入口。
 *
 * 各分组内的设置项已搬入对应的二级页（FocusSettingsScreen / PlanSettingsScreen / DataSettingsScreen）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenMaterial(
    onOpenTheme: () -> Unit,
    onOpenFocusSettings: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    onReplayWelcome: () -> Unit = {},
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    val themeMode by SettingsStore.themeMode
        .collectAsState(initial = SettingsStore.cache.themeMode)

    val focusIsland by SettingsStore.focusIslandEnabled
        .collectAsState(initial = SettingsStore.cache.focusIslandEnabled)
    val suspendFallback by SettingsStore.suspendFallbackMode
        .collectAsState(initial = SettingsStore.cache.suspendFallbackMode)
    var showReliabilityDialog by remember { mutableStateOf(false) }
    var showFallbackScopeDialog by remember { mutableStateOf(false) }
    var showFallbackWarning by remember { mutableStateOf(false) }
    var pendingFallbackMode by remember { mutableStateOf(SettingsStore.FALLBACK_OFF) }
    var restoreCount by remember { mutableStateOf(0) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

    val fallbackSummary = when (suspendFallback) {
        SettingsStore.FALLBACK_CLONE_ONLY -> stringResource(R.string.settings_force_freeze_summary_clone)
        SettingsStore.FALLBACK_ALL -> stringResource(R.string.settings_force_freeze_summary_all)
        else -> stringResource(R.string.settings_force_freeze_summary_off)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_settings)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomInnerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 界面：仅保留「主题设置」（界面风格选项已在主题设置二级页内）
            SettingCard(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.settings_theme_page_title),
                summary = stringResource(R.string.settings_theme_summary),
                onClick = onOpenTheme,
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            SettingCard(
                icon = Icons.Filled.Timer,
                title = stringResource(R.string.settings_group_focus),
                summary = stringResource(R.string.settings_group_focus_summary),
                onClick = onOpenFocusSettings,
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            SettingCard(
                icon = Icons.Filled.Cast,
                title = stringResource(R.string.settings_focus_island),
                summary = stringResource(R.string.settings_focus_island_summary),
                onClick = { SettingsStore.setFocusIslandEnabled(!focusIsland) },
                trailing = {
                    Switch(checked = focusIsland, onCheckedChange = { SettingsStore.setFocusIslandEnabled(it) })
                },
            )

            SettingCard(
                icon = Icons.Filled.Block,
                title = stringResource(R.string.settings_force_freeze),
                summary = fallbackSummary,
                onClick = { showFallbackScopeDialog = true },
                trailing = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )

            SettingCard(
                icon = Icons.Filled.VerifiedUser,
                title = stringResource(R.string.settings_plan_reliability),
                summary = stringResource(R.string.settings_plan_reliability_summary),
                onClick = { showReliabilityDialog = true },
                trailing = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )

            SettingCard(
                icon = Icons.Filled.LockOpen,
                title = stringResource(R.string.settings_restore_suspended),
                summary = stringResource(R.string.settings_restore_suspended_summary),
                onClick = { checkSuspended() },
                trailing = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )

            SettingCard(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.settings_group_data),
                summary = stringResource(R.string.settings_group_data_summary),
                onClick = onOpenDataSettings,
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            SettingCard(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(R.string.about_check_update),
                summary = stringResource(R.string.settings_update_entry_summary),
                onClick = onOpenUpdateSettings,
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            SettingCard(
                icon = Icons.Filled.Refresh,
                title = stringResource(R.string.settings_replay_welcome),
                summary = stringResource(R.string.settings_replay_welcome_summary),
                onClick = onReplayWelcome,
                trailing = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
    }

    PlanReliabilityDialog(show = showReliabilityDialog, onDismiss = { showReliabilityDialog = false })
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
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = suspendFallback == mode, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showFallbackScopeDialog = false
                                        if (mode == SettingsStore.FALLBACK_OFF) {
                                            SettingsStore.setSuspendFallbackMode(mode)
                                        } else if (suspendFallback != SettingsStore.FALLBACK_OFF) {
                                            SettingsStore.setSuspendFallbackMode(mode)
                                        } else {
                                            pendingFallbackMode = mode
                                            showFallbackWarning = true
                                        }
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFallbackScopeDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
                TextButton(onClick = { showFallbackWarning = false }) { Text(stringResource(R.string.action_cancel)) }
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
                            if (restored > 0) context.getString(R.string.focus_suspended_restored, restored)
                            else context.getString(R.string.focus_suspended_restore_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** 设置条目卡片（internal 供关于页复用，等高 64dp 统一规整） */
@Composable
internal fun SettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        // 固定行高保证所有设置项卡片等高，标题/摘要均单行省略，视觉规整
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(2.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 计划可靠性检查对话框：逐项检查省电豁免 / 精确闹钟 / 自启动 / Shizuku，
 * 未通过项提供跳转系统设置的入口；「重新检测」自增 key 触发重算。
 * internal：设置页（计划与可靠性二级页）与计划页（省电提醒横幅）复用。
 */
@Composable
internal fun PlanReliabilityDialogMaterial(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var checkKey by remember { mutableStateOf(0) }

    // 从系统设置页返回后自动重新检测（省电/精确闹钟/自启动跳转后无需手动点「重新检测」）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val batteryOk = remember(checkKey) { checkBatteryOptimization(context) }
    val exactAlarmOk = remember(checkKey) { checkExactAlarm(context) }
    val shizukuOk = remember(checkKey) { FocusManager.shizukuReady() }
    val allOk = batteryOk && exactAlarmOk && shizukuOk

    if (!show) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_rel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.plan_rel_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (allOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (allOk) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (allOk) R.string.plan_rel_all_ok else R.string.plan_rel_risk),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (allOk) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ReliabilityItem(
                    ok = batteryOk,
                    title = stringResource(R.string.plan_rel_battery),
                    desc = stringResource(
                        if (batteryOk) R.string.plan_rel_battery_ok else R.string.plan_rel_battery_fail
                    ),
                    actionLabel = if (batteryOk) null else stringResource(R.string.plan_rel_battery_action),
                    onAction = { openBatterySettings(context) },
                )
                ReliabilityItem(
                    ok = exactAlarmOk,
                    title = stringResource(R.string.plan_rel_exact_alarm),
                    desc = stringResource(
                        if (exactAlarmOk) R.string.plan_rel_exact_alarm_ok else R.string.plan_rel_exact_alarm_fail
                    ),
                    actionLabel = if (exactAlarmOk) null else stringResource(R.string.plan_rel_exact_alarm_action),
                    onAction = { openExactAlarmSettings(context) },
                )
                ReliabilityItem(
                    ok = null,
                    title = stringResource(R.string.plan_rel_autostart),
                    desc = stringResource(R.string.plan_rel_autostart_hint),
                    actionLabel = stringResource(R.string.plan_rel_autostart_action),
                    onAction = { openAutostartSettings(context) },
                )
                ReliabilityItem(
                    ok = shizukuOk,
                    title = stringResource(R.string.plan_rel_shizuku),
                    desc = stringResource(
                        if (shizukuOk) R.string.plan_rel_shizuku_ok else R.string.plan_rel_shizuku_fail
                    ),
                    actionLabel = if (shizukuOk) null else stringResource(R.string.plan_rel_shizuku_action),
                    onAction = {
                        // 未连接服务 → 打开 Shizuku 应用；已连接未授权 → 请求授权
                        if (!runCatching { !Shizuku.isPreV11() && Shizuku.pingBinder() }.getOrDefault(false)) {
                            ShizukuManager.openShizukuApp(context)
                        } else {
                            ShizukuManager.requestPermission()
                        }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { checkKey++ }) { Text(stringResource(R.string.plan_rel_retry)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 可靠性检查单项：ok=true 绿勾 / false 红叉 / null 中性（系统无法检测，如自启动） */
@Composable
private fun ReliabilityItem(
    ok: Boolean?,
    title: String,
    desc: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (ok) {
                true -> Icons.Filled.CheckCircle
                false -> Icons.Filled.Cancel
                null -> Icons.Filled.Info
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when (ok) {
                true -> Color(0xFF2E9E5B)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 电池优化是否豁免（豁免则深度休眠下闹钟不被延迟） */
internal fun checkBatteryOptimization(context: Context): Boolean = runCatching {
    context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
}.getOrDefault(true)

/** 精确闹钟是否可用（Manifest 已声明 USE_EXACT_ALARM，Android 13+ 安装即授） */
internal fun checkExactAlarm(context: Context): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    } else true
}.getOrDefault(true)

/** 跳转系统「电池优化」豁免申请页，失败回退豁免列表页 */
internal fun openBatterySettings(context: Context) {
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(request) }.isFailure) {
        runCatching { context.startActivity(list) }
    }
}

/** 跳转系统「精确闹钟」授权页（仅 Android 12+，低版本恒可用无需跳转） */
internal fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/**
 * 跳转本应用信息页：MIUI/HyperOS 的应用信息页内含「权限管理」「自启动」等入口。
 * 供欢迎页「读取已安装应用」手动授权引导等场景复用。
 */
internal fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * 跳转本应用信息页：MIUI/HyperOS 的应用信息页内含「自启动管理」入口，
 * 比直达安全中心自启动列表更通用、更贴近本应用上下文。
 */
internal fun openAutostartSettings(context: Context) = openAppSettings(context)
