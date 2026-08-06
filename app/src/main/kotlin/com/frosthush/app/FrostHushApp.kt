package com.frosthush.app

import android.app.Application
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager

class FrostHushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        SettingsStore.init()
        // 专注模式兜底：进程被杀/重启后恢复倒计时或补执行恢复。
        // 恢复涉及逐个暂停应用的跨进程调用，放到后台线程避免阻塞主线程。
        Thread { FocusManager.resumeAfterRestart(this) }.start()
    }

    companion object {
        lateinit var app: FrostHushApp
            private set
    }
}
