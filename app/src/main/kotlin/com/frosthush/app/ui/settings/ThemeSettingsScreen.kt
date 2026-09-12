package com.frosthush.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material.icons.filled.WbIridescent
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.icon.extended.Back
import com.frosthush.app.FrostHushApp
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.theme.UiMode
import kotlin.math.roundToInt

/**
 * 主题设置二级页：深浅模式 / 界面风格 / 模糊 / 悬浮底栏 / 液态玻璃 / 预测性返回 / 界面缩放。
 *
 * 与全项目一致的「双 UI」结构：按当前 [UiMode] 分派到 miuix 版或 material 版实现。
 */
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit, onReplayWelcome: () -> Unit = {}) {
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)
    when (UiMode.fromValue(uiModeValue)) {
        UiMode.Miuix -> ThemeSettingsMiuix(onBack = onBack, onReplayWelcome = onReplayWelcome)
        UiMode.Material -> ThemeSettingsMaterial(onBack = onBack, onReplayWelcome = onReplayWelcome)
    }
}

/** 悬浮底栏液态玻璃是否可用：需开启悬浮底栏，且系统支持运行时着色器（API 33+） */
private fun liquidGlassAvailable(floatingBar: Boolean): Boolean =
    floatingBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 预测性返回开关是「进程级」的 ApplicationInfo 标志（隐藏 API），需立即写回，
 * 重新启动应用后生效（与 KernelSU 行为一致）。
 */
private fun applyPredictiveBack(context: android.content.Context, enable: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        FrostHushApp.setEnableOnBackInvokedCallback(context.applicationInfo, enable)
    }
}

// ==================== miuix 版 ====================

