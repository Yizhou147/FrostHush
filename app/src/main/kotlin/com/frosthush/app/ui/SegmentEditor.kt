package com.frosthush.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager

/** 分段编辑器默认值：新增专注段回退 30 分钟（新增休息段默认值见 SettingsStore.defaultRestMinutes） */
const val DEFAULT_FOCUS_MINUTES = 30

/** 分段上限：最多 7 段（从专注开始交替排列即 4 专注 + 3 休息），达到后禁用添加按钮 */
const val MAX_SEGMENTS = 7

/**
 * 分段编辑器单行（手动专注对话框与计划编辑页共用）：
 * 卡片式整行——类型色点 + 标签 +（计划页）起止时间 + 时长胶囊按钮 + 删除。
 * 时长与结束时间均为可点击的大区域按钮（替代原孤立输入框，视觉统一、易点按）：
 * - onClickDuration：点时长胶囊弹数字输入对话框（手动对话框借此同时设置预设目标段）
 * - onEditEndTime：点结束时间打开选择器按时间段调整（仅计划页），时长自动反算
 */
@Composable
fun SegmentRow(
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
    val accent = if (isFocus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (isFocus) R.string.focus_segment_focus else R.string.focus_segment_rest),
                style = MaterialTheme.typography.titleSmall,
                color = if (isFocus) MaterialTheme.colorScheme.onSurface else accent,
            )
            // 起止时间：与标签同一行右侧（垂直居中），结束时间可点 → 主色 pill（仅计划页）
            if (startTimeText != null && endTimeText != null) {
                Spacer(Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        startTimeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        " → ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (endTimeEditable) {
                        Surface(
                            onClick = onEditEndTime,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        ) {
                            Text(
                                endTimeText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    } else {
                        Text(
                            endTimeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // 时长胶囊按钮：点击弹数字输入对话框（替代原孤立的输入框，整行视觉统一、点击区域大）
            Surface(
                onClick = onClickDuration,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        segment.minutes.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        stringResource(R.string.focus_time_unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (deletable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // 固定占位，避免出现/隐藏删除按钮时行宽跳动
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/** 分段时长数字输入对话框（手动专注/计划分段共用）：title 传段类型标题，range 校验范围 */
@Composable
fun SegmentMinutesDialog(
    title: String,
    selected: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf(selected.toString()) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter(Char::isDigit).take(4) },
                label = { Text(title) },
                suffix = { Text(stringResource(R.string.focus_time_unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val minutes = input.toIntOrNull()
                if (minutes != null && minutes in range) {
                    onConfirm(minutes)
                } else {
                    Toast.makeText(context, context.getString(R.string.focus_time_invalid), Toast.LENGTH_SHORT).show()
                }
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 一天内分钟数 → HH:mm */
fun minuteOfDayText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** 段结束时间文本：跨天（结束 ≥ 1440）显示「次日 HH:mm」 */
@Composable
fun segmentEndTimeText(endMinute: Int): String {
    val context = LocalContext.current
    return if (endMinute >= 1440) {
        context.getString(R.string.plan_segments_next_day, minuteOfDayText(endMinute % 1440))
    } else {
        minuteOfDayText(endMinute)
    }
}

/** 分段比例条：按时长比例分割，专注=主色实心，休息=次色，一眼总览整个安排。
 *  时长 0 的段跳过渲染（Compose 的 weight 要求 > 0，否则抛 IllegalArgumentException）。 */
@Composable
fun SegmentRatioBar(segments: List<FocusStore.Segment>) {
    val total = segments.sumOf { it.minutes }.coerceAtLeast(1)
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
                        if (seg.isFocus) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                    )
            )
        }
    }
}

/** 分段汇总文案：专注共 X · 休息 Y · 总计 Z（无休息时只显示总计） */
@Composable
fun segmentsSummaryText(segments: List<FocusStore.Segment>): String {
    val context = LocalContext.current
    val totalFocus = segments.filter { it.isFocus }.sumOf { it.minutes }
    val totalRest = segments.filter { it.type == FocusStore.SEGMENT_REST }.sumOf { it.minutes }
    val total = segments.sumOf { it.minutes }
    return if (totalRest > 0) {
        context.getString(
            R.string.focus_segments_summary,
            FocusManager.minutesText(totalFocus),
            FocusManager.minutesText(totalRest),
            FocusManager.minutesText(total),
        )
    } else {
        context.getString(R.string.focus_segments_summary_no_rest, FocusManager.minutesText(total))
    }
}

/**
 * 追加一段：末尾为专注 → 添加休息（默认取设置里的默认休息时长）；末尾为休息 → 添加专注
 * （默认取第一段专注时长，空列表按 30 分钟）。
 */
fun appendSegment(segments: List<FocusStore.Segment>): List<FocusStore.Segment> {
    val newType = if (segments.lastOrNull()?.isFocus != false) FocusStore.SEGMENT_REST else FocusStore.SEGMENT_FOCUS
    val minutes = when (newType) {
        FocusStore.SEGMENT_REST -> SettingsStore.cache.defaultRestMinutes
        else -> segments.firstOrNull()?.minutes?.takeIf { it >= FocusStore.MIN_MINUTES } ?: DEFAULT_FOCUS_MINUTES
    }
    return segments + FocusStore.Segment(newType, minutes)
}

/** 删除一段并合并相邻同类型段（专注→休息→专注 删休息 → 两段专注合并），保证以专注开始、以专注结束 */
fun removeSegment(segments: List<FocusStore.Segment>, index: Int): List<FocusStore.Segment> {
    if (index < 0 || index >= segments.size) return segments
    val list = segments.toMutableList().apply { removeAt(index) }
    val merged = mutableListOf<FocusStore.Segment>()
    list.forEach { s ->
        val last = merged.lastOrNull()
        if (last != null && last.type == s.type) {
            merged[merged.size - 1] = FocusStore.Segment(s.type, last.minutes + s.minutes)
        } else {
            merged.add(s)
        }
    }
    return merged
}
