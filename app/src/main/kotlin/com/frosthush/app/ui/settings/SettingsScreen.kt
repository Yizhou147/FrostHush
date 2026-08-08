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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
fun SettingsScreen() {
    val context = LocalContext.current
    val defaultMinutes by SettingsStore.defaultFocusMinutes
        .collectAsState(initial = SettingsStore.cache.defaultFocusMinutes)
    val notifyFinish by SettingsStore.notifyFinishEnabled
        .collectAsState(initial = SettingsStore.cache.notifyFinishEnabled)
    val focusIsland by SettingsStore.focusIslandEnabled
        .collectAsState(initial = SettingsStore.cache.focusIslandEnabled)
    val themeMode by SettingsStore.themeMode
        .collectAsState(initial = SettingsStore.cache.themeMode)
    val confirmBeforeStart by SettingsStore.confirmBeforeStart
        .collectAsState(initial = SettingsStore.cache.confirmBeforeStart)
    var showDurationDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }

    val themeLabel = when (themeMode) {
        SettingsStore.THEME_LIGHT -> stringResource(R.string.settings_theme_light)
        SettingsStore.THEME_DARK -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_system)
    }
    val dateTag = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }

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

    // 导入应用配置 → 系统文件选择器打开
    val configImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                FocusStore.importConfigJson(text)
            }.getOrDefault(false)
            if (ok) FocusManager.bumpVersion() // 刷新应用集 / 计划页
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.settings_import_success else R.string.settings_import_failed),
                Toast.LENGTH_SHORT,
            ).show()
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
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.settings_export_stats),
                summary = stringResource(R.string.settings_export_stats_summary),
                onClick = { statsExportLauncher.launch("frosthush-stats-$dateTag.json") },
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
                onClick = { configExportLauncher.launch("frosthush-config-$dateTag.json") },
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
                onClick = { confirmImport = true },
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
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
            selected = defaultMinutes,
            onSelect = { SettingsStore.setDefaultFocusMinutes(it); showDurationDialog = false },
            onCancel = { showDurationDialog = false },
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
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.settings_import_config)) },
            text = { Text(stringResource(R.string.settings_import_config_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    configImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
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

/** 默认专注时长设置对话框：数字输入（1-240 分钟） */
@Composable
private fun DurationDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(selected.toString()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_default_duration)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(3) },
                label = { Text(stringResource(R.string.focus_time_label)) },
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
