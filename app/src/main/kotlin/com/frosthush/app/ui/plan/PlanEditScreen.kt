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
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler
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
    var selectingDirect by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showFullDayConfirm by remember { mutableStateOf(false) }
    val groups = remember { FocusStore.appGroups() }

    // 编辑页内系统返回键：退回计划列表（应用选择页内的返回由 AppSelectScreen 自身拦截）
    BackHandler { onBack() }

    fun doSave() {
        val updated = FocusPlan(
            id = plan?.id ?: FocusStore.nextPlanId(),
            name = name.trim(),
            startMinute = startMinute,
            endMinute = endMinute,
            weekdays = weekdays,
            appGroupId = if (bindMode == 0) selectedGroupId else null,
            directEntries = if (bindMode == 1) directEntries else null,
            enabled = enabled,
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

    fun onSaveClick() {
        when {
            name.isBlank() -> Toast.makeText(context, context.getString(R.string.plan_name_required), Toast.LENGTH_SHORT).show()
            startMinute == endMinute -> showFullDayConfirm = true // 开始==结束：视为跨全天，需确认
            else -> doSave()
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
                    // 开始 / 结束时间：均分宽度，两行内容（标签 + 时间）避免换行
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    stringResource(R.string.plan_end_time),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(timeText(endMinute), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    if (endMinute < startMinute) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.plan_cross_midnight_hint),
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
