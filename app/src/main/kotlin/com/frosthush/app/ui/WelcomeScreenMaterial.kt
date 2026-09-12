package com.frosthush.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * 欢迎页 · material 版（原主题；版式与 miuix 版一致，对齐 HuaweiPods OnboardingPage）：
 * 顶栏（图标 + 应用名 + 跳过）→ Pager 翻页（外观 → 权限 → Shizuku → 省电自启动 → 就绪）
 * → 圆点指示 + 上一步/下一步页脚；主色渐变背景，翻页/入场动画。
 * 第 1 页外观选择切换界面风格后由 [WelcomeScreen] 分派器整体切换到另一套实现。
 */
private const val WELCOME_PAGE_COUNT_M = 5
private val GrantedGreenM = Color(0xFF2E9E5B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreenMaterial(onFinished: () -> Unit) {
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

    val pagerState = rememberPagerState(pageCount = { WELCOME_PAGE_COUNT_M })

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    val surface = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val backgroundBrush = remember(surface, accent) {
        Brush.verticalGradient(
            listOf(
                accent.copy(alpha = 0.10f).compositeOver(surface),
                surface,
                Color(0xFF6E9FE8).copy(alpha = 0.06f).compositeOver(surface),
            ),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFinished) { Text(stringResource(R.string.onboarding_skip)) }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            userScrollEnabled = false,
        ) { page ->
            // 内容不足一屏时垂直居中，超出仍可滚动（对齐 HuaweiPods）
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
                label = "welcomePageM",
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
                        0 -> AppearancePageMaterial()
                        1 -> {
                            PageHeaderMaterial(
                                title = stringResource(R.string.welcome_page_permissions),
                                summary = stringResource(R.string.welcome_subtitle),
                            )
                            Spacer(Modifier.height(16.dp))
                            PermissionItem(
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
                            PermissionItem(
                                icon = Icons.Filled.Apps,
                                title = stringResource(R.string.permission_apps),
                                desc = stringResource(R.string.permission_apps_desc),
                                granted = appsGranted,
                                actionText = stringResource(R.string.action_grant),
                                onAction = { showAppsGuide = true },
                            )
                        }
                        2 -> {
                            PageHeaderMaterial(
                                title = stringResource(R.string.welcome_page_shizuku),
                                summary = stringResource(R.string.permission_shizuku_desc),
                            )
                            Spacer(Modifier.height(16.dp))
                            PermissionItem(
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
                            PageHeaderMaterial(
                                title = stringResource(R.string.welcome_page_battery),
                                summary = stringResource(R.string.permission_battery_desc),
                            )
                            Spacer(Modifier.height(16.dp))
                            PermissionItem(
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
                            PermissionItem(
                                icon = Icons.Filled.Info,
                                title = stringResource(R.string.permission_autostart),
                                desc = stringResource(R.string.permission_autostart_desc),
                                ok = null,
                                statusText = stringResource(R.string.permission_autostart_hint),
                                actionText = stringResource(R.string.permission_autostart_action),
                                onAction = { openAutostartSettings(context) },
                            )
                        }
                        else -> ReadyPageMaterial(
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

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                repeat(WELCOME_PAGE_COUNT_M) { index ->
                    val selected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 22.dp else 7.dp,
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        label = "welcomeDotW",
                    )
                    val dotColor by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
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
                OutlinedButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = pagerState.currentPage > 0,
                    modifier = Modifier.weight(0.38f),
                ) { Text(stringResource(R.string.onboarding_previous)) }
                Button(
                    onClick = {
                        if (pagerState.currentPage == WELCOME_PAGE_COUNT_M - 1) {
                            onFinished()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    modifier = Modifier.weight(0.62f),
                ) {
                    Text(
                        stringResource(
                            if (pagerState.currentPage == WELCOME_PAGE_COUNT_M - 1) R.string.onboarding_start
                            else R.string.onboarding_next
                        )
                    )
                }
            }
        }
    }

    if (showAppsGuide) {
        AlertDialog(
            onDismissRequest = { showAppsGuide = false },
            title = { Text(stringResource(R.string.permission_apps_guide_title)) },
            text = { Text(stringResource(R.string.permission_apps_guide_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showAppsGuide = false
                    openAppSettings(context)
                }) { Text(stringResource(R.string.permission_apps_guide_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showAppsGuide = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

// ==================== 各页内容 ====================

/** 第 1 页：欢迎 + 外观选择（主题模式三选 + 界面风格二选，改动即时生效） */
@Composable
private fun AppearancePageMaterial() {
    val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
    val uiModeValue by SettingsStore.uiMode.collectAsState(initial = SettingsStore.cache.uiMode)

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(
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
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SettingsStore.THEME_SYSTEM to stringResource(R.string.settings_theme_system),
                SettingsStore.THEME_LIGHT to stringResource(R.string.settings_theme_light),
                SettingsStore.THEME_DARK to stringResource(R.string.settings_theme_dark),
            ).forEach { (mode, label) ->
                WelcomeChipMaterial(
                    label = label,
                    selected = themeMode == mode,
                    onClick = { SettingsStore.setThemeMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_ui_style),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SettingsStore.UI_MODE_MIUIX to stringResource(R.string.settings_ui_style_miuix),
                SettingsStore.UI_MODE_MATERIAL to stringResource(R.string.settings_ui_style_material),
            ).forEach { (mode, label) ->
                WelcomeChipMaterial(
                    label = label,
                    selected = uiModeValue == mode,
                    onClick = { SettingsStore.setUiMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 外观选择胶囊：material 版（选中主色容器，未选中浅灰） */
@Composable
private fun WelcomeChipMaterial(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        },
    )
}

/** 就绪页：各项状态清单汇总 */
@Composable
private fun ReadyPageMaterial(notifGranted: Boolean, appsGranted: Boolean, shizukuAuthorized: Boolean, batteryExempted: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.welcome_page_ready),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_ready_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                ReadyItemMaterial(stringResource(R.string.permission_notification), notifGranted)
                ReadyItemMaterial(stringResource(R.string.permission_apps), appsGranted)
                ReadyItemMaterial(stringResource(R.string.permission_shizuku), shizukuAuthorized)
                ReadyItemMaterial(stringResource(R.string.permission_battery), batteryExempted)
            }
        }
    }
}

@Composable
private fun ReadyItemMaterial(label: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ok) GrantedGreenM else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(if (ok) R.string.permission_granted else R.string.permission_not_granted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageHeaderMaterial(title: String, summary: String) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(12.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 权限/引导项：state=true 绿勾 / false 红叉 / null 中性（系统无法检测，如自启动） */
@Composable
private fun PermissionItem(
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val stateIcon = when (state) {
                        true -> Icons.Filled.CheckCircle
                        false -> Icons.Filled.Cancel
                        null -> Icons.Filled.Info
                    }
                    val stateTint = when (state) {
                        true -> GrantedGreenM
                        false -> MaterialTheme.colorScheme.error
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        stateIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = stateTint,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        statusText ?: when (state) {
                            true -> stringResource(R.string.permission_granted)
                            false -> stringResource(R.string.permission_not_granted)
                            null -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = stateTint,
                    )
                }
            }
            if (state != true && actionText != null) {
                OutlinedButton(onClick = onAction, shape = CircleShape) {
                    Text(actionText)
                }
            }
        }
    }
}
