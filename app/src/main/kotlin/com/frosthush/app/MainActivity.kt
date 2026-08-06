package com.frosthush.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        setContent {
            FrostHushTheme {
                AppRoot()
            }
        }
    }
}
