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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 专注计划页 · miuix 版（HyperOS 设计语言）：
 * - 大标题顶栏随列表滚动收起；actions 放「多选」「新建」两个图标按钮
 * - 列表项：计划名、时间段（跨天显示次日）、星期徽标（执行日高亮）、绑定信息、启用 Switch
 * - 多选操作栏、省电/冲突提醒横幅、长按进入多选、多选下长按拖拽排序等逻辑与 material 版逐字一致
 */
@Composable
fun PlanScreenMiuix(
    onNewPlan: () -> Unit,
    onEditPlan: (FocusPlan) -> Unit,
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
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

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.plan_title),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        if (!selectionMode) selected = emptySet()
                    }) {
                        Icon(
                            MiuixIcons.SelectAll,
                            contentDescription = stringResource(R.string.focus_action_select),
                        )
                    }
                    IconButton(onClick = onNewPlan) {
                        Icon(
                            MiuixIcons.Add,
                            contentDescription = stringResource(R.string.plan_new),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding),
        ) {
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
                            MiuixIcons.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                    Text(
                        text = stringResource(R.string.focus_selected, selected.size),
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.focus_select_all),
                        onClick = { selected = plans.map { it.id }.toSet() },
                    )
                    TextButton(
                        text = stringResource(R.string.focus_clear_selection),
                        onClick = { selected = emptySet() },
                    )
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
                            MiuixIcons.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            // 省电未豁免提醒横幅：点击打开计划可靠性检查（重装后系统白名单会丢失，这里主动提示）
            if (!batteryExempted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                    ),
                    onClick = { showReliability = true },
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            MiuixIcons.Report,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.plan_banner_battery_title),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.plan_banner_battery_action),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
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
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.errorContainer,
                        contentColor = MiuixTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            MiuixIcons.Info,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.plan_conflict_banner_title),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onErrorContainer,
                            )
                            conflicts.forEach { c ->
                                Text(
                                    text = stringResource(R.string.plan_conflict_pair, c.planA.name, c.planB.name),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        TextButton(
                            text = stringResource(R.string.plan_conflict_action),
                            onClick = {
                                highlightConflictIds = conflicts.flatMap { listOf(it.planA.id, it.planB.id) }.toSet()
                                highlightTrigger++
                                val firstId = highlightConflictIds.firstOrNull()
                                val idx = plans.indexOfFirst { it.id == firstId }
                                if (idx >= 0) {
                                    scope.launch { listState.animateScrollToItem(idx) }
                                }
                            },
                        )
                    }
                }
            }
            if (plans.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.plan_empty),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomInnerPadding),
                ) {
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
                            PlanRowMiuix(
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

/** 计划列表项（miuix 版） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanRowMiuix(
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
                // 选中态用浅灰底（不用主题蓝）；冲突高亮用错误红底，靠闪烁区分（与选中态不再同色）
                when {
                    conflictHighlight -> MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    selected -> MiuixTheme.colorScheme.surfaceContainerHigh
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = plan.name,
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = timeRangeText(plan),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeekdayBadgesMiuix(plan.weekdays)
                Spacer(Modifier.width(12.dp))
                if (plan.segments != null) {
                    Text(
                        text = stringResource(R.string.plan_segments_badge),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = bindingText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
        if (!selectionMode) {
            Switch(checked = plan.enabled, onCheckedChange = onToggle)
        } else {
            // 放大点击区域，保持与 material 版一致的 40dp 触控范围
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Checkbox(
                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                    onClick = { onClick() },
                )
            }
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
private fun WeekdayBadgesMiuix(weekdays: Set<Int>) {
    if (weekdays.isEmpty()) {
        Text(
            text = stringResource(R.string.plan_once_only),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
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
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.4f),
            )
        }
    }
}
