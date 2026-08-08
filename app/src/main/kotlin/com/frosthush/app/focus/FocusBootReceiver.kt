package com.frosthush.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机 / 应用升级后恢复专注会话 + 重建专注计划闹钟：
 * 1. 未到点则重新暂停应用并重启前台服务继续倒计时，已到点则补执行恢复；
 * 2. 重建所有启用计划的闹钟（AlarmManager 闹钟在重启后会失效），并恢复待启动计划的决策窗口。
 */
class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Thread {
            FocusManager.resumeAfterRestart(context)
            PlanScheduler.scheduleAll(context)
        }.start()
    }
}
