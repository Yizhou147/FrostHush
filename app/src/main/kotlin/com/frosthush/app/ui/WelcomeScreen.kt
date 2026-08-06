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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

    fun refresh() {
        notifGranted = checkNotification(context)
        appsGranted = checkApps(context)
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
                onAction = { refresh() },
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

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_finish))
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    desc: String,
    granted: Boolean,
    actionText: String?,
    onAction: () -> Unit,
    statusText: String? = null,
) {
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
                    Icon(
                        if (granted) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (granted) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        statusText ?: if (granted) stringResource(R.string.permission_granted)
                        else stringResource(R.string.permission_not_granted),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (granted) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (!granted && actionText != null) {
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
    runCatching { AppRepository(context).queryApps().isNotEmpty() }.getOrDefault(false)
