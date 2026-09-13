package com.frosthush.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import com.frosthush.app.data.AppRepository
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 欢迎页（首次启动 / 「重新查看引导」重放）：分页式引导——
 * 外观选择（主题模式 + 界面风格）→ 权限 → Shizuku → 省电与自启动 → 就绪清单。
 * 版式对齐 HuaweiPods OnboardingPage（顶栏跳过 + Pager 翻页 + 圆点指示 + 上一步/下一步）。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）；
 * 第 1 页切换「界面风格」后由本分派器整体切换到另一套实现（主题选择即时生效）。
 */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val uiMode = LocalUiMode.current
    // 切换「界面风格」时两套实现交叉淡入淡出，消除瞬间跳变
    AnimatedContent(
        targetState = uiMode,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        label = "welcomeUiMode",
    ) { mode ->
        when (mode) {
            UiMode.Miuix -> WelcomeScreenMiuix(onFinished = onFinished)
            UiMode.Material -> WelcomeScreenMaterial(onFinished = onFinished)
        }
    }
}

/** 是否已授予通知权限（API 33 以下自动视为已获取） */
internal fun checkNotification(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true // 低版本自动视为已获取

/** 是否可查询已安装应用列表 */
internal fun checkApps(context: Context): Boolean =
    // includeClones=false：仅检查主用户应用列表，避免 Shizuku 跨用户 IPC（主线程调用）
    runCatching { AppRepository(context).queryApps(includeClones = false).isNotEmpty() }.getOrDefault(false)
