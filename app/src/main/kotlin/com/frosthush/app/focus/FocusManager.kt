package com.frosthush.app.focus

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.frosthush.app.BuildConfig
import com.frosthush.app.FrostHushApp.Companion.app
import com.frosthush.app.MainActivity
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.util.Format
import com.frosthush.app.util.MiuiIsland
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * 专注模式核心逻辑（与原雹的专注模式保持一致）：
 * 开始（持久化会话 + 启服务 + 暂停应用）、结束（恢复应用 + 记历史 + 通知）、
 * 设备重启 / 进程被杀后的会话恢复。会话期间无任何退出入口，不可打断。
 */
object FocusManager {
    private const val FINISH_CHANNEL_ID = "focus_finished"
    private const val FINISH_NOTIFICATION_ID = 101

    /** 可选专注时长范围（分钟），见 FocusStore.MIN_MINUTES / MAX_MINUTES */
    val durationRange: IntRange = FocusStore.MIN_MINUTES..FocusStore.MAX_MINUTES

    /** 数据版本号：导入/移除/开始/结束时自增，驱动界面刷新 */
    val version = MutableStateFlow(0)

    /**
     * 当前活动会话所处阶段（由 FocusService 每秒 tick 更新）：
     * 休息段为 null 之外的 REST 阶段时 UI 隐藏全屏锁屏；无会话为 null。
     */
    val phase = MutableStateFlow<FocusStore.PhaseInfo?>(null)

    /**
     * 检测当前仍处于暂停状态的应用条目（所有应用集条目取并集）。
     * 专注过程中 Shizuku 崩溃导致结束后未能解冻时，用于手动恢复。
     * 检测走 PackageManager（主应用无需 Shizuku 连接），可在任意时刻调用。
     */
    fun suspendedEntries(): List<String> {
        val entries = FocusStore.appGroups().flatMap { it.entries }.distinct()
        return entries.filter { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            runCatching {
                val info = HShizuku.getApplicationInfoOrNull(pkg, userId)
                info != null && (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
            }.getOrDefault(false)
        }
    }

    /** 解冻当前仍被暂停的应用（需 Shizuku 可用），返回成功解冻数量。应在后台线程调用。 */
    fun restoreSuspendedApps(): Int {
        var restored = 0
        suspendedEntries().forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            if (HShizuku.setAppSuspendedForFocus(pkg, false, userId)) restored++
        }
        return restored
    }

    /** 结束专注的互斥锁：恢复/记历史/停服务可能被多线程并发触发，需串行化避免重复写历史 */
    private val restoreLock = Any()

    fun shizukuReady(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun bumpVersion() {
        version.value++
    }

    /**
     * 开始专注（单段连续，兼容旧入口）。
     * 返回 null 表示成功，否则返回错误提示文案。
     */
    suspend fun startFocus(minutes: Int): String? = startFocus(listOf(FocusStore.Segment(FocusStore.SEGMENT_FOCUS, minutes)))

    /**
     * 开始专注（可分段）。segments 从专注开始、以专注结束、类型交替，每段 ≥1 分钟，总时长在范围内。
     * 返回 null 表示成功，否则返回错误提示文案。
     */
    suspend fun startFocus(segments: List<FocusStore.Segment>): String? = withContext(Dispatchers.IO) {
        if (segments.isEmpty() || segments.first().type != FocusStore.SEGMENT_FOCUS || segments.last().type != FocusStore.SEGMENT_FOCUS) {
            return@withContext app.getString(R.string.focus_duration_invalid)
        }
        if (segments.any { it.minutes < FocusStore.MIN_MINUTES }) {
            return@withContext app.getString(R.string.focus_duration_invalid)
        }
        val total = segments.sumOf { it.minutes }
        if (total !in durationRange) return@withContext app.getString(R.string.focus_duration_invalid)
        // 防御：排除自身，避免误暂停本应用
        val packages = FocusStore.blacklist().filter { it != BuildConfig.APPLICATION_ID }
        if (packages.isEmpty()) return@withContext app.getString(R.string.focus_no_apps)
        if (!shizukuReady()) return@withContext app.getString(R.string.focus_shizuku_unavailable)
        val start = System.currentTimeMillis()
        // 先持久化会话并启动服务，再立即刷新 UI 进入全屏专注：
        // 逐个暂停应用（每次一次 Shizuku IPC）耗时较长，不能等全部挂起完成才显示锁屏
        FocusStore.saveActiveSession(FocusStore.ActiveSession(packages, start, total, segments = segments))
        startFocusService()
        bumpVersion()
        var suspended = 0
        packages.forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            if (HShizuku.setAppSuspendedForFocus(pkg, true, userId)) suspended++
        }
        if (suspended == 0) {
            // 全部失败：回滚会话、停止服务，并再次刷新 UI 退出全屏专注
            FocusStore.clearActiveSession()
            app.stopService(Intent(app, FocusService::class.java))
            bumpVersion()
            return@withContext app.getString(R.string.operation_failed)
        }
        null
    }

