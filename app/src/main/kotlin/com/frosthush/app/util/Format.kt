package com.frosthush.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 时长与日期格式化工具 */
object Format {

    /** 剩余时间：HH:MM:SS，不足 1 小时用 MM:SS */
    fun countdown(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    private val dateFmt = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    fun date(millis: Long): String = dateFmt.format(Date(millis))
    fun time(millis: Long): String = timeFmt.format(Date(millis))
    fun dateTime(millis: Long): String = dateTimeFmt.format(Date(millis))

    /** 按给定 pattern（如 "yyyy年M月" / "M月d日 EEE"）格式化日期标签，pattern 由字符串资源按语言提供 */
    fun dateLabel(millis: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

    /** 取某记录所在小时的时段分桶，供当日时长分布使用：<12 上午 / 12-18 下午 / >=18 晚上 */
    fun hourBucket(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY).let { hour ->
        when {
            hour < 12 -> 0
            hour < 18 -> 1
            else -> 2
        }
    }

    /** 所在日期的零点（毫秒） */
    fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun startOfWeek(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return c.timeInMillis
    }

    fun startOfMonth(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
        c.set(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    fun startOfYear(millis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
        c.set(Calendar.MONTH, Calendar.JANUARY)
        c.set(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }
}
