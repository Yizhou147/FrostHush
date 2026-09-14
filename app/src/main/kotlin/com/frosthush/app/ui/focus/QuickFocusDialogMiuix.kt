package com.frosthush.app.ui.focus

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.QuickFocus
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 快速专注确认流程 · miuix 版。
 * OverlayDialog 依赖 Scaffold 的 popup host，而 AppRoot 顶层没有 Scaffold，
 * 故与计划提醒对话框（同由 AppRoot 调用）一样，包一层透明 Scaffold。
 */
@Composable
internal fun QuickFocusDialogMiuix(minutes: Int, onDismiss: () -> Unit) {
    Scaffold(containerColor = Color.Transparent) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var step by remember { mutableStateOf(STEP_CONFIRM) }
        var conflicts by remember { mutableStateOf<List<FocusStore.FocusPlan>>(emptyList()) }

        when (step) {
            // ① 确认框
            STEP_CONFIRM -> OverlayDialog(
                show = true,
                title = stringResource(R.string.quick_focus_title),
                onDismissRequest = onDismiss,
            ) {
                Text(stringResource(R.string.quick_focus_confirm_text, minutes))
                QuickFocusButtonRow(
                    cancelText = stringResource(R.string.action_cancel),
                    confirmText = stringResource(R.string.action_start),
                    onCancel = onDismiss,
                    onConfirm = {
                        val found = QuickFocus.conflictsFor(minutes)
                        if (found.isEmpty()) {
                            step = STEP_WARNING
                        } else {
                            conflicts = found
                            step = STEP_CONFLICT
                        }
                    },
                )
            }

            // ② 计划冲突预判框（与普通专注同一文案与处理）
            STEP_CONFLICT -> OverlayDialog(
                show = true,
                title = stringResource(R.string.focus_start),
                onDismissRequest = onDismiss,
            ) {
                Text(stringResource(R.string.focus_conflict_text))
                conflicts.forEach { plan ->
                    Spacer(Modifier.height(4.dp))
                    Text("· " + plan.name, style = MiuixTheme.textStyles.body2)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.focus_conflict_hint),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                QuickFocusButtonRow(
                    cancelText = stringResource(R.string.action_cancel),
                    confirmText = stringResource(R.string.action_confirm),
                    onCancel = onDismiss,
                    onConfirm = {
                        QuickFocus.skipConflicts(conflicts)
                        step = STEP_WARNING
                    },
                )
            }

            // ③ 二次确认警告 → 开始专注
            else -> OverlayDialog(
                show = true,
                title = stringResource(R.string.focus_start),
                onDismissRequest = onDismiss,
            ) {
                Text(stringResource(R.string.focus_confirm_warning))
                QuickFocusButtonRow(
                    cancelText = stringResource(R.string.action_cancel),
                    confirmText = stringResource(R.string.action_start),
                    onCancel = onDismiss,
                    onConfirm = {
                        onDismiss()
                        scope.launch {
                            val err = QuickFocus.start(minutes)
                            if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }
        }
    }
}

/** 快速专注对话框的按钮行（三处共用，miuix 样式：取消在左、主操作在右高亮） */
@Composable
private fun QuickFocusButtonRow(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        TextButton(text = cancelText, onClick = onCancel, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        TextButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary(),
        )
    }
}
