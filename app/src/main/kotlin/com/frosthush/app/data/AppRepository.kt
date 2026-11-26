package com.frosthush.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.util.LruCache
import com.frosthush.app.FrostHushApp.Companion.app
import com.frosthush.app.R
import com.frosthush.app.focus.HShizuku
import com.frosthush.app.util.AppNameComparator
import com.frosthush.app.util.DebugLog
import com.frosthush.app.util.FuzzySearch
import com.frosthush.app.util.PinyinSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File

/**
 * 已安装应用查询（QUERY_ALL_PACKAGES）+ 图标缓存 + 搜索过滤。
 * 分身（应用克隆/双开空间，如小米 XSpace user 999）通过 Shizuku 跨用户读取。
 */
class AppRepository(private val context: Context) {

    companion object {
        /** 应用名称映射缓存（entry → displayName）：避免进入页面时名称加载慢导致闪现裸包名 */
        @Volatile
        private var appNameCache: Map<String, String>? = null

        /** 名称缓存磁盘持久化文件：分身名称读取走 Shizuku IPC 较慢，落盘后新进程可秒读 */
        private val appNameFile = File(app.filesDir, "appNames.json")

        /**
         * 已缓存的应用名称映射（内存优先，未加载时读磁盘缓存；无缓存返回空 Map）。
         * 调用方可放心在主线程执行：命中时是纯内存读。
         */
        fun cachedAppNames(): Map<String, String> {
            appNameCache?.let { return it }
            val fromDisk = runCatching {
                if (!appNameFile.exists()) return emptyMap()
                val json = JSONObject(appNameFile.readText())
                val map = LinkedHashMap<String, String>(json.length())
                val it = json.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    map[key] = json.optString(key)
                }
                map
            }.getOrDefault(emptyMap())
            appNameCache = fromDisk
            return fromDisk
        }

        fun updateAppNameCache(names: Map<String, String>) {
            appNameCache = names
            appNamesLoadedAt = SystemClock.elapsedRealtime()
            runCatching {
                appNameFile.parentFile?.mkdirs()
                appNameFile.writeText(JSONObject(names).toString())
            }
        }

        /** 上次全量名称查询完成时刻（elapsedRealtime）；-1 表示本进程还没做过全量查询 */
        @Volatile
        private var appNamesLoadedAt = -1L

        private val appNamesLoadMutex = Mutex()

        /** 全量名称缓存有效期：窗口内重复调用直接复用，过后允许后台刷新（新装应用也能拿到名称） */
        private const val APP_NAMES_TTL_MS = 10 * 60_000L

        /** 本进程做过全量查询且还在有效期内（磁盘缓存 alone 不算，避免刚开机时误判） */
        private fun isAppNamesFresh(): Boolean {
            val at = appNamesLoadedAt
            return at >= 0 && SystemClock.elapsedRealtime() - at < APP_NAMES_TTL_MS
        }

