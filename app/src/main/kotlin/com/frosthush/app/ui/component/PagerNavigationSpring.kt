// Adapted from KernelSU (ui/component/PagerNavigationSpring.kt) — Apache 2.0.
package com.frosthush.app.ui.component

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 底栏 tab 之间翻页所用的弹簧曲线（与 KernelSU 完全一致）：
 * 点击底栏时页面以该弹簧滑动到目标页，而非瞬间切换。
 */
internal val PagerNavigationSpringSpec: SpringSpec<Float> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * kotlin.math.sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)
