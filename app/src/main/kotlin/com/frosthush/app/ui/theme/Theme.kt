package com.frosthush.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.frosthush.app.data.SettingsStore
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** miuix 主色定制：miuix 默认蓝（#3482FF）偏浅，按用户要求略加深（仅浅色模式；深色模式保持默认） */
private val MiuixDeepBlue = Color(0xFF2F6FEC)

/** 亮色主题：与雹的 md_theme_*（values/colors.xml）一致 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF32628D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D34),
    secondary = Color(0xFF526070),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F7),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFF695779),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0DBFF),
    onTertiaryContainer = Color(0xFF241532),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC2C7CF),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEFF1F6),
    inversePrimary = Color(0xFF9DCBFC),
    primaryFixed = Color(0xFFCFE5FF),
    onPrimaryFixed = Color(0xFF001D34),
    primaryFixedDim = Color(0xFF9DCBFC),
    onPrimaryFixedVariant = Color(0xFF134A74),
    secondaryFixed = Color(0xFFD6E4F7),
    onSecondaryFixed = Color(0xFF0F1D2A),
    secondaryFixedDim = Color(0xFFBAC8DA),
    onSecondaryFixedVariant = Color(0xFF3A4857),
    tertiaryFixed = Color(0xFFF0DBFF),
    onTertiaryFixed = Color(0xFF241532),
    tertiaryFixedDim = Color(0xFFD4BEE6),
    onTertiaryFixedVariant = Color(0xFF514060),
    surfaceDim = Color(0xFFD8DAE0),
    surfaceBright = Color(0xFFF7F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F9),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    surfaceContainerHighest = Color(0xFFE0E2E8),
)

/** 暗色主题：与雹的 values-night/colors.xml 一致 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DCBFC),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF134A74),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFBAC8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF3A4857),
    onSecondaryContainer = Color(0xFFD6E4F7),
    tertiary = Color(0xFFD4BEE6),
    onTertiary = Color(0xFF392A49),
    tertiaryContainer = Color(0xFF514060),
    onTertiaryContainer = Color(0xFFF0DBFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E2E8),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF8C9199),
    outlineVariant = Color(0xFF42474E),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE0E2E8),
    inverseOnSurface = Color(0xFF2D3135),
    inversePrimary = Color(0xFF32628D),
    primaryFixed = Color(0xFFCFE5FF),
    onPrimaryFixed = Color(0xFF001D34),
    primaryFixedDim = Color(0xFF9DCBFC),
    onPrimaryFixedVariant = Color(0xFF134A74),
    secondaryFixed = Color(0xFFD6E4F7),
    onSecondaryFixed = Color(0xFF0F1D2A),
    secondaryFixedDim = Color(0xFFBAC8DA),
    onSecondaryFixedVariant = Color(0xFF3A4857),
    tertiaryFixed = Color(0xFFF0DBFF),
    onTertiaryFixed = Color(0xFF241532),
    tertiaryFixedDim = Color(0xFFD4BEE6),
    onTertiaryFixedVariant = Color(0xFF514060),
    surfaceDim = Color(0xFF101418),
    surfaceBright = Color(0xFF36393E),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
)

@Composable
fun FrostHushTheme(content: @Composable () -> Unit) {
    // 主题模式：0 跟随系统 / 1 浅色 / 2 深色
    val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
    val dark = when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)
    val uiMode = UiMode.fromValue(uiModeValue)

    // miuix 色板始终构造并包裹（与 MaterialTheme 同构）：
    // 若按 uiMode 分支包裹/不包裹，切换「界面风格」会改变组合树结构，
    // 导致下层全部 remember 状态丢失（页面跳回首页、欢迎覆盖层被关闭）——曾经的真实 bug。
    val miuixColors = remember(dark) {
        if (dark) {
            top.yukonga.miuix.kmp.theme.darkColorScheme()
        } else {
            top.yukonga.miuix.kmp.theme.lightColorScheme(
                primary = MiuixDeepBlue,
                primaryVariant = MiuixDeepBlue,
                // 选中态容器（应用集 chip / 星期胶囊等）：默认 #EAF2FF 太浅，加深一档
                tertiaryContainer = Color(0xFFD8E6FF),
                tertiaryContainerVariant = Color(0xFFD8E6FF),
                onTertiaryContainer = MiuixDeepBlue,
            )
        }
    }
    // MaterialTheme 始终提供：迁移期 material3 组件仍在各页面使用
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        MiuixTheme(colors = miuixColors) {
            CompositionLocalProvider(LocalUiMode provides uiMode) {
                content()
            }
        }
    }
}

/**
 * 当前是否深色（供悬浮底栏等组件按深浅使用不同描边/阴影参数）。
 * 与 [FrostHushTheme] 的判定保持一致：0 跟随系统 / 1 浅色 / 2 深色。
 */
@Composable
fun isInDarkTheme(): Boolean {
    val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
    return when (themeMode) {
        SettingsStore.THEME_LIGHT -> false
        SettingsStore.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
}
