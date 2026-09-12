package com.frosthush.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.frosthush.app.R
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.ShizukuManager
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode
import rikka.shizuku.Shizuku
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 计划可靠性检查对话框入口：按当前 [UiMode] 分派 miuix / material 实现。
 * material 版为 [PlanReliabilityDialogMaterial]（SettingsScreenMaterial.kt），业务逻辑两侧逐字一致。
 */
@Composable
internal fun PlanReliabilityDialog(show: Boolean, onDismiss: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> PlanReliabilityDialogMiuix(show = show, onDismiss = onDismiss)
        UiMode.Material -> PlanReliabilityDialogMaterial(show = show, onDismiss = onDismiss)
    }
}

/** miuix 无 success 语义色，沿用欢迎页的产品自有成功绿（WelcomeScreenMiuix.GrantedGreen） */
private val ReliabilityGreen = Color(0xFF2E9E5B)

/** 计划可靠性检查 · miuix 版（HyperOS 设计语言）：OverlayDialog + miuix 语义 token */
@Composable
private fun PlanReliabilityDialogMiuix(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var checkKey by remember { mutableStateOf(0) }

    // 从系统设置页返回后自动重新检测（省电/精确闹钟/自启动跳转后无需手动点「重新检测」）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val batteryOk = remember(checkKey) { checkBatteryOptimization(context) }
    val exactAlarmOk = remember(checkKey) { checkExactAlarm(context) }
    val shizukuOk = remember(checkKey) { FocusManager.shizukuReady() }
    val allOk = batteryOk && exactAlarmOk && shizukuOk

    OverlayDialog(
        show = show,
        title = stringResource(R.string.plan_rel_title),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.plan_rel_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (allOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (allOk) ReliabilityGreen else MiuixTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (allOk) R.string.plan_rel_all_ok else R.string.plan_rel_risk),
                    style = MiuixTheme.textStyles.body1,
                    color = if (allOk) ReliabilityGreen else MiuixTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            ReliabilityItemMiuix(
                ok = batteryOk,
                title = stringResource(R.string.plan_rel_battery),
                desc = stringResource(
                    if (batteryOk) R.string.plan_rel_battery_ok else R.string.plan_rel_battery_fail
                ),
                actionLabel = if (batteryOk) null else stringResource(R.string.plan_rel_battery_action),
                onAction = { openBatterySettings(context) },
            )
            ReliabilityItemMiuix(
                ok = exactAlarmOk,
                title = stringResource(R.string.plan_rel_exact_alarm),
                desc = stringResource(
                    if (exactAlarmOk) R.string.plan_rel_exact_alarm_ok else R.string.plan_rel_exact_alarm_fail
                ),
                actionLabel = if (exactAlarmOk) null else stringResource(R.string.plan_rel_exact_alarm_action),
                onAction = { openExactAlarmSettings(context) },
            )
            ReliabilityItemMiuix(
                ok = null,
                title = stringResource(R.string.plan_rel_autostart),
                desc = stringResource(R.string.plan_rel_autostart_hint),
                actionLabel = stringResource(R.string.plan_rel_autostart_action),
                onAction = { openAutostartSettings(context) },
            )
            ReliabilityItemMiuix(
                ok = shizukuOk,
                title = stringResource(R.string.plan_rel_shizuku),
                desc = stringResource(
                    if (shizukuOk) R.string.plan_rel_shizuku_ok else R.string.plan_rel_shizuku_fail
                ),
                actionLabel = if (shizukuOk) null else stringResource(R.string.plan_rel_shizuku_action),
                onAction = {
                    // 未连接服务 → 打开 Shizuku 应用；已连接未授权 → 请求授权
                    if (!runCatching { !Shizuku.isPreV11() && Shizuku.pingBinder() }.getOrDefault(false)) {
                        ShizukuManager.openShizukuApp(context)
                    } else {
                        ShizukuManager.requestPermission()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.plan_rel_retry),
                    onClick = { checkKey++ },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 可靠性检查单项 · miuix 版：ok=true 绿勾 / false 红叉 / null 中性（系统无法检测，如自启动） */
@Composable
private fun ReliabilityItemMiuix(
    ok: Boolean?,
    title: String,
    desc: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when (ok) {
                true -> Icons.Filled.CheckCircle
                false -> Icons.Filled.Cancel
                null -> Icons.Filled.Info
            },
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when (ok) {
                true -> ReliabilityGreen
                false -> MiuixTheme.colorScheme.error
                null -> MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MiuixTheme.textStyles.body1)
            Text(
                desc,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(
                text = actionLabel,
                onClick = onAction,
                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
