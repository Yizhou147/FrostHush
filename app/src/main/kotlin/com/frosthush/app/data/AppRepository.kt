package com.frosthush.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.frosthush.app.util.AppNameComparator
import com.frosthush.app.util.FuzzySearch
import com.frosthush.app.util.PinyinSearch

/**
 * 已安装应用查询（QUERY_ALL_PACKAGES）+ 图标缓存 + 搜索过滤。
 */
class AppRepository(private val context: Context) {

    data class AppInfo(
        val packageName: String,
        val name: String,
        val isSystem: Boolean,
    )

    private val iconCache = LruCache<String, Drawable>(256)

    private val pm get() = context.packageManager

    /** 全部已安装应用（按中文拼音排序） */
    fun queryApps(): List<AppInfo> = runCatching {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }
        list.asSequence()
            .filter { it.packageName != context.packageName } // 排除自身
            .map { ai ->
                AppInfo(
                    packageName = ai.packageName,
                    name = ai.loadLabel(pm).toString(),
                    isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedWith(AppNameComparator)
            .toList()
    }.getOrDefault(emptyList())

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
}
