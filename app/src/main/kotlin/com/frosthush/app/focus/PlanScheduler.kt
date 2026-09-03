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
import com.frosthush.app.util.DebugLog
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 专注计划调度（AlarmManager 闹钟）：
 * 每个启用计划注册下一次触发的闹钟——提醒（开始前 remindSeconds 秒，setAlarmClock 准点）、
 * 开始兜底（setAlarmClock 准点，保证 ReminderService 被回收时也准点开始）、结束（恢复应用）。
 * 冲突处理：两个启用计划时段重叠时到点自动跳过当日（计划页红色横幅提示，用户调整至无冲突才生效）；
 * 手动普通专注覆盖计划今天/次日开始时刻时由场景一预判告知（用户确定后该计划当日失效）。
 * 开机 / 应用升级后由 FocusBootReceiver 调 scheduleAll 重建全部闹钟。
 *
 * 闹钟策略（2026-09-02，对齐开源闹钟/日程类 app 做法）：
 * 提醒与开始用 setAlarmClock——系统闹钟类闹钟，Doze/省电豁免、不被合批提前/延迟投递。
 * 替代旧的「WINDOW 窗口缓冲 + ReminderService 提前保活」机制（其会被系统提前投递的
 * 闹钟提前拉起前台服务 → 强制出现一条用户可见的准备期普通通知，且内部每秒轮询等点会
 * 迟到 ~9s 导致倒计时从 20s 开始）；setAlarmClock 投递即准点，ReminderService 只在
 * 提醒时刻后短活，提醒通知即前台服务通知，不存在任何多余通知。
 */
object PlanScheduler {
    const val ACTION_REMIND = "com.frosthush.app.plan.REMIND"
    const val ACTION_START = "com.frosthush.app.plan.START"
    const val ACTION_END = "com.frosthush.app.plan.END"
    const val ACTION_REMIND_CLICK = "com.frosthush.app.plan.REMIND_CLICK"
    // 旧窗口闹钟（已废弃不再注册）：常量保留仅用于 scheduleAll/cancelPlan 清理升级残留
    const val ACTION_WINDOW = "com.frosthush.app.plan.WINDOW"

    const val EXTRA_PLAN_ID = "plan_id"
    // 提醒闹钟注册时确定的计划真实开始时刻：闹钟可能被系统延迟投递，
    // 投递时用注册时刻而非重算，避免因分钟已过而错算成下一天（24 小时倒计时 bug）
    const val EXTRA_START_MILLIS = "plan_start_millis"
    // 提醒闹钟携带的提醒提前秒数（注册时确定，避免投递时设置已变）
    const val EXTRA_REMIND_SECONDS = "plan_remind_seconds"
    // 触发发生日的 yyyyMMdd：同计划不同发生日的闹钟 PendingIntent 因 extra 不同而互相独立，
    // 保证重排下一次触发时不会覆盖当前发生日尚未触发的结束闹钟
    private const val EXTRA_DAY = "plan_day"

    private const val CHANNEL_ID = "focus_plan"

    private const val NOTIFICATION_ID_REMIND = 202
    private const val NOTIFICATION_ID_RESULT = 203

    /** 提醒通知被点击的事件（MainActivity 转发），AppRoot 收集后弹「距开始倒计时」对话框 */
    data class ReminderClick(val planId: Long)
    val reminderClick = MutableStateFlow<ReminderClick?>(null)

    // ---------- 注册 / 取消 ----------

    /** 重建所有启用计划的闹钟（开机、应用升级、计划改动后调用） */
    fun scheduleAll(context: Context) {
        val stack = Thread.currentThread().stackTrace.take(6).joinToString(" <- ") {
            it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber
        }
        DebugLog.d("Plan", "scheduleAll 调用来源: $stack")
        // 先取消所有计划（含停用）的全部闹钟（旧 REMIND/START + 新 WINDOW/END），
        // 防止升级后旧闹钟残留触发导致重复/错乱（START 残留会提前启动专注）
        FocusStore.focusPlans().forEach { cancelPlan(context, it.id) }
        FocusStore.focusPlans().filter { it.enabled }.forEach { schedulePlan(context, it) }
    }

