package com.frosthush.app.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.frosthush.app.FrostHushApp.Companion.app
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.FocusPlan
import java.util.Calendar

/**
 * 专注计划调度（AlarmManager 精确闹钟）：
 * 每个启用计划注册三个闹钟——开始前 15 秒提醒、开始（启动计划专注）、结束（恢复应用）。
 * 到点若已有专注进行中则延后：持久化 pendingPlan 并进入 5 分钟决策窗口
 * （通知带「继续/停止」操作 + AlarmManager 超时兜底，进程被杀可恢复）。
 * 开机 / 应用升级后由 FocusBootReceiver 调 scheduleAll 重建全部闹钟。
 */
object PlanScheduler {
    const val ACTION_REMIND = "com.frosthush.app.plan.REMIND"
    const val ACTION_START = "com.frosthush.app.plan.START"
    const val ACTION_END = "com.frosthush.app.plan.END"
    const val ACTION_PENDING_TIMEOUT = "com.frosthush.app.plan.PENDING_TIMEOUT"
    const val ACTION_RESUME = "com.frosthush.app.plan.RESUME"
    const val ACTION_CANCEL = "com.frosthush.app.plan.CANCEL"
    const val EXTRA_PLAN_ID = "plan_id"
    // 触发发生日的 yyyyMMdd：同计划不同发生日的闹钟 PendingIntent 因 extra 不同而互相独立，
    // 保证重排下一次触发时不会覆盖当前发生日尚未触发的结束闹钟
    private const val EXTRA_DAY = "plan_day"

    private const val CHANNEL_ID = "focus_plan"
    private const val REMINDER_OFFSET_MS = 15_000L
    const val PENDING_WINDOW_MS = 5 * 60_000L

    private const val NOTIFICATION_ID_REMIND = 202
    private const val NOTIFICATION_ID_PENDING = 201
    private const val NOTIFICATION_ID_RESULT = 203

    // ---------- 注册 / 取消 ----------

    /** 重建所有启用计划的闹钟（开机、应用升级、计划改动后调用） */
    fun scheduleAll(context: Context) {
        FocusStore.focusPlans().filter { it.enabled }.forEach { schedulePlan(context, it) }
        restorePendingAlarm(context)
    }

