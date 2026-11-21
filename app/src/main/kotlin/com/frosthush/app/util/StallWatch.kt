package com.frosthush.app.util

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 专注页 UI 停更看门狗的数据侧：
 * - markTick：倒计时写入时刻（refresh 内调用）
 * - markRender：子组合实际拿到新值并参与绘制的时刻（SideEffect 内调用）
 *
 * 卡死现场实证（2026-11-21）：进程 TOP、主线程空闲、SurfaceFlinger 每 16.6ms 收到新 buffer
 * （App 一直以 60fps 重绘同一帧）、超级岛正常，但页面数值几秒~几分钟不更新；切一次配置变更
 * （Activity 重建）立刻恢复。即「vsync 绘制链路活着，Handler/delay 的每秒刷新停了」。
 * 看门狗用于在再次停更时留证（stall.log，限长）+ 触发自愈，避免只留一个「整页定格」给用户。
 */
object StallWatch {
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private const val MAX_LOG_BYTES = 32 * 1024L

    @Volatile
    var lastTickAt = 0L
        private set

    @Volatile
    var lastRenderAt = 0L
        private set

    fun markTick() {
        lastTickAt = SystemClock.elapsedRealtime()
    }

    fun markRender() {
        lastRenderAt = SystemClock.elapsedRealtime()
    }

    fun log(context: Context, msg: String) {
        runCatching {
            val f = File(context.filesDir, "stall.log")
            f.appendText("${fmt.format(Date())} +${SystemClock.elapsedRealtime()} $msg\n")
            if (f.length() > MAX_LOG_BYTES) {
                val keep = f.readLines().takeLast(200)
                f.writeText(keep.joinToString("\n", postfix = "\n"))
            }
        }
    }
}
