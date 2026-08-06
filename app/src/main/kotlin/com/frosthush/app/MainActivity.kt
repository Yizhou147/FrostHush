package com.frosthush.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.frosthush.app.ui.AppRoot
import com.frosthush.app.ui.theme.FrostHushTheme

/** 单 Activity：欢迎页 / 底栏导航 / 导入页均由 Compose 管理 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FrostHushTheme {
                AppRoot()
            }
        }
    }
}
