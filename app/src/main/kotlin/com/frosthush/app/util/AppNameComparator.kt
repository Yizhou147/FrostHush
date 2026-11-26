package com.frosthush.app.util

import com.frosthush.app.data.AppRepository
import net.sourceforge.pinyin4j.PinyinHelper
import java.text.Collator

/**
 * 应用列表排序：中文名按拼音排序（首字母 + 全拼），非中文按系统排序规则。
 */
object AppNameComparator : Comparator<AppRepository.AppInfo> {
    /** Collator 非线程安全，而同一个比较器可能在多个后台线程同时排序（页面查询/预加载） */
    private val collator = ThreadLocal.withInitial { Collator.getInstance() }

    /**
     * 排序键缓存：排序会对同一名称比较 O(log n) 次，pinyin4j 转换不便宜，
     * 不缓存等于每个应用名被反复转换几十次（启动时列表全量排序尤其明显）。
     * 应用名称数量有限（几百条），直接全量保留。
     */
    private val keyCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun compare(a: AppRepository.AppInfo, b: AppRepository.AppInfo): Int {
        val pk = pinyinKey(a.name)
        val qk = pinyinKey(b.name)
        val c = pk.compareTo(qk)
        if (c != 0) return c
        return collator.get().compare(a.name, b.name)
    }

    /** 每个汉字取拼音首字母，非汉字保留原字符（小写），如 "微信" -> "wx" */
    private fun pinyinKey(name: String): String =
        keyCache[name] ?: buildString {
            for (ch in name) {
                val arr = PinyinHelper.toHanyuPinyinStringArray(ch)
                append(if (arr == null) ch.lowercaseChar() else arr[0][0])
            }
        }.also { keyCache[name] = it }
}
