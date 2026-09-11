package com.frosthush.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.frosthush.app.FrostHushApp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler
import com.frosthush.app.ui.about.AboutScreen
import com.frosthush.app.ui.component.bottombar.BottomBar
import com.frosthush.app.ui.component.bottombar.LocalMainPagerState
import com.frosthush.app.ui.component.bottombar.MainPagerState
import com.frosthush.app.ui.component.bottombar.MainTab
import com.frosthush.app.ui.component.bottombar.SideRail
import com.frosthush.app.ui.component.bottombar.rememberMainPagerState
import com.frosthush.app.ui.component.bottombar.useNavigationRail
import com.frosthush.app.ui.focus.FocusLockScreen
import com.frosthush.app.ui.focus.FocusScreen
import com.frosthush.app.ui.focus.ImportScreen
import com.frosthush.app.ui.group.AppGroupScreen
import com.frosthush.app.ui.navigation3.LocalNavigator
import com.frosthush.app.ui.navigation3.Navigator
import com.frosthush.app.ui.navigation3.Route
import com.frosthush.app.ui.navigation3.rememberNavigator
import com.frosthush.app.ui.plan.PlanEditScreen
import com.frosthush.app.ui.plan.PlanScreen
import com.frosthush.app.ui.settings.ConfigImportScreen
import com.frosthush.app.ui.settings.SettingsScreen
import com.frosthush.app.ui.settings.ThemeSettingsScreen
import com.frosthush.app.ui.stats.StatsScreen
import com.frosthush.app.ui.theme.LocalEnableBlur
import com.frosthush.app.ui.theme.LocalEnableFloatingBottomBar
import com.frosthush.app.ui.theme.LocalEnableFloatingBottomBarBlur
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode
import com.frosthush.app.ui.util.rememberBlurBackdrop
import java.util.Calendar
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用根组件：
 * 首次启动显示欢迎/权限页；之后进入主界面（底栏 + 5 个 tab 的横向 Pager）。
 * 专注进行中时在最顶层叠加全屏锁定倒计时（不可打断）。
 */
