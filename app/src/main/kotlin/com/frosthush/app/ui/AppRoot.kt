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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.ui.about.AboutScreen
import com.frosthush.app.ui.focus.FocusLockScreen
import com.frosthush.app.ui.focus.FocusScreen
import com.frosthush.app.ui.focus.ImportScreen
import com.frosthush.app.ui.group.AppGroupScreen
import com.frosthush.app.ui.plan.PlanEditScreen
import com.frosthush.app.ui.plan.PlanScreen
import com.frosthush.app.ui.settings.SettingsScreen
import com.frosthush.app.ui.stats.StatsScreen

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
    val version by FocusManager.version.collectAsState()
    var focusLocked by remember { mutableStateOf(FocusStore.activeSession() != null) }
    LaunchedEffect(version) {
        focusLocked = FocusStore.activeSession() != null
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
    }
}

/** 底栏 / 侧边栏容器色：浅灰（对应雹的 surfaceContainer） */
private val NavContainer = Color(0xFFECEEF4)

@Composable
private fun MainScaffold() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var importing by rememberSaveable { mutableStateOf(false) }
    // 应用集管理页覆盖层
    var showGroups by rememberSaveable { mutableStateOf(false) }
    // 计划编辑页覆盖层：target 为 null 表示新建
    var planEditOpened by remember { mutableStateOf(false) }
    var planEditTarget by remember { mutableStateOf<FocusPlan?>(null) }

    // 覆盖层时拦截系统返回键：逐层退回主界面而非直接退出应用
    BackHandler(enabled = importing || showGroups || planEditOpened) {
        when {
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
            // 应用集 / 计划编辑覆盖层与主界面之间淡入淡出过渡
            AnimatedContent(
                targetState = when {
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
                    else -> MainTabs(
                        tab = tab,
                        onTabChange = { tab = it },
                        onOpenStats = { tab = 2 },
                        onImport = { importing = true },
                        onOpenGroups = { showGroups = true },
                        onNewPlan = { planEditTarget = null; planEditOpened = true },
                        onEditPlan = { planEditTarget = it; planEditOpened = true },
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
                Box(Modifier.fillMaxWidth().background(NavContainer)) {
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
            3 -> SettingsScreen()
            4 -> AboutScreen()
        }
    }
}

private data class TabSpec(val label: Int, val icon: ImageVector)
