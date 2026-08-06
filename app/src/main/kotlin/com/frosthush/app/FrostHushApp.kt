package com.frosthush.app

import android.app.Application
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.FocusManager

class FrostHushApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        SettingsStore.init()
        // 专注模式兜底：进程被杀/重启后恢复倒计时或补执行恢复
        FocusManager.resumeAfterRestart(this)
    }

    companion object {
        lateinit var app: FrostHushApp
            private set
    }
}
