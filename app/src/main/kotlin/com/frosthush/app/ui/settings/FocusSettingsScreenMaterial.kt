package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore

/**
 * 专注设置二级页 · material 版：
 * 默认专注/休息时长、计划开始前提醒、专注结束通知、小米超级岛。
 *
 * 业务逻辑与改造前的设置页一致，仅位置调整到本二级页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSettingsMaterial(onBack: () -> Unit) {
    val defaultMinutes by SettingsStore.defaultFocusMinutes
        .collectAsState(initial = SettingsStore.cache.defaultFocusMinutes)
    val defaultRestMinutes by SettingsStore.defaultRestMinutes
        .collectAsState(initial = SettingsStore.cache.defaultRestMinutes)
    val notifyFinish by SettingsStore.notifyFinishEnabled
        .collectAsState(initial = SettingsStore.cache.notifyFinishEnabled)
    val focusIsland by SettingsStore.focusIslandEnabled
        .collectAsState(initial = SettingsStore.cache.focusIslandEnabled)
    val planRemindSeconds by SettingsStore.planRemindSeconds
        .collectAsState(initial = SettingsStore.cache.planRemindSeconds)
    var showDurationDialog by remember { mutableStateOf(false) }
    var showRestDurationDialog by remember { mutableStateOf(false) }
    var showRemindDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_group_focus)) },
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
    if (showRemindDialog) {
        RemindSecondsDialog(
            selected = planRemindSeconds,
            onSelect = { SettingsStore.setPlanRemindSeconds(it); showRemindDialog = false },
            onCancel = { showRemindDialog = false },
        )
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
