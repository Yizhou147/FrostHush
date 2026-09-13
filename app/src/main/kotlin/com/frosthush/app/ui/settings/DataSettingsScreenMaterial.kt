package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.util.DebugLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出文件名时间戳：到秒。每次导出时实时生成——不能缓存（remember），
 * 否则设置页停留期间多次导出会得到同名文件。
 */
private fun exportTimeTag(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

/**
 * 数据二级页 · material 版：
 * 导出专注统计、导出/导入应用配置（SAF 文件选择）、导出诊断日志、清空统计。
 *
 * 业务逻辑与改造前的设置页一致，仅位置调整到本二级页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsMaterial(
    onBack: () -> Unit,
    onOpenConfigImport: (FocusStore.ConfigData) -> Unit,
) {
    val context = LocalContext.current
    var showClearStatsDialog by remember { mutableStateOf(false) }

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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_group_data)) },
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
                icon = Icons.Filled.FileDownload,
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
            // 导出诊断日志入口：收集计划时间不准等 bug 的关键事件日志（内存环形日志，手动导出）
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
}
