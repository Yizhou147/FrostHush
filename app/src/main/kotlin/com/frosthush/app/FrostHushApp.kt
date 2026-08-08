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
        Thread {
            FocusManager.resumeAfterRestart(this)
            // 重建专注计划闹钟（进程被杀后重新拉起时兜底；开机由 FocusBootReceiver 处理）
            PlanScheduler.scheduleAll(this)
            // 后台预加载应用名称全量缓存（含分身），进入页面即可命中缓存，避免闪现裸包名
            preloadAppNames()
        }.start()
    }

    private fun preloadAppNames() {
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
