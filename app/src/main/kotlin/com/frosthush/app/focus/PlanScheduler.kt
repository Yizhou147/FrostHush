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
import com.frosthush.app.MainActivity
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.util.MiuiIsland
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow

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
    const val ACTION_REMIND_CLICK = "com.frosthush.app.plan.REMIND_CLICK"
    const val EXTRA_PLAN_ID = "plan_id"
    // 触发发生日的 yyyyMMdd：同计划不同发生日的闹钟 PendingIntent 因 extra 不同而互相独立，
    // 保证重排下一次触发时不会覆盖当前发生日尚未触发的结束闹钟
    private const val EXTRA_DAY = "plan_day"

    private const val CHANNEL_ID = "focus_plan"
    const val PENDING_WINDOW_MS = 5 * 60_000L

    private const val NOTIFICATION_ID_REMIND = 202
    private const val NOTIFICATION_ID_PENDING = 201
    private const val NOTIFICATION_ID_RESULT = 203

    /** 提醒通知被点击的事件（MainActivity 转发），AppRoot 收集后弹「距开始倒计时」对话框 */
    data class ReminderClick(val planId: Long)
    val reminderClick = MutableStateFlow<ReminderClick?>(null)

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
        // 开始前提醒（提前秒数由设置 planRemindSeconds 控制；0 = 不提醒，到点直接开始）
        val remindSeconds = SettingsStore.cache.planRemindSeconds
        val remindAt = start - remindSeconds * 1000L
        if (remindSeconds > 0 && remindAt > now) {
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
        // 提醒通知（焦点岛）到此结束——无论后续是否启动专注、是否进入决策窗口，
        // 都不再需要提醒岛，避免与即将发布的专注 FGS 岛共存导致两个焦点通知。
        cancelReminderNotification(context)
        if (plan.weekdays.isNotEmpty()) schedulePlan(context, plan) // 排下一次
        // 同一天已执行过则跳过（跨天/重复触发场景；「立刻开始/终止」也会标记当日已执行）
        if (FocusStore.planExecutedDay(planId) == FocusStore.todayCode()) return
        if (FocusStore.activeSession() != null) {
            // 已有专注进行中：延后 + 5 分钟决策窗口
            val deadline = System.currentTimeMillis() + PENDING_WINDOW_MS
            FocusStore.setPendingPlan(planId, deadline)
            schedulePendingTimeout(context, planId, deadline)
            showPendingNotification(context, plan)
            return
        }
        val err = startPlanFocusAndMark(context, plan)
        if (err != null) {
            showStartFailedNotification(context, err)
        }
    }

    /** 启动计划专注并标记当日已执行；不重复计划执行后自动停用。返回错误文案或 null。 */
    private fun startPlanFocusAndMark(context: Context, plan: FocusPlan): String? {
        val err = FocusManager.startPlanFocus(plan)
        if (err != null) return err
        FocusStore.markPlanExecuted(plan.id, FocusStore.todayCode())
        if (plan.weekdays.isEmpty()) {
            // 不重复计划：执行完成后自动停用，并通知用户（避免再次触发）
            FocusStore.updateFocusPlan(plan.copy(enabled = false))
            showOnceDoneNotification(context, plan)
        }
        return null
    }

    /**
     * 提醒通知被点击（MainActivity 转发）：仅当计划仍启用、当日未执行、且无该计划的活动会话时
     * 才发布事件，AppRoot 收集后弹对话框（防止迟到点击/重复触发）。
     */
    fun onReminderClicked(planId: Long) {
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        if (!plan.enabled) return
        if (FocusStore.planExecutedDay(planId) == FocusStore.todayCode()) return
        if (FocusStore.activeSession()?.planId == planId) return
        reminderClick.value = ReminderClick(planId)
    }

    /** 用户点「立刻开始」：立即启动该计划专注（后台线程调用），今日不再等闹钟 */
    fun onStartNow(context: Context, planId: Long) {
        cancelReminderNotification(context)
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        if (!plan.enabled) return
        if (FocusStore.activeSession() != null) return // 已有专注进行中，交给冲突逻辑
        val err = startPlanFocusAndMark(context, plan)
        if (err != null) showStartFailedNotification(context, err)
    }

    /**
     * 用户点「终止」：标记今日已跳过本次触发。
     * 不取消闹钟——今天 START 到点时 handleStart 会重排下次并因 executed 跳过；
     * 取消反而会丢失明天的重排。
     */
    fun onCancelToday(context: Context, planId: Long) {
        cancelReminderNotification(context)
        FocusStore.markPlanExecuted(planId, FocusStore.todayCode())
    }

    /** 到点结束：仅当活动会话由该计划启动时才恢复（避免误杀手动专注） */
    private fun handleEnd(context: Context, planId: Long) {
        cancelReminderNotification(context) // 兜底：理论上提醒早已发完，防止残留焦点岛
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
        cancelReminderNotification(context)
        val pending = FocusStore.pendingPlan() ?: return
        if (pending.planId != planId) return
        FocusStore.clearPendingPlan()
        cancelPendingTimeout(context, planId)
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        if (!plan.enabled) return
        if (FocusStore.activeSession() != null) return // 已有专注进行中，直接放弃
        val err = startPlanFocusAndMark(context, plan)
        if (err != null) showStartFailedNotification(context, err)
    }

    /** 用户点「停止」或决策窗口超时：放弃并清理 */
    fun onCancelPending(context: Context, planId: Long) {
        cancelReminderNotification(context)
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
        cancelReminderNotification(context)
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

    /**
     * 构造打开 MainActivity 的 PendingIntent（点击通知进入应用）。
     * FLAG_IMMUTABLE + FLAG_UPDATE_CURRENT：同一 requestCode 多次创建会更新而非堆叠。
     * 用于计划失败/停止/完成/决策等通知的 contentIntent。
     */
    private fun mainContentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * 取消计划前提醒通知（NOTIFICATION_ID_REMIND）。
     * 提醒通知在设置开启超级岛时注入了岛参数（焦点通知），autoCancel 对焦点通知不可靠
     * （HyperOS HideDeletedFocusController 机制），必须显式 cancel 才能让岛消失，
     * 否则与即将发布的专注 FGS 岛（FocusService ID=100）共存导致两个焦点通知。
     */
    private fun cancelReminderNotification(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_REMIND)
        }
    }

    private fun showReminderNotification(context: Context, plan: FocusPlan) {
        ensureChannel(context)
        // 点击通知 → 打开应用，AppRoot 弹「距开始倒计时」对话框（立刻开始 / 终止）
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_REMIND_CLICK
            putExtra(EXTRA_PLAN_ID, plan.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context, requestCode(plan.id, 0), clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.getString(R.string.plan_reminder_title)
        val text = context.getString(
            R.string.plan_reminder_text,
            plan.name,
            SettingsStore.cache.planRemindSeconds,
        )
        runCatching {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                // 设备在全屏应用/锁屏时 heads-up 会被系统降级为只进通知栏，
                // fullScreenIntent 强制全屏弹出（点击仍进「距开始倒计时」对话框）
                .setFullScreenIntent(contentIntent, true)
                .setAutoCancel(true)
            // 计划前提醒以焦点通知（超级岛）形式弹出：岛倒计时到计划开始时刻
            // （仅在设置开启超级岛时；关闭后此通知走普通通知，全屏弹出与点击行为不变）
            if (SettingsStore.cache.focusIslandEnabled) {
                runCatching {
                    val start = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, plan.startMinute / 60)
                        set(Calendar.MINUTE, plan.startMinute % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    builder.addExtras(
                        MiuiIsland.buildIslandExtras(context, title, text, start, System.currentTimeMillis())
                    )
                }
            }
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REMIND, builder.build())
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
                    // 点击通知正文（非按钮）打开应用：继续/停止由通知底部 Action 按钮触发
                    .setContentIntent(mainContentIntent(context))
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
                    // 点击通知打开应用（此前点不开）
                    .setContentIntent(mainContentIntent(context))
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
                    // 点击通知打开应用（此前点不开）
                    .setContentIntent(mainContentIntent(context))
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
                    // 点击通知打开应用（此前点不开）
                    .setContentIntent(mainContentIntent(context))
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}