@Composable
fun AppRoot() {
    var welcomeDone by remember { mutableStateOf(SettingsStore.cache.welcomeDone) }
    LaunchedEffect(Unit) {
        SettingsStore.welcomeDone.collect { welcomeDone = it }
    }
    // 进入主界面（前台）后再预加载应用名称缓存：
    // 包列表查询必须发生在应用前台，MIUI 才会弹「允许获取应用列表」确认框
    // （若在 Application.onCreate 后台预加载，会抢先触发查询并被静默拒绝）。
    LaunchedEffect(welcomeDone) {
        if (welcomeDone) FrostHushApp.app.preloadAppNames()
    }
    val version by FocusManager.version.collectAsState()
    // 当前阶段（FocusService 每秒更新）：休息段时锁屏隐藏，专注段重新覆盖
    val phase by FocusManager.phase.collectAsState()
    var focusLocked by remember { mutableStateOf(FocusStore.activeSession() != null) }
    LaunchedEffect(version, phase) {
        val session = FocusStore.activeSession()
        // 会话存在且当前非休息段（phase 为 null 时视为专注，避免服务未启动瞬间闪解锁）
        focusLocked = session != null && (phase?.isFocus ?: true)
    }
    // 计划提醒通知点击 → 弹「距开始倒计时」对话框（立刻开始 / 终止）
    val reminderClick by PlanScheduler.reminderClick.collectAsState()
    var showPlanReminder by remember { mutableStateOf(false) }
    var reminderPlanId by remember { mutableStateOf(-1L) }
    LaunchedEffect(reminderClick) {
        reminderClick?.let { click ->
            PlanScheduler.reminderClick.value = null // 消费一次，避免重复弹
            reminderPlanId = click.planId
            showPlanReminder = true
        }
    }
    // 专注已开始（全屏锁屏覆盖）时关闭提醒对话框，避免被遮挡
    LaunchedEffect(focusLocked) {
        if (focusLocked) showPlanReminder = false
    }
    // 统一根背景 = 当前界面风格的背景色，否则透明页面会透出窗口背景（纯白）
    val rootColor = when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
        UiMode.Material -> MaterialTheme.colorScheme.background
    }
    Box(Modifier.fillMaxSize().background(rootColor)) {
        if (!welcomeDone) {
            WelcomeScreen(onFinished = {
                SettingsStore.setWelcomeDone(true)
                welcomeDone = true
            })
        } else {
            MainNavHost()
        }
        // 专注进行中：全屏锁定倒计时覆盖一切（进入淡入 + 缩放落定，结束快速淡出）
        AnimatedVisibility(
            visible = focusLocked,
            enter = fadeIn(tween(250)) + scaleIn(
                initialScale = 1.15f,
                animationSpec = tween(450, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(tween(200)),
        ) {
            FocusLockScreen(onFinished = { focusLocked = false })
        }
        if (showPlanReminder && reminderPlanId > 0) {
            PlanReminderDialog(planId = reminderPlanId, onDismiss = { showPlanReminder = false })
        }
    }
}

/**
 * 页面栈导航（对齐 KernelSU）：主界面（含底栏与 5 个 tab）+ 各二级页面。
 * 转场动画由 navigation3 默认实现提供（横向滑动 + 视差），预测性返回手势自动接入。
 */
@Composable
private fun MainNavHost() {
    val navigator = rememberNavigator(Route.Main)
    // 配置导入预览数据：一次性载荷（不可序列化），与路由同生命周期
    var configImportData by remember { mutableStateOf<FocusStore.ConfigData?>(null) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = navigator.backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            onBack = { navigator.pop() },
            entryProvider = entryProvider {
                entry<Route.Main> {
                    MainScreen(
                        onOpenConfigImport = { data ->
                            configImportData = data
                            navigator.push(Route.ConfigImport)
                        },
                    )
                }
                entry<Route.Import> { ImportScreen(onBack = navigator::pop) }
                entry<Route.AppGroups> { AppGroupScreen(onBack = navigator::pop) }
                entry<Route.PlanEdit> { route ->
                    val plan = remember(route.planId) {
                        if (route.planId < 0) null
                        else FocusStore.focusPlans().firstOrNull { it.id == route.planId }
                    }
                    PlanEditScreen(plan = plan, onBack = navigator::pop)
                }
                entry<Route.ThemeSettings> { ThemeSettingsScreen(onBack = navigator::pop) }
                entry<Route.ConfigImport> {
                    val data = configImportData
                    if (data == null) {
                        // 进程被重建导致载荷丢失：直接退回主界面
                        LaunchedEffect(Unit) { navigator.pop() }
                    } else {
                        ConfigImportScreen(data = data, onBack = navigator::pop)
                    }
                }
            },
        )
    }
}

/**
 * 计划提醒对话框：显示距计划开始剩余秒数（每秒倒计时），可选「立刻开始 / 终止」。
 * 到点后专注由闹钟自动开始（锁屏覆盖），对话框自动关闭。
 */
@Composable
private fun PlanReminderDialog(planId: Long, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val plan = FocusStore.focusPlans().firstOrNull { it.id == planId }
    // 计划已被删除 / 专注已由该计划开始 → 无需弹
    if (plan == null || FocusStore.activeSession()?.planId == planId) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    // 本计划今日开始时刻
    val startMillis = remember(plan) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, plan.startMinute / 60)
            set(Calendar.MINUTE, plan.startMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    var remaining by remember {
        mutableStateOf(((startMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt())
    }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining = ((startMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0).toInt()
        }
        onDismiss() // 到点：专注由闹钟自动开始，关闭对话框
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plan_remind_dialog_title, plan.name)) },
        text = {
            Text(
                if (remaining > 0) {
                    pluralStringResource(R.plurals.plan_remind_dialog_text_seconds, remaining, remaining)
                } else {
                    stringResource(R.string.plan_remind_dialog_starting)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                Thread { PlanScheduler.onStartNow(context, planId) }.start()
            }) { Text(stringResource(R.string.plan_remind_dialog_start_now)) }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                Thread { PlanScheduler.onCancelToday(context, planId) }.start()
            }) { Text(stringResource(R.string.plan_remind_dialog_cancel)) }
        },
    )
}

/**
 * 主界面：底栏 / 侧边栏 + 5 个 tab 的横向 Pager（对齐 KernelSU）。
 *
 * - 竖屏：Scaffold 的 bottomBar 承载 [BottomBar]（Miuix / Material 分派）；
 * - 横屏：左侧 [SideRail]（对应雹 layout-land 的 NavigationRailView）；
 * - Pager 铺满整屏（内容可从底栏下方穿过），各页面只把底栏高度作为
 *   **列表底部内边距**，因此底栏下方不会再出现一条被占位的纯色横条。
 */