    /** 注册单个计划的下一次触发（提醒 + 开始 + 结束）；停用/无星期时取消。
     *  weekdays 为空表示"不重复"：同样注册最近一次触发，执行后自动停用。 */
    fun schedulePlan(context: Context, plan: FocusPlan) {
        if (!plan.enabled) {
            cancelPlan(context, plan.id)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val start = nextStartMillis(plan, now)
        val day = dayCodeOf(start)
        // 开始前 15 秒提醒
        val remindAt = start - REMINDER_OFFSET_MS
        if (remindAt > now) {
            setExact(am, remindAt, alarmIntent(context, ACTION_REMIND, plan.id, day))
        }
        // 开始
        setExact(am, start, alarmIntent(context, ACTION_START, plan.id, day))
        // 结束（到点恢复应用）
        setExact(am, start + plan.durationMinutes * 60_000L, alarmIntent(context, ACTION_END, plan.id, day))
    }

    /** 取消计划已注册的全部闹钟（当前 + 未来 7 天内的发生日；实际最多存在两个） */
    fun cancelPlan(context: Context, planId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val base = Calendar.getInstance()
        repeat(8) { i ->
            if (i > 0) base.add(Calendar.DAY_OF_YEAR, 1)
            val day = dayCodeOf(base.timeInMillis)
            listOf(ACTION_REMIND, ACTION_START, ACTION_END).forEach { action ->
                am.cancel(alarmIntent(context, action, planId, day))
            }
        }
    }

    // ---------- 闹钟回调 ----------

    fun onAlarm(context: Context, action: String, planId: Long) {
        when (action) {
            ACTION_REMIND -> {
                val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
                showReminderNotification(context, plan)
            }
            ACTION_START -> Thread { handleStart(context, planId) }.start()
            ACTION_END -> handleEnd(context, planId)
            ACTION_PENDING_TIMEOUT -> handlePendingTimeout(context)
        }
    }

    /** 到点：先排下一次触发；无冲突则启动计划专注，冲突则进入 5 分钟决策窗口 */
    private fun handleStart(context: Context, planId: Long) {
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        if (!plan.enabled) return
        if (plan.weekdays.isNotEmpty()) schedulePlan(context, plan) // 排下一次
        // 同一天已执行过则跳过（跨天/重复触发场景）
        if (FocusStore.planExecutedDay(planId) == FocusStore.todayCode()) return
        if (FocusStore.activeSession() != null) {
            // 已有专注进行中：延后 + 5 分钟决策窗口
            val deadline = System.currentTimeMillis() + PENDING_WINDOW_MS
            FocusStore.setPendingPlan(planId, deadline)
            schedulePendingTimeout(context, planId, deadline)
            showPendingNotification(context, plan)
            return
        }
        val err = FocusManager.startPlanFocus(plan)
        if (err != null) {
            showStartFailedNotification(context, err)
        } else {
            FocusStore.markPlanExecuted(planId, FocusStore.todayCode())
            if (plan.weekdays.isEmpty()) {
                // 不重复计划：执行完成后自动停用，并通知用户（避免再次触发）
                FocusStore.updateFocusPlan(plan.copy(enabled = false))
                showOnceDoneNotification(context, plan)
            }
        }
    }

    /** 到点结束：仅当活动会话由该计划启动时才恢复（避免误杀手动专注） */
    private fun handleEnd(context: Context, planId: Long) {
        val session = FocusStore.activeSession() ?: return
        if (session.planId == planId) {
            Thread { FocusManager.restoreAndEnd() }.start()
        }
    }

    // ---------- 5 分钟决策窗口 ----------

    /** 当前专注结束后检查：有待启动计划则（重新）发送决策通知并保证超时兜底 */
    fun checkPendingAfterFocusEnd() {
        val pending = FocusStore.pendingPlan() ?: return
        if (pending.deadline <= System.currentTimeMillis()) {
            FocusStore.clearPendingPlan() // 进程被杀期间已超时：直接放弃
            return
        }
        val plan = FocusStore.focusPlans().firstOrNull { it.id == pending.planId }
            ?: run { FocusStore.clearPendingPlan(); return }
        schedulePendingTimeout(app, pending.planId, pending.deadline)
        showPendingNotification(app, plan)
    }

    /** 用户点「继续」：启动该计划专注（后台线程调用） */
    fun onResumePending(context: Context, planId: Long) {
        val pending = FocusStore.pendingPlan() ?: return
        if (pending.planId != planId) return
        FocusStore.clearPendingPlan()
        cancelPendingTimeout(context, planId)
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        if (!plan.enabled) return
        if (FocusStore.activeSession() != null) return // 已有专注进行中，直接放弃
        val err = FocusManager.startPlanFocus(plan)
        if (err != null) showStartFailedNotification(context, err)
        else FocusStore.markPlanExecuted(planId, FocusStore.todayCode())
    }

    /** 用户点「停止」或决策窗口超时：放弃并清理 */
    fun onCancelPending(planId: Long) {
        val pending = FocusStore.pendingPlan() ?: return
        if (pending.planId != planId) return
        FocusStore.clearPendingPlan()
        cancelPendingTimeout(app, planId)
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId }
        if (plan != null) showStoppedNotification(app, plan)
    }

    /** 重启 / 进程被杀后恢复待启动计划的决策窗口 */
    fun restorePendingAlarm(context: Context) {
        val pending = FocusStore.pendingPlan() ?: return
        if (pending.deadline <= System.currentTimeMillis()) {
            FocusStore.clearPendingPlan()
            return
        }
        schedulePendingTimeout(context, pending.planId, pending.deadline)
        val plan = FocusStore.focusPlans().firstOrNull { it.id == pending.planId }
        if (plan != null) showPendingNotification(context, plan)
    }

    private fun handlePendingTimeout(context: Context) {
        val pending = FocusStore.pendingPlan() ?: return
        FocusStore.clearPendingPlan()
        val plan = FocusStore.focusPlans().firstOrNull { it.id == pending.planId }
        if (plan != null) showStoppedNotification(context, plan)
    }

    // ---------- 时间计算 ----------

