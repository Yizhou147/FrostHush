package com.frosthush.app.util

import com.frosthush.app.data.AppRepository
import net.sourceforge.pinyin4j.PinyinHelper
import java.text.Collator

/**
 * 应用列表排序：中文名按拼音排序（首字母 + 全拼），非中文按系统排序规则。
 */
object AppNameComparator : Comparator<AppRepository.AppInfo> {
    private val collator = Collator.getInstance()

    override fun compare(a: AppRepository.AppInfo, b: AppRepository.AppInfo): Int {
        val pk = pinyinKey(a.name)
        val qk = pinyinKey(b.name)
        val c = pk.compareTo(qk)
        if (c != 0) return c
        return collator.compare(a.name, b.name)
    }

    /** 每个汉字取拼音首字母，非汉字保留原字符（小写），如 "微信" -> "wx" */
    private fun pinyinKey(name: String): String = buildString {
        for (ch in name) {
            val arr = PinyinHelper.toHanyuPinyinStringArray(ch)
            append(if (arr == null) ch.lowercaseChar() else arr[0][0])
        }
    }
}
