package com.frosthush.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.frosthush.app.BuildConfig
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.ShizukuManager
import com.frosthush.app.util.DebugLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 导出文件名时间戳：到秒。每次导出时实时生成——不能缓存（remember），
 * 否则设置页停留期间多次导出会得到同名文件。
 */
private fun exportTimeTag(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

/**
 * 设置页：
 * - 默认专注时长（选择列表）
 * - 专注结束通知 / 小米超级岛开关
 * - 主题模式（跟随系统 / 浅色 / 深色）
 * - 开始前二次确认开关
 * - 数据：导出专注统计、导出/导入应用配置（SAF 文件选择）、清空统计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenConfigImport: (FocusStore.ConfigData) -> Unit) {
    val context = LocalContext.current
    val defaultMinutes by SettingsStore.defaultFocusMinutes
        .collectAsState(initial = SettingsStore.cache.defaultFocusMinutes)
    val defaultRestMinutes by SettingsStore.defaultRestMinutes
        .collectAsState(initial = SettingsStore.cache.defaultRestMinutes)
    val notifyFinish by SettingsStore.notifyFinishEnabled
        .collectAsState(initial = SettingsStore.cache.notifyFinishEnabled)
    val focusIsland by SettingsStore.focusIslandEnabled
        .collectAsState(initial = SettingsStore.cache.focusIslandEnabled)
    val themeMode by SettingsStore.themeMode
        .collectAsState(initial = SettingsStore.cache.themeMode)
    val confirmBeforeStart by SettingsStore.confirmBeforeStart
        .collectAsState(initial = SettingsStore.cache.confirmBeforeStart)
    val planRemindSeconds by SettingsStore.planRemindSeconds
        .collectAsState(initial = SettingsStore.cache.planRemindSeconds)
    var showDurationDialog by remember { mutableStateOf(false) }
    var showRestDurationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var showReliabilityDialog by remember { mutableStateOf(false) }
    var showRemindDialog by remember { mutableStateOf(false) }
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

    val themeLabel = when (themeMode) {
        SettingsStore.THEME_LIGHT -> stringResource(R.string.settings_theme_light)
        SettingsStore.THEME_DARK -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_system)
    }

    // 导出专注统计 → 系统文件选择器保存
    val statsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(FocusStore.exportStatsJson().toByteArray())
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.settings_export_success else R.string.settings_export_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 导出应用配置 → 系统文件选择器保存
    val configExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(FocusStore.exportConfigJson().toByteArray())
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.settings_export_success else R.string.settings_export_failed),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // 导入应用配置 → 系统文件选择器打开 → 解析校验后进入导入预览页
    val configImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
            }.getOrDefault("")
            val data = FocusStore.parseConfigJson(text)
            if (data == null) {
                Toast.makeText(context, context.getString(R.string.settings_import_failed), Toast.LENGTH_SHORT).show()
            } else {
                onOpenConfigImport(data)
            }
        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingCard(
                icon = Icons.Filled.Timer,
                title = stringResource(R.string.settings_default_duration),
                summary = stringResource(R.string.settings_default_duration_summary, defaultMinutes),
                onClick = { showDurationDialog = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.FreeBreakfast,
                title = stringResource(R.string.settings_default_rest_duration),
                summary = stringResource(R.string.settings_default_rest_duration_summary, defaultRestMinutes),
                onClick = { showRestDurationDialog = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.Alarm,
                title = stringResource(R.string.settings_plan_remind),
                summary = if (planRemindSeconds > 0) {
                    stringResource(R.string.settings_plan_remind_summary_seconds, planRemindSeconds)
                } else {
                    stringResource(R.string.settings_plan_remind_summary_none)
                },
                onClick = { showRemindDialog = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.Notifications,
                title = stringResource(R.string.settings_notify_finish),
                summary = stringResource(R.string.settings_notify_finish_summary),
                onClick = { SettingsStore.setNotifyFinishEnabled(!notifyFinish) },
                trailing = {
                    Switch(checked = notifyFinish, onCheckedChange = { SettingsStore.setNotifyFinishEnabled(it) })
                },
            )
            SettingCard(
                icon = Icons.Filled.Android,
                title = stringResource(R.string.settings_focus_island),
                summary = stringResource(R.string.settings_focus_island_summary),
                onClick = { SettingsStore.setFocusIslandEnabled(!focusIsland) },
                trailing = {
                    Switch(checked = focusIsland, onCheckedChange = { SettingsStore.setFocusIslandEnabled(it) })
                },
            )
            SettingCard(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.settings_theme),
                summary = stringResource(R.string.settings_theme_summary, themeLabel),
                onClick = { showThemeDialog = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
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
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.settings_export_stats),
                summary = stringResource(R.string.settings_export_stats_summary),
                onClick = { statsExportLauncher.launch("frosthush-stats-${exportTimeTag()}.json") },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.FileDownload,
                title = stringResource(R.string.settings_export_config),
                summary = stringResource(R.string.settings_export_config_summary),
                onClick = { configExportLauncher.launch("frosthush-config-${exportTimeTag()}.json") },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.FileOpen,
                title = stringResource(R.string.settings_import_config),
                summary = stringResource(R.string.settings_import_config_summary),
                onClick = { configImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
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
            // 导出诊断日志入口：仅 debug 构建显示（正式版不含），用于收集计划时间不准等 bug 的关键事件日志
            if (BuildConfig.DEBUG) {
                SettingCard(
                    icon = Icons.Filled.BugReport,
                    title = stringResource(R.string.debug_export_log),
                    summary = stringResource(R.string.debug_export_log_summary),
                    onClick = {
                        val result = DebugLog.export(context)
                        Toast.makeText(
                            context,
                            if (result != null) {
                                context.getString(R.string.debug_exported, result)
                            } else {
                                context.getString(R.string.debug_export_failed)
                            },
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    trailing = {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            SettingCard(
                icon = Icons.Filled.DeleteSweep,
                title = stringResource(R.string.settings_clear_stats),
                summary = stringResource(R.string.settings_clear_stats_summary),
                onClick = { showClearStatsDialog = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
    }

    if (showDurationDialog) {
        DurationDialog(
            title = stringResource(R.string.settings_default_duration),
            selected = defaultMinutes,
            onSelect = { SettingsStore.setDefaultFocusMinutes(it); showDurationDialog = false },
            onCancel = { showDurationDialog = false },
        )
    }
    if (showRestDurationDialog) {
        DurationDialog(
            title = stringResource(R.string.settings_default_rest_duration),
            selected = defaultRestMinutes,
            onSelect = { SettingsStore.setDefaultRestMinutes(it); showRestDurationDialog = false },
            onCancel = { showRestDurationDialog = false },
        )
    }
    if (showThemeDialog) {
        ThemeDialog(
            selected = themeMode,
            onSelect = { SettingsStore.setThemeMode(it) },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStatsDialog = false },
            title = { Text(stringResource(R.string.settings_clear_stats)) },
            text = { Text(stringResource(R.string.settings_clear_stats_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    FocusStore.clearHistory()
                    FocusManager.bumpVersion()
                    showClearStatsDialog = false
                    Toast.makeText(context, context.getString(R.string.settings_cleared), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearStatsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showReliabilityDialog) {
        PlanReliabilityDialog(onDismiss = { showReliabilityDialog = false })
    }
    if (showRemindDialog) {
        RemindSecondsDialog(
            selected = planRemindSeconds,
            onSelect = { SettingsStore.setPlanRemindSeconds(it); showRemindDialog = false },
            onCancel = { showRemindDialog = false },
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

/** 主题模式选择对话框 */
@Composable
private fun ThemeDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        SettingsStore.THEME_SYSTEM to stringResource(R.string.settings_theme_system),
        SettingsStore.THEME_LIGHT to stringResource(R.string.settings_theme_light),
        SettingsStore.THEME_DARK to stringResource(R.string.settings_theme_dark),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
        },
    )
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

