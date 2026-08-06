package com.frosthush.app.focus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
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
        // 前台服务必须成功启动：startForegroundService 后若 5 秒内未调用 startForeground，
        // 系统会抛 ForegroundServiceDidNotStartInTimeException 直接闪退。
        // 因此 notify（通知权限被拒时会抛 SecurityException）与 startForeground 分开保护，
        // notify 失败绝不能连带跳过 startForeground。
        runCatching { createNotificationChannel() }
        val notification = runCatching { buildNotification(remainingMillis()) }.getOrElse { fallbackNotification() }
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
        runCatching { startForeground(NOTIFICATION_ID, notification) }
        runCatching { updateNotification(remainingMillis()) }
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

    /**
     * 通知构建兜底：buildNotification 任一步意外失败时，也必须有合法的 Notification
     * 供 startForeground 使用，否则会触发 FGS 启动超时闪退。
     */
    private fun fallbackNotification(): Notification = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelID)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(getString(R.string.focus_active_title))
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(getString(R.string.focus_active_title))
                .build()
        }
    }.getOrElse { Notification() }

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
