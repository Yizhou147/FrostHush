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
        (0 until json.length()).map { i ->
            val obj = json.getJSONObject(i)
            HistoryRecord(obj.getLong("start"), obj.getLong("end"))
        }
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
}
