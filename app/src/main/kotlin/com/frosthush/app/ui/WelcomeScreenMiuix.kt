package com.frosthush.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.ShizukuManager
import com.frosthush.app.ui.settings.checkBatteryOptimization
import com.frosthush.app.ui.settings.openAppSettings
import com.frosthush.app.ui.settings.openAutostartSettings
import com.frosthush.app.ui.settings.openBatterySettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 欢迎页 · miuix 版（HyperOS 设计语言，版式对齐 HuaweiPods OnboardingPage）：
 * 顶栏（图标 + 应用名 + 跳过）→ HorizontalPager 翻页（外观 → 权限 → Shizuku → 省电自启动 → 就绪）
 * → 圆点指示 + 上一步/下一步胶囊页脚；主色渐变背景，翻页/入场动画。
 *
 * 第 1 页含外观选择（主题模式 + 界面风格）：切界面风格后由 [WelcomeScreen] 分派器整体切换到另一套实现。
 * 权限检测逻辑与旧版逐字一致（onResume 重检 / MIUI 应用列表授权引导 / Shizuku 状态机）。
 */
private const val WELCOME_PAGE_COUNT = 5
/** 授权成功的绿色：miuix 无 success 语义色，用应用自有 Color（与全应用一致） */
private val GrantedGreen = Color(0xFF2E9E5B)

