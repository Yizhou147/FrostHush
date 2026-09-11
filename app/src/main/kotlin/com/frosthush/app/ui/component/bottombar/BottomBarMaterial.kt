// Adapted from KernelSU (ui/component/bottombar/BottomBarMaterial.kt) — Apache 2.0.
package com.frosthush.app.ui.component.bottombar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

/**
 * Material 底栏：与改造前的实现保持一致（外层 Box 铺满底栏区域并延伸到手势条，
 * 内部 NavigationBar 只负责内容并避让操作杆）。
 */
@Composable
fun BottomBarMaterial(modifier: Modifier = Modifier) {
    val mainState = LocalMainPagerState.current

    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        NavigationBar(
            containerColor = Color.Transparent,
            windowInsets = WindowInsets.navigationBars,
        ) {
            MainTab.entries.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = mainState.selectedPage == index,
                    onClick = { mainState.animateToPage(index) },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        }
    }
}
