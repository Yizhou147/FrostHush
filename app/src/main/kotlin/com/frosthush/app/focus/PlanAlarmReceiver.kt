package com.frosthush.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 专注计划闹钟接收器：收到提醒（15 秒前）/ 开始 / 结束事件后转交 PlanScheduler。
 * 开始与结束涉及逐个暂停/恢复应用的跨进程调用，放到后台线程执行。
 */
class PlanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val planId = intent.getLongExtra(PlanScheduler.EXTRA_PLAN_ID, -1L)
        PlanScheduler.onAlarm(context, action, planId)
    }
}
