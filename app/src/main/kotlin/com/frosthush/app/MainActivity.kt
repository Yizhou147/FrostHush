package com.frosthush.app

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.focus.PlanScheduler
import com.frosthush.app.focus.QuickFocus
import com.frosthush.app.ui.AppRoot
import com.frosthush.app.util.DebugLog
import com.frosthush.app.ui.theme.FrostHushTheme
import com.frosthush.app.ui.theme.LocalEnableBlur
import com.frosthush.app.ui.theme.LocalEnableFloatingBottomBar
import com.frosthush.app.ui.theme.LocalEnableFloatingBottomBarBlur
import com.frosthush.app.ui.theme.LocalEnablePredictiveBack

/** 单 Activity：欢迎页 / 底栏导航 / 导入页均由 Compose 管理 */
class MainActivity : ComponentActivity() {

    /**
     * 已消费的入口 Intent 标记（action + 关键参数），随 savedInstanceState 保存。
     * 旧实现用 `savedInstanceState == null` 整体跳过：Activity 被系统重建（低内存回收/配置变化）后，
     * 系统带进来的**新**通知点击也会被一并丢弃——"点通知进应用不弹框"的一个独立成因。
     * 现在改成按标记去重：同一个 Intent（重建时的原始 launch intent）只消费一次，新点击照常处理。
     */
    private var handledEntryToken: String? = null

    private var createdRealtime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createdRealtime = SystemClock.elapsedRealtime()
        handledEntryToken = savedInstanceState?.getString(STATE_HANDLED_ENTRY)
        DebugLog.d(
            "Lifecycle",
            "MainActivity.onCreate savedState=${savedInstanceState != null} " +
                "action=${intent?.action} pid=${android.os.Process.myPid()}"
        )
        // 系统栏完全透明且不加对比 scrim（对齐雹的 navigationBarColor transparent +
        // enforceNavigationBarContrast false），让底栏 / 侧边栏背景真正沉浸到手势条区域
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // 显式强制：部分 ROM（MIUI 小窗）下仅靠 enableEdgeToEdge 样式仍会给导航栏画
        // 白色背景（Android 15 默认强制导航栏对比），导致小窗底部操作杆区域出现一条
        // 不透明的白色带遮挡应用内容。直接设置透明并关闭强制对比（与雹的 Theme.Base 一致）。
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // 计划提醒通知点击 / 快速专注入口（快捷方式 / 小部件）：
        // 冷启动走这里，已存在的 Activity 走 onNewIntent；重建场景由 handledEntryToken 去重
        handleEntryIntent(intent)
        setContent {
            // 系统栏图标深浅随主题模式：强制浅色→深色图标，强制深色→浅色图标
            val themeMode by SettingsStore.themeMode.collectAsState(initial = SettingsStore.cache.themeMode)
            val dark = when (themeMode) {
                SettingsStore.THEME_LIGHT -> false
                SettingsStore.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            // 外观设置：界面缩放（应用内密度缩放）+ 各外观开关，统一下发给 UI 子树
            val pageScale by SettingsStore.pageScale.collectAsState(initial = SettingsStore.cache.pageScale)
            val enableBlur by SettingsStore.enableBlur.collectAsState(initial = SettingsStore.cache.enableBlur)
            val floatingBar by SettingsStore.enableFloatingBottomBar
                .collectAsState(initial = SettingsStore.cache.enableFloatingBottomBar)
            val floatingBarBlur by SettingsStore.enableFloatingBottomBarBlur
                .collectAsState(initial = SettingsStore.cache.enableFloatingBottomBarBlur)
            val predictiveBack by SettingsStore.enablePredictiveBack
                .collectAsState(initial = SettingsStore.cache.enablePredictiveBack)
            val systemDensity = LocalDensity.current
            val density = remember(systemDensity, pageScale) {
                Density(systemDensity.density * pageScale, systemDensity.fontScale)
            }
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalEnableBlur provides enableBlur,
                LocalEnableFloatingBottomBar provides floatingBar,
                LocalEnableFloatingBottomBarBlur provides floatingBarBlur,
                LocalEnablePredictiveBack provides predictiveBack,
            ) {
                FrostHushTheme {
                    AppRoot()
                }
            }
        }
        // 首帧打点：用来区分「窗口已出现但主线程还在忙」与「首帧本身就慢」
        Choreographer.getInstance().postFrameCallback {
            DebugLog.d("Lifecycle", "首帧 距 onCreate ${SystemClock.elapsedRealtime() - createdRealtime}ms")
        }
    }

    /** 单 Activity（launchMode=singleTask）已存在时，通知点击的 Intent 走这里：始终是新的点击，直接处理 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DebugLog.d("Lifecycle", "MainActivity.onNewIntent action=${intent.action}")
        handleEntryIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        handledEntryToken?.let { outState.putString(STATE_HANDLED_ENTRY, it) }
    }

    /**
     * 入口 Intent 去重分发：重建时系统带回来的是原始 launch intent，用标记跳过；
     * 若是重建期间新送达的点击（token 不同），照常处理——这正是旧实现会丢弃的场景。
     */
    private fun handleEntryIntent(intent: Intent?) {
        val token = entryTokenOf(intent) ?: return
        if (token == handledEntryToken) {
            DebugLog.d("Lifecycle", "入口 Intent 已消费过，跳过 token=$token")
            return
        }
        handledEntryToken = token
        DebugLog.d("Lifecycle", "处理入口 Intent token=$token")
        handlePlanReminderIntent(intent)
        handleQuickFocusIntent(intent)
    }

    /** 入口 Intent 的唯一标识：只对「一次性入口」返回 token，普通启动返回 null */
    private fun entryTokenOf(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            PlanScheduler.ACTION_REMIND_CLICK ->
                "${intent.action}#${intent.getLongExtra(PlanScheduler.EXTRA_PLAN_ID, -1L)}"
            QuickFocus.ACTION_QUICK_FOCUS ->
                "${intent.action}#${intent.dataString}#${intent.getIntExtra(QuickFocus.EXTRA_MINUTES, 0)}"
            QuickFocus.ACTION_WIDGET_SETTINGS -> intent.action
            else -> null
        }
    }

    /** 计划提醒通知点击：转发给 PlanScheduler，AppRoot 收集后弹「距开始倒计时」对话框 */
    private fun handlePlanReminderIntent(intent: Intent?) {
        if (intent?.action == PlanScheduler.ACTION_REMIND_CLICK) {
            val planId = intent.getLongExtra(PlanScheduler.EXTRA_PLAN_ID, -1L)
            if (planId > 0) PlanScheduler.onReminderClicked(planId)
        }
    }

    /**
     * 快速专注入口（长按图标菜单的快捷方式 / 桌面小部件格子）：
     * 只把 Intent 转成待确认请求，由 AppRoot 弹确认框（与普通专注同一套冲突预判 + 二次确认）。
     */
    private fun handleQuickFocusIntent(intent: Intent?) {
        QuickFocus.handleIntent(intent)
    }

    private companion object {
        /** savedInstanceState 里保存「已消费的入口 Intent 标记」的 key */
        const val STATE_HANDLED_ENTRY = "frosthush.handled_entry"
    }
}
