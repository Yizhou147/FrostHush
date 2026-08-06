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

/**
 * 专注模式前台服务：常驻通知显示剩余时间，每秒检查是否到点。
 * 到点后恢复全部应用并结束；进程被杀后由 START_STICKY / Boot 接收器兜底。
 */
class FocusService : Service() {
    private val channelID = javaClass.simpleName
    private val handler = Handler(Looper.getMainLooper())

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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(FocusStore.activeSession()?.endMillis ?: 0L))
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
        return NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(getString(R.string.focus_active_title))
            .setContentText(getString(R.string.focus_remaining, FocusManager.countdownText(remaining)))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(channelID, NotificationManagerCompat.IMPORTANCE_LOW)
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
