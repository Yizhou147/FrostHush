package com.frosthush.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 待启动计划决策接收器：用户在决策通知上点「继续」（启动该计划专注）或「停止」（放弃）。
 */
class PlanDecisionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getLongExtra(PlanScheduler.EXTRA_PLAN_ID, -1L)
        when (intent.action) {
            PlanScheduler.ACTION_RESUME -> Thread { PlanScheduler.onResumePending(context, planId) }.start()
            PlanScheduler.ACTION_CANCEL -> PlanScheduler.onCancelPending(context, planId)
        }
    }
}
