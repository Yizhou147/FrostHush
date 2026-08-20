package com.frosthush.app.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler
import com.frosthush.app.ui.settings.PlanReliabilityDialog
import com.frosthush.app.ui.settings.checkBatteryOptimization
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 专注计划页：
 * - 列表项：计划名、时间段（跨天显示次日）、星期徽标（执行日高亮）、绑定应用集名/直选数、启用 Switch
 * - 右上角 + 新建、选择键进入多选删除（复用专注页多选操作栏模式）
 * - 点击项进入编辑页；空状态引导新建
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onNewPlan: () -> Unit, onEditPlan: (FocusPlan) -> Unit) {
    val context = LocalContext.current
    val version by FocusManager.version.collectAsState()
    var plans by remember(version) { mutableStateOf(FocusStore.focusPlans()) }
    // 应用集名映射（绑定信息展示用）
    val groupNames = remember(version) { FocusStore.appGroups().associate { it.id to it.name } }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    // 省电未豁免提醒横幅 + 计划可靠性检查对话框
    var showReliability by remember { mutableStateOf(false) }
    val batteryExempted by remember { mutableStateOf(checkBatteryOptimization(context)) }
    // 启用计划时段冲突检测 + 高亮闪烁
    val conflicts = remember(version) { PlanScheduler.findPlanConflicts() }
    var highlightConflictIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var highlightOn by remember { mutableStateOf(false) }
    var highlightTrigger by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(highlightTrigger) {
        if (highlightTrigger > 0) {
            // 闪烁 8 次（约 2.8 秒）后归位
            repeat(8) {
                highlightOn = !highlightOn
                delay(350)
            }
            highlightOn = false
        }
    }

    // 系统返回：选择模式下退出选择模式，否则放行退出应用
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selected = emptySet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plan_title)) },
                actions = {
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selected = emptySet()
                    }) {
                        Icon(
                            Icons.Filled.SelectAll,
                            contentDescription = stringResource(R.string.focus_action_select),
                        )
                    }
                    IconButton(onClick = onNewPlan) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.plan_new),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 多选操作栏（复用专注页模式）
            AnimatedVisibility(
                visible = selectionMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        selectionMode = false
                        selected = emptySet()
                    }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                    Text(
                        stringResource(R.string.focus_selected, selected.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { selected = plans.map { it.id }.toSet() }) {
                        Text(stringResource(R.string.focus_select_all))
                    }
                    TextButton(onClick = { selected = emptySet() }) {
                        Text(stringResource(R.string.focus_clear_selection))
                    }
                    IconButton(
                        onClick = {
                            selected.forEach { id ->
                                FocusStore.deleteFocusPlan(id)
                                PlanScheduler.cancelPlan(context, id)
                            }
                            FocusManager.bumpVersion()
                            selected = emptySet()
                            selectionMode = false
                        },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // 省电未豁免提醒横幅：点击打开计划可靠性检查（重装后系统白名单会丢失，这里主动提示）
            if (!batteryExempted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { showReliability = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.plan_banner_battery_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.plan_banner_battery_action),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            // 启用计划时段冲突提醒横幅（样式与 Shizuku 未授权/应用未解冻提醒一致）
            if (conflicts.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.plan_conflict_banner_title),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            conflicts.forEach { c ->
                                Text(
                                    stringResource(R.string.plan_conflict_pair, c.planA.name, c.planB.name),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        TextButton(onClick = {
                            highlightConflictIds = conflicts.flatMap { listOf(it.planA.id, it.planB.id) }.toSet()
                            highlightTrigger++
                            val firstId = highlightConflictIds.firstOrNull()
                            val idx = plans.indexOfFirst { it.id == firstId }
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx) }
                            }
                        }) {
                            Text(
                                stringResource(R.string.plan_conflict_action),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
            if (plans.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.plan_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // 排序交互：非多选模式长按行 → 进入多选；多选模式下长按某行 → 开始拖拽（该行放大并跟随手指，
                // 其余行通过 animateItem 平滑让位），拖动跨越半行即交换顺序并持久化
                var draggingId by remember { mutableStateOf<Long?>(null) }
                var dragOffsetY by remember { mutableStateOf(0f) }
                var draggedHeightPx by remember { mutableStateOf(0f) }
                val latestPlans by rememberUpdatedState(plans)

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(plans, key = { it.id }) { plan ->
                        val isDragging = draggingId == plan.id
                        val scale by animateFloatAsState(
                            targetValue = if (isDragging) 1.04f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "planDragScale",
                        )
                        Column(
                            modifier = (if (isDragging) Modifier else Modifier.animateItem())
                                // 被拖项禁用让位动画（仅跟手），避免 placement 动画与跟手位移叠加导致跳动；
                                // 其余项保留 animateItem 平滑让位
                                .graphicsLayer {
                                    // 被拖项跟随手指；其余项保持原位由 animateItem 平滑让位
                                    translationY = if (isDragging) dragOffsetY else 0f
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                                .scale(scale)
                                .onGloballyPositioned {
                                    if (isDragging) draggedHeightPx = it.size.height.toFloat()
                                }
                                .pointerInput(plan.id, selectionMode) {
                                    if (!selectionMode) return@pointerInput
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = plan.id
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (draggingId != plan.id) return@detectDragGesturesAfterLongPress
                                            dragOffsetY += amount.y
                                            val list = latestPlans
                                            val currentIndex = list.indexOfFirst { it.id == plan.id }
                                            if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                            val h = draggedHeightPx.takeIf { it > 0f }
                                                ?: 84.dp.toPx()
                                            val targetIndex = (currentIndex + (dragOffsetY / h).roundToInt())
                                                .coerceIn(0, list.size - 1)
                                            if (targetIndex != currentIndex) {
                                                val newList = list.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
                                                plans = newList
                                                FocusStore.saveFocusPlans(newList)
                                                dragOffsetY -= (targetIndex - currentIndex) * h
                                            }
                                        },
                                    )
                                },
                        ) {
                            PlanRow(
                                plan = plan,
                                bindingText = bindingText(context, plan, groupNames),
                                selectionMode = selectionMode,
                                selected = plan.id in selected,
                                conflictHighlight = plan.id in highlightConflictIds && highlightOn,
                                onClick = {
                                    if (selectionMode) {
                                        selected = if (plan.id in selected) selected - plan.id else selected + plan.id
                                    } else {
                                        onEditPlan(plan)
                                    }
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        selectionMode = true
                                        selected = setOf(plan.id)
                                    }
                                },
                                onToggle = { enabled ->
                                    val updated = plan.copy(enabled = enabled)
                                    FocusStore.updateFocusPlan(updated)
                                    if (enabled) PlanScheduler.schedulePlan(context, updated)
                                    else PlanScheduler.cancelPlan(context, plan.id)
                                    FocusManager.bumpVersion()
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showReliability) {
        PlanReliabilityDialog(onDismiss = { showReliability = false })
    }
}

/** 计划绑定的展示文案：应用集名（带「应用集：」前缀）/ 直选数 / 默认集 */
@Composable
private fun bindingText(context: android.content.Context, plan: FocusPlan, groupNames: Map<Long, String>): String = when {
    plan.appGroupId != null -> context.getString(
        R.string.plan_binding_group_label,
        groupNames[plan.appGroupId] ?: context.getString(R.string.plan_group_deleted),
    )
    !plan.directEntries.isNullOrEmpty() -> context.getString(R.string.plan_binding_direct_count, plan.directEntries!!.size)
    else -> context.getString(R.string.plan_default_group)
}

/** 计划列表项 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanRow(
    plan: FocusPlan,
    bindingText: String,
    selectionMode: Boolean,
    selected: Boolean,
    conflictHighlight: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            // 多选模式下不注册长按（长按留给拖拽排序，避免手势冲突）；非多选模式长按=进入多选
            .combinedClickable(onClick = onClick, onLongClick = if (selectionMode) null else onLongClick)
            .background(
                // 选中态与冲突高亮都用同一蓝色（与点击进入多选时一致），冲突高亮靠闪烁区分
                if (selected || conflictHighlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(plan.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(
                timeRangeText(plan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekdayBadges(plan.weekdays)
                Spacer(Modifier.width(12.dp))
                if (plan.segments != null) {
                    Text(
                        stringResource(R.string.plan_segments_badge),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    bindingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (!selectionMode) {
            Switch(checked = plan.enabled, onCheckedChange = onToggle)
        } else {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

/** 时间段文案：跨天显示「22:00 - 次日06:00」，开始==结束显示「全天」 */
@Composable
private fun timeRangeText(plan: FocusPlan): String = when {
    plan.endMinute > plan.startMinute -> stringResource(
        R.string.plan_time_range, timeText(plan.startMinute), timeText(plan.endMinute)
    )
    plan.endMinute < plan.startMinute -> stringResource(
        R.string.plan_time_range_cross, timeText(plan.startMinute), timeText(plan.endMinute)
    )
    else -> stringResource(R.string.plan_full_day)
}

private fun timeText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** 星期徽标：一二三四五六日，执行日高亮；weekdays 为空（不重复）时显示「仅一次」 */
@Composable
private fun WeekdayBadges(weekdays: Set<Int>) {
    if (weekdays.isEmpty()) {
        Text(
            stringResource(R.string.plan_once_only),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }
    val labels = listOf(
        1 to stringResource(R.string.plan_weekday_mon),
        2 to stringResource(R.string.plan_weekday_tue),
        3 to stringResource(R.string.plan_weekday_wed),
        4 to stringResource(R.string.plan_weekday_thu),
        5 to stringResource(R.string.plan_weekday_fri),
        6 to stringResource(R.string.plan_weekday_sat),
        7 to stringResource(R.string.plan_weekday_sun),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { (day, label) ->
            val active = day in weekdays
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}
