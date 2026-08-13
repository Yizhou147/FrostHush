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

    /** 分段阶段类型：专注 / 休息 */
    const val SEGMENT_FOCUS = 0
    const val SEGMENT_REST = 1

    /** 分段中的一段：专注或休息（时长分钟）。列表始终从专注开始、以专注结束、类型交替。 */
    data class Segment(val type: Int, val minutes: Int) {
        val isFocus: Boolean get() = type == SEGMENT_FOCUS
    }

    /** 历史记录中的一段（起止时间按实际发生计算） */
    data class HistorySegment(val type: Int, val start: Long, val end: Long) {
        val minutes: Long get() = (end - start) / 60_000L
    }

    /** 会话中某个时刻所处的阶段信息 */
    data class PhaseInfo(
        val index: Int,
        val type: Int,
        val segmentStart: Long,
        val segmentEnd: Long,
    ) {
        val isFocus: Boolean get() = type == SEGMENT_FOCUS

        fun remainingAt(now: Long): Long = (segmentEnd - now).coerceAtLeast(0L)
    }

    /**
     * 活动会话：{应用包名列表, 开始时间, 时长}。
     * planId 非空表示该会话由专注计划启动（不受 240 分钟限制，可跨天）。
     * segments 非空表示分段专注（专注→休息→专注…，从专注开始以专注结束）；
     * segments 为空表示单段连续专注（旧数据兼容，durationMinutes 即总时长）。
     */
    data class ActiveSession(
        val packages: List<String>,
        val startMillis: Long,
        val durationMinutes: Int,
        val planId: Long? = null,
        val segments: List<Segment>? = null,
    ) {
        /** 各段计划时长（分钟）；无分段时回退单段专注 */
        val segmentMinutes: List<Int>
            get() = segments?.map { it.minutes } ?: listOf(durationMinutes)

        /** 总时长（分钟）：各段之和；无分段时回退 durationMinutes */
        val totalMinutes: Int get() = segmentMinutes.sum()

        val endMillis: Long get() = startMillis + totalMinutes * 60_000L

        /** 是否分段（含休息段） */
        val isSegmented: Boolean get() = segments != null && segments.size > 1

        /** 某时刻所处的阶段（分段边界按计划时长推算） */
        fun phaseAt(millis: Long): PhaseInfo {
            val minutes = segmentMinutes
            var last = startMillis
            for (i in minutes.indices) {
                val end = last + minutes[i] * 60_000L
                if (millis < end || i == minutes.size - 1) {
                    return PhaseInfo(
                        index = i,
                        type = segments?.getOrNull(i)?.type ?: SEGMENT_FOCUS,
                        segmentStart = last,
                        segmentEnd = end,
                    )
                }
                last = end
            }
            // 不可达：segmentMinutes 恒非空
            return PhaseInfo(0, SEGMENT_FOCUS, startMillis, startMillis + durationMinutes * 60_000L)
        }

        /**
         * 生成历史明细：各段实际起止时间。
         * 各段边界按计划时长推算（跳过休息会重写对应休息段为实际耗时，故边界即实际）。
         * 最后一段结束时间用传入的 end（含提前结束）。
         */
        fun toHistorySegments(end: Long): List<HistorySegment>? {
            if (segments == null) return null
            val minutes = segmentMinutes
            val result = ArrayList<HistorySegment>(minutes.size)
            var last = startMillis
            for (i in minutes.indices) {
                val segEnd = if (i == minutes.size - 1) end else last + minutes[i] * 60_000L
                result.add(HistorySegment(segments[i].type, last, segEnd))
                last = segEnd
            }
            return result
        }
    }

    /** 一次已完成的专注会话（整段一条；分段会话含 segments 明细） */
    data class HistoryRecord(
        val start: Long,
        val end: Long,
        val segments: List<HistorySegment>? = null,
    ) {
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
            segments = json.optJSONArray("segments")?.let { segArr ->
                (0 until segArr.length()).map { i ->
                    val o = segArr.getJSONObject(i)
                    Segment(o.getInt("type"), o.getInt("minutes"))
                }
            },
        )
    }.getOrNull()

    fun saveActiveSession(session: ActiveSession) {
        dir.mkdirs()
        sessionFile.writeText(JSONObject().apply {
            put("packages", JSONArray(session.packages))
            put("start", session.startMillis)
            put("duration", session.durationMinutes)
            session.planId?.let { put("planId", it) }
            session.segments?.let { segs ->
                put("segments", JSONArray().apply {
                    segs.forEach { s -> put(JSONObject().put("type", s.type).put("minutes", s.minutes)) }
                })
            }
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
            val end = obj.getLong("end")
            result.add(
                HistoryRecord(
                    start, end,
                    segments = obj.optJSONArray("segments")?.let { segArr ->
                        (0 until segArr.length()).map { j ->
                            val o = segArr.getJSONObject(j)
                            HistorySegment(o.getInt("type"), o.getLong("start"), o.getLong("end"))
                        }
                    },
                )
            )
        }
        result
    }.getOrDefault(emptyList())

    fun addHistory(record: HistoryRecord) {
        if (record.start <= 0 || record.end <= record.start) return
        val list = history().toMutableList().apply { add(record) }
        while (list.size > MAX_HISTORY) list.removeAt(0)
        dir.mkdirs()
        historyFile.writeText(JSONArray().apply {
            list.forEach {
                val obj = JSONObject().put("start", it.start).put("end", it.end)
                it.segments?.let { segs ->
                    obj.put("segments", JSONArray().apply {
                        segs.forEach { s ->
                            put(JSONObject().put("type", s.type).put("start", s.start).put("end", s.end))
                        }
                    })
                }
                put(obj)
            }
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
        // 分段专注（专注→休息→专注…）；为空表示单段连续专注。
        // 分段计划保存时 endMinute 由「开始 + 各段总和」推导，两处保持一致
        val segments: List<Segment>? = null,
    ) {
        /** 是否跨天（结束时间在次日） */
        val crossesMidnight: Boolean get() = endMinute <= startMinute

        /** 计划专注时长（分钟），不受 240 分钟限制 */
        val durationMinutes: Int
            get() = segments?.sumOf { it.minutes }
                ?: when {
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
                segments = obj.optJSONArray("segments")?.let { segArr ->
                    (0 until segArr.length()).map { j ->
                        val o = segArr.getJSONObject(j)
                        Segment(o.getInt("type"), o.getInt("minutes"))
                    }
                },
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
                    p.segments?.let { segs ->
                        put("segments", JSONArray().apply {
                            segs.forEach { s -> put(JSONObject().put("type", s.type).put("minutes", s.minutes)) }
                        })
                    }
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

    // ---------- 统计清空 / 导入导出 ----------

    /** 清空全部专注统计 */
    fun clearHistory() {
        runCatching { historyFile.delete() }
    }

    /** 导出专注统计：JSON [{start,end,segments?},...] 字符串 */
    fun exportStatsJson(): String = JSONArray().apply {
        history().forEach {
            val obj = JSONObject().put("start", it.start).put("end", it.end)
            it.segments?.let { segs ->
                obj.put("segments", JSONArray().apply {
                    segs.forEach { s ->
                        put(JSONObject().put("type", s.type).put("start", s.start).put("end", s.end))
                    }
                })
            }
            put(obj)
        }
    }.toString()

    private const val CONFIG_VERSION = 1

    /** 导入配置文件的解析结果（应用集 + 选中集 + 计划 + 预设，不落盘） */
    data class ConfigData(
        val groups: List<AppGroup>,
        val selectedGroupId: Long?,
        val plans: List<FocusPlan>,
        val presets: List<FocusPreset>,
    )

    /** 导出应用配置：应用集 + 专注计划 + 预设 + 选中集，JSON 对象字符串 */
    fun exportConfigJson(): String = JSONObject().apply {
        put("version", CONFIG_VERSION)
        put("appGroups", JSONArray().apply {
            appGroups().forEach { g ->
                put(JSONObject().apply {
                    put("id", g.id)
                    put("name", g.name)
                    put("entries", JSONArray(g.entries))
                    put("isDefault", g.isDefault)
                })
            }
        })
        put("selectedGroupId", selectedGroupId() ?: JSONObject.NULL)
        put("focusPlans", JSONArray().apply {
            focusPlans().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("start", p.startMinute)
                    put("end", p.endMinute)
                    put("weekdays", JSONArray(p.weekdays))
                    p.appGroupId?.let { put("appGroupId", it) }
                    p.directEntries?.let { put("directEntries", JSONArray(it)) }
                    put("enabled", p.enabled)
                    p.segments?.let { segs ->
                        put("segments", JSONArray().apply {
                            segs.forEach { s -> put(JSONObject().put("type", s.type).put("minutes", s.minutes)) }
                        })
                    }
                })
            }
        })
        put("presets", JSONArray().apply {
            presets.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("minutes", it.minutes)) }
        })
    }.toString()

    /** 解析导入配置 JSON（仅解析校验，不落盘）；版本/格式不合法返回 null */
    fun parseConfigJson(text: String): ConfigData? = runCatching {
        val root = JSONObject(text)
        if (root.optInt("version", 0) != CONFIG_VERSION) return null
        val groups = root.getJSONArray("appGroups").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppGroup(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    entries = o.optJSONArray("entries")?.let { e ->
                        (0 until e.length()).map { e.getString(it) }
                    } ?: emptyList(),
                    isDefault = o.optBoolean("isDefault", false),
                )
            }
        }
        val selected = if (root.isNull("selectedGroupId")) null
        else root.optLong("selectedGroupId").takeIf { it > 0 }
        val plans = root.getJSONArray("focusPlans").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FocusPlan(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    startMinute = o.getInt("start"),
                    endMinute = o.getInt("end"),
                    weekdays = o.optJSONArray("weekdays")?.let { w ->
                        (0 until w.length()).map { w.getInt(it) }.toSet()
                    } ?: emptySet(),
                    appGroupId = if (o.has("appGroupId")) o.getLong("appGroupId") else null,
                    directEntries = if (o.has("directEntries")) {
                        o.getJSONArray("directEntries").let { e -> (0 until e.length()).map { e.getString(it) } }
                    } else null,
                    enabled = o.optBoolean("enabled", true),
                    segments = o.optJSONArray("segments")?.let { segArr ->
                        (0 until segArr.length()).map { j ->
                            val so = segArr.getJSONObject(j)
                            Segment(so.getInt("type"), so.getInt("minutes"))
                        }
                    },
                )
            }
        }
        val presetList = root.getJSONArray("presets").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FocusPreset(
                    id = if (o.has("id")) o.getLong("id") else System.currentTimeMillis() + i,
                    name = o.getString("name"),
                    minutes = o.getInt("minutes"),
                )
            }
        }
        ConfigData(groups, selected, plans, presetList)
    }.getOrNull()

    /** 覆盖模式：整体替换应用集/计划/预设/选中集（原导入行为） */
    fun applyConfigOverwrite(data: ConfigData) {
        saveAppGroups(data.groups)
        saveFocusPlans(data.plans)
        presets.clear()
        presets.addAll(data.presets)
        savePresets()
        setSelectedGroupId(data.selectedGroupId)
    }

    // ---------- 新增模式（合并导入） ----------

    /** 新增模式：单条导入计划的处理动作 */
    enum class PlanImportAction { ADD, SKIP, CONFLICT, RENAME }

    /** 新增模式：单条导入计划的预览结果 */
    data class PlanImportItem(
        val plan: FocusPlan,
        val action: PlanImportAction,
        /** 展示用名字：RENAME 时为重命名后的新名字，其余为原名 */
        val displayName: String,
        /** CONFLICT：与本机冲突（时段+星期+名字均不同名）的本地计划 id */
        val conflictLocalId: Long? = null,
    )

    /** 新增模式：整个导入的预览结果（不落盘，供预览页初始化逐项编辑状态） */
    data class ConfigMergePreview(
        /** 将导入的应用集：名字已去重、isDefault 保留原标记（导入时强制取消） */
        val groups: List<AppGroup>,
        val planItems: List<PlanImportItem>,
    )

    /** 新增模式导入结果统计 */
    data class MergeResult(
        val groupsAdded: Int,
        val plansAdded: Int,
        val plansSkipped: Int,
        val plansRenamed: Int,
        val plansKeepLocal: Int,
        val plansReplaced: Int,
        val presetsAdded: Int,
        val presetsSkipped: Int,
    )

    /**
     * 计算新增模式的导入预览：
     * - 应用集：全部作为新集导入，名字与本地（及本次已用名）冲突时加「 (2)」后缀；默认集标记保留展示、导入时取消
     * - 计划：时段+星期+名字 全同 → 跳过；时段+星期同、名字不同 → 冲突待用户决策；
     *   名字同、时段不同 → 重命名「原名 (2)」后新增；其余直接新增
     * - 预设：同名同分钟跳过（预览只统计，去重与上限在导入时执行）
     */
    fun previewConfigMerge(data: ConfigData): ConfigMergePreview {
        val localGroups = appGroups()
        val localPlans = focusPlans()

        val usedGroupNames = localGroups.map { it.name }.toMutableSet()
        val importedGroups = data.groups.map { g ->
            var name = g.name
            var n = 2
            while (name in usedGroupNames) {
                name = "${g.name} ($n)"
                n++
            }
            usedGroupNames.add(name)
            g.copy(name = name)
        }

        val usedPlanNames = localPlans.map { it.name }.toMutableSet()
        val planItems = data.plans.map { p ->
            val slotMatch = localPlans.firstOrNull {
                it.startMinute == p.startMinute && it.endMinute == p.endMinute && it.weekdays == p.weekdays
            }
            when {
                slotMatch != null && slotMatch.name == p.name ->
                    PlanImportItem(p, PlanImportAction.SKIP, p.name)
                slotMatch != null ->
                    PlanImportItem(p, PlanImportAction.CONFLICT, p.name, conflictLocalId = slotMatch.id)
                else -> {
                    val nameInUse = p.name in usedPlanNames
                    val uniqueName = if (nameInUse) {
                        var name = p.name
                        var n = 2
                        while (name in usedPlanNames) {
                            name = "${p.name} ($n)"
                            n++
                        }
                        name
                    } else p.name
                    usedPlanNames.add(uniqueName)
                    PlanImportItem(
                        p,
                        if (nameInUse) PlanImportAction.RENAME else PlanImportAction.ADD,
                        uniqueName,
                    )
                }
            }
        }

        return ConfigMergePreview(importedGroups, planItems)
    }

    /** 合并导入的单条计划请求（UI 按最终编辑状态生成） */
    data class PlanMergeRequest(
        val plan: FocusPlan,
        val action: PlanImportAction,
        val finalName: String,
        val conflictLocalId: Long? = null,
        val replaceLocal: Boolean = false,
    )

    /**
     * 合并导入（新增语义）：
     * - groups：全部作为新集导入（id 为文件 id 用于计划映射，名字为最终名，强制非默认）
     * - planRequests：SKIP 跳过；CONFLICT 按 replaceLocal 替换本地或保留本地；ADD/RENAME 新增（finalName）
     * - incomingPresets：全部导入（与本地及本次已导入的「名称+分钟」去重，超出 MAX_PRESETS 跳过）
     */
    fun applyConfigMerge(
        groups: List<AppGroup>,
        planRequests: List<PlanMergeRequest>,
        incomingPresets: List<FocusPreset>,
    ): MergeResult {
        val mergedGroups = appGroups().toMutableList()
        val mergedPlans = focusPlans().toMutableList()
        val mergedPresets = presets.toMutableList()
        val idMap = HashMap<Long, Long>()

        // 应用集：追加导入集（强制取消默认标记），建立 文件 id → 新 id 映射供计划引用
        groups.forEach { g ->
            val newId = (mergedGroups.maxOfOrNull { it.id } ?: 0L) + 1
            idMap[g.id] = newId
            mergedGroups.add(g.copy(id = newId, isDefault = false))
        }
        saveAppGroups(mergedGroups)

        var added = 0
        var skipped = 0
        var renamed = 0
        var keepLocal = 0
        var replaced = 0
        planRequests.forEach { req ->
            val p = req.plan
            when (req.action) {
                PlanImportAction.SKIP -> skipped++
                PlanImportAction.CONFLICT -> {
                    if (req.replaceLocal) {
                        val idx = mergedPlans.indexOfFirst { it.id == req.conflictLocalId }
                        if (idx >= 0) {
                            mergedPlans[idx] = remapGroupRef(p.copy(id = req.conflictLocalId!!, name = req.finalName), idMap)
                            replaced++
                        } else skipped++
                    } else keepLocal++
                }
                PlanImportAction.ADD -> {
                    mergedPlans.add(remapGroupRef(p.copy(id = (mergedPlans.maxOfOrNull { it.id } ?: 0L) + 1, name = req.finalName), idMap))
                    added++
                }
                PlanImportAction.RENAME -> {
                    mergedPlans.add(remapGroupRef(p.copy(id = (mergedPlans.maxOfOrNull { it.id } ?: 0L) + 1, name = req.finalName), idMap))
                    renamed++
                }
            }
        }
        saveFocusPlans(mergedPlans)

        // 预设：与本地及本次已导入的「名称+分钟」去重；超出上限跳过
        var presetsAdded = 0
        var presetsSkipped = 0
        val presetSet = mergedPresets.map { it.name to it.minutes }.toMutableSet()
        incomingPresets.forEach { pr ->
            if (pr.name to pr.minutes in presetSet || mergedPresets.size >= MAX_PRESETS) {
                presetsSkipped++
            } else {
                mergedPresets.add(FocusPreset((mergedPresets.maxOfOrNull { it.id } ?: 0L) + 1, pr.name, pr.minutes))
                presetSet.add(pr.name to pr.minutes)
                presetsAdded++
            }
        }
        if (mergedPresets != presets) {
            presets.clear()
            presets.addAll(mergedPresets)
            savePresets()
        }

        return MergeResult(
            groupsAdded = groups.size,
            plansAdded = added,
            plansSkipped = skipped,
            plansRenamed = renamed,
            plansKeepLocal = keepLocal,
            plansReplaced = replaced,
            presetsAdded = presetsAdded,
            presetsSkipped = presetsSkipped,
        )
    }

    /** 计划绑定的应用集引用随新增导入重映射；引用文件里不存在的应用集时回退默认集 */
    private fun remapGroupRef(plan: FocusPlan, idMap: Map<Long, Long>): FocusPlan {
        val gid = plan.appGroupId ?: return plan
        return if (gid in idMap) plan.copy(appGroupId = idMap[gid]) else plan.copy(appGroupId = null)
    }
}
