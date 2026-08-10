package com.frosthush.app

import android.app.Application
import com.frosthush.app.data.AppRepository
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler

class FrostHushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        SettingsStore.init()
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
