package com.frosthush.app

import android.app.Application
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.frosthush.app.data.AppRepository
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler
import com.frosthush.app.util.DebugLog

class FrostHushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        // 进程启动/被系统回收后重启打点：配合每条日志的 pid 判断闹钟投递是否因进程
        // 被杀/冻结而延迟（10:40:31 两闹钟同时补投现象的排查依据）
        DebugLog.d("Lifecycle", "Application.onCreate 进程启动 now=${System.currentTimeMillis()}")
        SettingsStore.init()
        // 清理历史残留的「专注阶段提醒」渠道（focus_phase）：
        // 工作总结第 22 项（2026-08-13）已删除该渠道对应代码与字符串，
        // 但 Android 不会因应用升级自动删除已注册的渠道，系统设置里仍残留显示。
        // deleteNotificationChannel 只能开发者主动调；若该 ID 后续无通知发布，
        // 系统设置会隐藏该项。无害，幂等。
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManagerCompat.from(this)
                    .deleteNotificationChannel("focus_phase")
            }
        }
        // 专注模式兜底：进程被杀/重启后恢复倒计时或补执行恢复。
        // 恢复涉及逐个暂停应用的跨进程调用，放到后台线程避免阻塞主线程。
        // 注意：不要在 onCreate 后台预加载应用列表——MIUI 的「允许获取应用列表」
        // 确认框只在应用前台首次查询包列表时弹出，后台预加载会抢先触发查询并被
        // MIUI 静默拒绝，导致欢迎页/应用内再也无法弹出授权框（雹等应用均因此可弹）。
        Thread {
            FocusManager.resumeAfterRestart(this)
            // 重建专注计划闹钟（进程被杀后重新拉起时兜底；开机由 FocusBootReceiver 处理）
            PlanScheduler.scheduleAll(this)
        }.start()
    }

    /** 后台预加载应用名称全量缓存（含分身）；由 AppRoot 在进入主界面（前台）后调用 */
    internal fun preloadAppNames() {
        runCatching {
            val full = AppRepository(this).queryApps().associate { it.entry to it.displayName }
            if (full.isNotEmpty()) AppRepository.updateAppNameCache(full)
        }
    }

    companion object {
        lateinit var app: FrostHushApp
            private set
    }
}
