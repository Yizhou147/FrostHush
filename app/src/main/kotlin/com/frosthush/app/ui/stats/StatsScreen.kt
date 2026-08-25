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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.HistoryRecord
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.util.Format
import kotlin.math.max

/** 一个日期下的若干会话记录 */
private class DayGroup(val dayStart: Long, val records: List<HistoryRecord>)

/** 一个月份下的若干日期分组 */
private class MonthGroup(val monthStart: Long, val days: List<DayGroup>) {
    val totalMinutes: Int get() = days.sumOf { d -> d.records.sumOf { it.minutes } }
    val count: Int get() = days.sumOf { it.records.size }
}

/**
 * 统计页：
 * - 主列表：聚合卡片（今日/本周/本月/本年 + 累计总时长/日均/最长单次）、近 7/30 天柱状图
 *   （可切换；点击某列直接进入对应月份的二级页面并展开/高亮该日会话明细）
 * - 会话明细：月份列表（点击某个月份进入二级页面）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
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
            StatsMain(
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
            )
        } else {
            val mg = monthGroups.firstOrNull { it.monthStart == key }
            if (mg != null) {
                MonthDetailScreen(
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
                )
            }
        }
    }
}

/** 统计页主列表：聚合卡片 + 柱状图 + 月份列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsMain(
    history: List<HistoryRecord>,
    chartDays: Int,
    onChartDaysChange: (Int) -> Unit,
    monthGroups: List<MonthGroup>,
    monthPattern: String,
    sessionsFmt: String,
    onOpenMonth: (Long) -> Unit,
    onDateSelect: (Long) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_stats)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.stats_no_sessions),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                item { PeriodCards(history) }
                item { SummaryRow(history) }
                item {
                    ChartSection(history, chartDays, onChartDaysChange, onDateSelect)
                }
                item {
                    Text(
                        stringResource(R.string.stats_sessions),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                monthGroups.forEach { mg ->
                    item(key = "m${mg.monthStart}") {
                        MonthHeader(
                            group = mg,
                            label = Format.dateLabel(mg.monthStart, monthPattern),
                            sessionsFmt = sessionsFmt,
                            onClick = { onOpenMonth(mg.monthStart) },
                        )
                    }
                }
            }
        }
    }
}

/** 今日 / 本周 / 本月 / 本年 */
@Composable
private fun PeriodCards(history: List<FocusStore.HistoryRecord>) {
    val now = System.currentTimeMillis()
    val periods = listOf(
        R.string.stats_today to Format.startOfDay(now),
        R.string.stats_week to Format.startOfWeek(now),
        R.string.stats_month to Format.startOfMonth(now),
        R.string.stats_year to Format.startOfYear(now),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        periods.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (labelRes, from) ->
                    StatCard(
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
private fun SummaryRow(history: List<FocusStore.HistoryRecord>) {
    val total = history.sumOf { it.minutes }
    val activeDays = history.map { Format.startOfDay(it.start) }.distinct().size
    val dailyAvg = if (activeDays == 0) 0 else total / activeDays
    val longest = history.maxOfOrNull { it.minutes } ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            title = stringResource(R.string.stats_total),
            value = FocusManager.minutesText(total),
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.TrendingUp,
        )
        StatCard(
            title = stringResource(R.string.stats_daily_avg),
            value = FocusManager.minutesText(dailyAvg),
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.DateRange,
        )
        StatCard(
            title = stringResource(R.string.stats_longest),
            value = FocusManager.minutesText(longest),
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.HourglassTop,
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** 近 7/30 天柱状图（点击某列进入对应月份二级页面） */
@Composable
private fun ChartSection(
    history: List<FocusStore.HistoryRecord>,
    days: Int,
    onChartDaysChange: (Int) -> Unit,
    onDateSelect: (Long) -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Row {
                FilterChip(
                    selected = days == 7,
                    onClick = { onChartDaysChange(7) },
                    label = { Text(stringResource(R.string.stats_chart_7d)) },
                )
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = days == 30,
                    onClick = { onChartDaysChange(30) },
                    label = { Text(stringResource(R.string.stats_chart_30d)) },
                )
            }
            Spacer(Modifier.height(12.dp))
            FocusBarChart(history, days, onSelect = onDateSelect)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    Format.date(System.currentTimeMillis() - (days - 1) * 86_400_000L),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Format.date(System.currentTimeMillis()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FocusBarChart(history: List<FocusStore.HistoryRecord>, days: Int, onSelect: (Long) -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val data = remember(history, days, now) {
        (days - 1 downTo 0).map { offset ->
            val dayStart = Format.startOfDay(now) - offset * 86_400_000L
            val dayEnd = dayStart + 86_400_000L
            history.filter { it.start >= dayStart && it.start < dayEnd }.sumOf { it.minutes }
        }
    }
    val maxMinutes = data.maxOrNull() ?: 0
    val primary = MaterialTheme.colorScheme.primary
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
                color = if (v > 0) primary else primary.copy(alpha = 0.15f),
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
        }
    }
}

/** 月份条目（主列表）：月份名 + 累计时长/次数 + 右箭头，点击进入二级页面 */
@Composable
private fun MonthHeader(
    group: MonthGroup,
    label: String,
    sessionsFmt: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${FocusManager.minutesText(group.totalMinutes)} · ${sessionsFmt.format(group.count)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun minutesInRange(history: List<FocusStore.HistoryRecord>, from: Long, to: Long): Int =
    history.filter { it.start >= from && it.start < to }.sumOf { it.minutes }

/** 月份二级页面：顶部总览 + 该月按日期折叠的会话明细 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthDetailScreen(
    group: MonthGroup,
    monthPattern: String,
    dayPattern: String,
    sessionsFmt: String,
    expandedDays: Set<Long>,
    selectedDay: Long?,
    onToggleDay: (Long) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(Format.dateLabel(group.monthStart, monthPattern)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard(
                            title = stringResource(R.string.stats_total),
                            value = FocusManager.minutesText(group.totalMinutes),
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.TrendingUp,
                        )
                        StatCard(
                            title = stringResource(R.string.stats_count_label),
                            value = sessionsFmt.format(group.count),
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.DateRange,
                        )
                    }
                }
            }
            group.days.forEach { day ->
                item(key = "d${day.dayStart}") {
                    Column(Modifier.fillMaxWidth()) {
                        DayHeader(
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
                            Column(Modifier.padding(start = 12.dp)) {
                                day.records.forEach { rec ->
                                    SessionRow(rec)
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 日期分组头部：日期 + 当日累计/次数 + 圆点 + 当日时长分布条 */
@Composable
private fun DayHeader(
    group: DayGroup,
    expanded: Boolean,
    selected: Boolean,
    label: String,
    sessionsFmt: String,
    onClick: () -> Unit,
) {
    val dayTotal = group.records.sumOf { it.minutes }
    val buckets = remember(group) {
        val b = intArrayOf(0, 0, 0)
        group.records.forEach { b[Format.hourBucket(it.start)] += it.minutes }
        b
    }
    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else null,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${FocusManager.minutesText(dayTotal)} · ${sessionsFmt.format(group.records.size)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .size(6.dp)
                    .padding(start = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 当日时长分布条（上午 / 下午 / 晚上）
        DayDistributionBar(buckets)
    }
}

/** 当日时长分布：三段横向条（上午/下午/晚上）按分钟数比例 */
@Composable
private fun DayDistributionBar(buckets: IntArray) {
    val total = buckets.sum()
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
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
private fun SessionRow(record: HistoryRecord) {
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
                    stringResource(
                        R.string.stats_session_item,
                        Format.date(record.start),
                        Format.time(record.start),
                        Format.time(record.end),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    Format.dateTime(record.start),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasDetail) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                FocusManager.minutesText(max(record.minutes, 1)),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
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
                                    if (seg.type == FocusStore.SEGMENT_FOCUS) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.tertiary
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(
                                if (seg.type == FocusStore.SEGMENT_FOCUS) R.string.focus_segment_focus
                                else R.string.focus_segment_rest
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (seg.type == FocusStore.SEGMENT_FOCUS) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "%s - %s".format(Format.time(seg.start), Format.time(seg.end)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            FocusManager.minutesText(seg.minutes.coerceAtLeast(1).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}