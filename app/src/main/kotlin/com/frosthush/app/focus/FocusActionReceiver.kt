package com.frosthush.app.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 专注阶段操作接收器：
 * - ACTION_SKIP_REST：跳过当前休息段（阶段提醒通知的「跳过休息」操作触发）
 */
class FocusActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FocusService.ACTION_SKIP_REST -> Thread { FocusManager.skipRest() }.start()
        }
    }
}
