package com.frosthush.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.util.Format
import kotlin.math.max

/**
 * 统计页：
 * - 聚合卡片：今日/本周/本月/本年 + 累计总时长/日均/最长单次
 * - 近 7/30 天柱状图（可切换）
 * - 会话明细：日期、起止时间、时长
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val version by FocusManager.version.collectAsState()
    var history by remember { mutableStateOf(FocusStore.history()) }
    LaunchedEffect(version) { history = FocusStore.history() }
    var chartDays by remember { mutableIntStateOf(7) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_stats)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
            item { ChartSection(history, chartDays) { chartDays = it } }
            item {
                Text(
                    stringResource(R.string.stats_sessions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(history.asReversed(), key = { it.start }) { record ->
                SessionRow(record)
                HorizontalDivider()
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
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 近 7/30 天柱状图 */
@Composable
private fun ChartSection(history: List<FocusStore.HistoryRecord>, days: Int, onDaysChange: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Row {
                FilterChip(
                    selected = days == 7,
                    onClick = { onDaysChange(7) },
                    label = { Text(stringResource(R.string.stats_chart_7d)) },
                )
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = days == 30,
                    onClick = { onDaysChange(30) },
                    label = { Text(stringResource(R.string.stats_chart_30d)) },
                )
            }
            Spacer(Modifier.height(12.dp))
            FocusBarChart(history, days)
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
private fun FocusBarChart(history: List<FocusStore.HistoryRecord>, days: Int) {
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
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
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

/** 会话明细行 */
@Composable
private fun SessionRow(record: FocusStore.HistoryRecord) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
        Text(
            FocusManager.minutesText(max(record.minutes, 1)),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun minutesInRange(history: List<FocusStore.HistoryRecord>, from: Long, to: Long): Int =
    history.filter { it.start >= from && it.start < to }.sumOf { it.minutes }
