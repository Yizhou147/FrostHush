package com.frosthush.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager

/**
 * 设置页：
 * - 默认专注时长（选择列表）
 * - 专注结束通知开关
 */
@Composable
fun SettingsScreen() {
    val defaultMinutes by SettingsStore.defaultFocusMinutes
        .collectAsState(initial = SettingsStore.cache.defaultFocusMinutes)
    val notifyFinish by SettingsStore.notifyFinishEnabled
        .collectAsState(initial = SettingsStore.cache.notifyFinishEnabled)
    var showDurationDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
    }

    if (showDurationDialog) {
        DurationDialog(
            selected = defaultMinutes,
            onSelect = { SettingsStore.setDefaultFocusMinutes(it); showDurationDialog = false },
            onCancel = { showDurationDialog = false },
        )
    }
}

/** 设置条目卡片 */
@Composable
private fun SettingCard(
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
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.size(2.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 默认专注时长选择对话框 */
@Composable
private fun DurationDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.settings_default_duration)) },
        text = {
            Column {
                FocusManager.DURATIONS.forEach { minutes ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(minutes) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == minutes, onClick = { onSelect(minutes) })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            context.resources.getQuantityString(
                                R.plurals.focus_duration_minutes, minutes, minutes
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
