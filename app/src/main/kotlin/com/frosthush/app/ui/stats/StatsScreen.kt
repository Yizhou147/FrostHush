package com.frosthush.app.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.data.FocusStore.HistoryRecord
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode
import com.frosthush.app.util.Format

/** 一个日期下的若干会话记录 */
internal class DayGroup(val dayStart: Long, val records: List<HistoryRecord>)

/** 一个月份下的若干日期分组 */
internal class MonthGroup(val monthStart: Long, val days: List<DayGroup>) {
    val totalMinutes: Int get() = days.sumOf { d -> d.records.sumOf { it.minutes } }
    val count: Int get() = days.sumOf { it.records.size }
}

/** 指定区间内的专注分钟数合计 */
internal fun minutesInRange(history: List<HistoryRecord>, from: Long, to: Long): Int =
    history.filter { it.start >= from && it.start < to }.sumOf { it.minutes }

/** 当日时长三段统计（上午 / 下午 / 晚上），按时间分桶 */
internal fun dayBuckets(day: DayGroup): IntArray {
    val b = intArrayOf(0, 0, 0)
    day.records.forEach { b[Format.hourBucket(it.start)] += it.minutes }
    return b
}

/**
 * 统计页：
 * - 主列表：聚合卡片（今日/本周/本月/本年 + 累计总时长/日均/最长单次）、近 7/30 天柱状图
 *   （可切换；点击某列直接进入对应月份的二级页面并展开/高亮该日会话明细）
 * - 会话明细：月份列表（点击某个月份进入二级页面）
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 */
@Composable
fun StatsScreen(
    /** 底栏高度：仅作为列表底部内边距，避免最后一项被悬浮底栏遮挡 */
    bottomInnerPadding: Dp = 0.dp,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> StatsScreenMiuix(bottomInnerPadding = bottomInnerPadding)
        UiMode.Material -> StatsScreenMaterial(bottomInnerPadding = bottomInnerPadding)
    }
}
