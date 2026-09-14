package com.frosthush.app.focus

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.frosthush.app.R
import com.frosthush.app.data.QuickFocusStore
import com.frosthush.app.util.DebugLog

/**
 * 桌面小部件（原生 AppWidget，三个尺寸共用一个 provider）：
 * 2×2 = 3 个时长格 + 右下角「设置」；2×3 / 2×4 = 5 个时长格 + 右下角「设置」。
 *
 * 设计取舍（见工作总结与设计稿）：
 * - 只做原生 AppWidget：HyperOS 的「小米小部件」需应用先上架小米商店并通过平台审核，本项目 GitHub 分发不做。
 * - 尺寸靠三份 appwidget-provider（targetCellWidth 2/3/4，同一 label → HyperOS 侧聚合为同一功能的不同尺寸），
 *   三个 receiver 指向同一个 provider 类，靠 [AppWidgetManager.getAppWidgetInfo] 的 initialLayout 分辨小/宽版。
 * - 不显示倒计时/专注状态，故不随会话刷新；只有配置变更或系统 onUpdate 时重绘。
 * - 点击时长格 → 打开应用弹确认（与长按菜单快捷方式同一入口）；点「设置」→ 打开应用的小部件设置页。
 */
open class FocusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    /** 用户调整尺寸后重绘（宽版布局本身能拉伸，这里只是保证尺寸变化后内容正确） */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        render(context, manager, appWidgetId)
    }

    companion object {
        private val CELL_IDS = intArrayOf(
            R.id.widget_cell_1,
            R.id.widget_cell_2,
            R.id.widget_cell_3,
            R.id.widget_cell_4,
            R.id.widget_cell_5,
        )

        /**
         * 三个尺寸各自的 receiver 类：Manifest 不允许同名 receiver 重复声明，
         * 且小米侧「同一 label 的多个小部件 = 同一功能的不同尺寸」也要求各有独立组件，
         * 因此每个尺寸一个子类（逻辑全部继承自本类）。
         */
        private val PROVIDERS = listOf(
            QuickWidgetSmallProvider::class.java,
            QuickWidgetMidProvider::class.java,
        )

        /** 三个尺寸的 receiver 组件，用于刷新全部实例 */
        private fun components(context: Context): List<ComponentName> =
            PROVIDERS.map { ComponentName(context.packageName, it.name) }

        /** 配置变更后刷新所有已添加的小部件 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            var count = 0
            components(context).forEach { component ->
                manager.getAppWidgetIds(component).forEach { id ->
                    render(context, manager, id)
                    count++
                }
            }
            DebugLog.d("Widget", "refreshAll 刷新 $count 个实例")
        }

        /**
         * 大盒子策略：**格子宽度按比例**（weight=1，每列 = 可用宽 / 列数），行高固定并整体垂直居中。
         * 因此只需按**盒子高度**决定档位（宽度不参与判断——按比例分配不会裁切，天然适配各种平板）：
         *   高 ≥174dp → 大档（行高 58dp，Pad 实测 313dp 走这档；番茄ToDo 在同类盒子里行高约 51dp）
         *   高 ≥150dp → 中档（行高 46dp）
         *   更小       → 手机版自适应拉伸（小盒子本来就该铺满）
         * 174 ≈ 2×58 + 行间距 8 + 标题约 26 + 卡片内边距 24。
         */
        private const val TIER_MEDIUM_H = 150
        private const val TIER_LARGE_H = 174

        /** 绘制单个实例 */
        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull() ?: return
            val small = info.initialLayout == R.layout.widget_quick_small
            val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            // 档位只看盒子高度；宽度按比例分配，任何平板尺寸都能自适应
            val tier = when {
                minHeight >= TIER_LARGE_H -> 2
                minHeight >= TIER_MEDIUM_H -> 1
                else -> 0
            }
            val layoutId = when {
                small && tier == 2 -> R.layout.widget_quick_small_capped_large
                small && tier == 1 -> R.layout.widget_quick_small_capped
                small -> R.layout.widget_quick_small
                tier == 2 -> R.layout.widget_quick_wide_capped_large
                tier == 1 -> R.layout.widget_quick_wide_capped
                else -> R.layout.widget_quick_wide
            }
            val minutes = if (small) QuickFocusStore.widgetSmallMinutes() else QuickFocusStore.widgetWideMinutes()
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_quick_title))
            minutes.forEachIndexed { index, value ->
                val cellId = CELL_IDS.getOrNull(index) ?: return@forEachIndexed
                views.setTextViewText(cellId, context.getString(R.string.widget_quick_minutes_text, value))
                views.setOnClickPendingIntent(
                    cellId,
                    QuickFocus.pendingIntent(
                        context,
                        id = "widget_${appWidgetId}_$index",
                        requestCode = requestCode(appWidgetId, index),
                        minutes = value,
                        label = "",
                        source = QuickFocus.SOURCE_WIDGET,
                    ),
                )
            }
            views.setTextViewText(R.id.widget_cell_settings, context.getString(R.string.widget_quick_settings))
            views.setOnClickPendingIntent(
                R.id.widget_cell_settings,
                PendingIntent.getActivity(
                    context,
                    requestCode(appWidgetId, SETTINGS_INDEX),
                    QuickFocus.settingsIntent(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            runCatching { manager.updateAppWidget(appWidgetId, views) }
            // 打出实测盒子尺寸：Pad 上要按这个值校准 CAP_* 阈值与固定容器尺寸
            DebugLog.d(
                "Widget",
                "render id=$appWidgetId small=$small tier=$tier " +
                    "size=${minWidth}x$minHeight layout=${layoutId} minutes=$minutes"
            )
        }

        /** 小部件点击的 requestCode：实例 id 与格子号组合（同一实例不同格子互不覆盖） */
        private fun requestCode(appWidgetId: Int, index: Int): Int = appWidgetId * 10 + (index % 10)

        private const val SETTINGS_INDEX = 9
    }
}

/** 2×2 小部件（3 个时长格 + 设置格） */
class QuickWidgetSmallProvider : FocusWidgetProvider()

/** 2×3 小部件（5 个时长格 + 设置格） */
class QuickWidgetMidProvider : FocusWidgetProvider()


