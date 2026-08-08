package com.frosthush.app.data

import android.os.Process
import com.frosthush.app.FrostHushApp.Companion.app
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

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
    private val appGroupsFile = File(dir, "appGroups.json")
    private val selectedGroupFile = File(dir, "selectedGroup.json")
    private val plansFile = File(dir, "focusPlans.json")
    private val pendingPlanFile = File(dir, "pendingPlan.json")
    private val planExecutedFile = File(dir, "planExecuted.json")

    /** 时长有效范围（分钟） */
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 240
    const val MAX_PRESETS = 20

    /**
     * 活动会话：{应用包名列表, 开始时间, 时长}。
     * planId 非空表示该会话由专注计划启动（不受 240 分钟限制，可跨天）。
     */
    data class ActiveSession(
        val packages: List<String>,
        val startMillis: Long,
        val durationMinutes: Int,
        val planId: Long? = null,
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
            planId = if (json.has("planId")) json.getLong("planId") else null,
        )
    }.getOrNull()

    fun saveActiveSession(session: ActiveSession) {
        dir.mkdirs()
        sessionFile.writeText(JSONObject().apply {
            put("packages", JSONArray(session.packages))
            put("start", session.startMillis)
            put("duration", session.durationMinutes)
            session.planId?.let { put("planId", it) }
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

    // ---------- 已选应用（黑名单语义 = 当前选中应用集/默认集） ----------

    /**
     * 首次运行时若尚无 appGroups.json，用旧 blacklist.json 内容自动创建默认应用集并迁移。
     * 迁移只执行一次（以 appGroups.json 存在为界）：之后删除默认集不会自动重建。
     */
    private fun ensureMigrated() {
        if (appGroupsFile.exists()) return
        dir.mkdirs()
        val legacy = runCatching {
            if (!blacklistFile.exists()) emptyList()
            else {
                val json = JSONArray(blacklistFile.readText())
                (0 until json.length()).map { json.getString(it) }
            }
        }.getOrDefault(emptyList())
        saveAppGroups(mutableListOf(AppGroup(1L, DEFAULT_GROUP_NAME, legacy, true)))
    }

    private fun readAppGroups(): List<AppGroup> = runCatching {
        if (!appGroupsFile.exists()) return emptyList()
        val json = JSONArray(appGroupsFile.readText())
        (0 until json.length()).mapNotNull { i ->
            val obj = json.getJSONObject(i)
            AppGroup(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                entries = runCatching {
                    val arr = obj.getJSONArray("entries")
                    (0 until arr.length()).map { arr.getString(it) }
                }.getOrDefault(emptyList()),
                isDefault = obj.optBoolean("isDefault", false),
            )
        }
    }.getOrDefault(emptyList())

    /** 当前选中应用集的条目（未选择时回退默认集；默认集被删则返回空列表） */
    fun blacklist(): List<String> {
        ensureMigrated()
        return selectedGroup()?.entries ?: emptyList()
    }

    /** 写入当前选中应用集（未选择时写入默认集）；旧 blacklist.json 不再维护 */
    fun saveBlacklist(list: List<String>) {
        ensureMigrated()
        val groups = appGroups().toMutableList()
        val selectedId = selectedGroupId()
        val idx = groups.indexOfFirst { it.id == selectedId }
        if (idx >= 0) {
            groups[idx] = groups[idx].copy(entries = list)
        } else {
            val dIdx = groups.indexOfFirst { it.isDefault }
            if (dIdx >= 0) groups[dIdx] = groups[dIdx].copy(entries = list)
        }
        saveAppGroups(groups)
    }

    // ---------- 应用集 ----------

    /** 应用集：一组暂停应用（条目支持分身 包名@userId）；isDefault=true 即默认应用集 */
    data class AppGroup(
        val id: Long,
        val name: String,
        val entries: List<String>,
        val isDefault: Boolean,
    )

    const val DEFAULT_GROUP_NAME = "默认"

    /** 应用集列表（保持存储顺序，不自动置顶默认集；顺序可在管理页手动拖拽调整） */
    fun appGroups(): List<AppGroup> {
        ensureMigrated()
        return readAppGroups()
    }

    fun saveAppGroups(groups: List<AppGroup>) {
        dir.mkdirs()
        appGroupsFile.writeText(JSONArray().apply {
            groups.forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("entries", JSONArray(g.entries))
                    put("isDefault", g.isDefault)
                })
            }
        }.toString())
    }

    /** 下一个可用的应用集 id */
    fun nextGroupId(): Long = (appGroups().maxOfOrNull { it.id } ?: 0L) + 1

    fun addAppGroup(name: String, entries: List<String>): AppGroup {
        ensureMigrated()
        val group = AppGroup(nextGroupId(), name.trim(), entries.distinct(), isDefault = false)
        saveAppGroups(appGroups() + group)
        return group
    }

    fun updateAppGroup(group: AppGroup) {
        val groups = appGroups().toMutableList()
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) {
            groups[idx] = group.copy(entries = group.entries.distinct())
            saveAppGroups(groups)
        }
    }

    fun deleteAppGroup(id: Long) {
        ensureMigrated()
        val groups = appGroups().filter { it.id != id }
        saveAppGroups(groups)
        // 删除的是当前选中集时清除选中，回退默认集
        if (selectedGroupId() == id) setSelectedGroupId(null)
        // 引用该应用集的计划回退到默认集（避免绑定悬空）
        val plans = focusPlans().map { p ->
            if (p.appGroupId == id) p.copy(appGroupId = null, directEntries = null) else p
        }
        if (plans != focusPlans()) saveFocusPlans(plans)
    }

    fun defaultGroup(): AppGroup? = appGroups().firstOrNull { it.isDefault }

    /** 设为默认：清除其他集的默认标记 */
    fun setDefaultGroup(id: Long) {
        val groups = appGroups().map { it.copy(isDefault = it.id == id) }
        saveAppGroups(groups)
    }

    // ---------- 当前选中应用集 ----------

    /** 当前选中应用集的 id（未选择/被删时回退默认集） */
    fun selectedGroupId(): Long? = runCatching {
        if (!selectedGroupFile.exists()) return null
        val json = JSONObject(selectedGroupFile.readText())
        json.optLong("id").takeIf { it > 0 }
    }.getOrNull()

    fun setSelectedGroupId(id: Long?) {
        dir.mkdirs()
        if (id == null) {
            runCatching { selectedGroupFile.delete() }
        } else {
            selectedGroupFile.writeText(JSONObject().put("id", id).toString())
        }
    }

    /** 当前生效应用集：优先选中集，否则默认集 */
    fun selectedGroup(): AppGroup? {
        ensureMigrated()
        val id = selectedGroupId()
        val groups = readAppGroups()
        return groups.firstOrNull { it.id == id } ?: groups.firstOrNull { it.isDefault }
    }

    // ---------- 黑名单条目（应用分身支持） ----------

    /**
     * 条目编码：主应用为纯包名（兼容旧数据），分身附加 @userId 与主应用区分。
     * 包名不含 @，可安全用 @ 分隔。
     */
    fun entryOf(packageName: String, userId: Int): String =
        if (userId == Process.myUserHandle().hashCode()) packageName else "$packageName@$userId"

    /** 解析条目 → (包名, userId)；无 @ 的旧数据视为当前用户主应用 */
    fun parseEntry(entry: String): Pair<String, Int> {
        val i = entry.lastIndexOf('@')
        if (i <= 0) return entry to Process.myUserHandle().hashCode()
        val pkg = entry.substring(0, i)
        val uid = entry.substring(i + 1).toIntOrNull() ?: Process.myUserHandle().hashCode()
        return pkg to uid
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

    // ---------- 专注计划 ----------

    /**
     * 专注计划：按时间段 + 每周固定几天自动开始/结束专注。
     * startMinute / endMinute 为 0..1439（当天分钟数）；endMinute < startMinute 表示跨天，
     * endMinute == startMinute 视为跨全天（24 小时）。
     * appGroupId 非空 → 暂停该应用集；为空且 directEntries 非空 → 直选应用；
     * 为空且空列表 → 用当前默认集。
     */
    data class FocusPlan(
        val id: Long,
        val name: String,
        val startMinute: Int,
        val endMinute: Int,
        val weekdays: Set<Int>, // 1=周一 .. 7=周日
        val appGroupId: Long? = null,
        val directEntries: List<String>? = null,
        val enabled: Boolean = true,
    ) {
        /** 是否跨天（结束时间在次日） */
        val crossesMidnight: Boolean get() = endMinute <= startMinute

        /** 计划专注时长（分钟），不受 240 分钟限制 */
        val durationMinutes: Int
            get() = when {
                endMinute > startMinute -> endMinute - startMinute
                endMinute < startMinute -> (1440 - startMinute) + endMinute
                else -> 1440 // 开始 == 结束：跨全天
            }
    }

    /** 计划绑定的应用条目：应用集 → 直选 → 默认集 */
    fun planEntries(plan: FocusPlan): List<String> = when {
        plan.appGroupId != null -> appGroups().firstOrNull { it.id == plan.appGroupId }?.entries ?: emptyList()
        !plan.directEntries.isNullOrEmpty() -> plan.directEntries.orEmpty()
        else -> defaultGroup()?.entries ?: emptyList()
    }

    fun focusPlans(): List<FocusPlan> = runCatching {
        if (!plansFile.exists()) return emptyList()
        val json = JSONArray(plansFile.readText())
        (0 until json.length()).mapNotNull { i ->
            val obj = json.getJSONObject(i)
            FocusPlan(
                id = obj.getLong("id"),
                name = obj.getString("name"),
                startMinute = obj.getInt("start"),
                endMinute = obj.getInt("end"),
                weekdays = runCatching {
                    val arr = obj.getJSONArray("weekdays")
                    (0 until arr.length()).map { arr.getInt(it) }.toSet()
                }.getOrDefault(emptySet()),
                appGroupId = if (obj.has("appGroupId")) obj.getLong("appGroupId") else null,
                directEntries = if (obj.has("directEntries")) {
                    runCatching {
                        val arr = obj.getJSONArray("directEntries")
                        (0 until arr.length()).map { arr.getString(it) }
                    }.getOrDefault(emptyList())
                } else null,
                enabled = obj.optBoolean("enabled", true),
            )
        }
    }.getOrDefault(emptyList())

    fun saveFocusPlans(plans: List<FocusPlan>) {
        dir.mkdirs()
        plansFile.writeText(JSONArray().apply {
            plans.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("start", p.startMinute)
                    put("end", p.endMinute)
                    put("weekdays", JSONArray(p.weekdays))
                    p.appGroupId?.let { put("appGroupId", it) }
                    p.directEntries?.let { put("directEntries", JSONArray(it)) }
                    put("enabled", p.enabled)
                })
            }
        }.toString())
    }

    /** 下一个可用的计划 id */
    fun nextPlanId(): Long = (focusPlans().maxOfOrNull { it.id } ?: 0L) + 1

    fun addFocusPlan(plan: FocusPlan) {
        saveFocusPlans(focusPlans() + plan)
    }

    fun updateFocusPlan(plan: FocusPlan) {
        val plans = focusPlans().toMutableList()
        val idx = plans.indexOfFirst { it.id == plan.id }
        if (idx >= 0) {
            plans[idx] = plan
            saveFocusPlans(plans)
        }
    }

    fun deleteFocusPlan(id: Long) {
        saveFocusPlans(focusPlans().filter { it.id != id })
        clearPlanExecuted(id)
        if (pendingPlan()?.planId == id) clearPendingPlan()
    }

    // ---------- 待启动计划（冲突延后 + 5 分钟决策窗口） ----------

    /** 待启动计划：计划到点但已有专注进行中时持久化，等当前专注结束后让用户决策 */
    data class PendingPlan(val planId: Long, val deadline: Long)

    fun pendingPlan(): PendingPlan? = runCatching {
        if (!pendingPlanFile.exists()) return null
        val json = JSONObject(pendingPlanFile.readText())
        PendingPlan(planId = json.getLong("planId"), deadline = json.getLong("deadline"))
    }.getOrNull()

    fun setPendingPlan(planId: Long, deadline: Long) {
        dir.mkdirs()
        pendingPlanFile.writeText(JSONObject().put("planId", planId).put("deadline", deadline).toString())
    }

    fun clearPendingPlan() {
        runCatching { pendingPlanFile.delete() }
    }

    // ---------- 计划已执行日（同一计划同一天只触发一次） ----------

    /** 计划最近一次已执行专注的日期（yyyyMMdd 整数） */
    fun planExecutedDay(planId: Long): Int? = runCatching {
        if (!planExecutedFile.exists()) return null
        val json = JSONArray(planExecutedFile.readText())
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            if (obj.getLong("id") == planId) return obj.getInt("day")
        }
        null
    }.getOrNull()

    fun markPlanExecuted(planId: Long, day: Int) {
        dir.mkdirs()
        val list = runCatching {
            val json = JSONArray(planExecutedFile.readText())
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                if (obj.getLong("id") == planId) JSONObject().put("id", planId).put("day", day)
                else obj
            }.toMutableList()
        }.getOrDefault(mutableListOf())
        if (list.none { it.getLong("id") == planId }) {
            list.add(JSONObject().put("id", planId).put("day", day))
        }
        planExecutedFile.writeText(JSONArray(list).toString())
    }

    fun clearPlanExecuted(planId: Long) {
        runCatching {
            if (!planExecutedFile.exists()) return
            val json = JSONArray(planExecutedFile.readText())
            val list = (0 until json.length()).map { json.getJSONObject(it) }.filter { it.getLong("id") != planId }
            planExecutedFile.writeText(JSONArray(list).toString())
        }
    }

    /** 当天日期码（yyyyMMdd） */
    fun todayCode(): Int = Calendar.getInstance().let {
        it.get(Calendar.YEAR) * 10000 + (it.get(Calendar.MONTH) + 1) * 100 + it.get(Calendar.DAY_OF_MONTH)
    }
}
