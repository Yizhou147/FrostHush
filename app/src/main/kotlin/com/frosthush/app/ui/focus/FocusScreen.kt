package com.frosthush.app.ui.focus

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Delete as OutlinedDelete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.ShizukuManager
import com.frosthush.app.ui.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 专注页（首页）：
 * - 顶部累计专注时长条（点击进统计页，无记录时隐藏）
 * - 已选应用列表（导入/移除）
 * - FAB「开始专注」：时长选择 → 警告 → 开始
 * - 专注进行中：剩余时间 + 已暂停应用数，无任何退出入口
 */
@Composable
fun FocusScreen(onOpenStats: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val version by FocusManager.version.collectAsState()
    val shizukuState by ShizukuManager.state.collectAsState()
    val defaultMinutes by SettingsStore.defaultFocusMinutes
        .collectAsState(initial = SettingsStore.cache.defaultFocusMinutes)

    var session by remember { mutableStateOf(FocusStore.activeSession()) }
    var blacklist by remember { mutableStateOf(FocusStore.blacklist()) }
    var history by remember { mutableStateOf(FocusStore.history()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingMinutes by remember { mutableIntStateOf(defaultMinutes) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }

    val repo = remember { AppRepository(context) }
    val appNames = remember { mutableStateOf(mapOf<String, String>()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            appNames.value = repo.queryApps().associate { it.packageName to it.name }
        }
    }

    // 数据变化（导入/移除/开始/结束）时刷新
    LaunchedEffect(version) {
        session = FocusStore.activeSession()
        blacklist = FocusStore.blacklist()
        history = FocusStore.history()
    }

    // 每秒刷新倒计时
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // 兜底：界面可见时若已到点（服务未拉起等情况）则补执行恢复
    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        if (s.endMillis <= System.currentTimeMillis()) {
            Thread { FocusManager.restoreAndEnd() }.start()
        }
    }

    val remaining = ((session?.endMillis ?: 0L) - now).coerceAtLeast(0L)
    val shizukuReady = shizukuState == ShizukuManager.State.AUTHORIZED

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (session == null) {
                ExtendedFloatingActionButton(
                    onClick = { pendingMinutes = defaultMinutes; showDurationDialog = true },
                    icon = { Icon(Icons.Filled.Timer, contentDescription = null) },
                    text = { Text(stringResource(R.string.focus_start)) },
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (history.isNotEmpty()) {
                TotalDurationBar(totalMinutes = FocusManager.totalMinutes(), onClick = onOpenStats)
                Spacer(Modifier.height(12.dp))
            }

            if (session != null) {
                // ---------- 专注进行中 ----------
                ActiveFocusContent(
                    remaining = remaining,
                    pausedCount = session!!.packages.size,
                    shizukuReady = shizukuReady,
                    onConnectShizuku = {
                        if (shizukuState == ShizukuManager.State.NOT_CONNECTED) ShizukuManager.openShizukuApp(context)
                        else ShizukuManager.requestPermission()
                    },
                )
            } else {
                // ---------- 空闲 ----------
                if (!shizukuReady) {
                    ShizukuBanner(
                        text = stringResource(R.string.focus_shizuku_unavailable),
                        actionText = when (shizukuState) {
                            ShizukuManager.State.NOT_CONNECTED -> stringResource(R.string.action_start_shizuku)
                            else -> stringResource(R.string.action_grant)
                        },
                        onAction = {
                            if (shizukuState == ShizukuManager.State.NOT_CONNECTED) ShizukuManager.openShizukuApp(context)
                            else ShizukuManager.requestPermission()
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                IdleContent(
                    blacklist = blacklist,
                    appNames = appNames.value,
                    onImport = onImport,
                    onRemove = { pkg ->
                        FocusStore.saveBlacklist(blacklist.filter { it != pkg })
                        FocusManager.bumpVersion()
                    },
                )
            }
        }
    }

    if (showDurationDialog) {
        FocusTimeDialog(
            initial = pendingMinutes,
            onDismiss = { showDurationDialog = false },
            onStart = { minutes ->
                pendingMinutes = minutes
                showDurationDialog = false
                showWarningDialog = true
            },
        )
    }
    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(stringResource(R.string.focus_start)) },
            text = { Text(stringResource(R.string.focus_confirm_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showWarningDialog = false
                    scope.launch {
                        val err = FocusManager.startFocus(pendingMinutes)
                        if (err != null) snackbarHostState.showSnackbar(err)
                    }
                }) { Text(stringResource(R.string.action_start)) }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** 顶部累计专注时长条 */
@Composable
private fun TotalDurationBar(totalMinutes: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.focus_total_duration, FocusManager.minutesText(totalMinutes)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 专注进行中：剩余时间 + 已暂停应用数（不可打断，无退出入口） */
@Composable
private fun ActiveFocusContent(
    remaining: Long,
    pausedCount: Int,
    shizukuReady: Boolean,
    onConnectShizuku: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.focus_active_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            FocusManager.countdownText(remaining),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            context.getString(
                R.plurals.focus_apps_paused, pausedCount, pausedCount
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!shizukuReady) {
            Spacer(Modifier.height(32.dp))
            ShizukuBanner(
                text = stringResource(R.string.focus_restore_prompt),
                actionText = stringResource(R.string.focus_connect_shizuku),
                onAction = onConnectShizuku,
            )
        }
    }
}

/** 空闲状态：已选应用列表 + 导入 */
@Composable
private fun IdleContent(
    blacklist: List<String>,
    appNames: Map<String, String>,
    onImport: () -> Unit,
    onRemove: (String) -> Unit,
) {
    if (blacklist.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.focus_apps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onImport) { Text(stringResource(R.string.focus_import)) }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.focus_selected_count, blacklist.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onImport) { Text(stringResource(R.string.focus_import)) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(blacklist, key = { it }) { pkg ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(pkg, 40.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(appNames[pkg] ?: pkg, style = MaterialTheme.typography.bodyLarge)
                        Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRemove(pkg) }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** Shizuku 未就绪提示条 */
@Composable
private fun ShizukuBanner(text: String, actionText: String, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) {
                Text(actionText, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

/** 开始专注的时间设置对话框：数字输入 + 预设快捷选择 + 保存/管理预设（与雹一致） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusTimeDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(if (initial in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES) initial.toString() else "") }
    var presets by remember { mutableStateOf(FocusStore.presets.toList()) }
    var showSavePreset by remember { mutableStateOf(false) }
    var showManagePresets by remember { mutableStateOf(false) }

    // 保存/管理预设对话框关闭后刷新预设列表
    LaunchedEffect(showSavePreset, showManagePresets) {
        presets = FocusStore.presets.toList()
    }

    if (showSavePreset) {
        PresetSaveDialog(minutes = input.toIntOrNull() ?: 0, onDismiss = { showSavePreset = false })
    }
    if (showManagePresets) {
        PresetManageDialog(onDismiss = { showManagePresets = false })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_select_duration)) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.focus_time_label)) },
                    placeholder = { Text(stringResource(R.string.focus_time_hint)) },
                    suffix = { Text(stringResource(R.string.focus_time_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (presets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.focus_empty_presets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FocusPresetChips(input = input, presets = presets, onSelect = { input = it.toString() })
                }
                Spacer(Modifier.height(4.dp))
                Row {
                    TextButton(onClick = { showSavePreset = true }) {
                        Text(stringResource(R.string.action_save_preset))
                    }
                    TextButton(onClick = { showManagePresets = true }) {
                        Text(stringResource(R.string.action_manage_presets))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = input.toIntOrNull()
                if (minutes != null && minutes in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES) {
                    onStart(minutes)
                } else {
                    Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 预设快捷选择 chips */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusPresetChips(input: String, presets: List<FocusStore.FocusPreset>, onSelect: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.minutes.toString() == input,
                onClick = { onSelect(preset.minutes) },
                label = { Text("${preset.name} ${preset.minutes}") },
            )
        }
    }
}

/** 保存为预设 */
@Composable
private fun PresetSaveDialog(minutes: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_save_preset)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.focus_preset_name)) },
                    placeholder = { Text(stringResource(R.string.focus_preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = context.getString(R.string.focus_time_label) + " " + minutes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    minutes !in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES ->
                        Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                    name.isBlank() ->
                        Toast.makeText(context, context.getString(R.string.focus_preset_name_required), Toast.LENGTH_SHORT).show()
                    FocusStore.presets.size >= FocusStore.MAX_PRESETS ->
                        Toast.makeText(context, context.getString(R.string.focus_preset_limit), Toast.LENGTH_SHORT).show()
                    else -> {
                        FocusStore.presets.add(FocusStore.FocusPreset(FocusStore.nextPresetId(), name.trim(), minutes))
                        FocusStore.savePresets()
                        Toast.makeText(context, context.getString(R.string.focus_preset_saved), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 管理预设：列表 + 删除 */
@Composable
private fun PresetManageDialog(onDismiss: () -> Unit) {
    var presets by remember { mutableStateOf(FocusStore.presets.toList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_manage_presets)) },
        text = {
            if (presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_empty_presets),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(presets, key = { it.id }) { preset ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${preset.name} ${preset.minutes}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            IconButton(onClick = {
                                FocusStore.presets.removeAll { it.id == preset.id }
                                FocusStore.savePresets()
                                presets = FocusStore.presets.toList()
                            }) {
                                Icon(
                                    imageVector = OutlinedDelete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
        },
    )
}
