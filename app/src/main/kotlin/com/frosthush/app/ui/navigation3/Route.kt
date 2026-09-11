package com.frosthush.app.ui.navigation3

import androidx.navigation3.runtime.NavKey

/**
 * 页面栈路由（navigation3）：每个目标是独立的 [NavKey]，由 `NavDisplay` 渲染。
 *
 * 说明：需要携带数据的页面（如计划编辑）只把**可序列化的 id** 放进路由，
 * 非序列化的一次性载荷（如配置导入预览数据）由 `MainNavHost` 持有。
 */
sealed interface Route : NavKey {
    /** 主界面（底栏 + 5 个 tab 的 Pager） */
    data object Main : Route

    /** 导入应用页 */
    data object Import : Route

    /** 应用集管理页 */
    data object AppGroups : Route

    /** 计划编辑页（[planId] 为负表示新建） */
    data class PlanEdit(val planId: Long) : Route

    /** 配置导入预览页 */
    data object ConfigImport : Route

    /** 主题设置二级页 */
    data object ThemeSettings : Route

    /** 专注设置二级页 */
    data object SettingsFocus : Route

    /** 计划与可靠性二级页 */
    data object SettingsPlan : Route

    /** 数据二级页 */
    data object SettingsData : Route
}
