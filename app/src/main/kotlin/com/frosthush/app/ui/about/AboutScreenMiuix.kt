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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.ColorFilter
import com.frosthush.app.BuildConfig
import com.frosthush.app.R
import com.frosthush.app.ui.component.miuix.effect.BgEffectBackground
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.theme.isInDarkTheme
import com.frosthush.app.update.UpdateChecker
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
    val scrollState = rememberScrollState()

    // 滚动联动收起动画（对齐 KernelSU 关于页）：头部（图标+名称+版本）整体高度为进度基准，
    // 上滚时 版本 → 应用名 → 图标 依次淡出并轻微缩小（后滚到的先消失）
    var headerHeightPx by remember { mutableFloatStateOf(0f) }
    val scrollProgress = if (headerHeightPx > 0f) (scrollState.value / headerHeightPx).coerceIn(0f, 1f) else 0f
    val versionProgress = (scrollProgress / 0.5f).coerceIn(0f, 1f)
    val nameProgress = ((scrollProgress - 0.15f) / 0.5f).coerceIn(0f, 1f)
    val iconProgress = ((scrollProgress - 0.3f) / 0.5f).coerceIn(0f, 1f)

    // 动态背景（对齐 KernelSU 关于页）：AGSL 着色器流动色块，仅在「模糊开启 + Android 15+」运行动态；
    // 不满足时退化为纯 surface 背景（API <33 直接走普通 Box），滚动收起时整体淡出（alpha 随 scrollProgress 降）
    // 动态背景独立开关（不再挂靠「模糊」开关）；运行条件 = 开关开启 + Android 15+（AGSL 着色器）
    val dynamicBackgroundEnabled by SettingsStore.enableDynamicBackground
        .collectAsState(initial = SettingsStore.cache.enableDynamicBackground)
    val effectBackground = remember(dynamicBackgroundEnabled) {
        isRuntimeShaderSupported() && dynamicBackgroundEnabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    }
    // 背景图层注册：logo 的 textureBlur 采样它，实现图标融入背景的混合观感（KernelSU 同款）
    val backdrop = rememberLayerBackdrop()
    val isDark = isInDarkTheme()
    val logoBlend = remember(isDark) {
        if (isDark) {
            listOf(
                BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
                BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
            )
        } else {
            listOf(
                BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
                BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
                BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
            )
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.tab_about),
                scrollBehavior = scrollBehavior,
                // 顶栏透明：动态背景延伸到状态栏/顶栏区域（背景全屏）
                color = Color.Transparent,
            )
        },
    ) { padding ->
        BgEffectBackground(
            dynamicBackground = effectBackground,
            modifier = Modifier.fillMaxSize(),
            bgModifier = Modifier.layerBackdrop(backdrop),
            isFullSize = true,
            effectBackground = effectBackground,
            alpha = { 1f - scrollProgress },
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                // 背景需延伸到顶栏区域：内容不用 padding(top) 占位，改为首部留出顶栏高度空白
                .padding(horizontal = 12.dp)
                .padding(bottom = 16.dp + bottomInnerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 24.dp))
            Column(
                Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height.toFloat() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 应用图标双形态：动态背景开启 → 单色剪影 + textureBlur 融合（方案A，对齐 KernelSU）；
                // 关闭 → 原版「白色圆角卡片 + 阴影」彩色 logo（与 material 版关于页一致）
                if (effectBackground) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clipToBounds()
                            .graphicsLayer {
                                alpha = 1f - iconProgress
                                scaleX = 1f - iconProgress * 0.05f
                                scaleY = 1f - iconProgress * 0.05f
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.frosthush_logo_mono),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onBackground),
                            modifier = Modifier
                                .requiredSize(150.dp)
                                .textureBlur(
                                    backdrop = backdrop,
                                    shape = RoundedCornerShape(0.dp),
                                    blurRadius = 150f,
                                    colors = BlurColors(blendColors = logoBlend),
                                    contentBlendMode = ComposeBlendMode.DstIn,
                                ),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .graphicsLayer {
                                alpha = 1f - iconProgress
                                scaleX = 1f - iconProgress * 0.05f
                                scaleY = 1f - iconProgress * 0.05f
                            }
                            .shadow(4.dp, RoundedCornerShape(20.dp))
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
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.title2,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - nameProgress
                        scaleX = 1f - nameProgress * 0.05f
                        scaleY = 1f - nameProgress * 0.05f
                    },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.graphicsLayer { alpha = 1f - versionProgress },
                )
                // 编译时间：正式版隐藏（仍写入诊断日志头部），其余构建显示，便于区分测试包
                if (!BuildConfig.IS_OFFICIAL_BUILD) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.about_build_time, BuildConfig.BUILD_TIME),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.graphicsLayer { alpha = 1f - versionProgress },
                    )
                }
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