@Composable
private fun MainScreen(onOpenConfigImport: (FocusStore.ConfigData) -> Unit) {
    val navigator = LocalNavigator.current
    val uiMode = LocalUiMode.current
    val enableBlur = LocalEnableBlur.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val useRail = useNavigationRail()

    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })
    val mainPagerState = rememberMainPagerState(pagerState, animatePageChanges = !useRail)

    // 悬浮底栏模糊采样所依据的内容图层（背景色 + 页面内容）
    val surfaceColor = when (uiMode) {
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
        UiMode.Material -> MaterialTheme.colorScheme.background
    }
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val liquidGlass = enableFloatingBottomBarBlur &&
        enableFloatingBottomBar &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { mainPagerState.syncPage() }

    MainScreenBackHandler(mainPagerState, navigator)

    CompositionLocalProvider(LocalMainPagerState provides mainPagerState) {
        // 页面内容：整屏铺满并注册进模糊图层；bottomInnerPadding 仅用于列表底部避让
        val pagerContent: @Composable (Dp) -> Unit = { bottomInnerPadding ->
            Box(
                modifier = if (blurBackdrop != null) Modifier.layerBackdrop(blurBackdrop) else Modifier,
            ) {
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (liquidGlass) Modifier.layerBackdrop(backdrop) else Modifier,
                        ),
                    state = pagerState,
                    beyondViewportPageCount = 2,
                    overscrollEffect = null,
                ) { page ->
                    TabPage(
                        tab = MainTab.entries[page],
                        bottomInnerPadding = bottomInnerPadding,
                        mainPagerState = mainPagerState,
                        onOpenConfigImport = onOpenConfigImport,
                    )
                }
            }
        }

        if (useRail) {
            // 横屏：侧边导航栏固定，内容区右侧铺满
            val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Row(Modifier.fillMaxSize()) {
                SideRail()
                Box(Modifier.weight(1f)) { pagerContent(navBarBottomPadding) }
            }
        } else {
            val bottomBar = @Composable {
                BottomBar(
                    blurBackdrop = blurBackdrop,
                    backdrop = backdrop,
                )
            }
            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    bottomBar = bottomBar,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Scaffold(
                    bottomBar = bottomBar,
                    containerColor = MiuixTheme.colorScheme.surface,
                    // 顶栏由各页面自己的 TopAppBar 处理，这里不重复叠加状态栏 inset
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}

/** 单个 tab 页：按当前界面风格分派到对应实现 */
@Composable
private fun TabPage(
    tab: MainTab,
    bottomInnerPadding: Dp,
    mainPagerState: MainPagerState,
    onOpenConfigImport: (FocusStore.ConfigData) -> Unit,
) {
    val navigator = LocalNavigator.current
    when (tab) {
        MainTab.Focus -> FocusScreen(
            onOpenStats = { mainPagerState.animateToPage(MainTab.Stats.ordinal) },
            onImport = { navigator.push(Route.Import) },
            onOpenGroups = { navigator.push(Route.AppGroups) },
            onOpenSettings = { mainPagerState.animateToPage(MainTab.Settings.ordinal) },
            bottomInnerPadding = bottomInnerPadding,
        )

        MainTab.Plan -> PlanScreen(
            onNewPlan = { navigator.push(Route.PlanEdit(PLAN_ID_NEW)) },
            onEditPlan = { navigator.push(Route.PlanEdit(it.id)) },
            bottomInnerPadding = bottomInnerPadding,
        )

        MainTab.Stats -> StatsScreen(bottomInnerPadding = bottomInnerPadding)

        MainTab.Settings -> SettingsScreen(
            onOpenConfigImport = onOpenConfigImport,
            onOpenTheme = { navigator.push(Route.ThemeSettings) },
            bottomInnerPadding = bottomInnerPadding,
        )

        MainTab.About -> AboutScreen(bottomInnerPadding = bottomInnerPadding)
    }
}

/** 新建计划的哨兵 id（对应 Route.PlanEdit 的 planId） */
internal const val PLAN_ID_NEW = -1L

/** 返回键：主界面且不在首个 tab 时回到「专注」页（对齐 KernelSU） */
@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navigator: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navigator.current() is Route.Main &&
                navigator.backStack.size == 1 &&
                mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = { mainState.animateToPage(0) },
    )
}
