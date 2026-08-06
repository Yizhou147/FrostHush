package com.frosthush.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机 / 应用升级后恢复专注会话：
 * 未到点则重新暂停应用并重启前台服务继续倒计时，已到点则补执行恢复。
 */
class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Thread {
            FocusManager.resumeAfterRestart(context)
        }.start()
    }
}
