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
            QuickWidgetPadSmallProvider::class.java,
            QuickWidgetPadWideProvider::class.java,
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
         * 大盒子阈值（dp，实测盒子尺寸超过即换「格子固定尺寸」布局）。
         * 阈值＝固定容器尺寸 + 卡片内边距 + 余量：小版容器 180×130、宽版 270×130，卡片内边距 12dp*2，
         * 标题约 26dp，故高度阈值 190dp；宽度留 6dp 余量（210 / 300）。
         * 依据：Pad 的「添加小部件」页把两个入口都分到同一个 ≈2×2 格子的大盒子（平板实测），
         * 声明 targetCell 无法改变（HyperOS Pad 未按 sw600dp 解析 appwidget-provider），
         * 所以改为按实测尺寸自适应渲染——不管拿到多大的盒子，格子都不被等比放大。
         */
        private const val CAP_SMALL_W = 185
        private const val CAP_SMALL_H = 170
        private const val CAP_WIDE_W = 245
        private const val CAP_WIDE_H = 170
        // 大档（Pad：实测盒子 297×313dp）：容器更大，药丸接近番茄ToDo 的 82×51dp，
        // 把 Pad 大盒子里的空白填掉。要求盒子能放下容器（否则退回中档，避免裁切）。
        private const val LARGE_SMALL_W = 240
        private const val LARGE_SMALL_H = 190
        private const val LARGE_WIDE_W = 330
        private const val LARGE_WIDE_H = 190

        /** 绘制单个实例 */
        fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull() ?: return
            val small = info.initialLayout == R.layout.widget_quick_small
            val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            val capped = if (small) {
                minWidth >= CAP_SMALL_W || minHeight >= CAP_SMALL_H
            } else {
                minWidth >= CAP_WIDE_W || minHeight >= CAP_WIDE_H
            }
            // 大档要求宽高同时够（容器 194×126 / 288×126 + 卡片内边距 12*2 + 标题约 26dp）
            val large = if (small) {
                minWidth >= LARGE_SMALL_W && minHeight >= LARGE_SMALL_H
            } else {
                minWidth >= LARGE_WIDE_W && minHeight >= LARGE_WIDE_H
            }
            val layoutId = when {
                small && large -> R.layout.widget_quick_small_capped_large
                small && capped -> R.layout.widget_quick_small_capped
                small -> R.layout.widget_quick_small
                large -> R.layout.widget_quick_wide_capped_large
                capped -> R.layout.widget_quick_wide_capped
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
                "render id=$appWidgetId small=$small capped=$capped large=$large " +
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


/**
 * 平板专用入口 · 1×1：内容与手机 2×2 相同（3 个时长 + 设置）。
 * 与 A/B 两个入口的区别只在声明尺寸（targetCellWidth/Height = 1/1），供 Pad 的 1×1 格子使用；
 * 手机上也会出现在列表里（≤1 格会偏小），故 label 里标明「平板 1×1」。
 */
class QuickWidgetPadSmallProvider : FocusWidgetProvider()

/** 平板专用入口 · 1×2（高 1 格、宽 2 格）：内容与手机 2×4 相同（5 个时长 + 设置） */
class QuickWidgetPadWideProvider : FocusWidgetProvider()