    /** 启动专注前台服务 */
    fun startFocusService(context: android.content.Context = app) {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
        }
    }

    /**
     * 启动计划专注（由 PlanScheduler 的后台线程调用，非挂起函数）：
     * 数据源为计划绑定的应用集/直选/默认集，时长不受 240 分钟限制（可跨天）。
     * 返回 null 表示成功，否则返回错误提示文案。
     */
    fun startPlanFocus(plan: FocusStore.FocusPlan): String? {
        if (!shizukuReady()) return app.getString(R.string.plan_start_failed_shizuku)
        // 防御：排除自身，避免误暂停本应用
        val packages = FocusStore.planEntries(plan).filter { it != BuildConfig.APPLICATION_ID }
        if (packages.isEmpty()) return app.getString(R.string.plan_start_failed_empty)
        val duration = plan.durationMinutes
        if (duration < FocusStore.MIN_MINUTES) return app.getString(R.string.focus_duration_invalid)
        val start = System.currentTimeMillis()
        FocusStore.saveActiveSession(
            FocusStore.ActiveSession(
                packages, start, duration, planId = plan.id, segments = plan.segments
            )
        )
        startFocusService()
        bumpVersion()
        var suspended = 0
        packages.forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            if (HShizuku.setAppSuspendedForFocus(pkg, true, userId)) suspended++
        }
        if (suspended == 0) {
            // 全部失败：回滚会话、停止服务，并再次刷新 UI 退出全屏专注
            FocusStore.clearActiveSession()
            app.stopService(Intent(app, FocusService::class.java))
            bumpVersion()
            return app.getString(R.string.operation_failed)
        }
        return null
    }

    /**
     * 结束专注：解除暂停全部应用 → 清理会话 → 记录历史 → 停止服务 → 结束通知。
     * 应在后台线程调用（暂停解除可能较慢）。
     * 加锁串行化：FocusService / FocusLockScreen / FocusScreen 兜底可能并发触发，
     * 若不加锁会同时读到同一会话并重复写入相同 start 的历史记录，
     * 导致统计页 LazyColumn key 冲突闪退。
     */
    fun restoreAndEnd(): Boolean {
        val done = synchronized(restoreLock) {
            val session = FocusStore.activeSession()
            if (session == null) {
                false
            } else {
                FocusStore.clearActiveSession()
                session.packages.forEach { entry ->
                    val (pkg, userId) = FocusStore.parseEntry(entry)
                    runCatching { HShizuku.setAppSuspendedForFocus(pkg, false, userId) }
                }
                val end = minOf(session.endMillis, System.currentTimeMillis())
                FocusStore.addHistory(
                    FocusStore.HistoryRecord(
                        session.startMillis,
                        end,
                        session.toHistorySegments(end),
                    )
                )
                // 焦点通知模式：结束岛通知已由 FocusService（currentNotificationId++ + notify）发布，
                // 这里不再调 showFinishNotification 避免重复发布；普通通知模式由 showFinishNotification 发布。
                if (SettingsStore.cache.notifyFinishEnabled && !SettingsStore.cache.focusIslandEnabled) {
                    showFinishNotification()
                }
                app.stopService(Intent(app, FocusService::class.java))
                phase.value = null
                bumpVersion()
                true
            }
        }
        return done
    }

    /**
     * 跳过当前休息段（通知操作触发）：把当前休息段截短为实际已休息时长（0 分钟即塌缩跳过），
     * 会话总时长相应提前，FocusService 下一 tick 观察到阶段推进后恢复暂停并继续下一段专注。
     */
    fun skipRest() {
        val session = FocusStore.activeSession() ?: return
        val phase = session.phaseAt(System.currentTimeMillis())
        if (phase.type != FocusStore.SEGMENT_REST) return
        val segments = session.segments ?: return
        if (phase.index >= segments.size) return
        val elapsed = ((System.currentTimeMillis() - phase.segmentStart) / 60_000L).toInt().coerceAtLeast(0)
        val updated = segments.toMutableList().apply {
            set(phase.index, FocusStore.Segment(FocusStore.SEGMENT_REST, elapsed))
        }
        FocusStore.saveActiveSession(session.copy(segments = updated))
    }

    /** 按当前阶段设置挂起状态：专注段暂停，休息段解除暂停 */
    fun applySuspensionByPhase(session: FocusStore.ActiveSession) {
        val focus = session.phaseAt(System.currentTimeMillis()).isFocus
        session.packages.forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            runCatching { HShizuku.setAppSuspendedForFocus(pkg, focus, userId) }
        }
    }

    /**
     * 设备重启 / 应用进程被杀后的会话恢复：
     * 未到点则按当前阶段恢复挂起状态（专注段重新暂停、休息段解除）并继续倒计时，已到点则补执行恢复。
     */
    fun resumeAfterRestart(context: android.content.Context) {
        val session = FocusStore.activeSession() ?: return
        if (session.endMillis <= System.currentTimeMillis()) {
            // 已到点：补执行恢复（重启后系统已自动解除暂停，此过程基本为空操作）
            restoreAndEnd()
        } else {
            // 未到点：若 Shizuku 可用则按当前阶段纠正挂起状态，并继续倒计时
            if (shizukuReady()) applySuspensionByPhase(session)
            startFocusService(context)
        }
    }

    /** Shizuku 授权恢复后：若有活动会话且未到点则按当前阶段纠正挂起状态 */
    fun resumeSuspensionIfNeeded() {
        if (!shizukuReady()) return
        val session = FocusStore.activeSession() ?: return
        if (session.endMillis <= System.currentTimeMillis()) return
        Thread { applySuspensionByPhase(session) }.start()
    }

    private fun showFinishNotification() {
        val manager = NotificationManagerCompat.from(app)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(FINISH_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(app.getString(R.string.focus_notification_channel_finished)).build()
        )
        val title = app.getString(R.string.focus_finished_title)
        val text = app.getString(R.string.focus_finished_text)
        runCatching {
            val builder = NotificationCompat.Builder(app, FINISH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(
                    PendingIntent.getActivity(
                        app, 0, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
            // 专注结束以焦点通知（岛）形式弹出（无倒计时），仅在设置开启超级岛时
            if (SettingsStore.cache.focusIslandEnabled) {
                runCatching {
                    builder.addExtras(MiuiIsland.buildIslandExtras(app, title, text, null, null))
                }
            }
            manager.notify(FINISH_NOTIFICATION_ID, builder.build())
        }
    }

    /** 累计专注时长（分钟） */
    fun totalMinutes(): Int = FocusStore.history().sumOf { it.minutes }

    /** 时长文案：>1 小时显示 "x小时y分"，否则 "x分钟" */
    fun minutesText(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) app.getString(R.string.stats_duration_hours_minutes, h, m)
        else app.getString(R.string.stats_duration_minutes_only, minutes)
    }

    /** 倒计时格式化 */
    fun countdownText(millis: Long): String = Format.countdown(millis)
}
