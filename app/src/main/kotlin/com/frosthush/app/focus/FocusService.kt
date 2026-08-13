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
import com.frosthush.app.data.FocusStore.ActiveSession
import com.frosthush.app.data.FocusStore.PhaseInfo
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.util.MiuiIsland

/**
 * 专注模式前台服务：常驻通知显示当前阶段（专注/休息）+ 原生倒计时；
 * 是否注入小米超级岛参数由设置 SettingsStore.focusIslandEnabled 控制，中途切换立即生效。
 *
 * 对齐番茄Todo 的岛行为（adb 逆向）：
 * - 卡片文案静态（专注中/休息中），倒计时走秒由通知 chronometer（when=本阶段结束时刻）与
 *   岛参数 timerInfo 原生渲染，**无需每秒 notify**
 * - 只在阶段切换/会话开始时 notify 岛通知，enableFloat 恒 true → 每次更新岛自动展开弹出
 *   （小岛变大、像普通通知一样滑入），不会每秒弹出
 *
 * 分段专注：每秒按会话时间轴计算当前阶段，阶段切换时：
 * - 进入休息段：解除暂停全部应用（锁屏随之隐藏，可自由使用手机）
 * - 进入专注段：重新暂停全部应用（锁屏重新覆盖）
 * - 最后一段专注结束：恢复并结束会话
 * 岛倒计时在每个阶段开始时重新锚定（timerWhen=本阶段结束时间）。
 * 进程被杀后由 START_STICKY / Boot 接收器兜底（恢复逻辑按当前阶段纠正挂起状态）。
 */
class FocusService : Service() {
    private val channelID = javaClass.simpleName
    private val handler = Handler(Looper.getMainLooper())
    // 超级岛开关（实时读取设置缓存，中途切换立即生效）
    private val islandEnabled get() = SettingsStore.cache.focusIslandEnabled

    /** 上次 tick 的阶段索引：变化时执行暂停/解除切换与提醒 */
    private var lastPhaseIndex = -1

    // timerSystemCurrent 锚点：每个阶段开始时固定一次，阶段内岛参数完全一致
    private var islandTimerAnchor = 0L

    /** 当前前台服务通知 ID：阶段切换时递增换新 ID 重新发布 → 新 key → 岛展开态滑入（对齐番茄Todo）；
     *  不能用 cancel+旧 ID 重建（HyperOS 焦点通知对同 key 重建会过滤导致岛消失） */
    private var currentNotificationId = NOTIFICATION_ID

    private val tickRunnable = object : Runnable {
        override fun run() {
            val session = FocusStore.activeSession()
            if (session == null) {
                FocusManager.phase.value = null
                Thread { FocusManager.restoreAndEnd() }.start()
                return
            }
            val now = System.currentTimeMillis()
            val phase = session.phaseAt(now)
            FocusManager.phase.value = phase
            val remaining = phase.remainingAt(now)
            // 最后一段（专注）到点：后台恢复并结束
            if (phase.isFocus && remaining <= 0) {
                Thread { FocusManager.restoreAndEnd() }.start()
                return
            }
            // 阶段切换：暂停/解除应用 + 重锚定岛 + 换新 ID 重新发布岛通知（新 key → 打断 → 岛滑入）。
            // 提醒由岛滑入承担，不再发独立 heads-up 通知
            if (phase.index != lastPhaseIndex) {
                lastPhaseIndex = phase.index
                islandTimerAnchor = now
                Thread { FocusManager.applySuspensionByPhase(session) }.start()
                val notification = buildNotification(phase, session)
                currentNotificationId++
                runCatching { startForeground(currentNotificationId, notification) }
                runCatching { NotificationManagerCompat.from(this@FocusService).notify(currentNotificationId, notification) }
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 前台服务必须成功启动：startForegroundService 后若 5 秒内未调用 startForeground，
        // 系统会抛 ForegroundServiceDidNotStartInTimeException 直接闪退。
        // 因此 notify（通知权限被拒时会抛 SecurityException）与 startForeground 分开保护，
        // notify 失败绝不能连带跳过 startForeground。
        islandTimerAnchor = System.currentTimeMillis()
        val session = FocusStore.activeSession()
        val phase = session?.phaseAt(System.currentTimeMillis())
        lastPhaseIndex = phase?.index ?: -1
        FocusManager.phase.value = phase
        runCatching { createNotificationChannel() }
        val notification = runCatching { buildNotification(phase, session) }.getOrElse { fallbackNotification() }
        runCatching { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification) }
        runCatching { startForeground(NOTIFICATION_ID, notification) }
        // 进程被杀后 START_STICKY 重启：按当前阶段纠正挂起状态（幂等，防止边界状态下状态丢失）
        if (session != null) Thread { FocusManager.applySuspensionByPhase(session) }.start()
        handler.post(tickRunnable)
        return START_STICKY
    }

    /** 更新前台服务通知（阶段切换时随换新 ID 一起调用；倒计时走秒由 chronometer 与岛 timerInfo 原生渲染） */
    private fun buildNotification(phase: PhaseInfo?, session: ActiveSession?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val isRest = phase?.isFocus == false
        val frontTitle = getString(if (isRest) R.string.focus_rest_title else R.string.focus_active_title)
        val builder = NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(frontTitle)
            .setContentText(frontTitle)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            // 不设 onlyAlertOnce：现在只在会话开始/阶段切换时 notify（不每秒刷），
            // 每次更新都作为打断性事件 → 岛浮动滑入（对齐番茄Todo；番茄通知亦无 ONLY_ALERT_ONCE，
            // dumpsys 显示 flags=ONGOING_EVENT + mIsInterruptive=true）
            // 卡片倒计时走秒：系统 chronometer（when=本阶段结束时刻，倒计时到 0），无需每秒 notify
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(phase?.segmentEnd ?: session?.endMillis ?: System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setChronometerCountDown(true)
        }
        // 注意：不再给通知加「跳过休息」action——HyperOS 焦点通知（岛）不渲染通知按钮，
        // 跳过休息改由应用内按钮提供（点击岛/通知进应用 → FocusScreen 休息态点「跳过休息」）
        // HyperOS 超级岛：仅当设置开启时才注入岛参数；倒计时指向当前阶段结束时间，
        // 锚点在本阶段开始时固定；enableFloat 恒 true → 每次更新岛自动展开弹出（对齐番茄Todo）
        if (islandEnabled) {
            runCatching {
                val endMillis = phase?.segmentEnd ?: session?.endMillis ?: System.currentTimeMillis()
                val anchor = if (islandTimerAnchor > 0L) islandTimerAnchor else System.currentTimeMillis()
                builder.addExtras(MiuiIsland.buildIslandExtras(this, frontTitle, frontTitle, endMillis, anchor))
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

        const val ACTION_SKIP_REST = "com.frosthush.app.focus.SKIP_REST"
        const val REQUEST_SKIP_REST = 5001
    }
}
