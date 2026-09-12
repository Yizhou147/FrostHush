package com.frosthush.app.ui.focus

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler
import com.frosthush.app.focus.ShizukuManager
import com.frosthush.app.ui.AppIcon
import com.frosthush.app.ui.WarningDefaults
import com.frosthush.app.ui.DEFAULT_FOCUS_MINUTES
import com.frosthush.app.ui.MAX_SEGMENTS
import com.frosthush.app.ui.MiuixTimePickerDialog
import com.frosthush.app.ui.SegmentMinutesDialogMiuix
import com.frosthush.app.ui.SegmentRatioBarMiuix
import com.frosthush.app.ui.SegmentRowMiuix
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 专注页（首页）· miuix 版（HyperOS 设计语言）：
 * - 大标题顶栏随列表滚动收起；actions 依次为「搜索」「多选」「应用集」「导入」，
 *   搜索展开时改为返回箭头 + 输入框（对齐 material 版交互）
 * - 顶部累计专注时长条（点击进统计页，无记录时隐藏）
 * - 已选应用列表（多选批量删除）+ FAB「开始专注」
 *
 * 业务逻辑与 material 版逐字一致，仅组件 / 颜色 / 文字样式替换为 miuix。
 */
@Composable
fun FocusScreenMiuix(
    onOpenStats: () -> Unit,
    onImport: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡（内容仍可从底栏下方穿过） */
    bottomInnerPadding: Dp = 0.dp,
) {
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
    // 空闲时仍处于暂停状态的应用数（Shizuku 崩溃等导致专注结束后未能解冻，>0 时显示恢复横幅）
    var suspendedCount by remember { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingSegments by remember {
        mutableStateOf(listOf(FocusStore.Segment(FocusStore.SEGMENT_FOCUS, defaultMinutes)))
    }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showWarningDialog by remember { mutableStateOf(false) }
    // 普通专注会覆盖到的启用计划（开始时刻被本次专注盖住）+ 冲突提醒对话框
    var conflictPlans by remember { mutableStateOf<List<FocusStore.FocusPlan>>(emptyList()) }
    var showConflictDialog by remember { mutableStateOf(false) }
    // 专注启动失败（应用全部未能冻结）→ 弹窗告知解决方法，替代笼统的「操作失败」
    var showFocusErrorDialog by remember { mutableStateOf(false) }

    /** 专注启动错误处理：全部冻结失败弹详细对话框，其余错误保持 snackbar */
    fun handleStartError(err: String) {
        if (err == context.getString(R.string.operation_failed)) {
            showFocusErrorDialog = true
        } else {
            scope.launch { snackbarHostState.showSnackbar(err) }
        }
    }
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

    // 回到前台（小窗切全屏、系统解冻后恢复）立即重算一次：进程被冻结期间上面的 1 秒循环
    // 不会执行，界面会停在冻结前那一帧（实测卡在 00:06），须在恢复瞬间纠正
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = System.currentTimeMillis()
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

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // 搜索展开时顶栏交叉淡入淡出为「返回箭头 + 无边框输入框」（对齐 material 版/雹 SearchView：
            // 搜索框出现在顶栏标题位置，无搜索图标/关闭小叉，空输入时浅灰占位）
            AnimatedContent(
                targetState = showSearch,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "focusSearchBar",
            ) { searching ->
                if (searching) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MiuixTheme.colorScheme.surface)
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(64.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showSearch = false }) {
                            Icon(
                                MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_cancel),
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester),
                            )
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.focus_search_hint),
                                    style = MiuixTheme.textStyles.body1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                    }
                } else {
                    TopAppBar(
                        title = stringResource(R.string.tab_focus),
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    MiuixIcons.Basic.Search,
                                    contentDescription = stringResource(R.string.focus_action_search),
                                )
                            }
                            IconButton(onClick = {
                                selectionMode = !selectionMode
                                if (!selectionMode) selected = emptySet()
                            }) {
                                Icon(
                                    MiuixIcons.SelectAll,
                                    contentDescription = stringResource(R.string.focus_action_select),
                                )
                            }
                            IconButton(onClick = onOpenGroups) {
                                Icon(
                                    MiuixIcons.Folder,
                                    contentDescription = stringResource(R.string.group_title),
                                )
                            }
                            IconButton(onClick = onImport) {
                                Icon(
                                    MiuixIcons.Add,
                                    contentDescription = stringResource(R.string.focus_import),
                                )
                            }
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // FAB 淡入/下滑退出（专注开始后按钮消失不再瞬跳）
            AnimatedVisibility(
                visible = session == null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it / 2 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(250, easing = FastOutSlowInEasing)) { it / 2 },
            ) {
                Button(
                    onClick = { showDurationDialog = true },
                    modifier = Modifier.padding(bottom = bottomInnerPadding),
                    // HyperOS 观感：主操作用中性胶囊（同应用集 chip 未选中态），避免大块主色
                    colors = ButtonDefaults.buttonColors(),
                    insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Icon(MiuixIcons.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.focus_start))
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (history.isNotEmpty()) {
                TotalDurationBarMiuix(totalMinutes = FocusManager.totalMinutes(), onClick = onOpenStats)
                Spacer(Modifier.height(12.dp))
            }

            if (session != null) {
                // ---------- 专注进行中 ----------
                ActiveFocusContentMiuix(
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
                AppGroupChipsMiuix(version)
                Spacer(Modifier.height(12.dp))
                // 专注结束后仍有应用被暂停（Shizuku 崩溃等）：提示手动恢复
                if (suspendedCount > 0) {
                    SuspendedRestoreBannerMiuix(
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
                    ShizukuBannerMiuix(
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
                IdleListMiuix(
                    blacklist = blacklist,
                    appNames = appNames.value,
                    query = query,
                    onQueryChange = { query = it },
                    selectionMode = selectionMode,
                    selected = selected,
                    // 应用集切换动画的 key：切换应用集（version 自增）时列表淡入淡出
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
                    bottomInnerPadding = bottomInnerPadding,
                )
            }
        }

        // ---------- 对话框（OverlayDialog 需置于 Scaffold 内容内由 popup host 渲染） ----------
        FocusTimeDialogMiuix(
            show = showDurationDialog,
            initial = defaultMinutes,
                onDismiss = { showDurationDialog = false },
                onStart = { segments ->
                    pendingSegments = segments
                    showDurationDialog = false
                    // 先预判：本次普通专注会覆盖哪些启用计划今天的开始时刻？
                    // 如有冲突，先弹提醒（确定后冲突计划本日失效一次），再走二次确认警告。
                    val now = System.currentTimeMillis()
                    val total = segments.sumOf { it.minutes }
                    val normalFocusEnd = now + total * 60_000L
                    val conflicts = PlanScheduler.findConflictingPlans(now, normalFocusEnd)
                    if (conflicts.isNotEmpty()) {
                        conflictPlans = conflicts
                        showConflictDialog = true
                    } else {
                        showWarningDialog = true
                    }
                },
            )
        // 普通专注会覆盖启用计划今天开始时刻：列出受影响计划，确定后标记当日失效，
        // 再走二次确认警告（如开关开启）；取消=不开始专注。
        OverlayDialog(
            show = showConflictDialog,
            title = stringResource(R.string.focus_start),
            onDismissRequest = { showConflictDialog = false },
        ) {
                Column {
                    Text(
                        text = stringResource(R.string.focus_conflict_text),
                        style = MiuixTheme.textStyles.body2,
                    )
                    Spacer(Modifier.height(8.dp))
                    conflictPlans.forEach { plan ->
                        Text(
                            text = "· " + plan.name,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.focus_conflict_hint),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = { showConflictDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.action_confirm),
                            onClick = {
                                PlanScheduler.markPlansSkippedToday(conflictPlans)
                                showConflictDialog = false
                                // 标记冲突计划失效后，继续走二次确认警告流程
                                showWarningDialog = true
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        OverlayDialog(
            show = showWarningDialog,
            title = stringResource(R.string.focus_start),
            summary = stringResource(R.string.focus_confirm_warning),
            onDismissRequest = { showWarningDialog = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showWarningDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_start),
                    onClick = {
                        showWarningDialog = false
                        scope.launch {
                            val err = FocusManager.startFocus(pendingSegments)
                            if (err != null) handleStartError(err)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // 全部应用未能冻结：弹窗告知原因与解决方法（替代笼统的「操作失败」）
        OverlayDialog(
            show = showFocusErrorDialog,
            title = stringResource(R.string.focus_fail_title),
            summary = stringResource(R.string.focus_fail_body),
            onDismissRequest = { showFocusErrorDialog = false },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.focus_fail_ok),
                    onClick = { showFocusErrorDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.focus_fail_goto_settings),
                    onClick = {
                        showFocusErrorDialog = false
                        onOpenSettings()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 顶部累计专注时长条（miuix 卡片，点击进统计页）。HyperOS 规范：卡片用中性 surfaceContainer，主色只做小面积点缀 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TotalDurationBarMiuix(totalMinutes: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
            MiuixIcons.Timer,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            Spacer(Modifier.width(12.dp))
            Text(
            text = stringResource(R.string.focus_total_duration, FocusManager.minutesText(totalMinutes)),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
            modifier = Modifier.weight(1f).basicMarquee(),
            )
            Icon(
                MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
    }
}

/** 应用集切换 chips：第一个固定「默认」，其余为各应用集名；当前选中集高亮。切换后生效集合变化，version 自增驱动下方列表刷新 */
@Composable
private fun AppGroupChipsMiuix(version: Int) {
    // 显式以 version 为 key 重算，保证切换集合后选中态立即刷新
    val groups = remember(version) { FocusStore.appGroups() }
    val selectedId = remember(version) { FocusStore.selectedGroup()?.id }
    val default = remember(version) { FocusStore.defaultGroup() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (default != null) {
            AppGroupChip(
                label = default.name.ifBlank { stringResource(R.string.group_default) },
                selected = selectedId == default.id,
                onClick = {
                    FocusStore.setSelectedGroupId(default.id)
                    FocusManager.bumpVersion()
                },
            )
        }
        groups.filter { it.id != default?.id }.forEach { group ->
            AppGroupChip(
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

/** 应用集 chip：选中时用 miuix 下拉选中的容器语义色（tertiaryContainer 浅蓝 + 深色文字），未选中为中性灰胶囊 */
@Composable
private fun AppGroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.tertiaryContainer,
                contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.button,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 专注进行中：当前阶段（专注/休息）+ 剩余时间 + 已暂停应用数（不可打断，无退出入口）。
 *  休息阶段提供「跳过休息」按钮（应用内入口，立即恢复下一段专注）。 */
@Composable
private fun ActiveFocusContentMiuix(
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
            text = stringResource(if (isResting) R.string.focus_rest_title else R.string.focus_active_title),
            style = MiuixTheme.textStyles.title4,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = FocusManager.countdownText(remaining),
            style = MiuixTheme.textStyles.title1,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (isResting) stringResource(R.string.focus_rest_apps_restored)
            else context.resources.getQuantityString(
                R.plurals.focus_apps_paused, pausedCount, pausedCount
            ),
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        if (isResting) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onSkipRest) {
                Text(stringResource(R.string.focus_skip_rest))
            }
        }
        if (!shizukuReady) {
            Spacer(Modifier.height(32.dp))
            ShizukuBannerMiuix(
                text = stringResource(R.string.focus_restore_prompt),
                actionText = stringResource(R.string.focus_connect_shizuku),
                onAction = onConnectShizuku,
            )
        }
    }
}

/** 空闲状态：已选应用列表（顶栏搜索/多选/导入 + 长按多选批量删除）。
 *  列表行样式与 AppSelectScreenMiuix（应用集编辑选应用）一致：卡片包裹、行内 16dp 边距、无选中底色 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IdleListMiuix(
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
    bottomInnerPadding: Dp = 0.dp,
    // 应用集切换淡入淡出的 key（切换应用集时列表 AnimatedContent 过渡）
) {
    if (blacklist.isEmpty()) {
        Box(
            Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 48.dp + bottomInnerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.focus_apps_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onImport) { Text(stringResource(R.string.focus_import)) }
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
            // 多选操作栏：退出 / 计数 / 全选 / 清空 / 删除（相邻按钮留 8dp 间距，胶囊不贴合）
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onExitSelection) {
                    Icon(
                        MiuixIcons.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                    )
                }
                Text(
                    text = stringResource(R.string.focus_selected, selected.size),
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.focus_select_all),
                    onClick = { onSelectAll(filtered.toSet()) },
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(R.string.focus_clear_selection),
                    onClick = onClearSelection,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDeleteSelected, enabled = selected.isNotEmpty()) {
                    Icon(
                        MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.import_nothing),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // 行样式与 AppSelectScreenMiuix（应用集编辑选应用）一致：Card 包裹 + 行内 16dp 边距，
                // 选中与否只由右侧勾选框表达（无整行底色）；保留长按进入多选
                Card(Modifier.fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(filtered, key = { _, it -> it }) { index, entry ->
                            val pkg = FocusStore.parseEntry(entry).first
                            val isSelected = entry in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onItemClick(entry) },
                                        onLongClick = { onItemLongClick(entry) },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(pkg, 36.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(appNames[entry] ?: entry, style = MiuixTheme.textStyles.body1)
                                    Text(
                                        text = pkg,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                // 与雹一致：列表项右侧无删除图标，删除通过长按多选 + 顶栏/操作栏完成
                                // 勾选框槽位常驻 40dp：进出选择模式行高不变（否则应用间距会突兀变大）；
                                // 选中与否只由勾选框表达（无整行底色）
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    if (selectionMode) {
                                        Checkbox(
                                            state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                                            onClick = { onItemClick(entry) },
                                        )
                                    }
                                }
                            }
                            // 行间浅色分隔线（对齐统计页等列表的 HorizontalDivider 样式）
                            if (index != filtered.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Shizuku 未就绪提示条 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShizukuBannerMiuix(text: String, actionText: String, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = WarningDefaults.containerColor(),
            contentColor = WarningDefaults.contentColor(),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                MiuixIcons.Info,
                contentDescription = null,
                tint = WarningDefaults.contentColor(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MiuixTheme.textStyles.footnote1,
                color = WarningDefaults.contentColor(),
                // 单行 + 溢出跑马灯滚动（KernelSU 同款 basicMarquee），不再截断为省略号
                maxLines = 1,
                modifier = Modifier.weight(1f).basicMarquee(),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(text = actionText, onClick = onAction)
        }
    }
}

/** 仍被暂停应用的恢复提示条：专注结束后因 Shizuku 崩溃等未能解冻时显示，点击一键恢复 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuspendedRestoreBannerMiuix(count: Int, onRestore: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = WarningDefaults.containerColor(),
            contentColor = WarningDefaults.contentColor(),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                MiuixIcons.Info,
                contentDescription = null,
                tint = WarningDefaults.contentColor(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.focus_suspended_restore_title, count),
                style = MiuixTheme.textStyles.footnote1,
                color = WarningDefaults.contentColor(),
                maxLines = 1,
                modifier = Modifier.weight(1f).basicMarquee(),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                text = stringResource(R.string.focus_suspended_restore_action),
                onClick = onRestore,
            )
        }
    }
}

/** 开始专注的时间设置对话框：分段列表（与计划专注页统一）+ 预设快捷选择 + 保存/管理预设。
 *  每段显示起止时间（基于对话框打开时系统当前时刻累加），点击结束时间可弹 TimePicker
 *  按具体时刻调整，该段时长自动反算（与计划编辑页交互一致）。
 *  点「添加休息/添加专注」可扩展为分段专注（专注→休息→专注…），不加休息即普通连续专注。 */
@Composable
private fun FocusTimeDialogMiuix(
    show: Boolean,
    initial: Int,
    onDismiss: () -> Unit,
    onStart: (List<FocusStore.Segment>) -> Unit,
) {
    val context = LocalContext.current
    // remember(show)：对话框每次打开重置（改为常驻组合后 remember{} 会跨开合保留状态，
    // 恢复 if(show) 时代的"每次打开都是新对话框"行为）
    var segments by remember(show) {
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
    var durationDialogIndex by remember(show) { mutableIntStateOf(-1) }
    // 正在按时间段调整的段索引（点该段结束时间打开 TimePicker，时长自动反算）；-1 = 无
    var editingEndIndex by remember(show) { mutableIntStateOf(-1) }
    // 对话框打开时取一次系统当前时刻的 minuteOfDay（0..1439）作为时间线基准；
    // 不在对话框内每秒刷新，避免用户调整时长时数字跳变。
    val startMinuteOfDay by remember(show) {
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

    OverlayDialog(
        show = show,
        title = stringResource(R.string.focus_select_duration),
        onDismissRequest = onDismiss,
    ) {
        Column {
            // 段列表（可滚动，避免分段较多时溢出屏幕）：
            // 第一段为专注（主输入），其后交替休息/专注；每行显示起止时间，结束时间可点弹 TimePicker 反算时长
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                segments.forEachIndexed { index, seg ->
                    val (s, e) = segmentBounds.getOrElse(index) { 0 to 0 }
                    SegmentRowMiuix(
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
                    text = stringResource(
                        if (segments.size >= MAX_SEGMENTS) R.string.focus_segments_limit
                        else if (segments.last().isFocus) R.string.focus_add_rest
                        else R.string.focus_add_focus
                    ),
                    onClick = { addSegment() },
                    enabled = segments.size < MAX_SEGMENTS,
                )
                if (segments.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    SegmentRatioBarMiuix(segments)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = segmentsSummaryText(segments),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(12.dp))
                if (presets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.focus_empty_presets),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    // 预设为完整分段列表：点击即替换整个时间安排（旧单段预设回退单段专注）
                    FocusPresetChipsMiuix(
                        segments = segments,
                        presets = presets,
                        onSelect = { preset ->
                            segments = preset.segmentList.toMutableList()
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.action_save_preset),
                        onClick = { showSavePreset = true },
                    )
                    TextButton(
                        text = stringResource(R.string.action_manage_presets),
                        onClick = { showManagePresets = true },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_start),
                    onClick = {
                        if (segments.any { it.minutes < FocusStore.MIN_MINUTES } ||
                            total !in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES
                        ) {
                            Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                        } else {
                            onStart(segments.toList())
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    PresetSaveDialogMiuix(
        show = showSavePreset,
        // 保存整个分段列表（含休息）；名称必填
        segments = segments,
        onDismiss = { showSavePreset = false },
    )
    PresetManageDialogMiuix(
        show = showManagePresets,
        onDismiss = { showManagePresets = false },
    )
    // 段时长输入对话框（嵌套在时间选择对话框之上；点时长胶囊触发）
    val index = durationDialogIndex
    val seg = segments.getOrNull(index) ?: FocusStore.Segment(FocusStore.SEGMENT_FOCUS, 0)
    SegmentMinutesDialogMiuix(
        show = index in segments.indices,
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
    // 按时间段调整分段：选择该段新的结束时刻 → 时长自动反算，后续段顺延
    // 单段最大 240 分钟（普通专注总时长上限）；新总时长超 240 在点开始时统一校验
    MiuixTimePickerDialog(
        show = editingEndIndex >= 0 && editingEndIndex < segmentBounds.size,
        initialHour = segmentBounds.getOrNull(editingEndIndex.coerceAtLeast(0))?.let { (it.second % 1440) / 60 } ?: 0,
            initialMinute = segmentBounds.getOrNull(editingEndIndex.coerceAtLeast(0))?.let { (it.second % 1440) % 60 } ?: 0,
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

/** 预设快捷选择 chips：显示「名称 段序列」（如「午休 30」「番茄 25+5+25」）；
 *  点击应用整个分段列表；与当前分段完全一致时高亮 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusPresetChipsMiuix(
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
            Button(
                onClick = { onSelect(preset) },
                // 选中态用 miuix 下拉选中语义色（tertiaryContainer 浅蓝底 + onTertiaryContainer 深字），
                // 不用饱和蓝 buttonColorsPrimary（规范：primary 不作选中底色）
                colors = if (preset.segmentList == segments) ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.tertiaryContainer,
                    contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
                ) else ButtonDefaults.buttonColors(),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    // 只显示名称（保存时必填）；旧数据空名回退段序列避免空白
                    text = preset.name.ifBlank { preset.sequenceText },
                    style = MiuixTheme.textStyles.button,
                )
            }
        }
    }
}

/** 保存为预设：保存当前完整分段列表（含休息），名称必填（不支持自动命名） */
@Composable
private fun PresetSaveDialogMiuix(show: Boolean, segments: List<FocusStore.Segment>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    val total = segments.sumOf { it.minutes }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.action_save_preset),
        onDismissRequest = onDismiss,
    ) {
        Column {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.focus_preset_name),
                useLabelAsPlaceholder = true,
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
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = segmentsSummaryText(segments),
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
                        when {
                            name.trim().isBlank() ->
                                Toast.makeText(context, context.getString(R.string.focus_preset_name_required), Toast.LENGTH_SHORT).show()
                            segments.any { it.minutes < FocusStore.MIN_MINUTES } ||
                                total !in FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES ->
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
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 管理预设：长按拖动排序（与应用集/计划列表同款：行放大跟手，其余行 animateItem 平滑让位）+ 删除 */
@Composable
private fun PresetManageDialogMiuix(show: Boolean, onDismiss: () -> Unit) {
    var presets by remember { mutableStateOf(FocusStore.presets.toList()) }
    val listState = rememberLazyListState()
    val latestPresets by rememberUpdatedState(presets)

    OverlayDialog(
        show = show,
        title = stringResource(R.string.action_manage_presets),
        onDismissRequest = onDismiss,
    ) {
        Column {
            if (presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_empty_presets),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                Text(
                    text = stringResource(R.string.focus_preset_drag_hint),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                // Reorderable：随手指接近屏幕边缘自动滚动、内部 requestScrollToItem 处理 LazyColumn
// 的索引锚定视口滑动（自实现方案「拖到顶部不跟手/拖出界限断触」的根因）
val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
    val newList = latestPresets.toMutableList().apply { add(to.index, removeAt(from.index)) }
    presets = newList
    FocusStore.presets.clear()
    FocusStore.presets.addAll(newList)
}
LazyColumn(state = listState, modifier = Modifier.heightIn(max = 320.dp)) {
                    items(presets, key = { it.id }) { preset ->
                        ReorderableItem(reorderableState, key = preset.id) { isDragging ->
                            val scale by animateFloatAsState(
                                targetValue = if (isDragging) 1.04f else 1f,
                                animationSpec = spring(stiffness = Spring.StiffnessLow),
                                label = "presetDragScale",
                            )
                            Column(
                            modifier = Modifier
                                .scale(scale)
                                .longPressDraggableHandle(
                                    onDragStopped = { FocusStore.savePresets() },
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        // 只显示名称（保存时必填）；旧数据空名回退段序列避免空白
                                        text = preset.name.ifBlank { preset.sequenceText },
                                        modifier = Modifier.weight(1f),
                                        style = MiuixTheme.textStyles.body1,
                                    )
                                    IconButton(onClick = {
                                        FocusStore.presets.removeAll { it.id == preset.id }
                                        FocusStore.savePresets()
                                        presets = FocusStore.presets.toList()
                                    }) {
                                        Icon(
                                            imageVector = MiuixIcons.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        )
                                    }
                                }
                        
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.action_confirm),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
