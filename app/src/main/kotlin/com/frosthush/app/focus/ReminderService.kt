package com.frosthush.app.focus

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import com.frosthush.app.util.DebugLog

/**
 * 计划前提醒每秒计时前台服务（仅普通通知模式使用）。
 *
 * 跟专注倒计时（FocusService）实现方法一致：前台服务 + Handler 每秒 postDelayed 循环，
 * 保证进程优先级稳定，ticker 不会因进程被杀而中断。
 * 焦点通知模式不启动此服务（岛倒计时由系统原生渲染）。
 *
 * 到计划开始时刻（remaining <= 0）自动 stopSelf；
 * PlanScheduler.cancelReminderNotification 也会停止此服务。
 */
class ReminderService : Service() {
    private val channelID = "focus_plan"
    private val handler = Handler(Looper.getMainLooper())

    private var planId: Long = 0
    private var planName: String = ""
    private var startMillis: Long = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val remaining = ((startMillis - now) / 1000L).coerceAtLeast(0L).toInt()
            if (remaining <= 0) {
                // 计划已开始（PlanScheduler.handleStart 会 cancel 通知），停止服务
                DebugLog.d("RemindTick", "到点停止 now=$now startMillis=$startMillis")
                stopSelf()
                return
            }
            updateNotification(remaining)
            DebugLog.d("RemindTick", "remaining=${remaining}s now=$now startMillis=$startMillis")
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        planId = intent?.getLongExtra(EXTRA_PLAN_ID, 0) ?: 0
        planName = intent?.getStringExtra(EXTRA_PLAN_NAME) ?: ""
        startMillis = intent?.getLongExtra(EXTRA_START_MILLIS, 0L) ?: 0L
        val now = System.currentTimeMillis()
        DebugLog.d(
            "Remind", "onStartCommand plan=$planName startMillis=$startMillis now=$now " +
                "remaining=${(startMillis - now) / 1000L}s"
        )
        runCatching { createChannel() }
        val remaining = ((startMillis - now) / 1000L).coerceAtLeast(0L).toInt()
        val notification = buildNotification(remaining)
        // 提醒通知 ID 复用 PlanScheduler.NOTIFICATION_ID_REMIND（202），保持同一 key 便于 cancel
        runCatching { startForeground(NOTIFICATION_ID, notification) }
        handler.removeCallbacksAndMessages(null)
        handler.post(tickRunnable)
        return START_STICKY
    }

    private fun updateNotification(remaining: Int) {
        val notification = buildNotification(remaining)
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(remaining: Int): Notification {
        val title = getString(R.string.plan_reminder_title)
        val text = getString(R.string.plan_reminder_text, planName, remaining)
        val contentIntent = PendingIntent.getActivity(
            this, planId.toInt(),
            Intent(this, MainActivity::class.java).apply {
                action = PlanScheduler.ACTION_REMIND_CLICK
                putExtra(PlanScheduler.EXTRA_PLAN_ID, planId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(channelID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(R.string.plan_notification_channel)).build()
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_PLAN_NAME = "plan_name"
        const val EXTRA_START_MILLIS = "start_millis"
        private const val NOTIFICATION_ID = 202 // 与 PlanScheduler.NOTIFICATION_ID_REMIND 一致
    }
}
