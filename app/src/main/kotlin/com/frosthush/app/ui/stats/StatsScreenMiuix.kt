package com.frosthush.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.HistoryRecord
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.util.Format
import kotlin.math.max
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统计页 · miuix 版（HyperOS 设计语言）：
 * - 主列表：聚合卡片（今日/本周/本月/本年 + 累计总时长/日均/最长单次）、近 7/30 天柱状图
 *   （TabRow 切换；点击某列直接进入对应月份的二级页面并展开/高亮该日会话明细）
 * - 会话明细：月份列表（点击某个月份进入二级页面）
 *
 * 业务逻辑与原 material 版逐字一致，仅组件 / 颜色 / 文字样式替换为 miuix。
 */
@Composable
fun StatsScreenMiuix(
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    val version by FocusManager.version.collectAsState()
    var history by remember { mutableStateOf(FocusStore.history()) }
    LaunchedEffect(version) { history = FocusStore.history() }
    var chartDays by remember { mutableIntStateOf(7) }

    val monthPattern = stringResource(R.string.stats_month_label)
    val dayPattern = stringResource(R.string.stats_day_label)
    val sessionsFmt = stringResource(R.string.stats_sessions_count)

    // 按月份→日期分组（月份倒序，日期倒序，当日记录倒序）
    val monthGroups = remember(history) {
        history.groupBy { Format.startOfMonth(it.start) }
            .map { (monthStart, records) ->
                MonthGroup(
                    monthStart,
                    records.groupBy { Format.startOfDay(it.start) }
                        .map { (dayStart, recs) -> DayGroup(dayStart, recs.sortedByDescending { it.start }) }
                        .sortedByDescending { it.dayStart },
                )
            }
            .sortedByDescending { it.monthStart }
    }

    // 月份二级页面导航：null = 主列表
    var openMonth by remember { mutableStateOf<Long?>(null) }
    var expandedDays by remember { mutableStateOf(setOf<Long>()) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = openMonth != null) { openMonth = null }

    AnimatedContent(
        targetState = openMonth,
        transitionSpec = {
            if (targetState != null) {
                // 前进：进入月份二级页面（自右滑入）
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300)))
            } else {
                // 返回：主列表自右滑回
                (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300)))
            }
        },
        label = "statsMonthNav",
    ) { key ->
        if (key == null) {
            StatsMainMiuix(
                history = history,
                chartDays = chartDays,
                onChartDaysChange = { chartDays = it },
                monthGroups = monthGroups,
                monthPattern = monthPattern,
                sessionsFmt = sessionsFmt,
                onOpenMonth = { openMonth = it },
                onDateSelect = { dayStart ->
                    val mg = monthGroups.firstOrNull { m -> m.days.any { it.dayStart == dayStart } }
                    if (mg != null) {
                        openMonth = mg.monthStart
                        expandedDays = expandedDays + dayStart
                        selectedDay = dayStart
                    }
                },
                bottomInnerPadding = bottomInnerPadding,
            )
        } else {
            val mg = monthGroups.firstOrNull { it.monthStart == key }
            if (mg != null) {
                MonthDetailScreenMiuix(
                    group = mg,
                    monthPattern = monthPattern,
                    dayPattern = dayPattern,
                    sessionsFmt = sessionsFmt,
                    expandedDays = expandedDays,
                    selectedDay = selectedDay,
                    onToggleDay = { day ->
                        expandedDays = if (day in expandedDays) expandedDays - day else expandedDays + day
                    },
                    onBack = { openMonth = null },
                    bottomInnerPadding = bottomInnerPadding,
                )
            }
        }
    }
}

