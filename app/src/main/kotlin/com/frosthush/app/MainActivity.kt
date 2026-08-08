package com.frosthush.app

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.AppRoot
import com.frosthush.app.ui.theme.FrostHushTheme

/** 单 Activity：欢迎页 / 底栏导航 / 导入页均由 Compose 管理 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 系统栏完全透明且不加对比 scrim（对齐雹的 navigationBarColor transparent +
        // enforceNavigationBarContrast false），让底栏 / 侧边栏背景真正沉浸到手势条区域
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 显式强制：部分 ROM（MIUI 小窗）下仅靠 enableEdgeToEdge 样式仍会给导航栏画
        // 白色背景（Android 15 默认强制导航栏对比），导致小窗底部操作杆区域出现一条
        // 不透明的白色带遮挡应用内容。直接设置透明并关闭强制对比（与雹的 Theme.Base 一致）。
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        setContent {
            // 系统栏图标深浅随主题模式：强制浅色→深色图标，强制深色→浅色图标
            val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
            val dark = when (themeMode) {
                SettingsStore.THEME_LIGHT -> false
                SettingsStore.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            FrostHushTheme {
                AppRoot()
            }
        }
    }
}
