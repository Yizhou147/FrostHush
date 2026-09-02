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
import com.frosthush.app.util.DebugLog

/**
 * 计划前提醒前台服务（2026-09-02 重构）。
 *
 * 由提醒闹钟（setAlarmClock 注册在 开始 - 提醒秒数，系统闹钟类闹钟准点投递）启动。
 * 投递即提醒时刻：立即显示提醒通知（焦点岛/普通走秒），服务只在提醒与开始之间
 * 短活对齐到开始时刻启动专注，然后自停。不再有旧的"窗口缓冲期提前保活"——
 * 那段期间的前台服务强制通知（静默准备通知）用户可见且被禁止，已删除。
 *
 * - 普通通知模式：提醒通知每秒更新倒计时（ticker），服务保持到开始时刻；
 * - 焦点岛模式：由系统按通知参数原生渲染倒计时（无需每秒 notify），服务同样保持到开始时刻。
 * - 投递已过开始时刻（闹钟延迟/异常）：直接启动专注，不发提醒（提醒无意义）。
 */
class ReminderService : Service() {
    private val channelID = "focus_plan"
    private val handler = Handler(Looper.getMainLooper())

    private var planId: Long = 0
    private var planName: String = ""
    private var startMillis: Long = 0L
    private var remindSeconds: Int = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now >= startMillis) {
                // 到开始时刻：启动专注（handleStart 内部会停止本服务并清理提醒通知）
                DebugLog.d("Remind", "到点启动专注 now=$now startMillis=$startMillis")
                runCatching { PlanScheduler.handleStart(this@ReminderService, planId) }
                stopSelf()
                return
            }
            // 提醒已显示：普通通知模式每秒更新倒计时（焦点岛模式静态由系统原生渲染）
            if (!SettingsStore.cache.focusIslandEnabled) {
                updateReminderTicker(now)
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        planId = intent?.getLongExtra(EXTRA_PLAN_ID, 0) ?: 0
        planName = intent?.getStringExtra(EXTRA_PLAN_NAME) ?: ""
        startMillis = intent?.getLongExtra(EXTRA_START_MILLIS, 0L) ?: 0L
        remindSeconds = intent?.getIntExtra(EXTRA_REMIND_SECONDS, SettingsStore.cache.planRemindSeconds)
            ?: SettingsStore.cache.planRemindSeconds
        val now = System.currentTimeMillis()
        DebugLog.d(
            "Remind", "onStartCommand plan=$planName startMillis=$startMillis remindAt=${startMillis - remindSeconds * 1000L} " +
                "now=$now remaining=${(startMillis - now) / 1000L}s"
        )
        runCatching { createChannel() }
        if (now >= startMillis) {
            // 投递已过开始时刻（闹钟延迟/异常）：直接启动专注，提醒无意义不再发
            DebugLog.d("Remind", "投递已过开始时刻，直接开始 planId=$planId late=${now - startMillis}ms")
            // 先 startForeground 保活（startForegroundService 后 5 秒内必须调用，防崩溃）
            startForeground(NOTIFICATION_ID, buildReminderTicker(now))
            runCatching { PlanScheduler.handleStart(this, planId) }
            stopSelf()
            return START_NOT_STICKY
        }
        // 正常投递（提醒时刻前后）：前台服务通知即提醒本身，显示后内部对齐到开始时刻
        startForeground(NOTIFICATION_ID, buildReminderTicker(now))
        showReminderNotification()
        handler.removeCallbacksAndMessages(null)
        handler.post(tickRunnable)
        return START_STICKY
    }

    /** 显示提醒通知（焦点岛注入/普通提醒）：notify 同 ID 覆盖前台服务通知为提醒内容 */
    private fun showReminderNotification() {
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        runCatching { PlanScheduler.showReminderNotification(this, plan, startMillis, remindSeconds) }
    }

    /** 普通通知模式：每秒更新提醒倒计时 */
    private fun updateReminderTicker(now: Long) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildReminderTicker(now))
        }
    }

    /** 提醒通知（普通模式，倒计时动态）：与 PlanScheduler.showReminderNotification 文案一致 */
    private fun buildReminderTicker(now: Long): Notification {
        val remaining = ((startMillis - now) / 1000L).coerceAtLeast(0L).toInt()
        val title = getString(R.string.plan_reminder_title)
        val text = getString(R.string.plan_reminder_text, planName, remaining)
        val contentIntent = reminderClickIntent()
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

    /** 点击通知打开应用（带 ACTION_REMIND_CLICK + planId，AppRoot 弹「距开始倒计时」对话框） */
    private fun reminderClickIntent(): PendingIntent = PendingIntent.getActivity(
        this, planId.toInt(),
        Intent(this, MainActivity::class.java).apply {
            action = PlanScheduler.ACTION_REMIND_CLICK
            putExtra(PlanScheduler.EXTRA_PLAN_ID, planId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannel() {
        // 提醒频道（高重要性：弹出/响铃）
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
        const val EXTRA_REMIND_SECONDS = "plan_remind_seconds"
        private const val NOTIFICATION_ID = 202 // 与 PlanScheduler.NOTIFICATION_ID_REMIND 一致
    }
}
