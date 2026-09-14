package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.QuickFocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusWidgetProvider
import com.frosthush.app.focus.QuickFocus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 专注设置二级页 · miuix 版（HyperOS 设计语言）：
 * 默认专注/休息时长、计划开始前提醒、专注结束通知、小米超级岛。
 *
 * 业务逻辑与 material 版逐字一致（Flow 订阅 / setter / Toast），仅组件 / 颜色 / 文字样式替换为 miuix。
 */
@Composable
fun FocusSettingsMiuix(onBack: () -> Unit) {
    val defaultMinutes by SettingsStore.defaultFocusMinutes
        .collectAsState(initial = SettingsStore.cache.defaultFocusMinutes)
    val defaultRestMinutes by SettingsStore.defaultRestMinutes
        .collectAsState(initial = SettingsStore.cache.defaultRestMinutes)
    val notifyFinish by SettingsStore.notifyFinishEnabled
        .collectAsState(initial = SettingsStore.cache.notifyFinishEnabled)
    val planRemindSeconds by SettingsStore.planRemindSeconds
        .collectAsState(initial = SettingsStore.cache.planRemindSeconds)
    var showDurationDialog by remember { mutableStateOf(false) }
    var showRestDurationDialog by remember { mutableStateOf(false) }
    var showRemindDialog by remember { mutableStateOf(false) }
    // 正在编辑的快速专注快捷方式（null = 未打开编辑框）；列表非可观察，用版本号触发重组
    var editingShortcut by remember { mutableStateOf<QuickFocusStore.QuickShortcut?>(null) }
    // 小部件时长编辑（两组：2×2 三格 / 宽版五格）
    var editingWidgetSmall by remember { mutableStateOf(false) }
    var editingWidgetWide by remember { mutableStateOf(false) }
    var widgetVersion by remember { mutableStateOf(0) }
    var quickVersion by remember { mutableStateOf(0) }
    val quickShortcuts = remember(quickVersion) { QuickFocusStore.shortcuts.toList() }
    val context = LocalContext.current

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_group_focus),
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
            // 通知：结束通知、超级岛
            Card {
                SwitchPreference(
                    checked = notifyFinish,
                    onCheckedChange = { SettingsStore.setNotifyFinishEnabled(it) },
                    title = stringResource(R.string.settings_notify_finish),
                    summary = stringResource(R.string.settings_notify_finish_summary),
                    startAction = { SettingIcon(MiuixIcons.Promotions) },
                )
            }
            // 小部件时长：格子内容（2×2 三格 / 2×3、2×4 五格），改完立即刷新已添加的小部件
            Text(
                stringResource(R.string.settings_widget_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            val widgetSmallMinutes = remember(widgetVersion) { QuickFocusStore.widgetSmallMinutes() }
            val widgetWideMinutes = remember(widgetVersion) { QuickFocusStore.widgetWideMinutes() }
            Card {
                ArrowPreference(
                    title = stringResource(R.string.settings_widget_small),
                    summary = widgetSmallMinutes.joinToString(" / ") + " " + stringResource(R.string.focus_time_unit),
                    startAction = { SettingIcon(MiuixIcons.Timer) },
                    onClick = { editingWidgetSmall = true },
                )
                ArrowPreference(
                    title = stringResource(R.string.settings_widget_wide),
                    summary = widgetWideMinutes.joinToString(" / ") + " " + stringResource(R.string.focus_time_unit),
                    startAction = { SettingIcon(MiuixIcons.Timer) },
                    onClick = { editingWidgetWide = true },
                )
            }
            // 快速专注：长按图标菜单里的三条快捷方式（点开改文案/时长/是否显示）
            Text(
                stringResource(R.string.settings_quick_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            Card {
                quickShortcuts.forEach { item ->
                    ArrowPreference(
                        title = item.label,
                        summary = if (item.enabled) {
                            stringResource(R.string.settings_quick_summary, item.minutes)
                        } else {
                            stringResource(R.string.settings_quick_disabled)
                        },
                        startAction = { SettingIcon(MiuixIcons.Timer) },
                        onClick = { editingShortcut = item },
                    )
                }
            }
        }

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
        if (editingWidgetSmall || editingWidgetWide) {
            val wide = editingWidgetWide
            MiuixWidgetDurationsDialog(
                title = stringResource(if (wide) R.string.settings_widget_wide else R.string.settings_widget_small),
                initial = if (wide) QuickFocusStore.widgetWideMinutes() else QuickFocusStore.widgetSmallMinutes(),
                onSave = { values ->
                    if (wide) QuickFocusStore.updateWidgetWideMinutes(values)
                    else QuickFocusStore.updateWidgetSmallMinutes(values)
                    FocusWidgetProvider.refreshAll(context)
                    widgetVersion++
                    editingWidgetSmall = false
                    editingWidgetWide = false
                },
                onDismiss = {
                    editingWidgetSmall = false
                    editingWidgetWide = false
                },
            )
        }
        editingShortcut?.let { item ->
            MiuixShortcutDialog(
                item = item,
                onSave = { updated ->
                    val labelUntouched = updated.label == item.label
                    QuickFocusStore.update(updated)
                    QuickFocus.syncShortcuts(context)
                    quickVersion++
                    // 时长改了但文案仍是「专注N分钟」旧默认形态 → 提示一次（不自动改写用户文案）
                    if (labelUntouched && QuickFocusStore.isDefaultLabel(updated.label) &&
                        updated.label != QuickFocusStore.defaultLabel(updated.minutes)
                    ) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_quick_label_stale, updated.label),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    editingShortcut = null
                },
                onDismiss = { editingShortcut = null },
            )
        }
    }
}

