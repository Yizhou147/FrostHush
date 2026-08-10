package com.frosthush.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import com.frosthush.app.focus.ShizukuManager
import com.frosthush.app.ui.settings.checkBatteryOptimization
import com.frosthush.app.ui.settings.openAppSettings
import com.frosthush.app.ui.settings.openAutostartSettings
import com.frosthush.app.ui.settings.openBatterySettings

/**
 * 首次启动欢迎页：逐项列出权限（通知 / 已安装应用 / Shizuku），
 * 每项显示 ✓/✗ 实时状态与「去授权」按钮；onResume 时重新检查全部权限。
 */
@Composable
fun WelcomeScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val shizukuState by ShizukuManager.state.collectAsState()

    var notifGranted by remember { mutableStateOf(checkNotification(context)) }
    var appsGranted by remember { mutableStateOf(checkApps(context)) }
    var batteryExempted by remember { mutableStateOf(checkBatteryOptimization(context)) }
    // 读取已安装应用手动授权引导（MIUI 平板等设备不弹系统授权框时）
    var showAppsGuide by remember { mutableStateOf(false) }

    fun refresh() {
        notifGranted = checkNotification(context)
        appsGranted = checkApps(context)
        batteryExempted = checkBatteryOptimization(context)
    }

    // onResume 重新检查权限
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

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

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
                // 首次包列表查询发生在欢迎页（前台）时 MIUI 手机端会弹「允许获取应用列表」，
                // 但平板等设备不弹——此时弹出应用内引导，让用户手动到系统设置开启。
                onAction = { showAppsGuide = true },
            )
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
                ok = null, // 自启动开关系统无法检测，恒为中性引导
                statusText = stringResource(R.string.permission_autostart_hint),
                actionText = stringResource(R.string.permission_autostart_action),
                onAction = { openAutostartSettings(context) },
            )

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_finish))
            }
        }
    }

    // 读取已安装应用手动授权引导：MIUI 平板等设备不会弹系统授权框，
    // 提示用户到系统设置（应用信息 → 权限管理 → 获取应用列表）手动开启
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

/** 权限/引导项：state=true 绿勾 / false 红叉 / null 中性（系统无法检测，如悬浮通知） */
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
                        true -> Color(0xFF2E9E5B)
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

private fun checkNotification(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else true // 低版本自动视为已获取

private fun checkApps(context: Context): Boolean =
    // includeClones=false：仅检查主用户应用列表，避免 Shizuku 跨用户 IPC（主线程调用）
    runCatching { AppRepository(context).queryApps(includeClones = false).isNotEmpty() }.getOrDefault(false)