@Composable
fun WelcomeScreenMiuix(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shizukuState by ShizukuManager.state.collectAsState()

    var notifGranted by remember { mutableStateOf(checkNotification(context)) }
    var appsGranted by remember { mutableStateOf(checkApps(context)) }
    var batteryExempted by remember { mutableStateOf(checkBatteryOptimization(context)) }
    var showAppsGuide by remember { mutableStateOf(false) }

    fun refresh() {
        notifGranted = checkNotification(context)
        appsGranted = checkApps(context)
        batteryExempted = checkBatteryOptimization(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }

    val pagerState = rememberPagerState(pageCount = { WELCOME_PAGE_COUNT })

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    val surface = MiuixTheme.colorScheme.surface
    val accent = MiuixTheme.colorScheme.primary
    val backgroundBrush = remember(surface, accent) {
        Brush.verticalGradient(
            listOf(
                accent.copy(alpha = 0.10f).compositeOver(surface),
                surface,
                Color(0xFF6E9FE8).copy(alpha = 0.06f).compositeOver(surface),
            ),
        )
    }

    // miuix OverlayDialog 依赖 Scaffold 内提供的 popup host 渲染，放在无 Scaffold 的组合里
    // 静默不显示且无报错。欢迎页是纯自绘布局，包一层透明 Scaffold（容器不画背景，渐变仍由
    // 内容 Column 自绘），授权引导弹窗随之落在 Scaffold 内容内。
    Scaffold(containerColor = Color.Transparent) {
        Column(
            Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
        // 顶栏：图标 + 应用名 + 跳过
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.frosthush_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                style = MiuixTheme.textStyles.body2,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = onFinished,
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = false,
        ) { page ->
            // BoxWithConstraints 提供视口高度：内容不足一屏时垂直居中（对齐 HuaweiPods），超出仍可滚动
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState > initialState
                    if (forward) {
                        (fadeIn(tween(240)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { it / 8 })
                            .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(240)) { -it / 10 })
                    } else {
                        (fadeIn(tween(240)) + slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { -it / 8 })
                            .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(240)) { it / 10 })
                    }
                },
                label = "welcomePage",
            ) { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = viewportHeight)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (current) {
                        0 -> AppearancePageMiuix()
                        1 -> {
                            PageHeaderMiuix(
                                title = stringResource(R.string.welcome_page_permissions),
                                summary = stringResource(R.string.welcome_subtitle),
                            )
                            Spacer(Modifier.height(16.dp))
                            PermissionCard(
                                icon = Icons.Filled.Notifications,
                                title = stringResource(R.string.permission_notification),
                                desc = stringResource(R.string.permission_notification_desc),
                                granted = notifGranted,
                                actionText = stringResource(R.string.action_grant),
                                onAction = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            )
                            PermissionCard(
                                icon = Icons.Filled.Apps,
                                title = stringResource(R.string.permission_apps),
                                desc = stringResource(R.string.permission_apps_desc),
                                granted = appsGranted,
                                actionText = stringResource(R.string.action_grant),
                                // MIUI 平板等设备不弹系统授权框时给应用内引导
                                onAction = { showAppsGuide = true },
                            )
                        }
                        2 -> {
                            PageHeaderMiuix(
                                title = stringResource(R.string.welcome_page_shizuku),
                                summary = stringResource(R.string.permission_shizuku_desc),
                            )
                            Spacer(Modifier.height(16.dp))
                            PermissionCard(
                                icon = Icons.Filled.Lock,
                                title = stringResource(R.string.permission_shizuku),
                                desc = stringResource(R.string.permission_shizuku_desc),
                                granted = shizukuState == ShizukuManager.State.AUTHORIZED,
                                actionText = when (shizukuState) {
                                    ShizukuManager.State.NOT_CONNECTED -> stringResource(R.string.action_start_shizuku)
                                    ShizukuManager.State.UNAUTHORIZED -> stringResource(R.string.action_grant)
                                    ShizukuManager.State.AUTHORIZED -> null
                                },
                                statusText = when (shizukuState) {
                                    ShizukuManager.State.NOT_CONNECTED -> stringResource(R.string.shizuku_state_not_connected)
                                    ShizukuManager.State.UNAUTHORIZED -> stringResource(R.string.shizuku_state_unauthorized)
                                    ShizukuManager.State.AUTHORIZED -> stringResource(R.string.shizuku_state_authorized)
                                },
                                onAction = {
                                    when (shizukuState) {
                                        ShizukuManager.State.NOT_CONNECTED -> ShizukuManager.openShizukuApp(context)
                                        ShizukuManager.State.UNAUTHORIZED -> ShizukuManager.requestPermission()
                                        ShizukuManager.State.AUTHORIZED -> {}
                                    }
                                },
                            )
                        }
                        3 -> {
                            PageHeaderMiuix(
                                title = stringResource(R.string.welcome_page_battery),
                                summary = stringResource(R.string.permission_battery_desc),
                            )
                            Spacer(Modifier.height(16.dp))
                            PermissionCard(
                                icon = Icons.Filled.Security,
                                title = stringResource(R.string.permission_battery),
                                desc = stringResource(R.string.permission_battery_desc),
                                granted = batteryExempted,
                                statusText = stringResource(
                                    if (batteryExempted) R.string.permission_battery_ok else R.string.permission_battery_fail
                                ),
                                actionText = stringResource(R.string.permission_battery_action),
                                onAction = { openBatterySettings(context) },
                            )
                            PermissionCard(
                                icon = Icons.Filled.Info,
                                title = stringResource(R.string.permission_autostart),
                                desc = stringResource(R.string.permission_autostart_desc),
                                ok = null, // 自启动开关系统无法检测，恒为中性引导
                                statusText = stringResource(R.string.permission_autostart_hint),
                                actionText = stringResource(R.string.permission_autostart_action),
                                onAction = { openAutostartSettings(context) },
                            )
                        }
                        else -> ReadyPageMiuix(
                            notifGranted = notifGranted,
                            appsGranted = appsGranted,
                            shizukuAuthorized = shizukuState == ShizukuManager.State.AUTHORIZED,
                            batteryExempted = batteryExempted,
                        )
                    }
                }
            }
            }
        }

        // 页脚：圆点指示 + 上一步/下一步
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(WELCOME_PAGE_COUNT) { index ->
                    val selected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 22.dp else 7.dp,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        label = "welcomeDotW",
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (selected) accent else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                        animationSpec = tween(180),
                        label = "welcomeDotC",
                    )
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(dotWidth)
                                .height(7.dp)
                                .background(dotColor, CircleShape),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = stringResource(R.string.onboarding_previous),
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.weight(0.38f),
                )
                Button(
                    onClick = {
                        if (pagerState.currentPage == WELCOME_PAGE_COUNT - 1) {
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.weight(0.62f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        stringResource(
                            if (pagerState.currentPage == WELCOME_PAGE_COUNT - 1) R.string.onboarding_start
                            else R.string.onboarding_next
                        )
                    )
                }
            }
        }
    }

    // 读取已安装应用手动授权引导（MIUI 平板等不弹系统授权框时）
    OverlayDialog(
        show = showAppsGuide,
        title = stringResource(R.string.permission_apps_guide_title),
        summary = stringResource(R.string.permission_apps_guide_text),
        onDismissRequest = { showAppsGuide = false },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = { showAppsGuide = false },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(20.dp))
            TextButton(
                text = stringResource(R.string.permission_apps_guide_action),
                onClick = {
                    showAppsGuide = false
                    openAppSettings(context)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
    } // Scaffold 内容结束（OverlayDialog 须落在其内才可经 popup host 渲染）
}