        /**
         * 后台加载全量应用名称缓存（含分身）。
         *
         * 禁止在 UI 线程直接调 [queryApps]：主列表 + 每个包 loadLabel + 拼音排序已经很重，
         * 分身还要走 Shizuku shell（execute 读线程最长 join 15s）。Shizuku 本体在后台被冻结时
         * 这些调用会阻塞调用线程数秒——那就是回前台卡住点不动的直接来源。
         * 并发调用由 Mutex 串行化，只有第一个真正查询，其余直接拿结果。
         */
        suspend fun ensureAppNamesLoaded(context: Context): Map<String, String> {
            if (isAppNamesFresh()) appNameCache?.let { return it }
            return appNamesLoadMutex.withLock {
                if (isAppNamesFresh()) appNameCache?.let { return@withLock it }
                val start = SystemClock.elapsedRealtime()
                val full = withContext(Dispatchers.IO) {
                    runCatching {
                        AppRepository(context).queryApps().associate { it.entry to it.displayName }
                    }.getOrDefault(emptyMap())
                }
                if (full.isNotEmpty()) updateAppNameCache(full)
                val cost = SystemClock.elapsedRealtime() - start
                if (cost > 200) {
                    DebugLog.d("AppRepo", "全量应用名称查询耗时 ${cost}ms size=${full.size}")
                }
                if (full.isNotEmpty()) full else cachedAppNames()
            }
        }
    }

    data class AppInfo(
        val packageName: String,
        val name: String,
        val isSystem: Boolean,
        /** 所在用户空间 id：主应用为当前用户，分身（克隆应用）为独立用户（如 999） */
        val userId: Int = Process.myUserHandle().hashCode(),
    ) {
        /** 是否应用分身（克隆应用） */
        val isClone: Boolean get() = userId != Process.myUserHandle().hashCode()

        /** 黑名单条目 key：主应用为纯包名（兼容旧数据），分身附加 @userId 与主应用区分 */
        val entry: String get() = if (isClone) "$packageName@$userId" else packageName

        /** 展示名：分身追加" · 分身"后缀便于区分 */
        val displayName: String
            get() = if (isClone) app.getString(R.string.app_clone_suffix, name) else name
    }

    private val iconCache = LruCache<String, Drawable>(256)

    private val pm get() = context.packageManager

    /** 全部已安装应用（含分身，按中文拼音排序）；includeClones=false 仅主用户应用（无跨用户 IPC） */
    fun queryApps(includeClones: Boolean = true): List<AppInfo> = runCatching {
        val myUserId = Process.myUserHandle().hashCode()
        // 基础列表：主用户应用（不带任何跨用户标志，保证始终可读）
        val base = queryInstalledApps(0, myUserId)
        // 分身列表：单独查询并 try-catch，失败只丢分身不影响主列表
        val clones = if (includeClones) queryCloneApps(myUserId) else emptyList()
        (base + clones)
            // 同一包名的主应用与分身各保留一条
            .distinctBy { it.packageName to it.userId }
            .sortedWith(AppNameComparator)
    }.getOrDefault(emptyList())

    /**
     * 逐用户查询分身/其他用户空间的应用。
     * 普通 API 跨用户查询需 INTERACT_ACROSS_USERS 权限会被拒，因此优先走 Shizuku（shell 有权限）；
     * Shizuku 不可用时回退到 MATCH_CLONE_PROFILE 标准克隆查询。各分支独立 try-catch。
     */
    private fun queryCloneApps(myUserId: Int): List<AppInfo> {
        // 候选其他用户：当前用户 profile 组（含工作/克隆 profile）+ 小米 XSpace 双开空间(999)
        val candidates = LinkedHashSet<Int>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching {
                context.getSystemService(UserManager::class.java)?.getUserProfiles()
                    ?.forEach { candidates.add(it.hashCode()) }
            }.getOrNull()
        }
        candidates.add(999) // 小米 XSpace 双开空间固定 user 999
        candidates.remove(myUserId)

        val result = ArrayList<AppInfo>()
        val shizukuOk = shizukuUsable()
        android.util.Log.i("FrostHush", "queryCloneApps: shizukuUsable=$shizukuOk, candidates=$candidates")
        if (shizukuOk) {
            // Shizuku：执行 pm list packages --user 读取分身（shell 有跨用户权限）
            candidates.forEach { uid ->
                val entries = HShizuku.listPackagesForUser(uid)
                android.util.Log.i("FrostHush", "Shizuku pm user $uid 读取到 ${entries.size} 个包")
                entries.forEach { (pkg, sysByPath) ->
                    // 系统分区路径：XSpace 自动预装的系统组件，跳过
                    if (sysByPath) return@forEach
                    // 名称/系统标志从主用户的应用信息取（分身与主应用同 APK）
                    val info = runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(pkg, 0)
                        }
                    }.getOrNull()
                    // 双开应用的主应用必在主用户存在且可查；主应用查不到（如 com.miui.analytics 这类
                    // 预装组件被禁用/卸载后残留）直接排除，避免混入非双开应用
                    if (info == null) return@forEach
                    // 系统应用更新后 APK 可能在 /data，需再用 flags 兜底（FLAG_SYSTEM/FLAG_UPDATED_SYSTEM_APP）
                    val flags = info.flags
                    if (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@forEach
                    result += AppInfo(
                        packageName = pkg,
                        name = runCatching { info.loadLabel(pm).toString() }.getOrNull() ?: pkg,
                        isSystem = false,
                        userId = uid,
                    )
                }
            }
        } else {
            // 无 Shizuku：标准克隆 profile 查询（MATCH_CLONE_PROFILE=0x80000000，API 32+）。
            // 注意 0x00400000 是 @hide 的 MATCH_ANY_USER，会触发跨用户查询抛 SecurityException。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                runCatching { result += queryInstalledApps(0x80000000.toInt(), myUserId) }.getOrNull()
            }
            // 普通权限的 getInstalledApplicationsAsUser 跨用户查询一般被拒，仅作最后兜底
            candidates.forEach { uid ->
                result += runCatching { queryInstalledAppsAsUser(0, uid) }.getOrDefault(emptyList())
            }
        }
        android.util.Log.i("FrostHush", "queryCloneApps 结果 ${result.size} 条")
        return result
    }

    /** Shizuku 是否已授权可用 */
    private fun shizukuUsable(): Boolean = runCatching {
        !Shizuku.isPreV11() && Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun toAppInfo(ai: ApplicationInfo): AppInfo = AppInfo(
        packageName = ai.packageName,
        // loadLabel 对分身（其他用户）应用可能异常，兜底显示包名，避免拖垮整个列表
        name = runCatching { ai.loadLabel(pm).toString() }.getOrElse { ai.packageName },
        isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        userId = ai.userIdCompat(),
    )

    /** 按附加标志查询已安装应用并映射为 AppInfo */
    private fun queryInstalledApps(cloneFlag: Int, myUserId: Int): List<AppInfo> {
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or cloneFlag
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(flags)
        }
        return list.asSequence()
            .filter { it.packageName != context.packageName } // 排除自身
            .map { ai ->
                AppInfo(
                    packageName = ai.packageName,
                    name = ai.loadLabel(pm).toString(),
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    userId = ai.userIdCompat(),
                )
            }
            .toList()
    }

    /**
     * 指定用户的应用列表：反射调 getInstalledApplicationsAsUser(int, int)（公开 API 17+）。
     * 本地裁剪版 android.jar 缺失该方法，运行时系统均支持。
     */
    private fun queryInstalledAppsAsUser(flags: Int, userId: Int): List<AppInfo> {
        @Suppress("UNCHECKED_CAST")
        val list = PackageManager::class.java
            .getMethod("getInstalledApplicationsAsUser", Int::class.java, Int::class.java)
            .invoke(pm, flags, userId) as List<ApplicationInfo>
        return list.asSequence()
            .filter { it.packageName != context.packageName } // 排除自身
            .map { ai ->
                AppInfo(
                    packageName = ai.packageName,
                    name = ai.loadLabel(pm).toString(),
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    userId = ai.userIdCompat(),
                )
            }
            .toList()
    }

    /** 按名称/包名/拼音过滤（后台线程调用） */
    fun filter(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        return apps.filter {
            FuzzySearch.search(it.name, query) ||
                FuzzySearch.search(it.packageName, query) ||
                PinyinSearch.searchPinyinAll(it.name, query)
        }
    }

    /** 应用图标（缓存） */
    fun iconOf(packageName: String): Drawable =
        iconCache.get(packageName) ?: run {
            val d = runCatching { pm.getApplicationIcon(packageName) }
                .getOrNull() ?: pm.defaultActivityIcon
            iconCache.put(packageName, d)
            d
        }

    /**
     * 应用所在用户 id：主应用=当前用户；分身（克隆应用）=独立用户（如小米 user 999）。
     * 通过公开 API UserHandle.getUserHandleForUid(uid).hashCode() 获取，避免读 @hide 字段。
     */
    private fun ApplicationInfo.userIdCompat(): Int = runCatching {
        android.os.UserHandle.getUserHandleForUid(uid).hashCode()
    }.getOrDefault(Process.myUserHandle().hashCode())
}
