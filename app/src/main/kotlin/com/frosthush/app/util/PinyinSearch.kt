package com.frosthush.app.util

import net.sourceforge.pinyin4j.PinyinHelper

/**
 * 中文拼音搜索，复用 Hail 的 PinyinSearch 算法：
 * 支持拼音首字母（如 "jsq" 匹配 "计算器"）与全拼（如 "jisuanqi"）。
 */
object PinyinSearch {
    /**
     * 满足首字母或全拼任一条件即视为匹配
     * @param raw   需要匹配的原始字符串（应用名）
     * @param query 输入的搜索词
     */
    fun searchPinyinAll(raw: String?, query: String?): Boolean {
        if (query.isNullOrEmpty()) return true
        if (raw.isNullOrEmpty()) return false
        return searchCap(raw, query) || searchAllSpell(raw, query)
    }

    /** 拼音首字母搜索，如搜索"计算器"时只需输入 "jsq" */
    private fun searchCap(raw: String, pinyinCap: String): Boolean {
        if (pinyinCap.length > 8) return false // "最强多媒体播放器".length
        for (index in getNameStringList(raw)) {
            if (index.contains(pinyinCap, ignoreCase = true)) return true
        }
        return false
    }

    /** 全拼搜索，如搜索"计算器"时输入 "jisuanqi" */
    private fun searchAllSpell(raw: String, pinyinAll: String): Boolean {
        if (pinyinAll.length > 48) return false // "chuang".length * 8
        for (index in getNameStringPinyinAll(raw)) {
            if (index.contains(pinyinAll, ignoreCase = true)) return true
        }
        return false
    }

    private fun getNameStringPinyinAll(target: String): ArrayList<String> {
        val res = ArrayList<String>()
        getNameCapListPinyinAll(Array(target.length) { "" }, 0, target, res)
        return res
    }

    private fun getNameStringList(target: String): ArrayList<String> {
        val res = ArrayList<String>()
        getNameCapList(CharArray(target.length), 0, target, res)
        return res
    }

    private fun getNameCapList(
        capList: CharArray, currentIndex: Int, target: String, result: ArrayList<String>
    ) {
        if (currentIndex == target.length - 1) {
            val arrayOrNull = PinyinHelper.toHanyuPinyinStringArray(target[currentIndex])
            if (arrayOrNull == null) {
                capList[currentIndex] = target[currentIndex]
                result.add(String(capList))
            } else {
                val arrayOrNullCharArray = arrayOrNull.map { e -> e[0] }.distinct().toCharArray()
                for (item in arrayOrNullCharArray) {
                    capList[currentIndex] = item
                    result.add(String(capList))
                }
            }
        } else {
            val arrayOrNull = PinyinHelper.toHanyuPinyinStringArray(target[currentIndex])
            if (arrayOrNull == null) {
                val arr = capList.copyOf()
                arr[currentIndex] = target[currentIndex]
                val newIndex = currentIndex + 1
                getNameCapList(arr, newIndex, target, result)
            } else {
                val arrayOrNullCharArray = arrayOrNull.map { e -> e[0] }.distinct().toCharArray()
                for (item in arrayOrNullCharArray) {
                    val arr = capList.copyOf()
                    arr[currentIndex] = item
                    val newIndex = currentIndex + 1
                    getNameCapList(arr, newIndex, target, result)
                }
            }
        }
    }

    private fun getNameCapListPinyinAll(
        fullList: Array<String>, currentIndex: Int, target: String, result: ArrayList<String>
    ) {
        if (currentIndex == target.length - 1) {
            val arrayOrNull = PinyinHelper.toHanyuPinyinStringArray(target[currentIndex])
            if (arrayOrNull == null) {
                fullList[currentIndex] = target[currentIndex].toString()
                result.add(fullList.joinToString(""))
            } else {
                val arrayDis = arrayOrNull.map { e -> e.substring(0, e.length - 1) }.distinct()
                for (item in arrayDis) {
                    fullList[currentIndex] = item
                    result.add(fullList.joinToString(""))
                }
            }
        } else {
            val arrayOrNull = PinyinHelper.toHanyuPinyinStringArray(target[currentIndex])
            if (arrayOrNull == null) {
                val arr = fullList.copyOf()
                arr[currentIndex] = target[currentIndex].toString()
                val newIndex = currentIndex + 1
                getNameCapListPinyinAll(arr, newIndex, target, result)
            } else {
                val arrayDis = arrayOrNull.map { e -> e.substring(0, e.length - 1) }.distinct()
                for (item in arrayDis) {
                    val arr = fullList.copyOf()
                    arr[currentIndex] = item
                    val newIndex = currentIndex + 1
                    getNameCapListPinyinAll(arr, newIndex, target, result)
                }
            }
        }
    }
}
