package com.frosthush.app.focus

import com.frosthush.app.data.FocusStore.FocusPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * PlanScheduler 时间计算回归测试：
 * - nextStartMillis 的星期过滤与毫秒级边界（24 小时倒计时 bug 的来源）
 * - inProgressEndMillis 的跨午夜进行中判断（END 兜底闹钟晚 24h bug 的来源）
 * - plansOverlap 的冲突判定（同日 / 相邻 / 跨天尾接次日头 / 全天）
 */
class PlanSchedulerTimeTest {

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun plan(
        startMinute: Int,
        endMinute: Int,
        weekdays: Set<Int>,
    ) = FocusPlan(
        id = 1, name = "test", startMinute = startMinute, endMinute = endMinute,
        weekdays = weekdays,
    )

    // ---------- nextStartMillis ----------

    @Test
    fun `重复计划顺延到下一个执行日`() {
        // 2026-09-12 是周六；周一执行的计划 → 2026-09-14 08:00
        val from = millisOf(2026, Calendar.SEPTEMBER, 12, 12, 0)
        val expected = millisOf(2026, Calendar.SEPTEMBER, 14, 8, 0)
        val result = PlanScheduler.nextStartMillis(plan(480, 600, setOf(1)), from)
        assertEquals(expected, result)
    }

    @Test
    fun `当天开始时刻未到则当天执行`() {
        val from = millisOf(2026, Calendar.SEPTEMBER, 14, 7, 0) // 周一 07:00
        val expected = millisOf(2026, Calendar.SEPTEMBER, 14, 8, 0)
        assertEquals(expected, PlanScheduler.nextStartMillis(plan(480, 600, setOf(1)), from))
    }

    @Test
    fun `开始时刻已过（毫秒级比较）顺延到下周`() {
        // 08:00:00.500 > 08:00:00.000：严格大于才算"今天未过"（分钟粒度误判 bug 的回归点）
        val from = millisOf(2026, Calendar.SEPTEMBER, 14, 8, 0) + 500
        val expected = millisOf(2026, Calendar.SEPTEMBER, 21, 8, 0)
        assertEquals(expected, PlanScheduler.nextStartMillis(plan(480, 600, setOf(1)), from))
    }

    @Test
    fun `不重复计划（空星期）今天未过则今天`() {
        val from = millisOf(2026, Calendar.SEPTEMBER, 12, 7, 0)
        val expected = millisOf(2026, Calendar.SEPTEMBER, 12, 8, 0)
        assertEquals(expected, PlanScheduler.nextStartMillis(plan(480, 600, emptySet()), from))
    }

    @Test
    fun `不重复计划今天已过则明天`() {
        val from = millisOf(2026, Calendar.SEPTEMBER, 12, 9, 0)
        val expected = millisOf(2026, Calendar.SEPTEMBER, 13, 8, 0)
        assertEquals(expected, PlanScheduler.nextStartMillis(plan(480, 600, emptySet()), from))
    }

    // ---------- inProgressEndMillis ----------

    @Test
    fun `今天这次的执行正在进行中`() {
        val now = millisOf(2026, Calendar.SEPTEMBER, 14, 9, 0) // 周一 09:00
        val expected = millisOf(2026, Calendar.SEPTEMBER, 14, 10, 0)
        val plan = plan(480, 600, setOf(1))
        assertEquals(expected, PlanScheduler.inProgressEndMillis(plan, now, 120 * 60_000L))
    }

    @Test
    fun `跨午夜计划次日凌晨仍算进行中（END 不再晚 24h 的回归点）`() {
        // 昨天（周日）23:00 开始、180 分钟：今天（周一）00:30 应于今天 02:00 结束
        val now = millisOf(2026, Calendar.SEPTEMBER, 14, 0, 30)
        val expected = millisOf(2026, Calendar.SEPTEMBER, 14, 2, 0)
        val plan = plan(23 * 60, 2 * 60, setOf(7)) // 7=周日执行
        assertEquals(expected, PlanScheduler.inProgressEndMillis(plan, now, 180 * 60_000L))
    }

    @Test
    fun `已结束返回 null`() {
        val mondayNoon = millisOf(2026, Calendar.SEPTEMBER, 14, 12, 0)
        // 08:00-09:00 的 60 分钟执行，中午 12:00 已结束
        assertNull(PlanScheduler.inProgressEndMillis(plan(480, 540, setOf(1)), mondayNoon, 60 * 60_000L))
    }

    @Test
    fun `今天的开始时刻未到返回 null`() {
        val mondayNoon = millisOf(2026, Calendar.SEPTEMBER, 14, 12, 0)
        // 23:00 开始（60 分钟）：中午未开始；昨天是周日非执行日 → 无进行中执行
        assertNull(PlanScheduler.inProgressEndMillis(plan(23 * 60, 24 * 60, setOf(1)), mondayNoon, 60 * 60_000L))
    }

    // ---------- plansOverlap ----------

    @Test
    fun `同日时段重叠`() {
        val a = plan(480, 600, setOf(1))   // 08:00-10:00 周一
        val b = plan(540, 660, setOf(1))   // 09:00-11:00 周一
        assertTrue(PlanScheduler.plansOverlap(a, b))
    }

    @Test
    fun `首尾相接不算重叠`() {
        val a = plan(480, 540, setOf(1))   // 08:00-09:00
        val b = plan(540, 600, setOf(1))   // 09:00-10:00
        assertFalse(PlanScheduler.plansOverlap(a, b))
    }

    @Test
    fun `不同执行日不算重叠`() {
        val a = plan(480, 600, setOf(1))
        val b = plan(480, 600, setOf(2))
        assertFalse(PlanScheduler.plansOverlap(a, b))
    }

    @Test
    fun `跨天计划的尾部与次日计划的重叠`() {
        val a = plan(23 * 60, 2 * 60, setOf(1)) // 周一 23:00-02:00（跨天）
        val b = plan(60, 180, setOf(2))         // 周二 01:00-03:00
        assertTrue(PlanScheduler.plansOverlap(a, b))
    }

    @Test
    fun `跨天计划的尾部与次日计划不重叠`() {
        val a = plan(23 * 60, 2 * 60, setOf(1))
        val b = plan(300, 360, setOf(2)) // 周二 05:00-06:00
        assertFalse(PlanScheduler.plansOverlap(a, b))
    }

    @Test
    fun `全天计划（开始等于结束）视为 24 小时`() {
        val a = plan(600, 600, setOf(1)) // 10:00-10:00 全天
        val b = plan(540, 660, setOf(1))
        assertTrue(PlanScheduler.plansOverlap(a, b))
    }
}