// ==================== 各页内容 ====================

/** 第 1 页：欢迎 + 外观选择（主题模式三选 + 界面风格二选，改动即时生效） */
@Composable
private fun AppearancePageMiuix() {
    val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.frosthush_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MiuixTheme.textStyles.title2,
            // TextStyle 不含颜色：继承 LocalContentColor（默认黑），深色模式下不可见，显式给色
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        // 主题模式三选
        Text(
            text = stringResource(R.string.settings_theme),
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SettingsStore.THEME_SYSTEM to stringResource(R.string.settings_theme_system),
                SettingsStore.THEME_LIGHT to stringResource(R.string.settings_theme_light),
                SettingsStore.THEME_DARK to stringResource(R.string.settings_theme_dark),
            ).forEach { (mode, label) ->
                WelcomeChipMiuix(
                    label = label,
                    selected = themeMode == mode,
                    onClick = { SettingsStore.setThemeMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // 界面风格二选（切换后整体分派到另一套欢迎页实现）
        Text(
            text = stringResource(R.string.settings_ui_style),
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SettingsStore.UI_MODE_MIUIX to stringResource(R.string.settings_ui_style_miuix),
                SettingsStore.UI_MODE_MATERIAL to stringResource(R.string.settings_ui_style_material),
            ).forEach { (mode, label) ->
                WelcomeChipMiuix(
                    label = label,
                    selected = uiModeValue == mode,
                    onClick = { SettingsStore.setUiMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 外观选择胶囊：选中态用 miuix 下拉选中容器色（与应用集 chip 一致） */
@Composable
private fun WelcomeChipMiuix(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (selected) {
            ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.tertiaryContainer,
                contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** 就绪页：各项状态清单汇总 */
@Composable
private fun ReadyPageMiuix(notifGranted: Boolean, appsGranted: Boolean, shizukuAuthorized: Boolean, batteryExempted: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.welcome_page_ready),
            style = MiuixTheme.textStyles.title2,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_ready_summary),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Column {
                ReadyItemMiuix(stringResource(R.string.permission_notification), notifGranted)
                ReadyItemMiuix(stringResource(R.string.permission_apps), appsGranted)
                ReadyItemMiuix(stringResource(R.string.permission_shizuku), shizukuAuthorized)
                ReadyItemMiuix(stringResource(R.string.permission_battery), batteryExempted)
            }
        }
    }
}

@Composable
private fun ReadyItemMiuix(label: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ok) GrantedGreen else MiuixTheme.colorScheme.error,
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MiuixTheme.textStyles.body1, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(if (ok) R.string.permission_granted else R.string.permission_not_granted),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun PageHeaderMiuix(title: String, summary: String) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            text = summary,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 权限/引导项：state=true 绿勾 / false 红叉 / null 中性（系统无法检测，如自启动） */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean? = null, // 旧调用：真实可检测状态
    ok: Boolean? = null,      // 新调用：三态，null=中性引导
    actionText: String?,
    onAction: () -> Unit,
    statusText: String? = null,
) {
    val state: Boolean? = ok ?: granted
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        insideMargin = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                // 与设置页一致：图标跟随正文色（不使用蓝色）
                tint = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MiuixTheme.textStyles.body1)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val stateIcon = when (state) {
                        true -> Icons.Filled.CheckCircle
                        false -> Icons.Filled.Cancel
                        null -> Icons.Filled.Info
                    }
                    val stateTint = when (state) {
                        true -> GrantedGreen
                        false -> MiuixTheme.colorScheme.error
                        null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    }
                    Icon(
                        imageVector = stateIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = stateTint,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = statusText ?: when (state) {
                            true -> stringResource(R.string.permission_granted)
                            false -> stringResource(R.string.permission_not_granted)
                            null -> ""
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = stateTint,
                    )
                }
            }
            if (state != true && actionText != null) {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(),
                ) {
                    Text(actionText)
                }
            }
        }
    }
}