    /** 计划下一次开始时间（毫秒）：按星期过滤，当天已过开始时间则顺延到下一匹配日；
     *  weekdays 为空（不重复）时取最近一次（今天未过则今天，否则明天）。 */
    private fun nextStartMillis(plan: FocusPlan, fromMillis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = fromMillis }
        val minuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        if (plan.weekdays.isEmpty()) {
            c.set(Calendar.HOUR_OF_DAY, plan.startMinute / 60)
            c.set(Calendar.MINUTE, plan.startMinute % 60)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            if (c.timeInMillis > fromMillis) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
            return c.timeInMillis
        }
        for (offset in 0 until 8) {
            val dow = c.get(Calendar.DAY_OF_WEEK)
            // Calendar: SUNDAY=1..SATURDAY=7；计划星期：1=周一..7=周日
            val weekday = if (dow == Calendar.SUNDAY) 7 else dow - 1
            if (weekday in plan.weekdays && (offset > 0 || minuteOfDay < plan.startMinute)) {
                c.set(Calendar.HOUR_OF_DAY, plan.startMinute / 60)
                c.set(Calendar.MINUTE, plan.startMinute % 60)
                c.set(Calendar.SECOND, 0)
                c.set(Calendar.MILLISECOND, 0)
                return c.timeInMillis
            }
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        // 星期集合非空时 7 天内必命中，这里仅兜底
        return fromMillis + 24 * 3600_000L
    }

    // ---------- 闹钟 / 通知辅助 ----------

    private fun setExact(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun actionIndex(action: String): Int = when (action) {
        ACTION_REMIND -> 0
        ACTION_START -> 1
        ACTION_END -> 2
        else -> 3 // PENDING_TIMEOUT / RESUME / CANCEL 决策类
    }

    private fun requestCode(planId: Long, actionIndex: Int): Int =
        ((planId % 1_000_000) * 10 + actionIndex).toInt()

    /** 触发发生日（yyyyMMdd） */
    private fun dayCodeOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.let {
            it.get(Calendar.YEAR) * 10000 + (it.get(Calendar.MONTH) + 1) * 100 + it.get(Calendar.DAY_OF_MONTH)
        }

    private fun alarmIntent(context: Context, action: String, planId: Long, day: Int? = null): PendingIntent {
        val intent = Intent(context, PlanAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_PLAN_ID, planId)
            if (day != null) putExtra(EXTRA_DAY, day)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(planId, actionIndex(action)), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedulePendingTimeout(context: Context, planId: Long, deadline: Long) {
        setExact(context.getSystemService(AlarmManager::class.java), deadline, alarmIntent(context, ACTION_PENDING_TIMEOUT, planId))
    }

    private fun cancelPendingTimeout(context: Context, planId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, ACTION_PENDING_TIMEOUT, planId))
    }

    private fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.plan_notification_channel)).build()
        )
    }

    private fun showReminderNotification(context: Context, plan: FocusPlan) {
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_REMIND,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_focus)
                    .setContentTitle(context.getString(R.string.plan_reminder_title))
                    .setContentText(context.getString(R.string.plan_reminder_text, plan.name))
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private fun showPendingNotification(context: Context, plan: FocusPlan) {
        ensureChannel(context)
        val resume = PendingIntent.getBroadcast(
            context, requestCode(plan.id, 3),
            Intent(context, PlanDecisionReceiver::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_PLAN_ID, plan.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getBroadcast(
            context, requestCode(plan.id, 4),
            Intent(context, PlanDecisionReceiver::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_PLAN_ID, plan.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_PENDING,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_focus)
                    .setContentTitle(context.getString(R.string.plan_pending_title))
                    .setContentText(context.getString(R.string.plan_pending_text, plan.name))
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_stat_focus, context.getString(R.string.plan_pending_continue), resume)
                    .addAction(R.drawable.ic_stat_focus, context.getString(R.string.plan_pending_stop), cancel)
                    .build()
            )
        }
    }

    private fun showStartFailedNotification(context: Context, message: String) {
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_RESULT,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_focus)
                    .setContentTitle(context.getString(R.string.plan_start_failed_title))
                    .setContentText(message)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private fun showStoppedNotification(context: Context, plan: FocusPlan) {
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_RESULT,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_focus)
                    .setContentTitle(context.getString(R.string.plan_pending_stopped))
                    .setContentText(context.getString(R.string.plan_pending_stopped_text, plan.name))
                    .setAutoCancel(true)
                    .build()
            )
        }
    }

    private fun showOnceDoneNotification(context: Context, plan: FocusPlan) {
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID_RESULT,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_focus)
                    .setContentTitle(context.getString(R.string.plan_once_done_title))
                    .setContentText(context.getString(R.string.plan_once_done_text, plan.name))
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}
