package com.frosthush.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import com.frosthush.app.data.AppRepository
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 首次启动欢迎页：逐项列出权限（通知 / 已安装应用 / Shizuku），
 * 每项显示 ✓/✗ 实时状态与「去授权」按钮；onResume 时重新检查全部权限。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> WelcomeScreenMiuix(onFinished = onFinished)
        UiMode.Material -> WelcomeScreenMaterial(onFinished = onFinished)
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
