package com.frosthush.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.about.AboutScreen
import com.frosthush.app.ui.focus.FocusScreen
import com.frosthush.app.ui.focus.ImportScreen
import com.frosthush.app.ui.settings.SettingsScreen
import com.frosthush.app.ui.stats.StatsScreen

/**
 * 应用根组件：
 * 首次启动显示欢迎/权限页；之后进入底栏导航（专注/统计/设置/关于）。
 */
@Composable
fun AppRoot() {
    var welcomeDone by remember { mutableStateOf(SettingsStore.cache.welcomeDone) }
    LaunchedEffect(Unit) {
        SettingsStore.welcomeDone.collect { welcomeDone = it }
    }
    if (!welcomeDone) {
        WelcomeScreen(onFinished = {
            SettingsStore.setWelcomeDone(true)
            welcomeDone = true
        })
    } else {
        MainScaffold()
    }
}

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

    Scaffold(
        bottomBar = {
            NavigationBar {
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> FocusScreen(
                    onOpenStats = { tab = 1 },
                    onImport = { importing = true },
                )
                1 -> StatsScreen()
                2 -> SettingsScreen()
                3 -> AboutScreen()
            }
        }
    }
}

private data class TabSpec(val label: Int, val icon: ImageVector)
