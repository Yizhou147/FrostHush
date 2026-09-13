package com.frosthush.app.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 关于页：图标 / 应用名 / 版本 / 简介；雹原版与本项目仓库链接（点击用浏览器打开）；
 * 开源声明、隐私说明。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun AboutScreen(bottomInnerPadding: Dp = 0.dp) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(bottomInnerPadding = bottomInnerPadding)
        UiMode.Material -> AboutScreenMaterial(bottomInnerPadding = bottomInnerPadding)
    }
}

/** 用系统浏览器打开链接 */
internal fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

internal const val HAIL_URL = "https://github.com/aistra0528/Hail"
internal const val PROJECT_URL = "https://github.com/Yizhou147/FrostHush"
