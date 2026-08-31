package com.frosthush.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 诊断日志（仅 debug 构建生效，release 构建全部 no-op）：
 * - 内存环形缓冲区，保存最近 MAX_ENTRIES 条，不写盘、低开销，供 bug 复现后一键导出分析。
 * - 每条记录 wall（墙钟）+ 运行时长（单调时钟）+ 线程名，便于还原事件时序。
 * - 墙钟跳变检测：wall 与单调时钟的偏移突变 ≥ JUMP_MS 时自动插入跳变日志，
 *   用于排查系统自动校时/时区调整导致的计划时间漂移（时间不准 bug）。
 * - 导出：API 29+ 写入公共 Downloads（MediaStore，无需额外权限），低版本写应用专属外部目录。
 */
object DebugLog {
    private const val MAX_ENTRIES = 3000

    /** 墙钟相对单调时钟偏移突变阈值（毫秒）：超过即视为墙钟被调整 */
    private const val JUMP_MS = 2000L

    private val buffer = ArrayDeque<String>(MAX_ENTRIES)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** 上次记录的 wall - elapsed 偏移；Long.MIN_VALUE 表示尚未初始化 */
    @Volatile
    private var lastOffset = Long.MIN_VALUE

    /** 写入一条诊断日志。 */
    fun d(tag: String, msg: String) {
        val wall = System.currentTimeMillis()
        val mono = SystemClock.elapsedRealtime()
        val offset = wall - mono
        if (lastOffset != Long.MIN_VALUE && kotlin.math.abs(offset - lastOffset) >= JUMP_MS) {
            append("!CLOCK", "墙钟跳变 ${offset - lastOffset}ms（offset $lastOffset→$offset，wall=$wall mono=$mono）")
        }
        lastOffset = offset
        append(tag, msg)
    }

    private fun append(tag: String, msg: String) {
        // pid 随每条日志带上：进程被系统回收/冻结后重启会换新 pid，日志里 pid 变化即进程换过
        val line = "${timeFmt.format(Date())} +${SystemClock.elapsedRealtime() / 1000}s [${Thread.currentThread().name}] pid=${Process.myPid()} $tag: $msg"
        synchronized(buffer) {
            buffer.addLast(line)
            if (buffer.size > MAX_ENTRIES) buffer.removeFirst()
        }
    }

    /** 日志快照（最早的在前），供导出/调试展示 */
    fun snapshot(): String = synchronized(buffer) { buffer.joinToString("\n") }

    /**
     * 导出诊断日志到公共 Downloads 目录（文件管理器可见，无需额外权限）。
     * 返回成功写入的显示文案；失败或非 debug 构建返回 null。
     */
    fun export(context: Context): String? {
        val name = "FrostHush-debug-log-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.txt"
        val content = buildString {
            appendLine("FrostHush 诊断日志（debug 构建）")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("导出时刻: wall=${System.currentTimeMillis()} mono=${SystemClock.elapsedRealtime()}")
            appendLine("------------------------")
            append(snapshot())
        }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                "Download/$name"
            } else {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = File(dir, name)
                file.writeText(content)
                file.absolutePath
            }
        }.getOrNull()
    }
}
