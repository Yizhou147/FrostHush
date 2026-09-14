package com.frosthush.app.data

import com.frosthush.app.FrostHushApp.Companion.app
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 「快速专注」配置：长按应用图标菜单里的三条快捷方式 + 小部件格子里的时长。
 *
 * 存储沿用项目既有范式（focus/ 目录 + JSON 文件，与 presets.json 同风格）。
 * 快捷方式文案与时长各自独立：改时长**不会**覆盖用户改过的文案；但文案仍是「专注N分钟」
 * 这种由旧时长生成的默认形态时，设置页会在保存后提示一次，提醒用户文案与新时长对不上。
 *
 * 小部件时长按尺寸分两组（2×2 三个格、2×3/2×4 五个格），所有同类小部件实例共用同一组
 * （与番茄ToDo 一致的模型：改的是格子内容，不是每个实例各配一套）。
 */
object QuickFocusStore {
    /** 快捷方式条数：长按菜单最多三行 */
    const val COUNT = 3

    /** 小尺寸小部件（2×2）的时长格数：3 个时长 + 1 个「设置」格 */
    const val WIDGET_SMALL_COUNT = 3

    /** 宽版小部件（2×3 / 2×4）的时长格数：5 个时长 + 1 个「设置」格 */
    const val WIDGET_WIDE_COUNT = 5

    /**
     * 短标签字数上限（中文按 1 字算）。Android 官方只给 shortLabel ≤10 字符的**软推荐**
     * （无硬限制，桌面按行宽截断），这里按桌面菜单行宽（约 200–250dp）与 14sp 中文收紧到 8 字，
     * 避免被截断成省略号；默认文案「专注30分钟」为 6 字符，留出改写余量。
     */
    const val LABEL_MAX = 8

    /** 长标签字数上限（官方软推荐 25 字符，中文按 1 字算收紧到 16） */
    const val LONG_LABEL_MAX = 16

    data class QuickShortcut(
        val id: Int,
        val label: String,
        val minutes: Int,
        val enabled: Boolean,
    )

    /** 默认文案：不带空格（「专注30分钟」＝6 字符） */
    fun defaultLabel(minutes: Int): String = "专注${minutes}分钟"

    /** 文案是否仍是「专注N分钟」的默认形态（N 任意数字） */
    fun isDefaultLabel(label: String): Boolean = Regex("^专注\\d+分钟$").matches(label)

    private val dir by lazy { File(app.filesDir, "focus") }
    private val file by lazy { File(dir, "quickFocus.json") }

    /** 小部件默认时长：2×2 用 30/60/90，宽版用 5/10/30/60/90（用户可逐格改） */
    private val defaultWidgetSmall = listOf(30, 60, 90)
    private val defaultWidgetWide = listOf(5, 10, 30, 60, 90)

    private fun defaultShortcuts(): List<QuickShortcut> = listOf(
        QuickShortcut(1, defaultLabel(30), 30, true),
        QuickShortcut(2, defaultLabel(60), 60, true),
        QuickShortcut(3, defaultLabel(90), 90, true),
    )

    /** 配置文件对象（不存在或损坏时全默认；缺项按默认补齐，长度恒定） */
    private val json by lazy {
        runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    }

    /** 三条快捷方式（内存缓存；缺失/旧数据按默认补齐，长度恒为 COUNT、id 恒为 1..COUNT） */
    val shortcuts: MutableList<QuickShortcut> by lazy {
        val loaded = mutableListOf<QuickShortcut>()
        runCatching {
            val arr = json.optJSONArray("shortcuts") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                loaded.add(
                    QuickShortcut(
                        id = o.getInt("id"),
                        label = o.getString("label").take(LABEL_MAX),
                        minutes = o.getInt("minutes"),
                        enabled = o.optBoolean("enabled", true),
                    )
                )
            }
        }
        val byId = loaded.associateBy { it.id }
        defaultShortcuts().map { d -> byId[d.id] ?: d }.toMutableList()
    }

    /** 2×2 小部件的时长（3 格） */
    private val widgetSmall: MutableList<Int> by lazy {
        readMinutes("widgetSmall", defaultWidgetSmall, WIDGET_SMALL_COUNT).toMutableList()
    }

    /** 2×3 / 2×4 小部件的时长（5 格） */
    private val widgetWide: MutableList<Int> by lazy {
        readMinutes("widgetWide", defaultWidgetWide, WIDGET_WIDE_COUNT).toMutableList()
    }

    private fun readMinutes(key: String, defaults: List<Int>, count: Int): List<Int> {
        val loaded = runCatching {
            val arr = json.optJSONArray(key) ?: JSONArray()
            (0 until arr.length()).map { arr.getInt(it) }
        }.getOrDefault(emptyList())
        return (0 until count).map { i -> loaded.getOrNull(i) ?: defaults[i] }
    }

    fun widgetSmallMinutes(): List<Int> = widgetSmall.toList()

    fun widgetWideMinutes(): List<Int> = widgetWide.toList()

    fun updateWidgetSmallMinutes(values: List<Int>) {
        widgetSmall.clear()
        widgetSmall.addAll(values.take(WIDGET_SMALL_COUNT))
        save()
    }

    fun updateWidgetWideMinutes(values: List<Int>) {
        widgetWide.clear()
        widgetWide.addAll(values.take(WIDGET_WIDE_COUNT))
        save()
    }

    /** 已启用、需要推送到桌面的快捷方式（全禁用时为空 → 长按菜单不显示快捷方式） */
    fun enabled(): List<QuickShortcut> = shortcuts.filter { it.enabled }.take(COUNT)

    fun update(item: QuickShortcut) {
        val index = shortcuts.indexOfFirst { it.id == item.id }
        if (index < 0) return
        shortcuts[index] = item.copy(label = item.label.take(LABEL_MAX))
        save()
    }

    fun save() {
        dir.mkdirs()
        file.writeText(
            JSONObject()
                .put("shortcuts", JSONArray().apply {
                    shortcuts.forEach {
                        put(
                            JSONObject()
                                .put("id", it.id)
                                .put("label", it.label)
                                .put("minutes", it.minutes)
                                .put("enabled", it.enabled)
                        )
                    }
                })
                .put("widgetSmall", JSONArray(widgetSmall))
                .put("widgetWide", JSONArray(widgetWide))
                .toString()
        )
    }
}