@Composable
private fun ThemeSettingsMiuix(onBack: () -> Unit, onReplayWelcome: () -> Unit = {}) {
    val context = LocalContext.current
    val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)
    val enableBlur by SettingsStore.enableBlur.collectAsState(initial = SettingsStore.cache.enableBlur)
    val dynamicBackground by SettingsStore.enableDynamicBackground
        .collectAsState(initial = SettingsStore.cache.enableDynamicBackground)
    val floatingBar by SettingsStore.enableFloatingBottomBar
        .collectAsState(initial = SettingsStore.cache.enableFloatingBottomBar)
    val floatingBarBlur by SettingsStore.enableFloatingBottomBarBlur
        .collectAsState(initial = SettingsStore.cache.enableFloatingBottomBarBlur)
    val predictiveBack by SettingsStore.enablePredictiveBack
        .collectAsState(initial = SettingsStore.cache.enablePredictiveBack)
    val pageScale by SettingsStore.pageScale.collectAsState(initial = SettingsStore.cache.pageScale)

    val themeItems = listOf(
        stringResource(R.string.settings_theme_system),
        stringResource(R.string.settings_theme_light),
        stringResource(R.string.settings_theme_dark),
    )
    val styleItems = listOf(
        stringResource(R.string.settings_ui_style_miuix),
        stringResource(R.string.settings_ui_style_material),
    )
    val styleLabel = if (UiMode.fromValue(uiModeValue) == UiMode.Miuix) {
        stringResource(R.string.settings_ui_style_miuix)
    } else {
        stringResource(R.string.settings_ui_style_material)
    }
    val scrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior()

    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = stringResource(R.string.settings_theme_page_title),
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                        top.yukonga.miuix.kmp.basic.Icon(top.yukonga.miuix.kmp.icon.MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
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
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 外观：主题模式 + 界面风格
            top.yukonga.miuix.kmp.basic.Card {
                top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
                    items = themeItems,
                    selectedIndex = themeMode.coerceIn(themeItems.indices),
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(R.string.settings_theme_mode_summary),
                    onSelectedIndexChange = { SettingsStore.setThemeMode(it) },
                )
                top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
                    items = styleItems,
                    selectedIndex = if (UiMode.fromValue(uiModeValue) == UiMode.Miuix) 0 else 1,
                    title = stringResource(R.string.settings_ui_style),
                    summary = stringResource(R.string.settings_ui_style_summary, styleLabel),
                    onSelectedIndexChange = {
                        SettingsStore.setUiMode(if (it == 0) SettingsStore.UI_MODE_MIUIX else SettingsStore.UI_MODE_MATERIAL)
                    },
                )
            }
            // 视觉效果：模糊 + 动态背景
            top.yukonga.miuix.kmp.basic.Card {
                top.yukonga.miuix.kmp.preference.SwitchPreference(
                    checked = enableBlur,
                    onCheckedChange = { SettingsStore.setEnableBlur(it) },
                    title = stringResource(R.string.settings_enable_blur),
                    summary = stringResource(R.string.settings_enable_blur_summary),
                )
                top.yukonga.miuix.kmp.preference.SwitchPreference(
                    checked = dynamicBackground,
                    onCheckedChange = { SettingsStore.setEnableDynamicBackground(it) },
                    title = stringResource(R.string.settings_dynamic_background),
                    summary = stringResource(R.string.settings_dynamic_background_summary),
                )
            }
            // 底栏：悬浮底栏 + 液态玻璃
            top.yukonga.miuix.kmp.basic.Card {
                top.yukonga.miuix.kmp.preference.SwitchPreference(
                    checked = floatingBar,
                    onCheckedChange = { SettingsStore.setEnableFloatingBottomBar(it) },
                    title = stringResource(R.string.settings_floating_bottom_bar),
                    summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                )
                if (liquidGlassAvailable(floatingBar)) {
                    top.yukonga.miuix.kmp.preference.SwitchPreference(
                        checked = floatingBarBlur,
                        onCheckedChange = { SettingsStore.setEnableFloatingBottomBarBlur(it) },
                        title = stringResource(R.string.settings_floating_bar_blur),
                        summary = stringResource(R.string.settings_floating_bar_blur_summary),
                    )
                }
            }
            // 手势与显示：预测性返回 + 界面缩放
            top.yukonga.miuix.kmp.basic.Card {
                top.yukonga.miuix.kmp.preference.SwitchPreference(
                    checked = predictiveBack,
                    onCheckedChange = {
                        SettingsStore.setEnablePredictiveBack(it)
                        applyPredictiveBack(context, it)
                    },
                    title = stringResource(R.string.settings_enable_predictive_back),
                    summary = stringResource(R.string.settings_enable_predictive_back_summary),
                )
                top.yukonga.miuix.kmp.preference.SliderPreference(
                    value = pageScale,
                    onValueChange = { SettingsStore.setPageScale(it) },
                    title = stringResource(R.string.settings_page_scale),
                    summary = stringResource(R.string.settings_page_scale_summary),
                    valueText = "${(pageScale * 100).roundToInt()}%",
                    valueRange = SettingsStore.PAGE_SCALE_RANGE,
                    steps = 7,
                )
            }
            top.yukonga.miuix.kmp.basic.Card {
                top.yukonga.miuix.kmp.preference.ArrowPreference(
                    title = stringResource(R.string.settings_replay_welcome),
                    summary = stringResource(R.string.settings_replay_welcome_summary),
                    onClick = {
                        onBack()
                        onReplayWelcome()
                    },
                )
            }
        }
    }
}

