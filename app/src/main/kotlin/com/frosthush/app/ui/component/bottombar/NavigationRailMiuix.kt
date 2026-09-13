package com.frosthush.app.ui.component.bottombar

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Miuix 横屏侧边导航栏（不传 state，保持经典不可展开布局） */
@Composable
fun NavigationRailMiuix(modifier: Modifier = Modifier) {
    val mainState = LocalMainPagerState.current

    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        color = MiuixTheme.colorScheme.surface,
    ) {
        MainTab.entries.forEachIndexed { index, tab ->
            NavigationRailItem(
                selected = mainState.selectedPage == index,
                onClick = { mainState.animateToPage(index) },
                icon = tab.icon,
                label = stringResource(tab.label),
            )
        }
    }
}
