package com.frosthush.app.ui.plan

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler
import com.frosthush.app.ui.DEFAULT_FOCUS_MINUTES
import com.frosthush.app.ui.MAX_SEGMENTS
import com.frosthush.app.ui.SegmentMinutesDialog
import com.frosthush.app.ui.SegmentRatioBar
import com.frosthush.app.ui.SegmentRow
import com.frosthush.app.ui.appendSegment
import com.frosthush.app.ui.removeSegment
import com.frosthush.app.ui.segmentEndTimeText
import com.frosthush.app.ui.minuteOfDayText
import com.frosthush.app.ui.segmentsSummaryText
import com.frosthush.app.ui.AppSelectScreen

/**
 * 新建 / 编辑专注计划：
 * - 名称输入（必填）
 * - 开始/结束时间：Material3 TimePicker（24 小时制），结束可小于开始（跨天）
 * - 星期多选 FilterChip + 工作日/周末/不重复快捷（不重复 = 空 weekdays，只执行一次）
 * - 绑定：SegmentedButton 切换「从应用集选择」或「直接选择应用」（复用 AppSelectScreen）
 * - 保存校验：名称非空；开始==结束视为跨全天（需确认）
 * 保存后立即注册/取消对应 AlarmManager 闹钟。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanEditScreen(plan: FocusPlan?, onBack: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(plan?.name ?: "") }
    var startMinute by remember { mutableStateOf(plan?.startMinute ?: 9 * 60) }
    var endMinute by remember { mutableStateOf(plan?.endMinute ?: 17 * 60) }
    // 空 weekdays 表示"不重复"（只执行一次）
    var weekdays by remember { mutableStateOf(plan?.weekdays ?: setOf(1, 2, 3, 4, 5)) }
    // 0 = 从应用集选择，1 = 直接选择应用
    var bindMode by remember {
        mutableStateOf(
            if (plan?.appGroupId != null) 0 else if (!plan?.directEntries.isNullOrEmpty()) 1 else 0
        )
    }
    var selectedGroupId by remember { mutableStateOf(plan?.appGroupId ?: FocusStore.defaultGroup()?.id) }
    var directEntries by remember { mutableStateOf(plan?.directEntries ?: emptyList()) }
    var enabled by remember { mutableStateOf(plan?.enabled ?: true) }
    // 分段专注：空列表 = 连续专注（结束时间手动选择）；非空 = 分段（结束时间自动 = 开始 + 各段总和）
    var segments by remember {
        mutableStateOf<MutableList<FocusStore.Segment>>(plan?.segments?.toMutableList() ?: mutableListOf())
    }
    var selectingDirect by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    // 正在按时间段调整的段索引（点该段结束时间打开选择器，时长自动反算）；-1 = 无
    var editingEndIndex by remember { mutableStateOf(-1) }
    // 正在弹时长输入对话框的段索引；-1 = 无
    var durationDialogIndex by remember { mutableStateOf(-1) }
    var showFullDayConfirm by remember { mutableStateOf(false) }
    var showLongDurationConfirm by remember { mutableStateOf(false) }
    val groups = remember { FocusStore.appGroups() }

    // 分段模式：结束时间只读推导值；显示用
    val segTotal = segments.sumOf { it.minutes }
    val displayEnd = if (segments.isNotEmpty()) (startMinute + segTotal) % 1440 else endMinute
    // 各段起止分钟数（基于计划开始时间累加，未取模便于跨天显示判断）
    val segmentBounds: List<Pair<Int, Int>> = remember(segments, startMinute) {
        val list = mutableListOf<Pair<Int, Int>>()
        var acc = startMinute
        segments.forEach { s ->
            list.add(acc to acc + s.minutes)
            acc += s.minutes
        }
        list
    }

    // 编辑页内系统返回键：退回计划列表（应用选择页内的返回由 AppSelectScreen 自身拦截）
    BackHandler { onBack() }

    fun doSave() {
        val segs = segments.takeIf { it.isNotEmpty() }?.toList()
        // 分段计划：结束时间自动 = 开始 + 各段总和（跨天自然环绕）
        val finalEnd = segs?.let { (startMinute + it.sumOf { s -> s.minutes }) % 1440 } ?: endMinute
        val updated = FocusPlan(
            id = plan?.id ?: FocusStore.nextPlanId(),
            name = name.trim(),
            startMinute = startMinute,
            endMinute = finalEnd,
            weekdays = weekdays,
            appGroupId = if (bindMode == 0) selectedGroupId else null,
            directEntries = if (bindMode == 1) directEntries else null,
            enabled = enabled,
            segments = segs,
        )
        if (plan == null) FocusStore.addFocusPlan(updated)
        else FocusStore.updateFocusPlan(updated)
        // 编辑后重置当天已执行标记，允许当天重新触发
        FocusStore.clearPlanExecuted(updated.id)
        if (updated.enabled) PlanScheduler.schedulePlan(context, updated)
        else PlanScheduler.cancelPlan(context, updated.id)
        FocusManager.bumpVersion()
        onBack()
    }

    /** 当前表单的单次专注时长（分钟），不受 240 分钟限制 */
    fun durationMinutes(): Int = when {
        endMinute > startMinute -> endMinute - startMinute
        endMinute < startMinute -> (1440 - startMinute) + endMinute
        else -> 1440
    }

    /** 表单当前总时长：分段模式取各段之和，连续模式取起止时间差 */
    fun currentTotal(): Int = if (segments.isNotEmpty()) segTotal else durationMinutes()

    /** 长时长确认文案：整小时省略分钟 */
    fun longDurationText(): String {
        val minutes = currentTotal()
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) {
            context.getString(R.string.plan_long_duration_confirm_hours, h)
        } else {
            context.getString(R.string.plan_long_duration_confirm, h, m)
        }
    }

    fun onSaveClick() {
        when {
            name.isBlank() -> Toast.makeText(context, context.getString(R.string.plan_name_required), Toast.LENGTH_SHORT).show()
            // 分段模式：每段 ≥1 分钟，总时长不能达到/超过 24 小时（与「全天」语义冲突）
            segments.isNotEmpty() && (segments.any { it.minutes < FocusStore.MIN_MINUTES } || segTotal >= 1440) ->
                Toast.makeText(context, context.getString(R.string.plan_segments_invalid), Toast.LENGTH_SHORT).show()
            startMinute == endMinute && segments.isEmpty() -> showFullDayConfirm = true // 开始==结束：视为跨全天，需确认
            currentTotal() > 240 -> showLongDurationConfirm = true // 单次专注超过 4 小时：需确认
            else -> doSave()
        }
    }

    /** 添加休息/专注段：连续计划首次添加时把原时长对半拆成两段专注 + 默认休息时长；最多 7 段 */
    fun addSegment() {
        if (segments.size >= MAX_SEGMENTS) return
        if (segments.isEmpty()) {
            val d = durationMinutes()
            val first = (d / 2).takeIf { it >= FocusStore.MIN_MINUTES } ?: d
            val second = d - first
            segments = mutableListOf(
                FocusStore.Segment(FocusStore.SEGMENT_FOCUS, first),
                FocusStore.Segment(FocusStore.SEGMENT_REST, SettingsStore.cache.defaultRestMinutes),
                FocusStore.Segment(
                    FocusStore.SEGMENT_FOCUS,
                    if (second >= FocusStore.MIN_MINUTES) second else DEFAULT_FOCUS_MINUTES,
                ),
            )
        } else {
            segments = appendSegment(segments).toMutableList()
        }
    }

    // 表单 ↔ 应用选择页淡入淡出过渡
    AnimatedContent(
        targetState = selectingDirect,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "planAppSelectTransition",
    ) { isSelecting ->
        if (isSelecting) {
            AppSelectScreen(
                initial = directEntries.toSet(),
                onBack = { selectingDirect = false },
                onDone = {
                    directEntries = it.sorted()
                    selectingDirect = false
                },
            )
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (plan == null) R.string.plan_new else R.string.plan_edit_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.action_cancel),
                                )
                            }
                        },
                    )
                },
                // 底部固定全宽「保存」主按钮，顶栏不再放次要文字按钮
                bottomBar = {
                    Button(
                        onClick = { onSaveClick() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.plan_name)) },
                        placeholder = { Text(stringResource(R.string.plan_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    // 开始 / 结束时间：均分宽度，两行内容（标签 + 时间）避免换行。
                    // 分段模式结束时自动 = 开始 + 各段总和，按钮只读显示推导值
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showStartPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.plan_start_time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(timeText(startMinute), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            enabled = segments.isEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.plan_end_time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(timeText(displayEnd), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    if (displayEnd < startMinute) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.plan_cross_midnight_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // ---------- 分段专注（中途休息） ----------
                    Text(
                        stringResource(R.string.plan_segments_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    segments.forEachIndexed { index, seg ->
                        val (s, e) = segmentBounds.getOrElse(index) { 0 to 0 }
                        SegmentRow(
                            segment = seg,
                            deletable = index > 0,
                            // 按具体时间分段：每行显示该段起止时间（跨天显示「次日」）；
                            // 结束时间可点 → 时间选择器按时间段调整，该段时长自动反算
                            onClickDuration = { durationDialogIndex = index },
                            startTimeText = minuteOfDayText(s % 1440),
                            endTimeText = segmentEndTimeText(e),
                            endTimeEditable = true,
                            onEditEndTime = { editingEndIndex = index },
                            onDelete = { segments = removeSegment(segments, index).toMutableList() },
                        )
                    }
                    TextButton(
                        onClick = { addSegment() },
                        enabled = segments.size < MAX_SEGMENTS,
                    ) {
                        Text(
                            stringResource(
                                if (segments.size >= MAX_SEGMENTS) R.string.focus_segments_limit
                                else if (segments.isEmpty() || segments.last().isFocus) R.string.focus_add_rest
                                else R.string.focus_add_focus
                            )
                        )
                    }
                    if (segments.isNotEmpty()) {
                        SegmentRatioBar(segments)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = segmentsSummaryText(segments),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.plan_segments_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.plan_weekdays),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val weekdayLabels = listOf(
                            1 to stringResource(R.string.plan_weekday_mon),
                            2 to stringResource(R.string.plan_weekday_tue),
                            3 to stringResource(R.string.plan_weekday_wed),
                            4 to stringResource(R.string.plan_weekday_thu),
                            5 to stringResource(R.string.plan_weekday_fri),
                            6 to stringResource(R.string.plan_weekday_sat),
                            7 to stringResource(R.string.plan_weekday_sun),
                        )
                        weekdayLabels.forEach { (day, label) ->
                            FilterChip(
                                selected = day in weekdays,
                                onClick = {
                                    weekdays = if (day in weekdays) weekdays - day else weekdays + day
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = { weekdays = setOf(1, 2, 3, 4, 5) }) {
                            Text(stringResource(R.string.plan_workdays))
                        }
                        TextButton(onClick = { weekdays = setOf(6, 7) }) {
                            Text(stringResource(R.string.plan_weekend))
                        }
                        TextButton(onClick = { weekdays = emptySet() }) {
                            Text(
                                stringResource(R.string.plan_once_only),
                                fontWeight = if (weekdays.isEmpty()) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                color = if (weekdays.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (weekdays.isEmpty()) {
                        Text(
                            stringResource(R.string.plan_once_only_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.plan_bind_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    // 绑定方式：SegmentedButton 二选一，与应用集列表的 Radio 区分层级
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = bindMode == 0,
                            onClick = { bindMode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Text(stringResource(R.string.plan_bind_group))
                        }
                        SegmentedButton(
                            selected = bindMode == 1,
                            onClick = { bindMode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Text(stringResource(R.string.plan_bind_direct))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (bindMode == 0) {
                        // 应用集列表：整行可点 + 选中行高亮 + 右侧 Radio
                        groups.forEach { group ->
                            val selected = selectedGroupId == group.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGroupId = group.id }
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(
                                        group.name.ifBlank { stringResource(R.string.group_default) },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        stringResource(R.string.group_items_count, group.entries.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                RadioButton(selected = selected, onClick = { selectedGroupId = group.id })
                            }
                        }
                    } else {
                        // 「直接选择应用」为次要动作：OutlinedButton
                        OutlinedButton(
                            onClick = { selectingDirect = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.plan_direct_count, directEntries.size))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showStartPicker) {
        MaterialTimePickerDialog(
            initialHour = startMinute / 60,
            initialMinute = startMinute % 60,
            onDismiss = { showStartPicker = false },
            onConfirm = { h, m ->
                startMinute = h * 60 + m
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        MaterialTimePickerDialog(
            initialHour = endMinute / 60,
            initialMinute = endMinute % 60,
            onDismiss = { showEndPicker = false },
            onConfirm = { h, m ->
                endMinute = h * 60 + m
                showEndPicker = false
            },
        )
    }
    // 按时间段调整分段：选择该段新的结束时间 → 时长自动反算，后续段顺延
    if (editingEndIndex >= 0 && editingEndIndex < segmentBounds.size) {
        MaterialTimePickerDialog(
            initialHour = (segmentBounds[editingEndIndex].second % 1440) / 60,
            initialMinute = (segmentBounds[editingEndIndex].second % 1440) % 60,
            onDismiss = { editingEndIndex = -1 },
            onConfirm = { h, m ->
                val chosen = h * 60 + m // 当天时刻 0..1439
                val segStart = segmentBounds[editingEndIndex].first
                var segEnd = chosen
                // 结束不晚于开始 → 视为次日结束
                if (segEnd <= segStart % 1440) segEnd += 1440
                val duration = segEnd - segStart
                if (duration < FocusStore.MIN_MINUTES || duration >= 1440) {
                    Toast.makeText(context, context.getString(R.string.plan_segments_invalid), Toast.LENGTH_SHORT).show()
                } else {
                    segments = segments.toMutableList().apply {
                        set(editingEndIndex, FocusStore.Segment(this[editingEndIndex].type, duration))
                    }
                }
                editingEndIndex = -1
            },
        )
    }
    // 段时长输入对话框（点时长胶囊触发；计划分段单段可超 240 分钟，但总时长 < 24 小时在保存时校验）
    if (durationDialogIndex in segments.indices) {
        val index = durationDialogIndex
        val seg = segments[index]
        SegmentMinutesDialog(
            title = stringResource(
                if (seg.isFocus) R.string.focus_segment_focus_duration_title
                else R.string.focus_segment_rest_duration_title
            ),
            selected = seg.minutes,
            range = FocusStore.MIN_MINUTES..1439,
            onConfirm = { minutes ->
                segments = segments.toMutableList().apply { set(index, FocusStore.Segment(this[index].type, minutes)) }
                durationDialogIndex = -1
            },
            onCancel = { durationDialogIndex = -1 },
        )
    }
    if (showFullDayConfirm) {
        AlertDialog(
            onDismissRequest = { showFullDayConfirm = false },
            title = { Text(stringResource(R.string.plan_full_day)) },
            text = { Text(stringResource(R.string.plan_full_day_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showFullDayConfirm = false
                    doSave()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showFullDayConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (showLongDurationConfirm) {
        AlertDialog(
            onDismissRequest = { showLongDurationConfirm = false },
            title = { Text(stringResource(R.string.plan_long_duration_title)) },
            text = { Text(longDurationText()) },
            confirmButton = {
                TextButton(onClick = {
                    showLongDurationConfirm = false
                    doSave()
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showLongDurationConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Material3 时间选择对话框（24 小时制） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.plan_time_title)) },
    ) {
        TimePicker(state = state)
    }
}

private fun timeText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