// ==================== material 版 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsMaterial(onBack: () -> Unit, onReplayWelcome: () -> Unit = {}) {
    val context = LocalContext.current
    val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)
    val enableBlur by SettingsStore.enableBlur.collectAsState(initial = SettingsStore.cache.enableBlur)
    val dynamicBackground by SettingsStore.enableDynamicBackground
        .collectAsState(initial = SettingsStore.cache.enableDynamicBackground)
    val floatingBar by SettingsStore.enableFloatingBottomBar
        .collectAsState(initial = SettingsStore.cache.enableFloatingBottomBar)
    val floatingBarBlur by SettingsStore.enableFloatingBottomBarBlur
        .collectAsState(initial = SettingsStore.cache.enableFloatingBottomBarBlur)
    val predictiveBack by SettingsStore.enablePredictiveBack
        .collectAsState(initial = SettingsStore.cache.enablePredictiveBack)
    val pageScale by SettingsStore.pageScale.collectAsState(initial = SettingsStore.cache.pageScale)

    var showThemeDialog by remember { mutableStateOf(false) }
    var showStyleDialog by remember { mutableStateOf(false) }

    val themeLabel = when (themeMode) {
        SettingsStore.THEME_LIGHT -> stringResource(R.string.settings_theme_light)
        SettingsStore.THEME_DARK -> stringResource(R.string.settings_theme_dark)
        else -> stringResource(R.string.settings_theme_system)
    }
    val styleLabel = if (UiMode.fromValue(uiModeValue) == UiMode.Miuix) {
        stringResource(R.string.settings_ui_style_miuix)
    } else {
        stringResource(R.string.settings_ui_style_material)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_theme_page_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingCard(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_theme),
                summary = themeLabel,
                onClick = { showThemeDialog = true },
                trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
            )
            SettingCard(
                icon = Icons.Filled.Brush,
                title = stringResource(R.string.settings_ui_style),
                summary = styleLabel,
                onClick = { showStyleDialog = true },
                trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
            )
            SettingCard(
                icon = Icons.Filled.BlurOn,
                title = stringResource(R.string.settings_enable_blur),
                summary = stringResource(R.string.settings_enable_blur_summary),
                onClick = { SettingsStore.setEnableBlur(!enableBlur) },
                trailing = { Switch(checked = enableBlur, onCheckedChange = { SettingsStore.setEnableBlur(it) }) },
            )
            SettingCard(
                icon = Icons.Filled.WbIridescent,
                title = stringResource(R.string.settings_dynamic_background),
                summary = stringResource(R.string.settings_dynamic_background_summary),
                onClick = { SettingsStore.setEnableDynamicBackground(!dynamicBackground) },
                trailing = {
                    Switch(
                        checked = dynamicBackground,
                        onCheckedChange = { SettingsStore.setEnableDynamicBackground(it) },
                    )
                },
            )
            SettingCard(
                icon = Icons.Filled.Layers,
                title = stringResource(R.string.settings_floating_bottom_bar),
                summary = stringResource(R.string.settings_floating_bottom_bar_summary),
                onClick = { SettingsStore.setEnableFloatingBottomBar(!floatingBar) },
                trailing = {
                    Switch(checked = floatingBar, onCheckedChange = { SettingsStore.setEnableFloatingBottomBar(it) })
                },
            )
            if (liquidGlassAvailable(floatingBar)) {
                SettingCard(
                    icon = Icons.Filled.Layers,
                    title = stringResource(R.string.settings_floating_bar_blur),
                    summary = stringResource(R.string.settings_floating_bar_blur_summary),
                    onClick = { SettingsStore.setEnableFloatingBottomBarBlur(!floatingBarBlur) },
                    trailing = {
                        Switch(
                            checked = floatingBarBlur,
                            onCheckedChange = { SettingsStore.setEnableFloatingBottomBarBlur(it) },
                        )
                    },
                )
            }
            SettingCard(
                icon = Icons.Filled.Gesture,
                title = stringResource(R.string.settings_enable_predictive_back),
                summary = stringResource(R.string.settings_enable_predictive_back_summary),
                onClick = {
                    SettingsStore.setEnablePredictiveBack(!predictiveBack)
                    applyPredictiveBack(context, !predictiveBack)
                },
                trailing = {
                    Switch(
                        checked = predictiveBack,
                        onCheckedChange = {
                            SettingsStore.setEnablePredictiveBack(it)
                            applyPredictiveBack(context, it)
                        },
                    )
                },
            )
            // 界面缩放：滑杆（0.8x ~ 1.2x）
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_page_scale),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.size(2.dp))
                            Text(
                                stringResource(R.string.settings_page_scale_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${(pageScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = pageScale,
                        onValueChange = { SettingsStore.setPageScale(it) },
                        valueRange = SettingsStore.PAGE_SCALE_RANGE,
                        steps = 7,
                    )
                }
            }
            SettingCard(
                icon = Icons.Filled.WavingHand,
                title = stringResource(R.string.settings_replay_welcome),
                summary = stringResource(R.string.settings_replay_welcome_summary),
                onClick = {
                    onBack()
                    onReplayWelcome()
                },
                trailing = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
            )
        }
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark),
            ),
            selectedIndex = themeMode.coerceIn(0, 2),
            onSelect = {
                SettingsStore.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showStyleDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_ui_style),
            options = listOf(
                stringResource(R.string.settings_ui_style_miuix),
                stringResource(R.string.settings_ui_style_material),
            ),
            selectedIndex = if (UiMode.fromValue(uiModeValue) == UiMode.Miuix) 0 else 1,
            onSelect = {
                SettingsStore.setUiMode(if (it == 0) SettingsStore.UI_MODE_MIUIX else SettingsStore.UI_MODE_MATERIAL)
                showStyleDialog = false
            },
            onDismiss = { showStyleDialog = false },
        )
    }
}

/** 单选对话框（主题模式 / 界面风格共用） */
@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedIndex == index, onClick = { onSelect(index) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
        },
    )
}
