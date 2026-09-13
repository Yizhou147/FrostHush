package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.FileDownloads
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 导出文件名时间戳：到秒。每次导出时实时生成——不能缓存（remember），
 * 否则设置页停留期间多次导出会得到同名文件。
 */
private fun exportTimeTagMiuix(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

/**
 * 数据二级页 · miuix 版（HyperOS 设计语言）：
 * 导出专注统计 / 导出应用配置 / 导入应用配置 / 导出诊断日志 / 清空统计。
 *
 * 业务逻辑与 material 版逐字一致（Flow 订阅 / setter / SAF 导入导出 / Toast），
 * 仅组件 / 颜色 / 文字样式替换为 miuix。
 */
@Composable
fun DataSettingsMiuix(
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

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_group_data),
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
        ) {
            // 数据与维护：导出统计/配置、导入配置、导出诊断日志、清空统计
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_export_stats),
                    summary = stringResource(R.string.settings_export_stats_summary),
                    startAction = { SettingIcon(MiuixIcons.FileDownloads) },
                    onClick = { statsExportLauncher.launch("frosthush-stats-${exportTimeTagMiuix()}.json") },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_export_config),
                    summary = stringResource(R.string.settings_export_config_summary),
                    startAction = { SettingIcon(MiuixIcons.UploadCloud) },
                    onClick = { configExportLauncher.launch("frosthush-config-${exportTimeTagMiuix()}.json") },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_import_config),
                    summary = stringResource(R.string.settings_import_config_summary),
                    startAction = { SettingIcon(MiuixIcons.Import) },
                    onClick = { configImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
                // 导出诊断日志入口：收集计划时间不准等 bug 的关键事件日志（内存环形日志，手动导出）
                ArrowPreference(
                    title = stringResource(R.string.debug_export_log),
                    summary = stringResource(R.string.debug_export_log_summary),
                    startAction = { SettingIcon(MiuixIcons.Report) },
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
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_clear_stats),
                    summary = stringResource(R.string.settings_clear_stats_summary),
                    startAction = { SettingIcon(MiuixIcons.Delete, MiuixTheme.colorScheme.error) },
                    onClick = { showClearStatsDialog = true },
                )
            }
        }

        MiuixConfirmDialog(
            show = showClearStatsDialog,
            title = stringResource(R.string.settings_clear_stats),
            summary = stringResource(R.string.settings_clear_stats_confirm),
            onConfirm = {
                FocusStore.clearHistory()
                FocusManager.bumpVersion()
                showClearStatsDialog = false
                Toast.makeText(context, context.getString(R.string.settings_cleared), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearStatsDialog = false },
        )
    }
}
