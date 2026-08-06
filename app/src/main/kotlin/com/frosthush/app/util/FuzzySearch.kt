package com.frosthush.app.util

/**
 * 模糊搜索：复用 Hail 的 FuzzySearch 算法（莱文斯坦距离 + 按序包含字符）。
 * 原始字符串依次包含输入字符串的每个字符，且编辑距离小于原始字符串长度即视为匹配。
 */
object FuzzySearch {
    /**
     * @param raw   需要匹配的原始字符串（应用名或包名）
     * @param query 输入的搜索词
     */
    fun search(raw: String?, query: String?): Boolean {
        if (query.isNullOrEmpty()) return true
        if (raw.isNullOrEmpty()) return false
        if (raw.contains(query, ignoreCase = true)) return true
        val rawUpp = raw.uppercase()
        val queryUpp = query.uppercase()
        val diff = levenshtein(rawUpp, queryUpp)
        return diff < rawUpp.length && containsInOrder(rawUpp, queryUpp)
    }

    /** 字符串 A 是否依次包含字符串 B 的每个字符 */
    private fun containsInOrder(strA: String, strB: String): Boolean {
        var indexA = 0
        for (charB in strB) {
            val foundIndex = strA.indexOf(charB, indexA)
            if (foundIndex == -1) return false
            indexA = foundIndex + 1
        }
        return true
    }

    /** 莱文斯坦编辑距离 */
    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
