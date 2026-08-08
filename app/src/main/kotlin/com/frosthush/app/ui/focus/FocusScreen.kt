package com.frosthush.app.ui.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.frosthush.app.util.FuzzySearch
import com.frosthush.app.util.PinyinSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 专注页（首页）：
 * - 顶部累计专注时长条（点击进统计页，无记录时隐藏）
 * - 已选应用列表（导入/移除）
 * - FAB「开始专注」：时长选择 → 警告 → 开始
 * - 专注进行中：剩余时间 + 已暂停应用数，无任何退出入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(onOpenStats: () -> Unit, onImport: () -> Unit, onOpenGroups: () -> Unit) {
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
    // 黑名单列表：搜索词 + 长按多选状态
    var query by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    // 顶栏搜索图标控制搜索框显隐（对齐雹的 SearchView 展开）
    var showSearch by remember { mutableStateOf(false) }
    // 打开搜索时自动聚焦输入框并呼出键盘（对齐雹 SearchView）
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showSearch) {
        if (showSearch) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val repo = remember { AppRepository(context) }
    // 优先使用缓存的应用名称（内存/磁盘），避免进入页面时因分身跨用户读取慢而闪现包名
    val appNames = remember { mutableStateOf(AppRepository.cachedAppNames()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            // 直接查询全量（含分身）一次到位；不再用不含分身的中间结果覆盖缓存，
            // 否则查询期间分身会闪现裸包名（pkg@999）
            val full = runCatching { repo.queryApps().associate { it.entry to it.displayName } }
                .getOrDefault(emptyMap())
            if (full.isNotEmpty()) {
                appNames.value = full
                AppRepository.updateAppNameCache(full)
            }
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

    // 开始专注后退出多选状态
    LaunchedEffect(session) {
        if (session != null) {
            selectionMode = false
            selected = emptySet()
            query = ""
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
        // 背景明确为主题背景色；顶栏由本页自己提供（左上"专注"，右上搜索/选择/导入，
        // 对齐雹的专注页 Toolbar）；内容区不再重复处理系统栏 insets
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    // 搜索展开：仅返回箭头 + 无边框输入框（对齐雹 SearchView），
                    // 无搜索图标/关闭小叉；空输入时显示浅灰"搜索…"占位
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (!showSearch) {
                            Text(stringResource(R.string.tab_focus))
                        }
                        if (showSearch) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { showSearch = false }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.action_cancel),
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(searchFocusRequester),
                                    )
                                    if (query.isEmpty()) {
                                        Text(
                                            stringResource(R.string.focus_search_hint),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // 搜索展开时隐藏搜索按键（雹的 SearchView 展开后无搜索图标/小叉）
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.focus_action_search),
                            )
                        }
                    }
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selected = emptySet()
                    }) {
                        Icon(
                            Icons.Filled.SelectAll,
                            contentDescription = stringResource(R.string.focus_action_select),
                        )
                    }
                    IconButton(onClick = onOpenGroups) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = stringResource(R.string.group_title),
                        )
                    }
                    IconButton(onClick = onImport) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.focus_import),
                        )
                    }
                },
            )
        },
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
                AppGroupChips(version)
                Spacer(Modifier.height(12.dp))
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
                    query = query,
                    onQueryChange = { query = it },
                    selectionMode = selectionMode,
                    selected = selected,
                    onItemClick = { pkg ->
                        if (selectionMode) {
                            selected = if (pkg in selected) selected - pkg else selected + pkg
                        }
                    },
                    onItemLongClick = { pkg ->
                        if (!selectionMode) {
                            selectionMode = true
                            selected = setOf(pkg)
                        }
                    },
                    onSelectAll = { visible -> selected = visible },
                    onClearSelection = { selected = emptySet() },
                    onDeleteSelected = {
                        FocusStore.saveBlacklist(blacklist.filter { it !in selected })
                        FocusManager.bumpVersion()
                        selected = emptySet()
                        selectionMode = false
                    },
                    onExitSelection = {
                        selectionMode = false
                        selected = emptySet()
                    },
                    onImport = onImport,
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

/** 应用集切换 chips：第一个固定「默认」，其余为各应用集名；当前选中集高亮。切换后生效集合变化，version 自增驱动下方列表刷新 */
@Composable
private fun AppGroupChips(version: Int) {
    // 显式以 version 为 key 重算，保证切换集合后选中态立即刷新
    val groups = remember(version) { FocusStore.appGroups() }
    val selectedId = remember(version) { FocusStore.selectedGroup()?.id }
    val default = remember(version) { FocusStore.defaultGroup() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (default != null) {
            GroupChip(
                label = default.name.ifBlank { stringResource(R.string.group_default) },
                selected = selectedId == default.id,
                onClick = {
                    FocusStore.setSelectedGroupId(default.id)
                    FocusManager.bumpVersion()
                },
            )
        }
        groups.filter { it.id != default?.id }.forEach { group ->
            GroupChip(
                label = group.name,
                selected = selectedId == group.id,
                onClick = {
                    FocusStore.setSelectedGroupId(group.id)
                    FocusManager.bumpVersion()
                },
            )
        }
    }
}

