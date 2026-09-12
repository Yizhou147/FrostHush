// Adapted from KernelSU (ui/navigation3/Navigator.kt) — Apache 2.0.
package com.frosthush.app.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

/**
 * 极简页面栈：持有 backStack，供 `NavDisplay` 渲染。
 *
 * 路由编码为字符串保存（[Saver]），因此无需引入 kotlinx-serialization / parcelize 插件，
 * 也能在配置变更与进程重建后恢复页面栈。
 */
class Navigator(initialKey: Route) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(initialKey)

    fun push(key: NavKey) {
        backStack.add(key)
    }

    fun pop() {
        backStack.removeLastOrNull()
    }

    fun current(): NavKey? = backStack.lastOrNull()

    companion object {
        val Saver: Saver<Navigator, Any> = listSaver(
            save = { navigator -> navigator.backStack.map(::encodeRoute) },
            restore = { saved ->
                val routes = saved.mapNotNull { decodeRoute(it) }
                val navigator = Navigator(routes.firstOrNull() ?: Route.Main)
                navigator.backStack.clear()
                navigator.backStack.addAll(routes)
                navigator
            },
        )
    }
}

@Composable
fun rememberNavigator(startRoute: Route): Navigator =
    rememberSaveable(saver = Navigator.Saver) { Navigator(startRoute) }

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}

private const val KEY_MAIN = "main"
private const val KEY_IMPORT = "import"
private const val KEY_APP_GROUPS = "appGroups"
private const val KEY_CONFIG_IMPORT = "configImport"
private const val KEY_THEME_SETTINGS = "themeSettings"
private const val KEY_SETTINGS_FOCUS = "settingsFocus"
private const val KEY_SETTINGS_PLAN = "settingsPlan"
private const val KEY_SETTINGS_DATA = "settingsData"
private const val KEY_UPDATE_SETTINGS = "updateSettings"
private const val PREFIX_PLAN_EDIT = "planEdit:"

private fun encodeRoute(route: NavKey): String = when (route) {
    Route.Main -> KEY_MAIN
    Route.Import -> KEY_IMPORT
    Route.AppGroups -> KEY_APP_GROUPS
    Route.ConfigImport -> KEY_CONFIG_IMPORT
    Route.ThemeSettings -> KEY_THEME_SETTINGS
    Route.SettingsFocus -> KEY_SETTINGS_FOCUS
    Route.SettingsPlan -> KEY_SETTINGS_PLAN
    Route.SettingsData -> KEY_SETTINGS_DATA
    Route.UpdateSettings -> KEY_UPDATE_SETTINGS
    is Route.PlanEdit -> "$PREFIX_PLAN_EDIT${route.planId}"
    else -> KEY_MAIN
}

private fun decodeRoute(value: String): Route? = when {
    value == KEY_MAIN -> Route.Main
    value == KEY_IMPORT -> Route.Import
    value == KEY_APP_GROUPS -> Route.AppGroups
    value == KEY_CONFIG_IMPORT -> Route.ConfigImport
    value == KEY_THEME_SETTINGS -> Route.ThemeSettings
    value == KEY_SETTINGS_FOCUS -> Route.SettingsFocus
    value == KEY_SETTINGS_PLAN -> Route.SettingsPlan
    value == KEY_SETTINGS_DATA -> Route.SettingsData
    value == KEY_UPDATE_SETTINGS -> Route.UpdateSettings
    value.startsWith(PREFIX_PLAN_EDIT) ->
        value.removePrefix(PREFIX_PLAN_EDIT).toLongOrNull()?.let { Route.PlanEdit(it) }
    else -> null
}
