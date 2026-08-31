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
 * 计划提醒窗口前台服务（新机制，2026-08-31）。
 *
 * 由窗口闹钟（ACTION_WINDOW，注册在 开始 - 提醒秒数 - 5min 缓冲）触发启动。
 * 前台服务在应用内对齐三个时刻，吸收系统闹钟合批投递的提前/延迟波动：
 *  - 投递即已过开始时刻（重度延迟）：直接启动专注，不发提醒（提醒无意义）；
 *  - 到提醒时刻（start - remindSeconds）：显示提醒通知（焦点岛/普通）；
 *  - 到开始时刻（start）：调用 PlanScheduler.handleStart 启动专注并停止。
 * 只要系统延迟 ≤ 缓冲（5min），提醒必然出现在开始之前，不再有"提醒被吞/提前开始"。
 *
 * 普通通知模式的提醒通知每秒更新倒计时（ticker）；焦点岛模式由系统原生渲染静态倒计时。
 */
class ReminderService : Service() {
    private val channelID = "focus_plan"
    private val handler = Handler(Looper.getMainLooper())

    private var planId: Long = 0
    private var planName: String = ""
    private var startMillis: Long = 0L
    private var remindSeconds: Int = 0
    private var remindAt: Long = 0L
    private var reminderShown = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            when {
                now >= startMillis -> {
                    // 到开始时刻：启动专注（handleStart 内部会停止本服务并清理提醒通知）
                    DebugLog.d("Remind", "到点启动专注 now=$now startMillis=$startMillis")
                    runCatching { PlanScheduler.handleStart(this@ReminderService, planId) }
                    stopSelf()
                    return
                }
                now >= remindAt && !reminderShown -> {
                    // 到提醒时刻：显示提醒通知（一次；普通模式后续由每秒 tick 更新倒计时）
                    reminderShown = true
                    showReminderNotification()
                }
                reminderShown -> {
                    // 提醒已显示：普通通知模式每秒更新倒计时（焦点岛模式静态无需更新）
                    if (!SettingsStore.cache.focusIslandEnabled) {
                        updateReminderTicker(now)
                    }
                }
                else -> {
                    // 准备状态：每秒更新剩余时间（低调通知，不打扰）
                    updatePreparingNotification(now)
                }
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
        remindAt = startMillis - remindSeconds * 1000L
        reminderShown = false
        val now = System.currentTimeMillis()
        DebugLog.d(
            "Remind", "onStartCommand plan=$planName startMillis=$startMillis remindAt=$remindAt " +
                "now=$now remaining=${(startMillis - now) / 1000L}s"
        )
        runCatching { createChannel() }
        if (now >= startMillis) {
            // 窗口闹钟重度延迟（投递已过开始时刻）：直接启动专注，提醒无意义不再发
            DebugLog.d("Remind", "投递已过开始时刻，直接开始 planId=$planId late=${now - startMillis}ms")
            // 先 startForeground 保活（startForegroundService 后 5 秒内必须调用，防崩溃）
            startForeground(NOTIFICATION_ID, buildPreparingNotification(now))
            runCatching { PlanScheduler.handleStart(this, planId) }
            stopSelf()
            return START_NOT_STICKY
        }
        if (now >= remindAt) {
            // 轻度延迟（投递落在提醒窗口内）：立即显示提醒（倒计时缩短）
            reminderShown = true
            startForeground(NOTIFICATION_ID, buildReminderTicker(now))
            showReminderNotification()
        } else {
            // 提前/准点投递：先显示"即将开始"准备通知（低调），到提醒时刻升级为提醒
            startForeground(NOTIFICATION_ID, buildPreparingNotification(now))
        }
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

    /** 准备状态：每秒更新剩余时间 */
    private fun updatePreparingNotification(now: Long) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildPreparingNotification(now))
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

    /** 准备状态通知（低调，不响铃）：计划名 + 距开始剩余时间 */
    private fun buildPreparingNotification(now: Long): Notification {
        val remainingMs = startMillis - now
        val text = getString(R.string.plan_preparing_text, planName, formatRemaining(remainingMs))
        return NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(getString(R.string.plan_preparing_title))
            .setContentText(text)
            .setContentIntent(reminderClickIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun formatRemaining(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0L)
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) getString(R.string.plan_preparing_min_sec, m, s)
        else getString(R.string.plan_preparing_sec, s)
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
