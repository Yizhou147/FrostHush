package com.frosthush.app.focus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.frosthush.app.MainActivity
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.util.MiuiIsland

/**
 * 专注模式前台服务：常驻通知显示剩余时间；是否注入小米超级岛参数由设置
 * SettingsStore.focusIslandEnabled 控制，中途切换立即生效。每秒检查是否到点，
 * 到点后按快照恢复并结束。进程被杀后由 START_STICKY / Boot 接收器兜底。
 */
class FocusService : Service() {
    private val channelID = javaClass.simpleName
    private val handler = Handler(Looper.getMainLooper())
    // 超级岛开关（实时读取设置缓存，中途切换立即生效）
    private val islandEnabled get() = SettingsStore.cache.focusIslandEnabled

    private val tickRunnable = object : Runnable {
        override fun run() {
            val session = FocusStore.activeSession()
            val remaining = (session?.endMillis ?: 0) - System.currentTimeMillis()
            if (session == null || remaining <= 0) {
                // 到点：后台恢复并结束
                Thread { FocusManager.restoreAndEnd() }.start()
                return
            }
            updateNotification(remaining)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台启动整体兜底：通知 / 前台服务任何异常都不应拖垮应用进程
        runCatching {
            createNotificationChannel()
            val notification = buildNotification(remainingMillis())
            // HyperOS 智能省电会延迟受限应用 FGS（startForeground）的首发通知约 10 秒才发布，
            // 而普通 notify 发布/更新已存在的通知是即时的。因此先 notify 发布普通通知，再
            // startForeground 将同 id 通知标记为前台通知，使通知立刻出现。
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
            startForeground(NOTIFICATION_ID, notification)
            updateNotification(remainingMillis())
        }
        handler.post(tickRunnable)
        return START_STICKY
    }

    private fun remainingMillis(): Long =
        (FocusStore.activeSession()?.endMillis ?: 0L) - System.currentTimeMillis()

    private fun updateNotification(remaining: Long) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(remaining))
        }
    }

    private fun buildNotification(remaining: Long): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val frontTitle = getString(R.string.focus_active_title)
        val remainingText = FocusManager.countdownText(remaining)
        val contentText = getString(R.string.focus_remaining, remainingText)
        val builder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(frontTitle)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        // HyperOS 超级岛：仅当设置开启时才注入岛参数
        if (islandEnabled) {
            runCatching {
                builder.addExtras(MiuiIsland.buildIslandExtras(this, frontTitle, remainingText, contentText))
            }
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        // 重要度 HIGH：HyperOS 超级岛仅对高重要度通知呈现，且首次只 alert 一次
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(channelID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(R.string.focus_notification_channel)).build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 100
    }
}
