package com.frosthush.app.ui.component.bottombar

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Material 横屏侧边导航栏：背景与页面背景同色，菜单项垂直居中
 * （对应雹 layout-land 的 NavigationRailView / menuGravity="center"）。
 */
@Composable
fun NavigationRailMaterial(modifier: Modifier = Modifier) {
    val mainState = LocalMainPagerState.current

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxHeight(),
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        MainTab.entries.forEachIndexed { index, tab ->
            NavigationRailItem(
                selected = mainState.selectedPage == index,
                onClick = { mainState.animateToPage(index) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
    }
}
