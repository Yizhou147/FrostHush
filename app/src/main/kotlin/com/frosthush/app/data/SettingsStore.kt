package com.frosthush.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val focusIslandEnabled: Boolean = false,
        val themeMode: Int = THEME_SYSTEM, // 0 跟随系统 / 1 浅色 / 2 深色
        val confirmBeforeStart: Boolean = true,
        val welcomeDone: Boolean = false,
        // 计划开始前提醒秒数（0 = 不提醒，到点直接开始专注）
        val planRemindSeconds: Int = DEFAULT_PLAN_REMIND_SECONDS,
        // 冻结失败兜底模式：0 关闭 / 1 仅分身应用 / 2 所有用户应用。
        // suspend（暂停，系统弹窗体验）失败时回退 disable（禁用）——被禁用应用冻结更彻底，
        // 但专注期间桌面图标会消失（专注结束后恢复）。
        val suspendFallbackMode: Int = FALLBACK_OFF,
        // ===== 外观 / 主题（HyperOS Miuix 改造）=====
        /** 界面风格：miuix（HyperOS，默认）/ material（原有主题） */
        val uiMode: String = UI_MODE_MIUIX,
        /** 顶栏 / 底栏模糊效果 */
        val enableBlur: Boolean = true,
        /** 悬浮底栏（默认开启） */
        val enableFloatingBottomBar: Boolean = true,
        /** 悬浮底栏液态玻璃（默认开启；仅 API 33+ 生效，低版本自动降级） */
        val enableFloatingBottomBarBlur: Boolean = true,
        /** 关于页动态流动背景（AGSL 着色器，仅 Android 15+ 实际生效） */
        val enableDynamicBackground: Boolean = true,
        /** 检查更新镜像站（UpdateChecker.UpdateMirror.id） */
        val updateMirror: String = "gh-proxy",
        /** 自定义镜像站前缀（updateMirror == "custom" 时使用，如 https://your-mirror.example/） */
        val customMirror: String = "",
        /** 启动时自动检查更新（24 小时节流） */
        val autoCheckUpdate: Boolean = false,
        /** 上次检查更新时间戳（节流用） */
        val lastUpdateCheckMillis: Long = 0L,
        /** 预测性返回手势 */
        val enablePredictiveBack: Boolean = false,
        /** 界面缩放（应用内密度缩放，1.0 = 不缩放） */
        val pageScale: Float = 1.0f,
    )

    const val DEFAULT_FOCUS_MINUTES = 30
    const val DEFAULT_REST_MINUTES = 10
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2
    const val DEFAULT_PLAN_REMIND_SECONDS = 15
    /** 计划提醒秒数合法范围：0（不提醒）~ 3600 */
    val PLAN_REMIND_RANGE = 0..3600
    /** 冻结兜底模式：关闭（suspend 失败不额外处理） */
    const val FALLBACK_OFF = 0
    /** 冻结兜底模式：仅分身应用（suspend 失败时回退禁用分身） */
    const val FALLBACK_CLONE_ONLY = 1
    /** 冻结兜底模式：所有用户应用（suspend 失败时一律回退禁用） */
    const val FALLBACK_ALL = 2

    /** 界面风格：Miuix（HyperOS 设计语言） */
    const val UI_MODE_MIUIX = "miuix"
    /** 界面风格：Material（原有主题，迁移期作为默认） */
    const val UI_MODE_MATERIAL = "material"
    /** 界面缩放合法范围 */
    val PAGE_SCALE_RANGE = 0.8f..1.2f

    private val KEY_DEFAULT_MINUTES = intPreferencesKey("default_focus_minutes")
    private val KEY_DEFAULT_REST_MINUTES = intPreferencesKey("default_rest_minutes")
    private val KEY_NOTIFY_FINISH = booleanPreferencesKey("notify_finish_enabled")
    private val KEY_FOCUS_ISLAND = booleanPreferencesKey("focus_island_enabled")
    private val KEY_THEME_MODE = intPreferencesKey("theme_mode")
    private val KEY_WELCOME_DONE = booleanPreferencesKey("welcome_done")
    private val KEY_PLAN_REMIND_SECONDS = intPreferencesKey("plan_remind_seconds")
    private val KEY_SUSPEND_FALLBACK = intPreferencesKey("suspend_fallback_mode")
    private val KEY_UI_MODE = stringPreferencesKey("ui_mode")
    private val KEY_ENABLE_BLUR = booleanPreferencesKey("enable_blur")
    private val KEY_FLOATING_BOTTOM_BAR = booleanPreferencesKey("floating_bottom_bar")
    private val KEY_FLOATING_BOTTOM_BAR_BLUR = booleanPreferencesKey("floating_bottom_bar_blur")
    private val KEY_DYNAMIC_BACKGROUND = booleanPreferencesKey("dynamic_background")
    private val KEY_UPDATE_MIRROR = stringPreferencesKey("update_mirror")
    private val KEY_CUSTOM_MIRROR = stringPreferencesKey("custom_mirror")
    private val KEY_AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
    private val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_millis")
    private val KEY_PREDICTIVE_BACK = booleanPreferencesKey("predictive_back")
    private val KEY_PAGE_SCALE = floatPreferencesKey("page_scale")

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
                    focusIslandEnabled = prefs[KEY_FOCUS_ISLAND] ?: false,
                    themeMode = prefs[KEY_THEME_MODE] ?: THEME_SYSTEM,
                    welcomeDone = prefs[KEY_WELCOME_DONE] ?: false,
                    planRemindSeconds = prefs[KEY_PLAN_REMIND_SECONDS] ?: DEFAULT_PLAN_REMIND_SECONDS,
                    suspendFallbackMode = prefs[KEY_SUSPEND_FALLBACK] ?: FALLBACK_OFF,
                    uiMode = prefs[KEY_UI_MODE] ?: UI_MODE_MATERIAL,
                    enableBlur = prefs[KEY_ENABLE_BLUR] ?: true,
                    enableFloatingBottomBar = prefs[KEY_FLOATING_BOTTOM_BAR] ?: true,
                    enableFloatingBottomBarBlur = prefs[KEY_FLOATING_BOTTOM_BAR_BLUR] ?: true,
                    enableDynamicBackground = prefs[KEY_DYNAMIC_BACKGROUND] ?: true,
                    updateMirror = prefs[KEY_UPDATE_MIRROR] ?: "gh-proxy",
                    customMirror = prefs[KEY_CUSTOM_MIRROR] ?: "",
                    autoCheckUpdate = prefs[KEY_AUTO_CHECK_UPDATE] ?: false,
                    lastUpdateCheckMillis = prefs[KEY_LAST_UPDATE_CHECK] ?: 0L,
                    enablePredictiveBack = prefs[KEY_PREDICTIVE_BACK] ?: false,
                    pageScale = prefs[KEY_PAGE_SCALE] ?: 1.0f,
                )
            }
        }
    }

    val defaultFocusMinutes: Flow<Int> = app.dataStore.data.map { it[KEY_DEFAULT_MINUTES] ?: DEFAULT_FOCUS_MINUTES }
    val defaultRestMinutes: Flow<Int> = app.dataStore.data.map { it[KEY_DEFAULT_REST_MINUTES] ?: DEFAULT_REST_MINUTES }
    val notifyFinishEnabled: Flow<Boolean> = app.dataStore.data.map { it[KEY_NOTIFY_FINISH] ?: true }
    val focusIslandEnabled: Flow<Boolean> = app.dataStore.data.map { it[KEY_FOCUS_ISLAND] ?: false }
    val themeMode: Flow<Int> = app.dataStore.data.map { it[KEY_THEME_MODE] ?: THEME_SYSTEM }
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

    val suspendFallbackMode: Flow<Int> = app.dataStore.data.map { it[KEY_SUSPEND_FALLBACK] ?: FALLBACK_OFF }

    fun setSuspendFallbackMode(mode: Int) {
        scope.launch {
            app.dataStore.edit { it[KEY_SUSPEND_FALLBACK] = mode }
            cache = cache.copy(suspendFallbackMode = mode)
        }
    }

    // ===== 外观 / 主题（HyperOS Miuix 改造）=====

    val uiMode: Flow<String> = app.dataStore.data.map { it[KEY_UI_MODE] ?: UI_MODE_MIUIX }
    val enableBlur: Flow<Boolean> = app.dataStore.data.map { it[KEY_ENABLE_BLUR] ?: true }
    val enableFloatingBottomBar: Flow<Boolean> = app.dataStore.data.map { it[KEY_FLOATING_BOTTOM_BAR] ?: true }
    val enableFloatingBottomBarBlur: Flow<Boolean> = app.dataStore.data.map { it[KEY_FLOATING_BOTTOM_BAR_BLUR] ?: true }
    val enableDynamicBackground: Flow<Boolean> = app.dataStore.data.map { it[KEY_DYNAMIC_BACKGROUND] ?: true }
    val updateMirror: Flow<String> = app.dataStore.data.map { it[KEY_UPDATE_MIRROR] ?: "gh-proxy" }
    val customMirror: Flow<String> = app.dataStore.data.map { it[KEY_CUSTOM_MIRROR] ?: "" }
    val autoCheckUpdate: Flow<Boolean> = app.dataStore.data.map { it[KEY_AUTO_CHECK_UPDATE] ?: false }
    val lastUpdateCheckMillis: Flow<Long> = app.dataStore.data.map { it[KEY_LAST_UPDATE_CHECK] ?: 0L }
    val enablePredictiveBack: Flow<Boolean> = app.dataStore.data.map { it[KEY_PREDICTIVE_BACK] ?: false }
    val pageScale: Flow<Float> = app.dataStore.data.map { it[KEY_PAGE_SCALE] ?: 1.0f }

    fun setUiMode(mode: String) {
        scope.launch {
            app.dataStore.edit { it[KEY_UI_MODE] = mode }
            cache = cache.copy(uiMode = mode)
        }
    }

    fun setEnableBlur(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_ENABLE_BLUR] = enabled }
            cache = cache.copy(enableBlur = enabled)
        }
    }

    fun setEnableFloatingBottomBar(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_FLOATING_BOTTOM_BAR] = enabled }
            cache = cache.copy(enableFloatingBottomBar = enabled)
        }
    }

    fun setEnableFloatingBottomBarBlur(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_FLOATING_BOTTOM_BAR_BLUR] = enabled }
            cache = cache.copy(enableFloatingBottomBarBlur = enabled)
        }
    }

    fun setEnableDynamicBackground(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_DYNAMIC_BACKGROUND] = enabled }
            cache = cache.copy(enableDynamicBackground = enabled)
        }
    }

    fun setUpdateMirror(id: String) {
        scope.launch {
            app.dataStore.edit { it[KEY_UPDATE_MIRROR] = id }
            cache = cache.copy(updateMirror = id)
        }
    }

    fun setCustomMirror(prefix: String) {
        scope.launch {
            app.dataStore.edit { it[KEY_CUSTOM_MIRROR] = prefix }
            cache = cache.copy(customMirror = prefix)
        }
    }

    fun setAutoCheckUpdate(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_AUTO_CHECK_UPDATE] = enabled }
            cache = cache.copy(autoCheckUpdate = enabled)
        }
    }

    fun setLastUpdateCheckMillis(millis: Long) {
        scope.launch {
            app.dataStore.edit { it[KEY_LAST_UPDATE_CHECK] = millis }
            cache = cache.copy(lastUpdateCheckMillis = millis)
        }
    }

    fun setEnablePredictiveBack(enabled: Boolean) {
        scope.launch {
            app.dataStore.edit { it[KEY_PREDICTIVE_BACK] = enabled }
            cache = cache.copy(enablePredictiveBack = enabled)
        }
    }

    fun setPageScale(scale: Float) {
        scope.launch {
            app.dataStore.edit { it[KEY_PAGE_SCALE] = scale }
            cache = cache.copy(pageScale = scale)
        }
    }
}
