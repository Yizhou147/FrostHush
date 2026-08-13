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
        // 新增休息段默认时长（手动专注/计划分段添加休息时使用）
        val defaultRestMinutes: Int = DEFAULT_REST_MINUTES,
        val notifyFinishEnabled: Boolean = true,
        val focusIslandEnabled: Boolean = true,
        val themeMode: Int = THEME_SYSTEM, // 0 跟随系统 / 1 浅色 / 2 深色
        val confirmBeforeStart: Boolean = true,
        val welcomeDone: Boolean = false,
        // 计划开始前提醒秒数（0 = 不提醒，到点直接开始专注）
        val planRemindSeconds: Int = DEFAULT_PLAN_REMIND_SECONDS,
    )

    const val DEFAULT_FOCUS_MINUTES = 30
    const val DEFAULT_REST_MINUTES = 10
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2
    const val DEFAULT_PLAN_REMIND_SECONDS = 15
    /** 计划提醒秒数合法范围：0（不提醒）~ 3600 */
    val PLAN_REMIND_RANGE = 0..3600

    private val KEY_DEFAULT_MINUTES = intPreferencesKey("default_focus_minutes")
    private val KEY_DEFAULT_REST_MINUTES = intPreferencesKey("default_rest_minutes")
    private val KEY_NOTIFY_FINISH = booleanPreferencesKey("notify_finish_enabled")
    private val KEY_FOCUS_ISLAND = booleanPreferencesKey("focus_island_enabled")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    private val KEY_CONFIRM_BEFORE_START = booleanPreferencesKey("confirm_before_start")
    private val KEY_WELCOME_DONE = booleanPreferencesKey("welcome_done")
    private val KEY_PLAN_REMIND_SECONDS = intPreferencesKey("plan_remind_seconds")

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
                    defaultRestMinutes = prefs[KEY_DEFAULT_REST_MINUTES] ?: DEFAULT_REST_MINUTES,
                    notifyFinishEnabled = prefs[KEY_NOTIFY_FINISH] ?: true,
                    focusIslandEnabled = prefs[KEY_FOCUS_ISLAND] ?: true,
                    themeMode = prefs[KEY_THEME_MODE] ?: THEME_SYSTEM,
                    confirmBeforeStart = prefs[KEY_CONFIRM_BEFORE_START] ?: true,
                    welcomeDone = prefs[KEY_WELCOME_DONE] ?: false,
                    planRemindSeconds = prefs[KEY_PLAN_REMIND_SECONDS] ?: DEFAULT_PLAN_REMIND_SECONDS,
                )
            }
        }
    }

    val defaultFocusMinutes: Flow<Int> = app.dataStore.data.map { it[KEY_DEFAULT_MINUTES] ?: DEFAULT_FOCUS_MINUTES }
    val defaultRestMinutes: Flow<Int> = app.dataStore.data.map { it[KEY_DEFAULT_REST_MINUTES] ?: DEFAULT_REST_MINUTES }
    val notifyFinishEnabled: Flow<Boolean> = app.dataStore.data.map { it[KEY_NOTIFY_FINISH] ?: true }
    val focusIslandEnabled: Flow<Boolean> = app.dataStore.data.map { it[KEY_FOCUS_ISLAND] ?: true }
    val themeMode: Flow<Int> = app.dataStore.data.map { it[KEY_THEME_MODE] ?: THEME_SYSTEM }
    val confirmBeforeStart: Flow<Boolean> = app.dataStore.data.map { it[KEY_CONFIRM_BEFORE_START] ?: true }
    val welcomeDone: Flow<Boolean> = app.dataStore.data.map { it[KEY_WELCOME_DONE] ?: false }
    val planRemindSeconds: Flow<Int> = app.dataStore.data.map { it[KEY_PLAN_REMIND_SECONDS] ?: DEFAULT_PLAN_REMIND_SECONDS }

    fun setDefaultFocusMinutes(minutes: Int) {
        scope.launch {
            app.dataStore.edit { it[KEY_DEFAULT_MINUTES] = minutes }
            cache = cache.copy(defaultFocusMinutes = minutes)
        }
    }

    fun setDefaultRestMinutes(minutes: Int) {
        scope.launch {
            app.dataStore.edit { it[KEY_DEFAULT_REST_MINUTES] = minutes }
            cache = cache.copy(defaultRestMinutes = minutes)
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

    fun setPlanRemindSeconds(seconds: Int) {
        scope.launch {
            app.dataStore.edit { it[KEY_PLAN_REMIND_SECONDS] = seconds }
            cache = cache.copy(planRemindSeconds = seconds)
        }
    }
}
