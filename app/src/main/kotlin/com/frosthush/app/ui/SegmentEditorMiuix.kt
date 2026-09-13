package com.frosthush.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * miuix（HyperOS）版分段编辑器组件——与 [SegmentEditor] 中的 material 版一一对应，
 * 供 miuix 页面的「开始专注 / 计划编辑」使用，避免对话框里出现原生 Android 样式。
 */

/** 分段编辑器单行（miuix）：类型色点 + 标签 +（可选）起止时间 + 时长胶囊 + 删除 */
@Composable
fun SegmentRowMiuix(
    segment: FocusStore.Segment,
    deletable: Boolean,
    onDelete: () -> Unit,
    onClickDuration: () -> Unit,
    startTimeText: String? = null,
    endTimeText: String? = null,
    endTimeEditable: Boolean = false,
    onEditEndTime: () -> Unit = {},
) {
    val isFocus = segment.isFocus
    // 专注用主题色点，休息用中性灰点（避免整行出现大块彩色）
    val accent = if (isFocus) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MiuixTheme.colorScheme.surfaceContainer,
        contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(if (isFocus) R.string.focus_segment_focus else R.string.focus_segment_rest),
                style = MiuixTheme.textStyles.subtitle,
                color = if (isFocus) MiuixTheme.colorScheme.onSurfaceContainer else accent,
            )
            // 起止时间：结束时间可点 → 弹时间选择器（仅计划页）
            if (startTimeText != null && endTimeText != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "$startTimeText → ",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (endTimeEditable) {
                    Surface(
                        onClick = onEditEndTime,
                        shape = RoundedCornerShape(8.dp),
                        color = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = endTimeText,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Text(
                        text = endTimeText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // 时长胶囊按钮：点击弹数字输入对话框
            Surface(
                onClick = onClickDuration,
                shape = RoundedCornerShape(10.dp),
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = segment.minutes.toString(), style = MiuixTheme.textStyles.body1)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.focus_time_unit),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (deletable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                // 固定占位，避免出现/隐藏删除按钮时行宽跳动
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/**
 * 分段比例条（miuix）：按时长比例分割。
 * 刻意用中性明暗（深灰 / 浅灰）而非主题蓝，避免出现用户反馈的「大块蓝色块」。
 */
@Composable
fun SegmentRatioBarMiuix(segments: List<FocusStore.Segment>) {
    val total = segments.sumOf { it.minutes }.coerceAtLeast(1)
    val onSurface = MiuixTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        segments.forEach { seg ->
            val fraction = seg.minutes.toFloat() / total
            if (fraction <= 0f) return@forEach
            Box(
                Modifier
                    .weight(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (seg.isFocus) onSurface.copy(alpha = 0.75f) else onSurface.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

/** 分段时长数字输入对话框（miuix）：title 传段类型标题，range 校验范围 */
@Composable
fun SegmentMinutesDialogMiuix(
    show: Boolean,
    title: String,
    selected: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember(show) { mutableStateOf(selected.toString()) }
    OverlayDialog(
        show = show,
        title = title,
        onDismissRequest = onCancel,
    ) {
        Column {
            TextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = stringResource(R.string.focus_time_unit),
                useLabelAsPlaceholder = true,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = {
                        val minutes = input.toIntOrNull()
                        if (minutes != null && minutes in range) {
                            onConfirm(minutes)
                        } else {
                            Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * miuix 时间选择对话框（24 小时制）：两个 NumberPicker（HyperOS 滚轮样式），
 * 替代 material3 的 TimePicker（其顶部大色块即用户反馈的「原生风格 + 大块蓝色」）。
 */
@Composable
fun MiuixTimePickerDialog(
    show: Boolean,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    // remember(show)：每次打开以最新 initial 重置（常驻组合下 remember{} 会冻结首次组合的初值，
    // 复用同一对话框调不同段的时间时会显示上一次的值——同 SegmentMinutesDialogMiuix 的做法）
    var hour by remember(show) { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var minute by remember(show) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.plan_time_title),
        onDismissRequest = onDismiss,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NumberPicker(
                    value = hour,
                    onValueChange = { hour = it },
                    range = 0..23,
                    label = { it.toString().padStart(2, '0') },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
                Text(text = ":", style = MiuixTheme.textStyles.title3)
                NumberPicker(
                    value = minute,
                    onValueChange = { minute = it },
                    range = 0..59,
                    label = { it.toString().padStart(2, '0') },
                    wrapAround = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.action_confirm),
                    onClick = { onConfirm(hour, minute) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