/** 小部件时长编辑框（miuix）：每格一个数字输入（1-240），确认时整体校验 */
@Composable
private fun MiuixWidgetDurationsDialog(
    title: String,
    initial: List<Int>,
    onSave: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var inputs by remember(initial) { mutableStateOf(initial.map { it.toString() }) }
    OverlayDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.settings_widget_dialog_hint, initial.size),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            inputs.forEachIndexed { index, value ->
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = value,
                    onValueChange = { new ->
                        inputs = inputs.toMutableList().also { it[index] = new.filter(Char::isDigit).take(3) }
                    },
                    label = stringResource(R.string.settings_widget_cell, index + 1),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = {
                        Text(
                            text = stringResource(R.string.focus_time_unit),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                        val values = inputs.map { it.toIntOrNull() }
                        if (values.any { it == null || it !in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES }) {
                            Toast.makeText(context, context.getString(R.string.settings_widget_invalid), Toast.LENGTH_SHORT).show()
                        } else {
                            onSave(values.filterNotNull())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** 快速专注快捷方式编辑框（miuix）：文案（≤[QuickFocusStore.LABEL_MAX] 字）+ 时长 + 是否在长按菜单显示 */
@Composable
private fun MiuixShortcutDialog(
    item: QuickFocusStore.QuickShortcut,
    onSave: (QuickFocusStore.QuickShortcut) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var label by remember(item.id) { mutableStateOf(item.label) }
    var minutesInput by remember(item.id) { mutableStateOf(item.minutes.toString()) }
    var enabled by remember(item.id) { mutableStateOf(item.enabled) }
    OverlayDialog(
        show = true,
        title = stringResource(R.string.settings_quick_title, item.id),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = label,
                onValueChange = { label = it.take(QuickFocusStore.LABEL_MAX) },
                label = stringResource(R.string.settings_quick_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_quick_label_limit, label.length, QuickFocusStore.LABEL_MAX),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = minutesInput,
                onValueChange = { minutesInput = it.filter(Char::isDigit).take(3) },
                label = stringResource(R.string.settings_quick_minutes),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    Text(
                        text = stringResource(R.string.focus_time_unit),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SwitchPreference(
                checked = enabled,
                onCheckedChange = { enabled = it },
                title = stringResource(R.string.settings_quick_enabled),
                summary = stringResource(R.string.settings_quick_enabled_summary),
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
                        val minutes = minutesInput.toIntOrNull()
                        if (minutes == null || minutes !in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES) {
                            Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                        } else {
                            onSave(
                                item.copy(
                                    label = label.ifBlank { QuickFocusStore.defaultLabel(minutes) },
                                    minutes = minutes,
                                    enabled = enabled,
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
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
