package com.frosthush.app.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.widget.Toast
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.ShizukuManager
import com.frosthush.app.ui.AppIcon
import com.frosthush.app.ui.DEFAULT_FOCUS_MINUTES
import com.frosthush.app.ui.MAX_SEGMENTS
import com.frosthush.app.ui.MaterialTimePickerDialog
import com.frosthush.app.ui.SegmentMinutesDialog
import com.frosthush.app.ui.SegmentRatioBar
import com.frosthush.app.ui.SegmentRow
import com.frosthush.app.ui.minuteOfDayText
import com.frosthush.app.ui.removeSegment
import com.frosthush.app.ui.segmentEndTimeText
import com.frosthush.app.ui.segmentsSummaryText
import com.frosthush.app.util.FuzzySearch
import com.frosthush.app.util.PinyinSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
    val confirmBeforeStart by SettingsStore.confirmBeforeStart
        .collectAsState(initial = SettingsStore.cache.confirmBeforeStart)

    var session by remember { mutableStateOf(FocusStore.activeSession()) }
    var blacklist by remember { mutableStateOf(FocusStore.blacklist()) }
    var history by remember { mutableStateOf(FocusStore.history()) }
    // 空闲时仍处于暂停状态的应用数（Shizuku 崩溃等导致专注结束后未能解冻，>0 时显示恢复横幅）
    var suspendedCount by remember { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingSegments by remember {
        mutableStateOf(listOf(FocusStore.Segment(FocusStore.SEGMENT_FOCUS, defaultMinutes)))
    }
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

    // 系统返回：搜索模式下退出搜索，选择模式下退出选择；两者都非激活时放行退出应用
    BackHandler(enabled = showSearch || selectionMode) {
        if (showSearch) {
            showSearch = false
        } else if (selectionMode) {
            selectionMode = false
            selected = emptySet()
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

    // 空闲时检测是否有应用仍被暂停（专注结束后 Shizuku 崩溃未能解冻的场景），有则显示恢复横幅
    LaunchedEffect(version) {
        if (FocusStore.activeSession() == null) {
            suspendedCount = withContext(Dispatchers.Default) { FocusManager.suspendedEntries().size }
        } else {
            suspendedCount = 0
        }
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

    val activeSession = session
    val remaining = activeSession?.phaseAt(now)?.remainingAt(now) ?: 0L
    val isResting = activeSession != null && activeSession.phaseAt(now).type == FocusStore.SEGMENT_REST
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
                            Icons.Outlined.Folder,
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
                    onClick = { showDurationDialog = true },
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
                    isResting = isResting,
                    pausedCount = session!!.packages.size,
                    shizukuReady = shizukuReady,
                    onConnectShizuku = {
                        if (shizukuState == ShizukuManager.State.NOT_CONNECTED) ShizukuManager.openShizukuApp(context)
                        else ShizukuManager.requestPermission()
                    },
                    // 跳过休息：休息阶段应用内按钮（点击岛/通知进应用后操作；HyperOS 焦点通知不渲染通知按钮）
                    onSkipRest = {
                        if (isResting) Thread { FocusManager.skipRest() }.start()
                    },
                )
            } else {
                // ---------- 空闲 ----------
                AppGroupChips(version)
                Spacer(Modifier.height(12.dp))
                // 专注结束后仍有应用被暂停（Shizuku 崩溃等）：提示手动恢复
                if (suspendedCount > 0) {
                    SuspendedRestoreBanner(
                        count = suspendedCount,
                        onRestore = {
                            scope.launch {
                                val restored = withContext(Dispatchers.Default) { FocusManager.restoreSuspendedApps() }
                                // 恢复后重新检测，未解冻的继续显示横幅
                                suspendedCount = withContext(Dispatchers.Default) { FocusManager.suspendedEntries().size }
                                snackbarHostState.showSnackbar(
                                    if (restored > 0) {
                                        context.getString(R.string.focus_suspended_restored, restored)
                                    } else {
                                        context.getString(R.string.focus_suspended_restore_failed)
                                    }
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                }
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
            initial = defaultMinutes,
            onDismiss = { showDurationDialog = false },
            onStart = { segments ->
                pendingSegments = segments
                showDurationDialog = false
                // 二次确认开关关闭时跳过警告直接开始
                if (confirmBeforeStart) {
                    showWarningDialog = true
                } else {
                    scope.launch {
                        val err = FocusManager.startFocus(segments)
                        if (err != null) snackbarHostState.showSnackbar(err)
                    }
                }
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
                        val err = FocusManager.startFocus(pendingSegments)
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

/** 专注进行中：当前阶段（专注/休息）+ 剩余时间 + 已暂停应用数（不可打断，无退出入口）。
 *  休息阶段提供「跳过休息」按钮（应用内入口，立即恢复下一段专注）。 */
@Composable
private fun ActiveFocusContent(
    remaining: Long,
    isResting: Boolean,
    pausedCount: Int,
    shizukuReady: Boolean,
    onConnectShizuku: () -> Unit,
    onSkipRest: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (isResting) R.string.focus_rest_title else R.string.focus_active_title),
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
            if (isResting) stringResource(R.string.focus_rest_apps_restored)
            else context.resources.getQuantityString(
                R.plurals.focus_apps_paused, pausedCount, pausedCount
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isResting) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onSkipRest) {
                Text(stringResource(R.string.focus_skip_rest))
            }
        }
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

/** 仍被暂停应用的恢复提示条：专注结束后因 Shizuku 崩溃等未能解冻时显示，点击一键恢复 */
@Composable
private fun SuspendedRestoreBanner(count: Int, onRestore: () -> Unit) {
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
                stringResource(R.string.focus_suspended_restore_title, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRestore) {
                Text(
                    stringResource(R.string.focus_suspended_restore_action),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

/** 开始专注的时间设置对话框：分段列表（与计划专注页统一）+ 预设快捷选择 + 保存/管理预设。
 *  每段显示起止时间（基于对话框打开时系统当前时刻累加），点击结束时间可弹 TimePicker
 *  按具体时刻调整，该段时长自动反算（与计划编辑页交互一致）。
 *  点「添加休息/添加专注」可扩展为分段专注（专注→休息→专注…），不加休息即普通连续专注。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FocusTimeDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onStart: (List<FocusStore.Segment>) -> Unit,
) {
    val context = LocalContext.current
    var segments by remember {
        mutableStateOf(
            mutableListOf(
                FocusStore.Segment(FocusStore.SEGMENT_FOCUS, initial.coerceIn(FocusStore.MIN_MINUTES, FocusStore.MAX_MINUTES))
            )
        )
    }
    var presets by remember { mutableStateOf(FocusStore.presets.toList()) }
    var showSavePreset by remember { mutableStateOf(false) }
    var showManagePresets by remember { mutableStateOf(false) }
    // 正在弹时长输入对话框的段索引；-1 = 无
    var durationDialogIndex by remember { mutableIntStateOf(-1) }
    // 正在按时间段调整的段索引（点该段结束时间打开 TimePicker，时长自动反算）；-1 = 无
    var editingEndIndex by remember { mutableIntStateOf(-1) }
    // 对话框打开时取一次系统当前时刻的 minuteOfDay（0..1439）作为时间线基准；
    // 不在对话框内每秒刷新，避免用户调整时长时数字跳变。
    val startMinuteOfDay by remember {
        mutableIntStateOf(
            java.util.Calendar.getInstance().let {
                it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
            }
        )
    }
    // 各段起止分钟数（基于开始分钟累加，未取模便于跨天显示判断）
    val segmentBounds: List<Pair<Int, Int>> = remember(segments, startMinuteOfDay) {
        val list = mutableListOf<Pair<Int, Int>>()
        var acc = startMinuteOfDay
        segments.forEach { s ->
            list.add(acc to acc + s.minutes)
            acc += s.minutes
        }
        list
    }

    // 保存/管理预设对话框关闭后刷新预设列表
    LaunchedEffect(showSavePreset, showManagePresets) {
        presets = FocusStore.presets.toList()
    }

    if (showSavePreset) {
        // 保存整个分段列表（含休息）；名称必填
        PresetSaveDialog(
            segments = segments,
            onDismiss = { showSavePreset = false },
        )
    }
    if (showManagePresets) {
        PresetManageDialog(onDismiss = { showManagePresets = false })
    }

    val total = segments.sumOf { it.minutes }

    fun addSegment() {
        if (segments.size >= MAX_SEGMENTS) return
        val newType = if (segments.last().isFocus) FocusStore.SEGMENT_REST else FocusStore.SEGMENT_FOCUS
        val minutes = when (newType) {
            FocusStore.SEGMENT_REST -> SettingsStore.cache.defaultRestMinutes
            else -> segments.first().minutes.takeIf { it >= FocusStore.MIN_MINUTES } ?: DEFAULT_FOCUS_MINUTES
        }
        segments = segments.toMutableList().apply { add(FocusStore.Segment(newType, minutes)) }
    }

    fun deleteSegment(index: Int) {
        if (index <= 0 || segments.size <= 1) return
        segments = removeSegment(segments, index).toMutableList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.focus_select_duration)) },
        // 弹窗背景与页面一致：分段卡片（surfaceContainerLow）在 M3 默认对话框深背景上观感发灰，
        // 与计划编辑页（页面背景）显示效果不同；改为页面背景后两处配色完全一致
        containerColor = MaterialTheme.colorScheme.background,
        text = {
            Column {
                // 段列表：第一段为专注（主输入），其后交替休息/专注；
                // 每行显示起止时间（基准=打开对话框时的系统时刻），结束时间可点弹 TimePicker 按时刻反算时长
                segments.forEachIndexed { index, seg ->
                    val (s, e) = segmentBounds.getOrElse(index) { 0 to 0 }
                    SegmentRow(
                        segment = seg,
                        deletable = index > 0,
                        onClickDuration = { durationDialogIndex = index },
                        startTimeText = minuteOfDayText(s % 1440),
                        endTimeText = segmentEndTimeText(e),
                        endTimeEditable = true,
                        onEditEndTime = { editingEndIndex = index },
                        onDelete = { deleteSegment(index) },
                    )
                }
                TextButton(
                    onClick = { addSegment() },
                    enabled = segments.size < MAX_SEGMENTS,
                ) {
                    Text(
                        stringResource(
                            if (segments.size >= MAX_SEGMENTS) R.string.focus_segments_limit
                            else if (segments.last().isFocus) R.string.focus_add_rest
                            else R.string.focus_add_focus
                        )
                    )
                }
                if (segments.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    SegmentRatioBar(segments)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = segmentsSummaryText(segments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (presets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.focus_empty_presets),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // 预设为完整分段列表：点击即替换整个时间安排（旧单段预设回退单段专注）
                    FocusPresetChips(
                        segments = segments,
                        presets = presets,
                        onSelect = { preset ->
                            segments = preset.segmentList.toMutableList()
                        },
                    )
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
                if (segments.any { it.minutes < FocusStore.MIN_MINUTES } || total !in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES) {
                    Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                } else {
                    onStart(segments.toList())
                }
            }) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
    // 段时长输入对话框（嵌套在时间选择对话框之上；点时长胶囊触发）
    if (durationDialogIndex in segments.indices) {
        val index = durationDialogIndex
        val seg = segments[index]
        SegmentMinutesDialog(
            title = stringResource(
                if (seg.isFocus) R.string.focus_segment_focus_duration_title
                else R.string.focus_segment_rest_duration_title
            ),
            selected = seg.minutes,
            range = FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES,
            onConfirm = { minutes ->
                segments = segments.toMutableList().apply { set(index, FocusStore.Segment(this[index].type, minutes)) }
                durationDialogIndex = -1
            },
            onCancel = { durationDialogIndex = -1 },
        )
    }
    // 按时间段调整分段：选择该段新的结束时刻 → 时长自动反算，后续段顺延
    // 单段最大 240 分钟（普通专注总时长上限）；新总时长超 240 在点开始时统一校验
    if (editingEndIndex >= 0 && editingEndIndex < segmentBounds.size) {
        MaterialTimePickerDialog(
            initialHour = (segmentBounds[editingEndIndex].second % 1440) / 60,
            initialMinute = (segmentBounds[editingEndIndex].second % 1440) % 60,
            onDismiss = { editingEndIndex = -1 },
            onConfirm = { h, m ->
                val chosen = h * 60 + m // 当天时刻 0..1439
                val segStart = segmentBounds[editingEndIndex].first
                var segEnd = chosen
                // 结束不晚于开始 → 视为次日结束（普通专注总时长上限 240 分钟，跨天很少见但兼容）
                if (segEnd <= segStart % 1440) segEnd += 1440
                val duration = segEnd - segStart
                // 单段需 ≥1 分钟且 ≤ MAX_MINUTES（240）；普通专注总时长上限由点开始时统一校验
                if (duration < FocusStore.MIN_MINUTES || duration > FocusStore.MAX_MINUTES) {
                    Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                } else {
                    segments = segments.toMutableList().apply {
                        set(editingEndIndex, FocusStore.Segment(this[editingEndIndex].type, duration))
                    }
                }
                editingEndIndex = -1
            },
        )
    }
}

/** 预设快捷选择 chips：显示「名称 段序列」（如「午休 30」「番茄 25+5+25」）；
 *  点击应用整个分段列表；与当前分段完全一致时高亮 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusPresetChips(
    segments: List<FocusStore.Segment>,
    presets: List<FocusStore.FocusPreset>,
    onSelect: (FocusStore.FocusPreset) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.segmentList == segments,
                onClick = { onSelect(preset) },
                label = {
                    Text(
                        // 只显示名称（保存时必填）；旧数据空名回退段序列避免空白
                        text = preset.name.ifBlank { preset.sequenceText },
                    )
                },
            )
        }
    }
}

/** 保存为预设：保存当前完整分段列表（含休息），名称必填（不支持自动命名） */
@Composable
private fun PresetSaveDialog(segments: List<FocusStore.Segment>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val total = segments.sumOf { it.minutes }
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
                // 分段预览：逐段展示（专注 30 · 休息 10 · 专注 30）+ 汇总
                Text(
                    text = segments.joinToString(" · ") { seg ->
                        context.getString(
                            if (seg.isFocus) R.string.focus_segment_focus else R.string.focus_segment_rest
                        ) + " " + seg.minutes
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = segmentsSummaryText(segments),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.trim().isBlank() ->
                        Toast.makeText(context, context.getString(R.string.focus_preset_name_required), Toast.LENGTH_SHORT).show()
                    segments.any { it.minutes < FocusStore.MIN_MINUTES } || total !in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES ->
                        Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                    FocusStore.presets.size >= FocusStore.MAX_PRESETS ->
                        Toast.makeText(context, context.getString(R.string.focus_preset_limit), Toast.LENGTH_SHORT).show()
                    else -> {
                        FocusStore.presets.add(
                            FocusStore.FocusPreset(FocusStore.nextPresetId(), name.trim(), total, segments)
                        )
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

/** 管理预设：长按拖动排序（与应用集/计划列表同款：行放大跟手，其余行 animateItem 平滑让位）+ 删除 */
@Composable
private fun PresetManageDialog(onDismiss: () -> Unit) {
    var presets by remember { mutableStateOf(FocusStore.presets.toList()) }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var draggedHeightPx by remember { mutableStateOf(0f) }
    val latestPresets by rememberUpdatedState(presets)

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
                            val isDragging = draggingId == preset.id
                            val scale by animateFloatAsState(
                                targetValue = if (isDragging) 1.04f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "presetDragScale",
                            )
                            Column(
                                modifier = (if (isDragging) Modifier else Modifier.animateItem())
                                    // 被拖项禁用让位动画（仅跟手），其余项 animateItem 平滑让位
                                    .graphicsLayer {
                                        translationY = if (isDragging) dragOffsetY else 0f
                                    }
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .scale(scale)
                                    .onGloballyPositioned {
                                        if (isDragging) draggedHeightPx = it.size.height.toFloat()
                                    }
                                    .pointerInput(preset.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingId = preset.id
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                draggingId = null
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                if (draggingId != preset.id) return@detectDragGesturesAfterLongPress
                                                dragOffsetY += amount.y
                                                val list = latestPresets
                                                val currentIndex = list.indexOfFirst { it.id == preset.id }
                                                if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                                val h = draggedHeightPx.takeIf { it > 0f }
                                                    ?: 48.dp.toPx()
                                                val targetIndex = (currentIndex + (dragOffsetY / h).roundToInt())
                                                    .coerceIn(0, list.size - 1)
                                                if (targetIndex != currentIndex) {
                                                    val newList = list.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
                                                    presets = newList
                                                    FocusStore.presets.clear()
                                                    FocusStore.presets.addAll(newList)
                                                    FocusStore.savePresets()
                                                    dragOffsetY -= (targetIndex - currentIndex) * h
                                                }
                                            },
                                        )
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        // 只显示名称（保存时必填）；旧数据空名回退段序列避免空白
                                        text = preset.name.ifBlank { preset.sequenceText },
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
