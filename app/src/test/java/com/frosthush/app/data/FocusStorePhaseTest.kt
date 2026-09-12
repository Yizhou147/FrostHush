package com.frosthush.app.data

import com.frosthush.app.data.FocusStore.ActiveSession
import com.frosthush.app.data.FocusStore.SEGMENT_FOCUS
import com.frosthush.app.data.FocusStore.SEGMENT_REST
import com.frosthush.app.data.FocusStore.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ActiveSession.phaseAt 分段推算回归测试（分段边界 / 0 时长休息段跳过）。
 * FocusStore 的文件字段为 by lazy：纯 JVM 下仅构造数据类不触碰磁盘。
 */
class FocusStorePhaseTest {

    private val start = 1_000_000_000_000L
    private val min = 60_000L

    @Test
    fun `单段连续专注`() {
        val s = ActiveSession(emptyList(), start, 30)
        val p1 = s.phaseAt(start)
        assertEquals(0, p1.index)
        assertEquals(SEGMENT_FOCUS, p1.type)
        assertEquals(start, p1.segmentStart)
        assertEquals(start + 30 * min, p1.segmentEnd)
        // 超过结束时刻：钳制在最后一段（remaining=0 触发结束）
        val p2 = s.phaseAt(start + 30 * min + 1)
        assertEquals(0, p2.index)
        assertEquals(start + 30 * min, p2.segmentEnd)
    }

    @Test
    fun `分段 25+5+25 的阶段边界`() {
        val s = ActiveSession(
            emptyList(), start, 55,
            segments = listOf(Segment(SEGMENT_FOCUS, 25), Segment(SEGMENT_REST, 5), Segment(SEGMENT_FOCUS, 25)),
        )
        assertEquals(0, s.phaseAt(start + 24 * min).index)
        val rest = s.phaseAt(start + 25 * min) // 恰在边界：进入休息段
        assertEquals(1, rest.index)
        assertEquals(SEGMENT_REST, rest.type)
        assertEquals(2, s.phaseAt(start + 30 * min).index)
        assertEquals(SEGMENT_FOCUS, s.phaseAt(start + 50 * min - 1).type)
        // 超尾钳制到最后一段
        assertEquals(2, s.phaseAt(start + 55 * min + 1).index)
    }

    @Test
    fun `零时长休息段被跳过（skipRest 塌缩的回归点）`() {
        val s = ActiveSession(
            emptyList(), start, 50,
            segments = listOf(Segment(SEGMENT_FOCUS, 25), Segment(SEGMENT_REST, 0), Segment(SEGMENT_FOCUS, 25)),
        )
        val p = s.phaseAt(start + 25 * min)
        assertEquals(2, p.index) // 直接进入第二段专注，不落在 0 分钟的休息段
        assertEquals(SEGMENT_FOCUS, p.type)
        assertEquals(start + 25 * min, p.segmentStart)
    }

    @Test
    fun `toHistorySegments 用实际结束时间截断最后一段`() {
        val s = ActiveSession(
            emptyList(), start, 35,
            segments = listOf(Segment(SEGMENT_FOCUS, 25), Segment(SEGMENT_REST, 10)),
        )
        val end = start + 30 * min // 实际提前 5 分钟结束
        val history = s.toHistorySegments(end)!!
        assertEquals(2, history.size)
        assertEquals(start + 25 * min, history[0].end)
        assertEquals(end, history[1].end)
    }
}
