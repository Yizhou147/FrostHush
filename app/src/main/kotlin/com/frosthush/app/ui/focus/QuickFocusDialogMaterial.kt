package com.frosthush.app.ui.focus

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.QuickFocus
import kotlinx.coroutines.launch

/** 快速专注确认流程 · material 版（步骤常量见 [STEP_CONFIRM] 等） */
@Composable
internal fun QuickFocusDialogMaterial(minutes: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 已有专注进行中：直接提示，不再走确认流程（与普通专注的 focus_session_exists 一致）
    var step by remember {
        mutableStateOf(if (FocusStore.activeSession() != null) STEP_ALREADY_FOCUSING else STEP_CONFIRM)
    }
    var conflicts by remember { mutableStateOf<List<FocusStore.FocusPlan>>(emptyList()) }

    when (step) {
        // ⓪ 已在专注：只提示
        STEP_ALREADY_FOCUSING -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.focus_start)) },
            text = { Text(stringResource(R.string.focus_session_exists)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
            },
        )

        // ① 确认框：本次快速专注的时长（文案来自快捷方式设置，正文用实际分钟数更准确）
        STEP_CONFIRM -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.quick_focus_title)) },
            text = { Text(stringResource(R.string.focus_confirm_warning, minutes)) },
            confirmButton = {
                TextButton(onClick = {
                    val found = QuickFocus.conflictsFor(minutes)
                    if (found.isEmpty()) {
                        step = STEP_WARNING
                    } else {
                        conflicts = found
                        step = STEP_CONFLICT
                    }
                }) { Text(stringResource(R.string.action_start)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        // ② 计划冲突预判框（与普通专注同一文案与处理）
        STEP_CONFLICT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.focus_start)) },
            text = {
                Column {
                    Text(stringResource(R.string.focus_conflict_text))
                    Spacer(Modifier.height(8.dp))
                    conflicts.forEach { plan ->
                        Text("· " + plan.name, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.focus_conflict_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    QuickFocus.skipConflicts(conflicts)
                    step = STEP_WARNING
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )

        // ③ 二次确认警告 → 开始专注
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.focus_start)) },
            text = { Text(stringResource(R.string.focus_confirm_warning, minutes)) },
            confirmButton = {
                TextButton(onClick = {
                    onDismiss()
                    scope.launch {
                        val err = QuickFocus.start(minutes)
                        if (err != null) Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                }) { Text(stringResource(R.string.action_start)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
