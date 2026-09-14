package com.frosthush.app.focus

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.frosthush.app.MainActivity
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.QuickFocusStore
import com.frosthush.app.util.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 「快速专注」入口（长按应用图标菜单的快捷方式 / 桌面小部件格子）。
 *
 * 点击只做一件事：带上时长参数打开 [MainActivity]，由 AppRoot 弹确认框（后续与普通专注
 * 完全同一套流程：计划冲突预判 → 二次确认 → FocusManager.startFocus）。
 *
 * 为什么不在点击时直接 startForegroundService 起专注：Android 12+ 后台启动前台服务受限，
 * 且 startFocus 需要 Shizuku 就绪判定与应用集，失败必须有界面落点——经 MainActivity
 * 是唯一稳妥路径，也正合产品要求「点开应用弹一次确认」。
 */
object QuickFocus {
    const val ACTION_QUICK_FOCUS = "com.frosthush.app.action.QUICK_FOCUS"
    const val EXTRA_MINUTES = "quick_focus_minutes"
    const val EXTRA_LABEL = "quick_focus_label"
    const val EXTRA_SOURCE = "quick_focus_source"

    const val SOURCE_SHORTCUT = "shortcut"
    const val SOURCE_WIDGET = "widget"

    /** 小部件「设置」格：打开应用的小部件设置（专注设置页里的快速专注区块） */
    const val ACTION_WIDGET_SETTINGS = "com.frosthush.app.action.WIDGET_SETTINGS"

    /** 一次待确认的快速专注请求（AppRoot 消费后置空，避免重复弹框） */
    data class Request(val minutes: Int, val label: String, val source: String)

    val request = MutableStateFlow<Request?>(null)

    /** 待打开的小部件设置页请求（AppRoot 收集后 push 路由） */
    val openWidgetSettings = MutableStateFlow(false)

    /**
     * 构造点击 Intent：data 里带唯一标识（快捷方式 id / 小部件实例与格子号），
     * 让桌面按条目分别缓存、互不顶替；[MainActivity] 是 singleTask，热启动走 onNewIntent。
     */
    fun intent(context: Context, id: String, minutes: Int, label: String, source: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_QUICK_FOCUS
            data = Uri.parse("frosthush://quick/$source/$id")
            putExtra(EXTRA_MINUTES, minutes)
            putExtra(EXTRA_LABEL, label)
            putExtra(EXTRA_SOURCE, source)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 小部件「设置」格的点击 Intent */
    fun settingsIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_WIDGET_SETTINGS
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 由请求构造点击用的 PendingIntent（小部件格子用；requestCode 由调用方保证唯一） */
    fun pendingIntent(
        context: Context,
        id: String,
        requestCode: Int,
        minutes: Int,
        label: String,
        source: String,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        intent(context, id, minutes, label, source),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** MainActivity 收到快速专注 / 小部件设置 Intent；返回是否已消费该 Intent */
    fun handleIntent(intent: Intent?): Boolean {
        if (intent?.action == ACTION_WIDGET_SETTINGS) {
            DebugLog.d("Quick", "handleIntent 打开小部件设置页")
            openWidgetSettings.value = true
            return true
        }
        if (intent?.action != ACTION_QUICK_FOCUS) return false
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_SHORTCUT
        if (minutes <= 0) return true
        DebugLog.d("Quick", "handleIntent minutes=$minutes source=$source label=$label")
        request.value = Request(minutes, label, source)
        return true
    }

    /**
     * 重推桌面动态快捷方式（应用启动后 / 快捷方式设置变更后调用）。
     * 动态快捷方式需要运行时可改文案（静态 shortcuts.xml 的 label 只能来自资源），
     * 不需要任何权限；本函数用整表替换，禁用的条目自然从长按菜单消失。
     */
    fun syncShortcuts(context: Context) {
        val items = QuickFocusStore.enabled()
        val shortcuts = items.map { item ->
            ShortcutInfoCompat.Builder(context, "quick_${item.id}")
                .setShortLabel(item.label.take(QuickFocusStore.LABEL_MAX))
                .setLongLabel(item.label.take(QuickFocusStore.LONG_LABEL_MAX))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_focus))
                .setIntent(
                    intent(context, "shortcut_${item.id}", item.minutes, item.label, SOURCE_SHORTCUT)
                )
                .build()
        }
        runCatching {
            if (shortcuts.isEmpty()) ShortcutManagerCompat.removeAllDynamicShortcuts(context)
            else ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }.onFailure { DebugLog.e("Quick", "推送动态快捷方式失败（长按菜单可能不显示）", it) }
        DebugLog.d(
            "Quick",
            "syncShortcuts 已推送 ${shortcuts.size} 条：" + items.joinToString { "${it.label}=${it.minutes}min" }
        )
    }

    // ---------- 确认流程（两版 UI 共用，保证业务逻辑逐字一致） ----------

    /** 冲突预判：本次快速专注是否会盖住某个启用计划今天的开始时刻（与普通专注同一判据） */
    fun conflictsFor(minutes: Int): List<FocusStore.FocusPlan> {
        val now = System.currentTimeMillis()
        return PlanScheduler.findConflictingPlans(now, now + minutes * 60_000L)
    }

    /** 用户在冲突框点「确定」：冲突计划标记为当日失效一次（与普通专注同一套处理） */
    fun skipConflicts(plans: List<FocusStore.FocusPlan>) {
        PlanScheduler.markPlansSkippedToday(plans)
    }

    /** 真正开始：走普通专注同一入口（1..240 校验 / Shizuku 判定 / 当前选中应用集） */
    suspend fun start(minutes: Int): String? =
        FocusManager.startFocus(listOf(FocusStore.Segment(FocusStore.SEGMENT_FOCUS, minutes)))
}
