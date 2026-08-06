package com.frosthush.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
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
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.ui.about.AboutScreen
import com.frosthush.app.ui.focus.FocusLockScreen
import com.frosthush.app.ui.focus.FocusScreen
import com.frosthush.app.ui.focus.ImportScreen
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
        // 专注进行中：全屏锁定倒计时覆盖一切
        if (focusLocked) {
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

    if (importing) {
        ImportScreen(onBack = { importing = false })
        return
    }

    val tabs = listOf(
        TabSpec(R.string.tab_focus, Icons.Filled.Timer),
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
                        onClick = { tab = index },
                        icon = { Icon(spec.icon, contentDescription = null) },
                        label = { Text(stringResource(spec.label)) },
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            TabContent(
                tab = tab,
                onOpenStats = { tab = 1 },
                onImport = { importing = true },
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    } else {
        Scaffold(
            bottomBar = {
                // 对齐雹的 insetter 实现（BottomNavigationView 加 bottom insets padding）：
                // 外层 Box 背景浅灰填满整个底栏区域（含手势条区域），内部 NavigationBar
                // 内容通过 navigationBarsPadding 避让手势条，使手势条区域沉浸为底栏颜色，
                // 小窗 / 手势导航下不再是白色背景。
                Box(Modifier.fillMaxWidth().background(NavContainer)) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        tabs.forEachIndexed { index, spec ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
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
                onOpenStats = { tab = 1 },
                onImport = { importing = true },
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
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (tab) {
            0 -> FocusScreen(onOpenStats = onOpenStats, onImport = onImport)
            1 -> StatsScreen()
            2 -> SettingsScreen()
            3 -> AboutScreen()
        }
    }
}

private data class TabSpec(val label: Int, val icon: ImageVector)
