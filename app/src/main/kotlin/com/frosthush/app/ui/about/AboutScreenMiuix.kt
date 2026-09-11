package com.frosthush.app.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.BuildConfig
import com.frosthush.app.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页 · miuix 版（HyperOS 设计语言）：
 * 大标题顶栏随内容滚动收起 + 卡片分组（链接项用 ArrowPreference，说明项用静态行）。
 */
@Composable
fun AboutScreenMiuix(bottomInnerPadding: Dp = 0.dp) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.tab_about),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding)
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp + bottomInnerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            // 应用图标：白色圆角卡片（logo 为白底深色图形）
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.frosthush_logo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MiuixTheme.textStyles.title2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            // 编译时间：正式版隐藏（仍写入诊断日志头部），其余构建显示，便于区分测试包
            if (!BuildConfig.IS_OFFICIAL_BUILD) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.about_build_time, BuildConfig.BUILD_TIME),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_description),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card {
                    ArrowPreference(
                        title = stringResource(R.string.about_hail_title),
                        summary = stringResource(R.string.about_hail_url),
                        onClick = { openUrl(context, HAIL_URL) },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.about_project_title),
                        summary = stringResource(R.string.about_project_url),
                        onClick = { openUrl(context, PROJECT_URL) },
                    )
                }
                Card {
                    AboutInfoRow(
                        title = stringResource(R.string.about_opensource),
                        summary = stringResource(R.string.about_opensource_summary),
                    )
                    AboutInfoRow(
                        title = stringResource(R.string.about_privacy),
                        summary = stringResource(R.string.about_privacy_text),
                    )
                }
            }
        }
    }
}

/** 卡片内的静态说明行（标题 + 摘要，不可点击） */
@Composable
private fun AboutInfoRow(title: String, summary: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MiuixTheme.textStyles.body1)
        Spacer(Modifier.height(2.dp))
        Text(
            text = summary,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
