package com.frosthush.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.frosthush.app.util.DebugLog

/**
 * 专注计划闹钟接收器：收到提醒（15 秒前）/ 开始 / 结束事件后转交 PlanScheduler。
 * 开始与结束涉及逐个暂停/恢复应用的跨进程调用，放到后台线程执行。
 */
class PlanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val planId = intent.getLongExtra(PlanScheduler.EXTRA_PLAN_ID, -1L)
        val startMillis = intent.getLongExtra(PlanScheduler.EXTRA_START_MILLIS, 0L)
        // 记录闹钟实际投递时刻（对照注册时刻可算投递延迟，排查计划时间不准）
        DebugLog.d("Alarm", "onReceive action=$action planId=$planId startMillis=$startMillis now=${System.currentTimeMillis()}")
        PlanScheduler.onAlarm(context, action, planId, startMillis)
    }
}
