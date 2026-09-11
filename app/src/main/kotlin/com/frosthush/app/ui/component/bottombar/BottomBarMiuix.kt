// Adapted from KernelSU (ui/component/bottombar/BottomBarMiuix.kt) — Apache 2.0.
package com.frosthush.app.ui.component.bottombar

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frosthush.app.ui.component.FloatingBottomBar
import com.frosthush.app.ui.component.FloatingBottomBarItem
import com.frosthush.app.ui.theme.LocalEnableFloatingBottomBar
import com.frosthush.app.ui.theme.LocalEnableFloatingBottomBarBlur
import com.frosthush.app.ui.util.BlurredBar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix 底栏：默认 miuix NavigationBar（可带顶栏模糊）；
 * 开启「悬浮底栏」后改用自研液态玻璃胶囊（可再叠「液态玻璃」开关）。
 */
@Composable
fun BottomBarMiuix(
    blurBackdrop: LayerBackdrop?,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val mainState = LocalMainPagerState.current
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current

    if (!enableFloatingBottomBar) {
        BlurredBar(backdrop = blurBackdrop) {
            NavigationBar(
                modifier = modifier,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
            ) {
                MainTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        modifier = Modifier.weight(1f),
                        icon = tab.icon,
                        label = stringResource(tab.label),
                        selected = mainState.selectedPage == index,
                        onClick = { mainState.animateToPage(index) },
                    )
                }
            }
        }
    } else {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            .let { inset -> if (inset != 0.dp) 8.dp + inset else 28.dp }
        FloatingBottomBar(
            modifier = modifier
                .pointerInput(Unit) {
                    detectTapGestures { }
                }
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
            selectedIndex = mainState.selectedPage,
            onSelected = { mainState.animateToPage(it) },
            backdrop = backdrop,
            tabsCount = MainTab.entries.size,
            isBlurEnabled = enableFloatingBottomBarBlur,
        ) { activateTab ->
            MainTab.entries.forEachIndexed { index, tab ->
                FloatingBottomBarItem(
                    selected = mainState.selectedPage == index,
                    onClick = { activateTab(index) },
                    // FloatingBottomBar 外层是 Modifier.width(IntrinsicSize.Min)，整条胶囊宽度等于
                    // 各子项固有最小宽度之和；不给最小宽度会缩成文字宽度挤成一团。
                    modifier = Modifier.defaultMinSize(minWidth = 64.dp),
                ) {
                    Icon(imageVector = tab.icon, contentDescription = null)
                    Text(
                        text = stringResource(tab.label),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}
