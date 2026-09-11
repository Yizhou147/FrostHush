package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.util.DebugLog
import com.frosthush.app.ui.theme.UiMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 导出文件名时间戳：到秒。每次导出时实时生成——不能缓存（remember），
 * 否则设置页停留期间多次导出会得到同名文件。
 */
private fun exportTimeTag(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

/**
 * 设置页 · miuix 版（HyperOS 设计语言）：
 * 大标题顶栏随内容滚动收起 + 卡片分组；跳转项用 ArrowPreference，开关用 SwitchPreference，
 * 时长/提醒等输入用 OverlayDialog（内嵌 miuix TextField），确认与单选同理。
 *
 * 业务逻辑与 material 版逐字一致（Flow 订阅 / setter / SAF 导入导出 / 暂停恢复检测 /
 * Toast / 电池优化与精确闹钟跳转），仅组件 / 颜色 / 文字样式替换为 miuix。
 */
@Composable
fun SettingsScreenMiuix(
    onOpenConfigImport: (FocusStore.ConfigData) -> Unit,
    onOpenTheme: () -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
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
    val uiModeValue by SettingsStore.uiMode
        .collectAsState(initial = SettingsStore.cache.uiMode)
    val confirmBeforeStart by SettingsStore.confirmBeforeStart
        .collectAsState(initial = SettingsStore.cache.confirmBeforeStart)
    val planRemindSeconds by SettingsStore.planRemindSeconds
        .collectAsState(initial = SettingsStore.cache.planRemindSeconds)
    val suspendFallback by SettingsStore.suspendFallbackMode
        .collectAsState(initial = SettingsStore.cache.suspendFallbackMode)
    var showDurationDialog by remember { mutableStateOf(false) }
    var showRestDurationDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var showReliabilityDialog by remember { mutableStateOf(false) }
    var showRemindDialog by remember { mutableStateOf(false) }
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

    val themeLabel = when (themeMode) {
        SettingsStore.THEME_LIGHT -> stringResource(R.string.settings_theme_light)
        SettingsStore.THEME_DARK -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_system)
    }
    // 界面风格当前值文案（miuix / material）
    val styleLabel = if (UiMode.fromValue(uiModeValue) == UiMode.Miuix) {
        stringResource(R.string.settings_ui_style_miuix)
    } else {
        stringResource(R.string.settings_ui_style_material)
    }

    // 强制冻结当前范围文案
    val fallbackSummary = when (suspendFallback) {
        SettingsStore.FALLBACK_CLONE_ONLY -> stringResource(R.string.settings_force_freeze_summary_clone)
        SettingsStore.FALLBACK_ALL -> stringResource(R.string.settings_force_freeze_summary_all)
        else -> stringResource(R.string.settings_force_freeze_summary_off)
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
            // 专注：默认专注/休息时长、计划提前提醒
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_default_duration),
                    summary = stringResource(R.string.settings_default_duration_summary, defaultMinutes),
                    startAction = { SettingIcon(MiuixIcons.Timer) },
                    onClick = { showDurationDialog = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_default_rest_duration),
                    summary = stringResource(R.string.settings_default_rest_duration_summary, defaultRestMinutes),
                    startAction = { SettingIcon(MiuixIcons.Recent) },
                    onClick = { showRestDurationDialog = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_plan_remind),
                    summary = if (planRemindSeconds > 0) {
                        stringResource(R.string.settings_plan_remind_summary_seconds, planRemindSeconds)
                    } else {
                        stringResource(R.string.settings_plan_remind_summary_none)
                    },
                    startAction = { SettingIcon(MiuixIcons.Alarm) },
                    onClick = { showRemindDialog = true },
                )
            }
            // 通知与界面：结束通知、超级岛、界面风格、主题
            Card {
                SwitchPreference(
                    checked = notifyFinish,
                    onCheckedChange = { SettingsStore.setNotifyFinishEnabled(it) },
                    title = stringResource(R.string.settings_notify_finish),
                    summary = stringResource(R.string.settings_notify_finish_summary),
                    startAction = { SettingIcon(MiuixIcons.Promotions) },
                )
                SwitchPreference(
                    checked = focusIsland,
                    onCheckedChange = { SettingsStore.setFocusIslandEnabled(it) },
                    title = stringResource(R.string.settings_focus_island),
                    summary = stringResource(R.string.settings_focus_island_summary),
                    startAction = { SettingIcon(MiuixIcons.ScreenMirroring) },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_ui_style),
                    summary = stringResource(R.string.settings_ui_style_summary, styleLabel),
                    startAction = { SettingIcon(MiuixIcons.Tune) },
                    onClick = { showStyleDialog = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_page_title),
                    summary = stringResource(R.string.settings_theme_summary, themeLabel),
                    startAction = { SettingIcon(MiuixIcons.Theme) },
                    onClick = onOpenTheme,
                )
            }
            // 行为与可靠性：开始前二次确认、强制冻结、计划可靠性
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
            }
            // 数据与维护：导出统计/配置、导入配置、恢复被暂停应用、导出诊断日志、清空统计
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_export_stats),
                    summary = stringResource(R.string.settings_export_stats_summary),
                    startAction = { SettingIcon(MiuixIcons.Tasks) },
                    onClick = { statsExportLauncher.launch("frosthush-stats-${exportTimeTag()}.json") },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_export_config),
                    summary = stringResource(R.string.settings_export_config_summary),
                    startAction = { SettingIcon(MiuixIcons.UploadCloud) },
                    onClick = { configExportLauncher.launch("frosthush-config-${exportTimeTag()}.json") },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_import_config),
                    summary = stringResource(R.string.settings_import_config_summary),
                    startAction = { SettingIcon(MiuixIcons.Import) },
                    onClick = { configImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_restore_suspended),
                    summary = stringResource(R.string.settings_restore_suspended_summary),
                    startAction = { SettingIcon(MiuixIcons.Unlock) },
                    onClick = { checkSuspended() },
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

        // ===== 对话框（miuix OverlayDialog，置于 Scaffold 内容内由 popup host 渲染）=====
        MiuixDurationDialog(
            show = showDurationDialog,
            title = stringResource(R.string.settings_default_duration),
            selected = defaultMinutes,
            unit = stringResource(R.string.focus_time_unit),
            range = FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES,
            invalidHint = stringResource(R.string.focus_time_invalid),
            onSelect = { SettingsStore.setDefaultFocusMinutes(it); showDurationDialog = false },
            onDismiss = { showDurationDialog = false },
        )
        MiuixDurationDialog(
            show = showRestDurationDialog,
            title = stringResource(R.string.settings_default_rest_duration),
            selected = defaultRestMinutes,
            unit = stringResource(R.string.focus_time_unit),
            range = FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES,
            invalidHint = stringResource(R.string.focus_time_invalid),
            onSelect = { SettingsStore.setDefaultRestMinutes(it); showRestDurationDialog = false },
            onDismiss = { showRestDurationDialog = false },
        )
        MiuixRemindDialog(
            show = showRemindDialog,
            selected = planRemindSeconds,
            onSelect = { SettingsStore.setPlanRemindSeconds(it); showRemindDialog = false },
            onDismiss = { showRemindDialog = false },
        )
        MiuixChoiceDialog(
            show = showStyleDialog,
            title = stringResource(R.string.settings_ui_style),
            options = listOf(
                stringResource(R.string.settings_ui_style_miuix),
                stringResource(R.string.settings_ui_style_material),
            ),
            selectedIndex = if (UiMode.fromValue(uiModeValue) == UiMode.Miuix) 0 else 1,
            onSelect = {
                SettingsStore.setUiMode(if (it == 0) SettingsStore.UI_MODE_MIUIX else SettingsStore.UI_MODE_MATERIAL)
                showStyleDialog = false
            },
            onDismiss = { showStyleDialog = false },
        )
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
        // 计划可靠性检查沿用 material3 AlertDialog（与计划页共用同一实现）
        if (showReliabilityDialog) {
            PlanReliabilityDialog(onDismiss = { showReliabilityDialog = false })
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

/** 设置项图标（统一 primary 色；危险项可传入 error 色） */
@Composable
private fun SettingIcon(icon: ImageVector, tint: Color = MiuixTheme.colorScheme.primary) {
    Icon(icon, contentDescription = null, tint = tint)
}

/** 默认时长设置对话框：数字输入（range 分钟），确认时校验范围，越界 Toast 提示 */
@Composable
private fun MiuixDurationDialog(
    show: Boolean,
    title: String,
    selected: Int,
    unit: String,
    range: IntRange,
    invalidHint: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember(show) { mutableStateOf(selected.toString()) }
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(3) },
                label = title,
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    Text(
                        text = unit,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        val minutes = input.toIntOrNull()
                        if (minutes != null && minutes in range) {
                            onSelect(minutes)
                        } else {
                            Toast.makeText(context, invalidHint, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 计划开始前提醒秒数对话框：数字输入（0-3600，0 = 不提醒到点直接开始） */
@Composable
private fun MiuixRemindDialog(
    show: Boolean,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember(show) { mutableStateOf(selected.toString()) }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.settings_plan_remind),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = stringResource(R.string.settings_plan_remind),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    Text(
                        text = stringResource(R.string.settings_plan_remind_unit),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_plan_remind_dialog_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
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
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 单选对话框（OverlayDialog + RadioButtonPreference 选项列表） */
@Composable
private fun MiuixChoiceDialog(
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

/** 通用确认对话框（OverlayDialog：标题 + 说明 + 取消/确认） */
@Composable
private fun MiuixConfirmDialog(
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