/** 应用集 chip：选中时明显变暗（primaryContainer 填充 + 勾选图标），未选中为浅色容器 */
@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
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
            context.resources.getQuantityString(
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

/** 空闲状态：已选应用列表（顶栏搜索/多选/导入 + 长按多选批量删除） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdleContent(
    blacklist: List<String>,
    appNames: Map<String, String>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectionMode: Boolean,
    selected: Set<String>,
    onItemClick: (String) -> Unit,
    onItemLongClick: (String) -> Unit,
    onSelectAll: (Set<String>) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onExitSelection: () -> Unit,
    onImport: () -> Unit,
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
        return
    }
    // 按名称/包名/拼音过滤（黑名单规模小，组合期直接计算）
    val filtered = remember(blacklist, appNames, query) {
        val q = query.trim()
        if (q.isEmpty()) blacklist
        else blacklist.filter { entry ->
            val name = appNames[entry] ?: entry
            val pkg = FocusStore.parseEntry(entry).first
            FuzzySearch.search(name, q) || FuzzySearch.search(pkg, q) || PinyinSearch.searchPinyinAll(name, q)
        }
    }
    Column(Modifier.fillMaxSize()) {
        // 多选操作栏：高度平滑展开/收起，下方应用列表随之一同平滑移动
        AnimatedVisibility(
            visible = selectionMode,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            // 多选操作栏：退出 / 计数 / 全选 / 清空 / 删除
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExitSelection) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                    )
                }
                Text(
                    stringResource(R.string.focus_selected, selected.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSelectAll(filtered.toSet()) }) {
                    Text(stringResource(R.string.focus_select_all))
                }
                TextButton(onClick = onClearSelection) {
                    Text(stringResource(R.string.focus_clear_selection))
                }
                IconButton(onClick = onDeleteSelected, enabled = selected.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.import_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // 列表高度由操作栏的 expandVertically 平滑让出（布局尺寸渐变），
            // 此处不再对列表自身做尺寸动画，避免 items 间距被拉伸
            LazyColumn(
                Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it }) { entry ->
                    val pkg = FocusStore.parseEntry(entry).first
                    val isSelected = entry in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onItemClick(entry) },
                                onLongClick = { onItemLongClick(entry) },
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(pkg, 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(appNames[entry] ?: entry, style = MaterialTheme.typography.bodyLarge)
                            Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 与雹一致：列表项右侧无删除图标，删除通过长按多选 + 顶栏/操作栏完成
                        // 复选框固定 40dp：否则 M3 最小交互尺寸 48dp 会把行撑高，导致
                        // 进入多选后每个列表项变高、下方列表间距被拉宽
                        if (selectionMode) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onItemClick(entry) },
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    HorizontalDivider()
                }
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
                    FocusStore.presets.size >= FocusStore.MAX_PRESETS ->
                        Toast.makeText(context, context.getString(R.string.focus_preset_limit), Toast.LENGTH_SHORT).show()
                    else -> {
                        // 允许不命名：留空时自动用「N 分钟」作为名称
                        val presetName = if (name.isBlank()) {
                            context.resources.getQuantityString(R.plurals.focus_duration_minutes, minutes, minutes)
                        } else name.trim()
                        FocusStore.presets.add(FocusStore.FocusPreset(FocusStore.nextPresetId(), presetName, minutes))
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

/** 管理预设：长按拖动排序 + 删除 */
@Composable
private fun PresetManageDialog(onDismiss: () -> Unit) {
    var presets by remember { mutableStateOf(FocusStore.presets.toList()) }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index != to.index) {
            // 更新本地列表并同步数据源持久化
            presets = presets.toMutableList().apply { add(to.index, removeAt(from.index)) }
            FocusStore.presets.clear()
            FocusStore.presets.addAll(presets)
            FocusStore.savePresets()
        }
    }
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
                Column {
                    Text(
                        text = stringResource(R.string.focus_preset_drag_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    LazyColumn(state = listState, modifier = Modifier.heightIn(max = 320.dp)) {
                        items(presets, key = { it.id }) { preset ->
                            ReorderableItem(reorderableState, key = preset.id) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        .longPressDraggableHandle(),
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
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
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
