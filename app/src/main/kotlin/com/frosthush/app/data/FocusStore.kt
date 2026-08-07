package com.frosthush.app.data

import com.frosthush.app.FrostHushApp.Companion.app
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 专注数据存储（JSON 文件，纯本地）：
 * - 活动会话 {包名列表, 开始时间, 时长}：设备重启后据此恢复
 * - 会话记录 [{start, end}]：统计页数据来源
 * - 已选应用列表（黑名单）：导入/移除的持久化
 * 文件很小且需要跨进程（Boot 接收器）同步读取，故采用直接文件 IO。
 */
object FocusStore {
    private const val MAX_HISTORY = 1000

    private val dir = File(app.filesDir, "focus")
    private val sessionFile = File(dir, "session.json")
    private val historyFile = File(dir, "sessions.json")
    private val blacklistFile = File(dir, "blacklist.json")
    private val presetsFile = File(dir, "presets.json")

    /** 时长有效范围（分钟） */
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240
    const val MAX_PRESETS = 20

    /** 活动会话：{应用包名列表, 开始时间, 时长} */
    data class ActiveSession(
        val packages: List<String>,
        val startMillis: Long,
        val durationMinutes: Int,
    ) {
        val endMillis: Long get() = startMillis + durationMinutes * 60_000L
    }

    /** 一次已完成的专注会话 */
    data class HistoryRecord(val start: Long, val end: Long) {
        /** 时长（分钟），向下取整 */
        val minutes: Int get() = ((end - start) / 60_000L).toInt()
    }

    // ---------- 活动会话 ----------

    fun activeSession(): ActiveSession? = runCatching {
        if (!sessionFile.exists()) return null
        val json = JSONObject(sessionFile.readText())
        val arr = json.getJSONArray("packages")
        ActiveSession(
            packages = (0 until arr.length()).map { arr.getString(it) },
            startMillis = json.getLong("start"),
            durationMinutes = json.getInt("duration"),
        )
    }.getOrNull()

    fun saveActiveSession(session: ActiveSession) {
        dir.mkdirs()
        sessionFile.writeText(JSONObject().apply {
            put("packages", JSONArray(session.packages))
            put("start", session.startMillis)
            put("duration", session.durationMinutes)
        }.toString())
    }

    fun clearActiveSession() {
        runCatching { sessionFile.delete() }
    }

    // ---------- 会话记录 ----------

    fun history(): List<HistoryRecord> = runCatching {
        if (!historyFile.exists()) return emptyList()
        val json = JSONArray(historyFile.readText())
        // 按 start 去重：并发结束会话曾可能写入重复记录（start 相同），
        // 重复会让统计页 LazyColumn 以 start 为 key 时冲突闪退
        val seen = HashSet<Long>()
        val result = ArrayList<HistoryRecord>(json.length())
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            val start = obj.getLong("start")
            if (!seen.add(start)) continue
            result.add(HistoryRecord(start, obj.getLong("end")))
        }
        result
    }.getOrDefault(emptyList())

    fun addHistory(record: HistoryRecord) {
        if (record.start <= 0 || record.end <= record.start) return
        val list = history().toMutableList().apply { add(record) }
        while (list.size > MAX_HISTORY) list.removeAt(0)
        dir.mkdirs()
        historyFile.writeText(JSONArray().apply {
            list.forEach { put(JSONObject().put("start", it.start).put("end", it.end)) }
        }.toString())
    }

    // ---------- 已选应用（黑名单） ----------

    fun blacklist(): List<String> = runCatching {
        if (!blacklistFile.exists()) return emptyList()
        val json = JSONArray(blacklistFile.readText())
        (0 until json.length()).map { json.getString(it) }
    }.getOrDefault(emptyList())

    fun saveBlacklist(list: List<String>) {
        dir.mkdirs()
        blacklistFile.writeText(JSONArray(list).toString())
    }

    // ---------- 专注时长预设 ----------

    /** 预设：id 用于列表稳定 key */
    data class FocusPreset(val id: Long, val name: String, val minutes: Int)

    /** 预设列表 */
    val presets: MutableList<FocusPreset> by lazy {
        mutableListOf<FocusPreset>().apply {
            runCatching {
                val json = JSONArray(presetsFile.readText())
                for (i in 0 until json.length()) {
                    val obj = json.getJSONObject(i)
                    // 兼容旧数据：无 id 字段时按索引生成唯一 id
                    val id = if (obj.has("id")) obj.getLong("id") else System.currentTimeMillis() + i
                    add(FocusPreset(id, obj.getString("name"), obj.getInt("minutes")))
                }
            }
        }
    }

    /** 下一个可用的预设 id（取现有最大值 + 1，保证单调递增不冲突） */
    fun nextPresetId(): Long = (presets.maxOfOrNull { it.id } ?: 0L) + 1

    fun savePresets() {
        dir.mkdirs()
        presetsFile.writeText(JSONArray().apply {
            presets.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("minutes", it.minutes)) }
        }.toString())
    }
}