/** 默认时长设置对话框（默认专注/休息时长共用）：数字输入（1-240 分钟） */
@Composable
private fun DurationDialog(
    title: String,
    selected: Int,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(selected.toString()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(3) },
                label = { Text(title) },
                suffix = { Text(stringResource(R.string.focus_time_unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = input.toIntOrNull()
                if (minutes != null && minutes in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES) {
                    onSelect(minutes)
                } else {
                    Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 计划开始前提醒秒数设置对话框：数字输入（0-3600，0 = 不提醒到点直接开始） */
@Composable
private fun RemindSecondsDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(selected.toString()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_plan_remind)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.settings_plan_remind)) },
                    suffix = { Text(stringResource(R.string.settings_plan_remind_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_plan_remind_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val seconds = input.toIntOrNull()
                if (seconds != null && seconds in SettingsStore.PLAN_REMIND_RANGE) {
                    onSelect(seconds)
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_plan_remind_invalid),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 计划可靠性检查对话框：逐项检查省电豁免 / 精确闹钟 / 自启动 / Shizuku，
 * 未通过项提供跳转系统设置的入口；「重新检测」自增 key 触发重算。
 * internal：设置页与计划页（省电提醒横幅）复用。
 */
@Composable
internal fun PlanReliabilityDialog(onDismiss: () -> Unit) {
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
private fun checkExactAlarm(context: Context): Boolean = runCatching {
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
private fun openExactAlarmSettings(context: Context) {
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
