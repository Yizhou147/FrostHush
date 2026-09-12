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
import com.frosthush.app.data.SettingsStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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
