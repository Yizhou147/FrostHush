package com.frosthush.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.frosthush.app.FrostHushApp.Companion.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 设置存储（DataStore）。
 * 提供 Flow 供 UI 观察，同时维护内存缓存供后台（服务/Boot 接收器）同步读取。
 */
object SettingsStore {
    private val Context.dataStore by preferencesDataStore(name = "settings")

    data class Settings(
        val defaultFocusMinutes: Int = DEFAULT_FOCUS_MINUTES,
        val notifyFinishEnabled: Boolean = true,
        val focusIslandEnabled: Boolean = true,
        val themeMode: Int = THEME_SYSTEM, // 0 跟随系统 / 1 浅色 / 2 深色
        val confirmBeforeStart: Boolean = true,
        val welcomeDone: Boolean = false,
    )

    const val DEFAULT_FOCUS_MINUTES = 30
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private val KEY_DEFAULT_MINUTES = intPreferencesKey("default_focus_minutes")
    private val KEY_NOTIFY_FINISH = booleanPreferencesKey("notify_finish_enabled")
    private val KEY_FOCUS_ISLAND = booleanPreferencesKey("focus_island_enabled")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    private val KEY_CONFIRM_BEFORE_START = booleanPreferencesKey("confirm_before_start")
    private val KEY_WELCOME_DONE = booleanPreferencesKey("welcome_done")

    /** 内存缓存：供不便于挂起的后台代码同步读取 */
    var cache: Settings = Settings()
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 应用启动时初始化：收集 DataStore 到内存缓存 */
    fun init() {
        scope.launch {
            app.dataStore.data.collect { prefs ->
                cache = Settings(
                    defaultFocusMinutes = prefs[KEY_DEFAULT_MINUTES] ?: DEFAULT_FOCUS_MINUTES,
                    notifyFinishEnabled = prefs[KEY_NOTIFY_FINISH] ?: true,
                    focusIslandEnabled = prefs[KEY_FOCUS_ISLAND] ?: true,
                    themeMode = prefs[KEY_THEME_MODE] ?: THEME_SYSTEM,
                    confirmBeforeStart = prefs[KEY_CONFIRM_BEFORE_START] ?: true,
                    welcomeDone = prefs[KEY_WELCOME_DONE] ?: false,
                )
            }
        }
    }

    val defaultFocusMinutes: Flow<Int> = app.dataStore.data.map { it[KEY_DEFAULT_MINUTES] ?: DEFAULT_FOCUS_MINUTES }
    val notifyFinishEnabled: Flow<Boolean> = app.dataStore.data.map { it[KEY_NOTIFY_FINISH] ?: true }
    val focusIslandEnabled: Flow<Boolean> = app.dataStore.data.map { it[KEY_FOCUS_ISLAND] ?: true }
    val themeMode: Flow<Int> = app.dataStore.data.map { it[KEY_THEME_MODE] ?: THEME_SYSTEM }
    val confirmBeforeStart: Flow<Boolean> = app.dataStore.data.map { it[KEY_CONFIRM_BEFORE_START] ?: true }
    val welcomeDone: Flow<Boolean> = app.dataStore.data.map { it[KEY_WELCOME_DONE] ?: false }

    fun setDefaultFocusMinutes(minutes: Int) {
        scope.launch {
            app.dataStore.edit { it[KEY_DEFAULT_MINUTES] = minutes }
            cache = cache.copy(defaultFocusMinutes = minutes)
        }
    }

    fun setNotifyFinishEnabled(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_NOTIFY_FINISH] = enabled }
            cache = cache.copy(notifyFinishEnabled = enabled)
        }
    }

    fun setFocusIslandEnabled(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_FOCUS_ISLAND] = enabled }
            cache = cache.copy(focusIslandEnabled = enabled)
        }
    }

    fun setThemeMode(mode: Int) {
        scope.launch {
            app.dataStore.edit { it[KEY_THEME_MODE] = mode }
            cache = cache.copy(themeMode = mode)
        }
    }

    fun setConfirmBeforeStart(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_CONFIRM_BEFORE_START] = enabled }
            cache = cache.copy(confirmBeforeStart = enabled)
        }
    }

    fun setWelcomeDone(done: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_WELCOME_DONE] = done }
            cache = cache.copy(welcomeDone = done)
        }
    }
}