    /**
     * 注册单个计划的下一次触发（提醒/开始 + 结束）；停用/无星期时取消。
     * weekdays 为空表示"不重复"：同样注册最近一次触发，执行后自动停用。
     *
     * 提醒/开始用 setAlarmClock（系统闹钟类闹钟：Doze/省电豁免、准点投递不提前/延迟），
     * 提醒投递后由 ReminderService 前台服务短活显示提醒倒计时并到点启动专注；
     * 另注册同刻的 START 兜底闹钟：ReminderService 被系统回收时仍准点开始
     * （ReminderService 正常启动后 handleStart 会标记当日已执行，兜底闹钟幂等跳过）。
     */
    fun schedulePlan(context: Context, plan: FocusPlan) {
        if (!plan.enabled) {
            cancelPlan(context, plan.id)
            return
        }
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val start = nextStartMillis(plan, now)
        val day = dayCodeOf(start)
        val remindSeconds = SettingsStore.cache.planRemindSeconds
        val end = start + plan.durationMinutes * 60_000L
        DebugLog.d(
            "Plan", "schedulePlan id=${plan.id} weekdays=${plan.weekdays} remindSeconds=$remindSeconds " +
                "remindAt=${start - remindSeconds * 1000L} start=$start end=$end now=$now"
        )
        if (remindSeconds > 0) {
            // 提醒闹钟：到点启动 ReminderService 显示提醒倒计时
            setAlarmClock(context, am, start - remindSeconds * 1000L,
                alarmIntent(context, ACTION_REMIND, plan.id, day, start), ACTION_REMIND, plan.id)
            // 开始兜底闹钟（幂等）：ReminderService 存活则到时已开始被跳过
            setAlarmClock(context, am, start,
                alarmIntent(context, ACTION_START, plan.id, day, start), ACTION_START, plan.id)
        } else {
            // 无提醒（0 秒）：直接到点准点开始
            setAlarmClock(context, am, start,
                alarmIntent(context, ACTION_START, plan.id, day, start), ACTION_START, plan.id)
        }
        // 结束（到点恢复应用）
        setExact(am, end, alarmIntent(context, ACTION_END, plan.id, day), ACTION_END, plan.id)
    }

    /** 取消计划已注册的全部闹钟（当前 + 未来 7 天内的发生日；实际最多存在两三个）。
     *  同时取消旧 REMIND/START/WINDOW（升级残留清理）。 */
    fun cancelPlan(context: Context, planId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val base = Calendar.getInstance()
        repeat(8) { i ->
            if (i > 0) base.add(Calendar.DAY_OF_YEAR, 1)
            val day = dayCodeOf(base.timeInMillis)
            listOf(ACTION_REMIND, ACTION_START, ACTION_END, ACTION_WINDOW).forEach { action ->
                am.cancel(alarmIntent(context, action, planId, day))
            }
        }
    }

