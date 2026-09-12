package com.frosthush.app.focus

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
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
import com.frosthush.app.util.DebugLog
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

    /** 上一次结束岛通知使用的 id：发布前先 cancel 它，避免残留的同 id 通知让本次被判成"更新" */
    private var lastFinishIslandId = 0

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
     * 主用户检测走 PackageManager（无需 Shizuku 连接），分身经 Shizuku shell 检测，可在任意时刻调用。
     */
    fun suspendedEntries(): List<String> {
        val entries = FocusStore.appGroups().flatMap { it.entries }.distinct()
        val myUser = Process.myUserHandle().hashCode()
        return entries.filter { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            if (userId == myUser) {
                // 主用户：PackageManager 直查（无需 Shizuku 连接，Shizuku 崩溃后也能检测到）
                runCatching {
                    val info = HShizuku.getApplicationInfoOrNull(pkg, userId)
                    info != null && (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
                }.getOrDefault(false)
            } else {
                // 分身：主应用 uid 跨用户反射 getApplicationInfoAsUser 会被系统拒绝返回 null
                // （无 INTERACT_ACROSS_USERS），导致分身永远漏检、无法恢复；
                // 改走 Shizuku shell（dumpsys，与 listPackagesForUser 同通道）检测。
                // Shizuku 不可用时视为未暂停（恢复分身同样依赖 Shizuku）。
                HShizuku.isSuspendedInUser(pkg, userId) == true
            }
        }
    }

    /** 解冻当前仍被暂停的应用（需 Shizuku 可用），返回成功解冻数量。应在后台线程调用。 */
    fun restoreSuspendedApps(): Int {
        var restored = 0
        suspendedEntries().forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            if (HShizuku.restoreForFocus(pkg, userId)) restored++
        }
        return restored
    }

    /** 结束专注的互斥锁：恢复/记历史/停服务可能被多线程并发触发，需串行化避免重复写历史 */
    private val restoreLock = Any()

    /** 开始专注的互斥锁：手动开始与计划 START 闹钟（各自独立线程）的"检查→写会话"必须原子，
     *  否则并发时后写者覆盖前者的会话文件，被覆盖那场冻结的应用永远无人解冻 */
    private val startLock = Any()

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
        // 尾部休息段自动修剪：分段必须以专注结束，末尾追加/删除后残留的休息段没有意义
        // （原实现对尾部休息直接报"时长无效"，用户难以理解原因）
        val trimmed = segments.dropLastWhile { !it.isFocus }
        if (trimmed.isEmpty() || trimmed.first().type != FocusStore.SEGMENT_FOCUS || trimmed.last().type != FocusStore.SEGMENT_FOCUS) {
            return@withContext app.getString(R.string.focus_duration_invalid)
        }
        if (trimmed.any { it.minutes < FocusStore.MIN_MINUTES }) {
            return@withContext app.getString(R.string.focus_duration_invalid)
        }
        val total = trimmed.sumOf { it.minutes }
        if (total !in durationRange) return@withContext app.getString(R.string.focus_duration_invalid)
        // 防御：排除自身，避免误暂停本应用
        val packages = FocusStore.blacklist().filter { it != BuildConfig.APPLICATION_ID }
        if (packages.isEmpty()) return@withContext app.getString(R.string.focus_no_apps)
        if (!shizukuReady()) return@withContext app.getString(R.string.focus_shizuku_unavailable)
        val start = System.currentTimeMillis()
        synchronized(startLock) {
            // 锁内复查：并发时另一入口可能刚写入会话（手动开始 vs 计划 START 闹钟）
            if (FocusStore.activeSession() != null) return@withContext app.getString(R.string.focus_session_exists)
            // 开新会话前清掉上一场的结束提醒：残留的结束岛会占用"每应用一条焦点通知"的位置，
            // 导致本场的岛不显示 / 倒计时不更新（2026-09-11 日志实证）
            clearFinishIsland()
            // 先持久化会话并启动服务，再立即刷新 UI 进入全屏专注：
            // 逐个暂停应用（每次一次 Shizuku IPC）耗时较长，不能等全部挂起完成才显示锁屏
            FocusStore.saveActiveSession(FocusStore.ActiveSession(packages, start, total, segments = trimmed))
            startFocusService()
        }
        bumpVersion()
        var suspended = 0
        packages.forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            if (HShizuku.freezeForFocus(pkg, userId)) suspended++
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
        synchronized(startLock) {
            // 锁内复查：并发时手动开始可能刚写入会话（handleStart 的预检查与本写入之间有窗口）
            if (FocusStore.activeSession() != null) return app.getString(R.string.focus_session_exists)
            // 清掉上一场的结束提醒，避免残留结束岛占用焦点通知位（同 startFocus）
            clearFinishIsland()
            FocusStore.saveActiveSession(
                FocusStore.ActiveSession(
                    packages, start, duration, planId = plan.id, segments = plan.segments
                )
            )
            startFocusService()
        }
        bumpVersion()
        var suspended = 0
        packages.forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            if (HShizuku.freezeForFocus(pkg, userId)) suspended++
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
                // 结束提醒在这里统一发布（本方法是所有结束路径的唯一汇聚点：tick 到点 / 计划 END
                // 闹钟 / 开机与冷启动兜底 / 界面兜底）。放在这里的意义是"谁结束会话都必然发一次"——
                // 之前只在 FocusService tick 的"到点"分支发，END 闹钟抢先结束时 tick 会走"会话已空"
                // 分支，通知永远发不出来（2026-09-10 20:30 实证：全程无结束通知）。
                // 只对"刚结束"的会话发（10 分钟内），避免进程重启/开机时给早已结束的会话补发迟到提醒。
                if (SettingsStore.cache.notifyFinishEnabled &&
                    System.currentTimeMillis() - session.endMillis <= 10 * 60_000L
                ) {
                    if (SettingsStore.cache.focusIslandEnabled) showFinishIsland(session)
                    else showFinishNotification(session)
                }
                FocusStore.clearActiveSession()
                session.packages.forEach { entry ->
                    val (pkg, userId) = FocusStore.parseEntry(entry)
                    runCatching { HShizuku.restoreForFocus(pkg, userId) }
                }
                val end = minOf(session.endMillis, System.currentTimeMillis())
                FocusStore.addHistory(
                    FocusStore.HistoryRecord(
                        session.startMillis,
                        end,
                        session.toHistorySegments(end),
                    )
                )
                // 延迟停止 FGS：让结束通知先完整滑出展示，再停服务移除"专注中"前台通知。
                // 结束通知是独立 id（不属于本服务的前台通知），停服的 REMOVE 不会影响它。
                Thread {
                    try {
                        Thread.sleep(5000)
                    } catch (_: InterruptedException) {
                    }
                    // 5 秒内可能有新会话启动（快速重开 / 背靠背计划首尾相接）：
                    // 此时服务已属于新会话，停服会杀掉新专注的 tick 与前台通知
                    if (FocusStore.activeSession() != null) {
                        DebugLog.d("Focus", "延迟停服前检测到新会话，跳过停服")
                        return@Thread
                    }
                    // 诊断：停服前后各查一次活动通知，确认结束通知是否被随前台服务一起清掉
                    logActiveNotifications("停服前")
                    app.stopService(Intent(app, FocusService::class.java))
                    try {
                        Thread.sleep(500)
                    } catch (_: InterruptedException) {
                    }
                    logActiveNotifications("停服后")
                }.start()
                phase.value = null
                bumpVersion()
                true
            }
        }
        return done
    }

    /**
     * 跳过当前休息段（通知操作触发）：把当前休息段截短为实际已休息时长（0 分钟即塌缩跳过），
     * 会话总时长相应提前，FocusService 下一 tick 观察到阶段推进后恢复暂停并继续下一段。
     * 纳入 restoreLock：读-改-写期间若 restoreAndEnd 清掉会话，旧会话会被写回成"幽灵会话"。
     * 调用方均在后台线程，可安全持锁。
     */
    fun skipRest() {
        synchronized(restoreLock) {
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
    }

    /** 按当前阶段设置挂起状态：专注段暂停，休息段解除暂停 */
    fun applySuspensionByPhase(session: FocusStore.ActiveSession) {
        val focus = session.phaseAt(System.currentTimeMillis()).isFocus
        session.packages.forEach { entry ->
            val (pkg, userId) = FocusStore.parseEntry(entry)
            // 专注段冻结（suspend 优先，失败回退 disable-user）、休息段解除（两路都恢复）
            runCatching {
                if (focus) HShizuku.freezeForFocus(pkg, userId)
                else HShizuku.restoreForFocus(pkg, userId)
            }
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

    /** 诊断打点：记录当前活动通知 id，定位结束通知"发了却看不到"是没发出去还是被清掉 */
    private fun logActiveNotifications(scene: String) {
        runCatching {
            val ids = app.getSystemService(NotificationManager::class.java).activeNotifications
                .joinToString(",") { it.id.toString() }
            DebugLog.d("Focus", "$scene 活动通知id=[$ids]")
        }.onFailure { DebugLog.e("Focus", "查询活动通知失败 $scene", it) }
    }

    /**
     * 发布结束岛通知（焦点通知模式）。由 restoreAndEnd 统一调用，保证所有结束路径恰好发一次。
     *
     * 两个关键点（针对"通知栏里什么都没有"的实测故障）：
     * - **id 每个会话唯一**：HyperOS 对同一 (包名, id) 的通知按"更新"处理，复用上一次的 id 时
     *   既不滑入岛也不重新冒泡；这里按会话开始时刻推导 id，并在发布前 cancel 上一次的 id。
     * - **不静默失败**：岛参数构建/发布异常一律打日志，发布后立刻回查 activeNotifications。
     * endMillis 必须传值（now+1000），否则 HyperOS FocusPlugin 抛 FocusParamsException: content is empty。
     */
    private fun showFinishIsland(session: FocusStore.ActiveSession) {
        val manager = NotificationManagerCompat.from(app)
        runCatching {
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(FINISH_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(app.getString(R.string.focus_notification_channel_finished)).build()
            )
        }
        val title = app.getString(R.string.focus_finished_title)
        val text = app.getString(R.string.focus_finished_text)
        // 按会话开始时刻推导 id：不同会话必然不同（秒级唯一），避免与上一次结束通知同 id
        val id = finishIslandId(session.startMillis)
        if (lastFinishIslandId != 0 && lastFinishIslandId != id) {
            runCatching { manager.cancel(lastFinishIslandId) }
        }
        runCatching {
            val now = System.currentTimeMillis()
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
            runCatching {
                builder.addExtras(MiuiIsland.buildIslandExtras(app, title, text, now + 1000L, now))
            }.onFailure {
                // 不静默：岛参数构建失败会退化成普通通知（抓日志即可确认）
                DebugLog.e("Focus", "结束通知岛参数构建失败（退化为普通通知）", it)
            }
            manager.notify(id, builder.build())
            lastFinishIslandId = id
            DebugLog.d("Focus", "结束通知已发布 id=$id")
        }.onFailure { DebugLog.e("Focus", "结束通知发布失败 id=$id", it) }
        logActiveNotifications("结束通知发布后")
    }

    /** 结束岛通知 id：由会话开始时刻推导（秒级唯一），保证每一场专注的结束通知都是新 key */
    private fun finishIslandId(sessionStartMillis: Long): Int =
        1000 + ((sessionStartMillis / 1000L) % 1_000_000L).toInt()

    /**
     * 清除上一场专注的结束提醒。新会话开始时调用：
     * HyperOS 同一时刻只保留一条焦点通知，残留的结束岛会把本场的岛顶掉（不显示/不更新，
     * 2026-09-11 日志实证：结束通知发布后前台服务通知 id=100 从活动列表消失）。
     * 进程内直接用记录下来的 id；进程重启过则用最后一条历史记录的 start 反推同一个 id。
     */
    private fun clearFinishIsland() {
        val id = lastFinishIslandId.takeIf { it != 0 }
            ?: FocusStore.history().lastOrNull()?.let { finishIslandId(it.start) }
            ?: return
        runCatching { NotificationManagerCompat.from(app).cancel(id) }
        DebugLog.d("Focus", "清除上一场结束提醒 id=$id")
        lastFinishIslandId = 0
    }

    private fun showFinishNotification(session: FocusStore.ActiveSession) {
        val manager = NotificationManagerCompat.from(app)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(FINISH_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(app.getString(R.string.focus_notification_channel_finished)).build()
        )
        val title = app.getString(R.string.focus_finished_title)
        val text = app.getString(R.string.focus_finished_text)
        // id 与结束岛同规则、按会话开始时刻推导。旧实现用固定 101：FocusService 阶段切换的
        // 前台通知从 100 起递增（含休息段的会话第一次切换即占用 101），结束通知发布后先被
        // 覆盖、再被停服的 REMOVE 一起移除 → 通知消失。改为会话级唯一 id 后，
        // clearFinishIsland 对两种模式也都能正确清理上一场的结束提醒。
        val id = finishIslandId(session.startMillis)
        if (lastFinishIslandId != 0 && lastFinishIslandId != id) {
            runCatching { manager.cancel(lastFinishIslandId) }
        }
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
            manager.notify(id, builder.build())
            lastFinishIslandId = id
            DebugLog.d("Focus", "结束通知已发布 id=$id")
        }.onFailure { DebugLog.e("Focus", "结束通知发布失败 id=$id", it) }
        logActiveNotifications("结束通知发布后")
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
