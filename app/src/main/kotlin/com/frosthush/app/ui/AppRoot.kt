package com.frosthush.app.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.FrostHushApp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler
import com.frosthush.app.ui.about.AboutScreen
import com.frosthush.app.ui.focus.FocusLockScreen
import com.frosthush.app.ui.focus.FocusScreen
import com.frosthush.app.ui.focus.ImportScreen
import com.frosthush.app.ui.group.AppGroupScreen
import com.frosthush.app.ui.plan.PlanEditScreen
import com.frosthush.app.ui.plan.PlanScreen
import com.frosthush.app.ui.settings.ConfigImportScreen
import com.frosthush.app.ui.settings.SettingsScreen
import com.frosthush.app.ui.stats.StatsScreen
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * 应用根组件：
 * 首次启动显示欢迎/权限页；之后进入底栏导航（专注/统计/设置/关于）。
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
    // 统一根背景 = 主题背景色（雹色板 F7F9FF），否则透明页面会透出窗口背景（纯白）
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (!welcomeDone) {
            WelcomeScreen(onFinished = {
                SettingsStore.setWelcomeDone(true)
                welcomeDone = true
            })
        } else {
            MainScaffold()
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
                PlanScheduler.onCancelToday(context, planId)
            }) { Text(stringResource(R.string.plan_remind_dialog_cancel)) }
        },
    )
}

@Composable
private fun MainScaffold() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var importing by rememberSaveable { mutableStateOf(false) }
    // 应用集管理页覆盖层
    var showGroups by rememberSaveable { mutableStateOf(false) }
    // 计划编辑页覆盖层：target 为 null 表示新建
    var planEditOpened by remember { mutableStateOf(false) }
    var planEditTarget by remember { mutableStateOf<FocusPlan?>(null) }
    // 配置导入预览页覆盖层：data 为解析后的导入配置
    var configImportOpened by remember { mutableStateOf(false) }
    var configImportData by remember { mutableStateOf<FocusStore.ConfigData?>(null) }

    // 覆盖层时拦截系统返回键：逐层退回主界面而非直接退出应用
    BackHandler(enabled = importing || showGroups || planEditOpened || configImportOpened) {
        when {
            configImportOpened -> configImportOpened = false
            planEditOpened -> planEditOpened = false
            showGroups -> showGroups = false
            importing -> importing = false
        }
    }

    // 导入页与主界面之间平滑过渡（对齐雹 Fragment 切换动画）
    AnimatedContent(
        targetState = importing,
        transitionSpec = {
            if (targetState) {
                (fadeIn() + slideInHorizontally { it / 4 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 4 })
            } else {
                (fadeIn() + slideInHorizontally { -it / 4 }) togetherWith (fadeOut() + slideOutHorizontally { it / 4 })
            }
        },
        label = "importTransition",
    ) { isImporting ->
        if (isImporting) {
            ImportScreen(onBack = { importing = false })
        } else {
            // 应用集 / 计划编辑 / 配置导入覆盖层与主界面之间淡入淡出过渡
            AnimatedContent(
                targetState = when {
                    configImportOpened -> 3
                    planEditOpened -> 2
                    showGroups -> 1
                    else -> 0
                },
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "overlayTransition",
            ) { key ->
                when (key) {
                    1 -> AppGroupScreen(onBack = { showGroups = false })
                    2 -> PlanEditScreen(plan = planEditTarget, onBack = { planEditOpened = false })
                    3 -> configImportData?.let {
                        ConfigImportScreen(data = it, onBack = { configImportOpened = false })
                    }
                    else -> MainTabs(
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpenStats = { tab = 2 },
                        onImport = { importing = true },
                        onOpenGroups = { showGroups = true },
                        onNewPlan = { planEditTarget = null; planEditOpened = true },
                        onEditPlan = { planEditTarget = it; planEditOpened = true },
                        onOpenConfigImport = { data ->
                            configImportData = data
                            configImportOpened = true
                        },
                    )
                }
            }
        }
    }
}

/** 底栏 / 侧边栏主界面（5 个 tab：专注 / 计划 / 统计 / 设置 / 关于） */
@Composable
private fun MainTabs(
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpenStats: () -> Unit,
    onImport: () -> Unit,
    onOpenGroups: () -> Unit,
    onNewPlan: () -> Unit,
    onEditPlan: (FocusPlan) -> Unit,
    onOpenConfigImport: (FocusStore.ConfigData) -> Unit,
) {
    val tabs = listOf(
        TabSpec(R.string.tab_focus, Icons.Filled.Timer),
        TabSpec(R.string.tab_plan, Icons.Filled.CalendarMonth),
        TabSpec(R.string.tab_stats, Icons.Filled.BarChart),
        TabSpec(R.string.tab_settings, Icons.Filled.Settings),
        TabSpec(R.string.tab_about, Icons.Filled.Info),
    )

    // 横屏：底栏自动变为侧边导航栏（对应雹 layout-land 的 NavigationRailView）。
    // 侧边栏背景与页面背景同色，菜单项垂直居中（对应雹的 menuGravity="center"）。
    if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Spacer(Modifier.weight(1f))
                tabs.forEachIndexed { index, spec ->
                    NavigationRailItem(
                        selected = tab == index,
                        onClick = { onTabChange(index) },
                        icon = { Icon(spec.icon, contentDescription = null) },
                        label = { Text(stringResource(spec.label)) },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            TabContent(
                tab = tab,
                onOpenStats = onOpenStats,
                onImport = onImport,
                onOpenGroups = onOpenGroups,
                onNewPlan = onNewPlan,
                onEditPlan = onEditPlan,
                onOpenConfigImport = onOpenConfigImport,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Scaffold(
            // 顶部状态栏由各页面自己的 TopAppBar 处理；底部由 bottomBar 兜底覆盖
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                // 底栏背景强制延伸到窗口底部（含小窗底部操作杆区域），不依赖系统
                // navigationBars insets 是否报告：外层 Box 背景铺满 bottomBar 区域，
                // 内部 NavigationBar 仅负责内容并避让操作杆。
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        tabs.forEachIndexed { index, spec ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { onTabChange(index) },
                                icon = { Icon(spec.icon, contentDescription = null) },
                                label = { Text(stringResource(spec.label)) },
                            )
                        }
                    }
                }
            }
        ) { padding ->
            TabContent(
                tab = tab,
                onOpenStats = onOpenStats,
                onImport = onImport,
                onOpenGroups = onOpenGroups,
                onNewPlan = onNewPlan,
                onEditPlan = onEditPlan,
                onOpenConfigImport = onOpenConfigImport,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun TabContent(
    tab: Int,
    onOpenStats: () -> Unit,
    onImport: () -> Unit,
    onOpenGroups: () -> Unit,
    onNewPlan: () -> Unit,
    onEditPlan: (FocusPlan) -> Unit,
    onOpenConfigImport: (FocusStore.ConfigData) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 切换底栏页面时淡入淡出过渡
    AnimatedContent(
        targetState = tab,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "tabTransition",
        modifier = modifier,
    ) { current ->
        when (current) {
            0 -> FocusScreen(onOpenStats = onOpenStats, onImport = onImport, onOpenGroups = onOpenGroups)
            1 -> PlanScreen(onNewPlan = onNewPlan, onEditPlan = onEditPlan)
            2 -> StatsScreen()
            3 -> SettingsScreen(onOpenConfigImport = onOpenConfigImport)
            4 -> AboutScreen()
        }
    }
}

private data class TabSpec(val label: Int, val icon: ImageVector)