    /**
     * 预判：本次新建普通专注会覆盖哪些启用计划的开始时刻（今天 + 跨天时的次日）。
     * 判定：计划开始时刻晚于当前（未到点）且普通专注结束时刻 ≥ 计划开始时刻
     * （含等号——结束=开始视为盖住开始瞬间）。
     * 已执行/已跳过、非执行日、开始时刻已过的计划不计入。
     * 不重复计划（weekdays 空）只检查今天；重复计划检查今天 + 次日（跨天普通专注覆盖次日凌晨）。
     */
    fun findConflictingPlans(now: Long, normalFocusEnd: Long): List<FocusPlan> {
        val today = FocusStore.todayCode()
        val todayCal = Calendar.getInstance().apply { timeInMillis = now }
        val todayDow = todayCal.get(Calendar.DAY_OF_WEEK)
        val todayWeekday = if (todayDow == Calendar.SUNDAY) 7 else todayDow - 1
        val tomorrowCal = (todayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowDow = tomorrowCal.get(Calendar.DAY_OF_WEEK)
        val tomorrowWeekday = if (tomorrowDow == Calendar.SUNDAY) 7 else tomorrowDow - 1
        return FocusStore.focusPlans().filter { plan ->
            plan.enabled && run {
                val startC = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, plan.startMinute / 60)
                    set(Calendar.MINUTE, plan.startMinute % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val planStartToday = startC.timeInMillis
                val planStartTomorrow = (startC.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                // 今天：未执行/未跳过 + 今天执行日 + planStartToday 在 (now, normalFocusEnd]
                val todayConflict = FocusStore.planExecutedDay(plan.id) != today &&
                    (plan.weekdays.isEmpty() || todayWeekday in plan.weekdays) &&
                    planStartToday > now && normalFocusEnd >= planStartToday
                // 明天：仅重复计划 + 明天执行日 + planStartTomorrow 在 (now, normalFocusEnd]
                // （跨天普通专注覆盖次日凌晨计划；不重复计划明天不会自动触发，不检查）
                val tomorrowConflict = plan.weekdays.isNotEmpty() &&
                    tomorrowWeekday in plan.weekdays &&
                    planStartTomorrow > now && normalFocusEnd >= planStartTomorrow
                todayConflict || tomorrowConflict
            }
        }
    }

    /**
     * 标记给定计划当日已执行（跳过本次触发，到点 handleStart 会因 executed 跳过启动）。
     * 重复计划（weekdays 非空）：handleStart 会先 schedulePlan 重排下次 → 第二天正常生效。
     * 不重复计划（weekdays 空）：handleStart 不重排、保持 enabled，下次 scheduleAll
     * （重启/打开应用）才可能重新注册闹钟——即"将不再执行"。
     */
    fun markPlansSkippedToday(plans: List<FocusPlan>) {
        val today = FocusStore.todayCode()
        plans.forEach { plan -> FocusStore.markPlanExecuted(plan.id, today) }
    }

    /** 一对冲突的启用计划（执行时段重叠，含跨天尾部与次日头部） */
    data class PlanConflict(val planA: FocusPlan, val planB: FocusPlan)

    /**
     * 检测所有启用计划两两之间的时段冲突（含跨天尾部与次日头部重叠）。
     * 不重复计划（weekdays 空）视为在其执行日（nextStartMillis 计算的最近日期）执行，
     * 只在该日与其它计划冲突才计入。
     */
    fun findPlanConflicts(): List<PlanConflict> {
        val plans = FocusStore.focusPlans().filter { it.enabled }
        val conflicts = mutableListOf<PlanConflict>()
        for (i in plans.indices) {
            for (j in i + 1 until plans.size) {
                val a = plans[i]
                val b = plans[j]
                if (plansOverlap(a, b)) conflicts.add(PlanConflict(a, b))
            }
        }
        return conflicts
    }

    /** 两个启用计划的执行时段是否重叠（含跨天尾部与次日头部） */
    private fun plansOverlap(a: FocusPlan, b: FocusPlan): Boolean {
        val daysA = executionDays(a)
        val daysB = executionDays(b)
        for (d in 1..7) {
            val nextD = if (d == 7) 1 else d + 1
            // case 1: A 和 B 都在 d 执行，同日时段重叠
            if (d in daysA && d in daysB && segmentsOverlap(a, b)) return true
            // case 2: A 在 d 执行且跨天，B 在次日执行，A 尾部与 B 头部重叠
            if (d in daysA && crossesMidnight(a) && nextD in daysB && tailOverlapsHead(a, b)) return true
            // case 3: B 在 d 执行且跨天，A 在次日执行，B 尾部与 A 头部重叠
            if (d in daysB && crossesMidnight(b) && nextD in daysA && tailOverlapsHead(b, a)) return true
        }
        return false
    }

    /** 计划的执行日集合：重复计划为 weekdays；不重复计划为 nextStartMillis 返回日期的 weekday */
    private fun executionDays(plan: FocusPlan): Set<Int> {
        if (plan.weekdays.isNotEmpty()) return plan.weekdays
        val startMillis = nextStartMillis(plan, System.currentTimeMillis())
        val c = Calendar.getInstance().apply { timeInMillis = startMillis }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        return setOf(if (dow == Calendar.SUNDAY) 7 else dow - 1)
    }

    /** 是否跨天（结束时间在次日，或全天 24h） */
    private fun crossesMidnight(plan: FocusPlan): Boolean = plan.endMinute <= plan.startMinute

    /**
     * 两计划同日时段是否重叠（在 [0, 2880) 范围内，跨天计划时段延伸到次日）。
     * 全天计划（end == start）视为 [start, start + 1440)，长度 24h。
     */
    private fun segmentsOverlap(a: FocusPlan, b: FocusPlan): Boolean {
        val extA = if (a.endMinute > a.startMinute) a.endMinute else a.endMinute + 1440
        val extB = if (b.endMinute > b.startMinute) b.endMinute else b.endMinute + 1440
        return maxOf(a.startMinute, b.startMinute) < minOf(extA, extB)
    }

    /**
     * 跨天计划 A 的尾部（次日 [0, endA) 或全天 [0, 1440)）与 B 头部（[startB, extB)）在 [0, 1440) 内重叠。
     * 仅在 case 2/3 调用（A 已确定跨天）。
     */
    private fun tailOverlapsHead(a: FocusPlan, b: FocusPlan): Boolean {
        val aTailEnd = if (a.endMinute < a.startMinute) a.endMinute else 1440
        val extB = if (b.endMinute > b.startMinute) b.endMinute else b.endMinute + 1440
        return maxOf(0, b.startMinute) < minOf(aTailEnd, extB)
    }

    /** 该计划是否处于当前启用计划的时段冲突中（到点将不生效，需用户调整至无冲突） */
    fun isInConflictGroup(plan: FocusPlan): Boolean {
        return findPlanConflicts().any { it.planA.id == plan.id || it.planB.id == plan.id }
    }

    // ---------- 闹钟回调 ----------

    fun onAlarm(context: Context, action: String, planId: Long, startMillis: Long = 0L) {
        when (action) {
            // 提醒闹钟（setAlarmClock 准点投递）：投递即提醒时刻，直接发布提醒通知。
            // 焦点岛模式直接 notify 上岛，不经 ReminderService——FGS 通知同 ID 覆盖成岛
            // 会被 HyperOS 推迟约 10s 上屏（v1.2.0 直接 notify 是准的，重构后引入延迟）；
            // 普通通知模式 notify 后由 ReminderService 前台服务支撑每秒走秒。
            ACTION_REMIND -> showReminder(context, planId, startMillis)
            // 开始闹钟（含无提醒计划）：到点启动专注
            ACTION_START -> Thread { handleStart(context, planId) }.start()
            ACTION_END -> handleEnd(context, planId)
            // 旧窗口闹钟（废弃机制残留，升级后由 scheduleAll cancelPlan 清理，不会触发）
        }
    }

    /**
     * 提醒闹钟投递：已过开始时刻则交给 START 兜底（不发过期提醒）；否则发布提醒通知。
     * setAlarmClock 投递误差毫秒级，投递即提醒时刻，无中间服务层。
     */
    private fun showReminder(context: Context, planId: Long, startMillis: Long) {
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        // 计划今天已执行/已跳过：不发提醒（避免"发通知但点击无反应"）
        if (FocusStore.planExecutedDay(planId) == FocusStore.todayCode()) return
        // 优先用注册闹钟时的真实开始时刻（旧闹钟无该 extra 时回退重算）
        val start = if (startMillis > 0L) startMillis
                    else nextStartMillis(plan, System.currentTimeMillis())
        // 提醒闹钟延迟投递（已到/已过开始时刻）：计划应由 START 启动，不再发提醒
        if (System.currentTimeMillis() >= start) return
        showReminderNotification(context, plan, start)
    }

    /** 到点：先排下一次触发；冲突组/已有专注则当日跳过，否则启动计划专注。
     *  由 START 闹钟投递 / 「立刻开始」触发。 */
    internal fun handleStart(context: Context, planId: Long) {
        val plan = FocusStore.focusPlans().firstOrNull { it.id == planId } ?: return
        if (!plan.enabled) {
            DebugLog.d("Plan", "handleStart 计划已停用 id=$planId")
            return
        }
        // 提醒通知（焦点岛）到此结束——无论后续是否启动专注，
        // 都不再需要提醒岛，避免与即将发布的专注 FGS 岛共存导致两个焦点通知。
        cancelReminderNotification(context)
        if (plan.weekdays.isNotEmpty()) schedulePlan(context, plan) // 排下一次
        // 同一天已执行过则跳过（跨天/重复触发场景；「立刻开始/终止」也会标记当日已执行）
        if (FocusStore.planExecutedDay(planId) == FocusStore.todayCode()) {
            DebugLog.d("Plan", "handleStart 今日已执行/跳过 id=$planId")
            return
        }
        // 冲突组检查：该计划处于当前启用计划的时段冲突中 → 当日不生效。
        // 用户需调整至无冲突才会生效（开关仍开但到点跳过，计划页红色横幅已提示）。
        // 标记当日已执行后，重复计划 schedulePlan 已重排下次→次日再检查；不重复计划保持 enabled，
        // 下次 scheduleAll（重启/打开应用）才可能重新注册闹钟。
        if (isInConflictGroup(plan)) {
            DebugLog.d("Plan", "handleStart 冲突组跳过 id=$planId")
            FocusStore.markPlanExecuted(plan.id, FocusStore.todayCode())
            return
        }
        // 已有专注进行中：当日跳过（兜底——冲突组检查 + 场景一预判已覆盖常规情况）
        if (FocusStore.activeSession() != null) {
            DebugLog.d("Plan", "handleStart 已有专注进行中跳过 id=$planId")
            FocusStore.markPlanExecuted(plan.id, FocusStore.todayCode())
            return
        }
        DebugLog.d("Plan", "handleStart 启动计划专注 id=$planId now=${System.currentTimeMillis()}")
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
        DebugLog.d("Plan", "handleEnd id=$planId sessionPlanId=${session.planId} match=${session.planId == planId}")
        if (session.planId == planId) {
            Thread { FocusManager.restoreAndEnd() }.start()
        }
    }

    // ---------- 时间计算 ----------

    /** 计划下一次开始时间（毫秒）：按星期过滤，当天已过开始时间则顺延到下一匹配日；
     *  weekdays 为空（不重复）时取最近一次（今天未过则今天，否则明天）。 */
    private fun nextStartMillis(plan: FocusPlan, fromMillis: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = fromMillis }
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
            if (weekday in plan.weekdays) {
                // 候选日当天开始时刻：毫秒级比较（now 严格早于开始时刻才算"今天未过"），
                // 避免分钟粒度误判导致跳过当天、顺延到明天
                val candidate = (c.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, plan.startMinute / 60)
                    set(Calendar.MINUTE, plan.startMinute % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (candidate.timeInMillis > fromMillis) return candidate.timeInMillis
            }
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        // 星期集合非空时 7 天内必命中，这里仅兜底
        return fromMillis + 24 * 3600_000L
    }

    // ---------- 闹钟 / 通知辅助 ----------

    private fun setExact(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent, action: String, planId: Long) {
        val now = System.currentTimeMillis()
        // ahead 为负说明注册了"已过时间"的闹钟：AlarmManager 会立即投递——这是排查
        // "提前开始/延迟开始"的关键（区分系统延迟投递 vs 应用注册过期时间）
        DebugLog.d(
            "Alarm", "setExact action=$action planId=$planId triggerAt=$triggerAtMillis " +
                "now=$now ahead=${triggerAtMillis - now}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    /**
     * 注册系统闹钟类闹钟（setAlarmClock）：Doze/省电豁免、不被合批提前/延迟投递，
     * 用于计划提醒/开始这类"必须准点"的时刻（对齐闹钟类 app 的做法）。
     * 副作用：状态栏会显示"下一个闹钟"小图标（时间 = 最近一次计划提醒/开始时刻）。
     * 无需 SCHEDULE_EXACT_ALARM 权限（manifest 已声明 USE_EXACT_ALARM，系统自动授予）。
     */
    private fun setAlarmClock(
        context: Context, am: AlarmManager, triggerAtMillis: Long,
        pi: PendingIntent, action: String, planId: Long
    ) {
        val now = System.currentTimeMillis()
        DebugLog.d(
            "Alarm", "setAlarmClock action=$action planId=$planId triggerAt=$triggerAtMillis " +
                "now=$now aheadSec=${(triggerAtMillis - now) / 1000L}"
        )
        runCatching {
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtMillis, mainContentIntent(context)),
                pi
            )
        }.onFailure {
            DebugLog.e("Alarm", "setAlarmClock 失败 action=$action planId=$planId", it)
        }
    }

    private fun actionIndex(action: String): Int = when (action) {
        ACTION_REMIND -> 0
        ACTION_START -> 1
        ACTION_END -> 2
        ACTION_WINDOW -> 4 // 3 预留（无冲突），保证 requestCode 唯一
        else -> 3 // 兜底（目前无其他 action）
    }

    private fun requestCode(planId: Long, actionIndex: Int): Int =
        ((planId % 1_000_000) * 10 + actionIndex).toInt()

    /** 触发发生日（yyyyMMdd） */
    private fun dayCodeOf(millis: Long): Int =
        Calendar.getInstance().apply { timeInMillis = millis }.let {
            it.get(Calendar.YEAR) * 10000 + (it.get(Calendar.MONTH) + 1) * 100 + it.get(Calendar.DAY_OF_MONTH)
        }

    private fun alarmIntent(
        context: Context, action: String, planId: Long, day: Int? = null, startMillis: Long? = null
    ): PendingIntent {
        val intent = Intent(context, PlanAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_PLAN_ID, planId)
            if (day != null) putExtra(EXTRA_DAY, day)
            if (startMillis != null) putExtra(EXTRA_START_MILLIS, startMillis)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(planId, actionIndex(action)), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
     * 取消计划前提醒通知（NOTIFICATION_ID_REMIND）并停止 ReminderService。
     * 提醒通知在设置开启超级岛时注入了岛参数（焦点通知），autoCancel 对焦点通知不可靠
     * （HyperOS HideDeletedFocusController 机制），必须显式 cancel 才能让岛消失，
     * 否则与即将发布的专注 FGS 岛（FocusService ID=100）共存导致两个焦点通知。
     */
    private fun cancelReminderNotification(context: Context) {
        // 停止 ReminderService（普通通知模式下支撑每秒 ticker 的前台服务）
        runCatching { context.stopService(Intent(context, ReminderService::class.java)) }
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_REMIND)
        }
    }

    /**
     * 显示计划前提醒通知（焦点岛/普通通知）。
     * 焦点岛模式：直接 notify（不经前台服务，避免 FGS 覆盖成岛被 HyperOS 延迟上屏）；
     * 普通通知模式：notify 后启动 ReminderService 前台服务支撑每秒走秒。
     */
    private fun showReminderNotification(context: Context, plan: FocusPlan, startMillis: Long) {
        ensureChannel(context)
        val islandEnabled = SettingsStore.cache.focusIslandEnabled
        val now = System.currentTimeMillis()
        DebugLog.d(
            "Remind", "showReminder plan=${plan.name} start=$startMillis now=$now " +
                "remaining=${(startMillis - now) / 1000L}s island=$islandEnabled"
        )
        val contentIntent = reminderContentIntent(context, plan.id)
        val title = context.getString(R.string.plan_reminder_title)
        // 焦点通知模式：文案用设置提醒秒数（静态，岛倒计时由系统原生渲染）
        // 普通通知模式：文案用实时剩余秒数（ReminderService 每秒更新）
        val text = if (islandEnabled) {
            context.getString(R.string.plan_reminder_text, plan.name, SettingsStore.cache.planRemindSeconds)
        } else {
            val remaining = ((startMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L).toInt()
            context.getString(R.string.plan_reminder_text, plan.name, remaining)
        }
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
            if (islandEnabled) {
                // 焦点通知模式：注入岛参数，岛倒计时到计划开始时刻（不每秒 notify）
                runCatching {
                    builder.addExtras(
                        MiuiIsland.buildIslandExtras(context, title, text, startMillis, System.currentTimeMillis())
                    )
                }
            } else {
                // 普通通知模式：setOnlyAlertOnce 防每秒 notify 提醒打扰
                builder.setOnlyAlertOnce(true)
            }
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_REMIND, builder.build())
        }
        // 普通通知模式：启动 ReminderService 前台服务支撑每秒 ticker（跟专注倒计时实现方法一致）。
        // 焦点通知模式不启动（岛倒计时由系统原生渲染，开始由 START 闹钟准点处理）
        if (!islandEnabled) {
            val serviceIntent = Intent(context, ReminderService::class.java).apply {
                putExtra(ReminderService.EXTRA_PLAN_ID, plan.id)
                putExtra(ReminderService.EXTRA_PLAN_NAME, plan.name)
                putExtra(ReminderService.EXTRA_START_MILLIS, startMillis)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    /** 提醒通知点击 PendingIntent（打开 MainActivity，带 ACTION_REMIND_CLICK + planId） */
    private fun reminderContentIntent(context: Context, planId: Long): PendingIntent {
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_REMIND_CLICK
            putExtra(EXTRA_PLAN_ID, planId)
        }
        return PendingIntent.getActivity(
            context, requestCode(planId, 0), clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