/** 统计页主列表：聚合卡片 + 柱状图 + 月份列表 */
@Composable
private fun StatsMainMiuix(
    history: List<HistoryRecord>,
    chartDays: Int,
    onChartDaysChange: (Int) -> Unit,
    monthGroups: List<MonthGroup>,
    monthPattern: String,
    sessionsFmt: String,
    onOpenMonth: (Long) -> Unit,
    onDateSelect: (Long) -> Unit,
    bottomInnerPadding: Dp = 0.dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.tab_stats),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 12.dp + bottomInnerPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(vertical = 48.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                MiuixIcons.Recent,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.stats_no_sessions),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                item { PeriodCardsMiuix(history) }
                item { SummaryRowMiuix(history) }
                item {
                    ChartSectionMiuix(history, chartDays, onChartDaysChange, onDateSelect)
                }
                item {
                    Text(
                        text = stringResource(R.string.stats_sessions),
                        style = MiuixTheme.textStyles.title4,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item {
                    Card {
                        monthGroups.forEachIndexed { index, mg ->
                            MonthHeaderMiuix(
                                group = mg,
                                label = Format.dateLabel(mg.monthStart, monthPattern),
                                sessionsFmt = sessionsFmt,
                                onClick = { onOpenMonth(mg.monthStart) },
                            )
                            if (index != monthGroups.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 今日 / 本周 / 本月 / 本年 */
@Composable
private fun PeriodCardsMiuix(history: List<FocusStore.HistoryRecord>) {
    val now = System.currentTimeMillis()
    val periods = listOf(
        R.string.stats_today to Format.startOfDay(now),
        R.string.stats_week to Format.startOfWeek(now),
        R.string.stats_month to Format.startOfMonth(now),
        R.string.stats_year to Format.startOfYear(now),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        periods.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (labelRes, from) ->
                    StatCardMiuix(
                        title = stringResource(labelRes),
                        value = FocusManager.minutesText(minutesInRange(history, from, now)),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 累计总时长 / 日均 / 最长单次 */
@Composable
private fun SummaryRowMiuix(history: List<FocusStore.HistoryRecord>) {
    val total = history.sumOf { it.minutes }
    val activeDays = history.map { Format.startOfDay(it.start) }.distinct().size
    val dailyAvg = if (activeDays == 0) 0 else total / activeDays
    val longest = history.maxOfOrNull { it.minutes } ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCardMiuix(
            title = stringResource(R.string.stats_total),
            value = FocusManager.minutesText(total),
            modifier = Modifier.weight(1f),
            icon = MiuixIcons.Stopwatch,
        )
        StatCardMiuix(
            title = stringResource(R.string.stats_daily_avg),
            value = FocusManager.minutesText(dailyAvg),
            modifier = Modifier.weight(1f),
            icon = MiuixIcons.Recent,
        )
        StatCardMiuix(
            title = stringResource(R.string.stats_longest),
            value = FocusManager.minutesText(longest),
            modifier = Modifier.weight(1f),
            icon = MiuixIcons.Timer,
        )
    }
}

/** 聚合卡片（miuix Card 容器） */
@Composable
private fun StatCardMiuix(title: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.body1.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** 近 7/30 天柱状图（TabRow 切换，点击某列进入对应月份二级页面） */
@Composable
private fun ChartSectionMiuix(
    history: List<FocusStore.HistoryRecord>,
    days: Int,
    onChartDaysChange: (Int) -> Unit,
    onDateSelect: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // 白色卡片内默认 TabRow 选中块（surfaceContainer=白）与卡片同色不可见：
            // 未选中透明底+灰字，选中浅灰块+黑字
            // 带描边的 TabRow（HyperOS 观感，选中块更醒目）
            TabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.stats_chart_7d),
                    stringResource(R.string.stats_chart_30d),
                ),
                selectedTabIndex = if (days == 7) 0 else 1,
                onTabSelected = { onChartDaysChange(if (it == 0) 7 else 30) },
                colors = TabRowDefaults.tabRowColors(
                    backgroundColor = Color.Transparent,
                    contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    selectedBackgroundColor = MiuixTheme.colorScheme.secondaryVariant,
                    selectedContentColor = MiuixTheme.colorScheme.onBackground,
                ),
            )
            Spacer(Modifier.height(12.dp))
            // 切换 7/30 天：图表与日期区间交叉淡入淡出（内容按 days 快照，新旧画面才有差异）
            AnimatedContent(
                targetState = days,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "statsChartRange",
            ) { chartDays ->
                Column(Modifier.fillMaxWidth()) {
                    FocusBarChartMiuix(history, chartDays, onSelect = onDateSelect)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = Format.date(System.currentTimeMillis() - (chartDays - 1) * 86_400_000L),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Format.date(System.currentTimeMillis()),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusBarChartMiuix(history: List<FocusStore.HistoryRecord>, days: Int, onSelect: (Long) -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val data = remember(history, days, now) {
        (days - 1 downTo 0).map { offset ->
            val dayStart = Format.startOfDay(now) - offset * 86_400_000L
            val dayEnd = dayStart + 86_400_000L
            history.filter { it.start >= dayStart && it.start < dayEnd }.sumOf { it.minutes }
        }
    }
    val maxMinutes = data.maxOrNull() ?: 0
    val primary = MiuixTheme.colorScheme.primary
    val empty = MiuixTheme.colorScheme.secondaryContainer
    Canvas(
        Modifier.fillMaxWidth().height(140.dp).pointerInput(days, now) {
            detectTapGestures { tap ->
                val col = (tap.x * days / size.width).toInt().coerceIn(0, days - 1)
                onSelect(Format.startOfDay(now - (days - 1 - col) * 86_400_000L))
            }
        }
    ) {
        if (maxMinutes <= 0) return@Canvas
        val gap = size.width / data.size
        val barWidth = gap * 0.6f
        data.forEachIndexed { i, v ->
            val barHeight = (size.height - 4.dp.toPx()) * (v.toFloat() / maxMinutes)
            val x = i * gap + (gap - barWidth) / 2
            drawRoundRect(
                color = if (v > 0) primary else empty,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }
    }
}

/** 月份条目（主列表）：月份名 + 累计时长/次数 + 右箭头，点击进入二级页面 */
@Composable
private fun MonthHeaderMiuix(
    group: MonthGroup,
    label: String,
    sessionsFmt: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${FocusManager.minutesText(group.totalMinutes)} · ${sessionsFmt.format(group.count)}",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 月份二级页面：顶部总览 + 该月按日期折叠的会话明细 */
@Composable
private fun MonthDetailScreenMiuix(
    group: MonthGroup,
    monthPattern: String,
    dayPattern: String,
    sessionsFmt: String,
    expandedDays: Set<Long>,
    selectedDay: Long?,
    onToggleDay: (Long) -> Unit,
    onBack: () -> Unit,
    bottomInnerPadding: Dp = 0.dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = Format.dateLabel(group.monthStart, monthPattern),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 12.dp + bottomInnerPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCardMiuix(
                            title = stringResource(R.string.stats_total),
                            value = FocusManager.minutesText(group.totalMinutes),
                            modifier = Modifier.weight(1f),
                            icon = MiuixIcons.Stopwatch,
                        )
                        StatCardMiuix(
                            title = stringResource(R.string.stats_count_label),
                            value = sessionsFmt.format(group.count),
                            modifier = Modifier.weight(1f),
                            icon = MiuixIcons.Recent,
                        )
                    }
                }
            }
            group.days.forEachIndexed { index, day ->
                item(key = "d${day.dayStart}") {
                    Column(Modifier.fillMaxWidth()) {
                        DayHeaderMiuix(
                            group = day,
                            expanded = day.dayStart in expandedDays,
                            selected = day.dayStart == selectedDay,
                            label = Format.dateLabel(day.dayStart, dayPattern),
                            sessionsFmt = sessionsFmt,
                            onClick = { onToggleDay(day.dayStart) },
                        )
                        AnimatedVisibility(
                            visible = day.dayStart in expandedDays,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Column(Modifier.padding(start = 12.dp, top = 4.dp)) {
                                DayDistributionBarMiuix(dayBuckets(day))
                                Spacer(Modifier.height(4.dp))
                                day.records.forEach { rec ->
                                    SessionRowMiuix(rec)
                                    HorizontalDivider()
                                }
                            }
                        }
                        if (index != group.days.lastIndex) {
                            HorizontalDivider(Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 日期分组头部：日期 + 当日累计/次数 + 圆点（折叠态，分布条移入展开区） */
@Composable
private fun DayHeaderMiuix(
    group: DayGroup,
    expanded: Boolean,
    selected: Boolean,
    label: String,
    sessionsFmt: String,
    onClick: () -> Unit,
) {
    val dayTotal = group.records.sumOf { it.minutes }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${FocusManager.minutesText(dayTotal)} · ${sessionsFmt.format(group.records.size)}",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Box(
            Modifier
                .padding(start = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.surfaceVariant
                )
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            MiuixIcons.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** 当日时长分布：三段横向条（上午/下午/晚上）按分钟数比例 */
@Composable
private fun DayDistributionBarMiuix(buckets: IntArray) {
    val total = buckets.sum()
    val colors = listOf(
        MiuixTheme.colorScheme.primary,
        MiuixTheme.colorScheme.tertiaryContainer,
        MiuixTheme.colorScheme.secondaryContainer,
    )
    val placeholder = MiuixTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().padding(bottom = 2.dp).height(6.dp)) {
        if (total <= 0) {
            drawRoundRect(
                color = placeholder,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
            return@Canvas
        }
        var x = 0f
        buckets.forEachIndexed { i, v ->
            if (v <= 0) return@forEachIndexed
            val w = size.width * (v.toFloat() / total)
            drawRoundRect(
                color = colors[i % colors.size],
                topLeft = Offset(x, 0f),
                size = Size(w, size.height),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
            x += w
        }
    }
}

/** 会话明细行：分段会话可点击展开时间线（每段专注/休息的起止与时长） */
@Composable
private fun SessionRowMiuix(record: HistoryRecord) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetail = record.segments != null
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (hasDetail) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.stats_session_item,
                        Format.date(record.start),
                        Format.time(record.start),
                        Format.time(record.end),
                    ),
                    style = MiuixTheme.textStyles.body2,
                )
                Text(
                    text = Format.dateTime(record.start),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (hasDetail) {
                Icon(
                    MiuixIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = FocusManager.minutesText(max(record.minutes, 1)),
                style = MiuixTheme.textStyles.subtitle,
                color = MiuixTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
                record.segments?.forEach { seg ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (seg.type == FocusStore.SEGMENT_FOCUS) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.tertiaryContainer
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(
                                if (seg.type == FocusStore.SEGMENT_FOCUS) R.string.focus_segment_focus
                                else R.string.focus_segment_rest
                            ),
                            style = MiuixTheme.textStyles.body2,
                            color = if (seg.type == FocusStore.SEGMENT_FOCUS) MiuixTheme.colorScheme.onSurface
                            else MiuixTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "%s - %s".format(Format.time(seg.start), Format.time(seg.end)),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = FocusManager.minutesText(seg.minutes.coerceAtLeast(1).toInt()),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}
