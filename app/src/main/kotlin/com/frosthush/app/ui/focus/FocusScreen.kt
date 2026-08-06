package com.frosthush.app.ui.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        DurationDialog(
            selected = pendingMinutes,
            onSelect = { pendingMinutes = it },
            onCancel = { showDurationDialog = false },
            onConfirm = { showDurationDialog = false; showWarningDialog = true },
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

/** 时长选择对话框 */
@Composable
private fun DurationDialog(
    selected: Int,
    onSelect: (Int) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.focus_select_duration)) },
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
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
