package com.frosthush.app.update

import com.frosthush.app.R
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * 检查更新：请求 GitHub Releases latest（结构对齐 HuaweiPods GitHubReleaseChecker，简化版）。
 *
 * 镜像站体系（国内网络 api.github.com 常不可达）：[UpdateMirror] 定义各镜像的 URL 前缀，
 * 检查时**优先用户所选镜像、失败自动遍历其余镜像**（API 优先，全部失败再试 302 重定向解析）。
 * 已实测：gh-proxy.com 可代理 API；ghfast/moeyy 等仅代理文件下载（API 403），只用于下载兜底。
 *
 * 全部为阻塞网络调用，必须在后台线程调用（UI 层用 Thread/IO 调度）。
 */
object UpdateChecker {

    private const val REPO = "Yizhou147/FrostHush"
    private const val USER_AGENT = "FrostHush-App"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /** 镜像站：[prefix] 拼在完整 GitHub URL 前；[id] 持久化到设置；CUSTOM 的前缀运行时由设置提供 */
    enum class UpdateMirror(val id: String, val labelRes: Int, val prefix: String) {
        GHPROXY("gh-proxy", R.string.mirror_gh_proxy, "https://gh-proxy.com/"),
        DIRECT("direct", R.string.mirror_direct, ""),
        GHFAST("ghfast", R.string.mirror_ghfast, "https://ghfast.top/"),
        MOEYY("moeyy", R.string.mirror_moeyy, "https://github.moeyy.xyz/"),
        CUSTOM("custom", R.string.mirror_custom, ""),
        ;

        companion object {
            fun fromId(id: String): UpdateMirror = entries.firstOrNull { it.id == id } ?: GHPROXY
        }

        /** 解析实际前缀：CUSTOM 用设置里的自定义前缀（规范化：补 https:// 前缀与结尾 /）；未配置时返回 null（跳过该镜像） */
        fun resolvePrefix(customMirror: String): String? {
            if (this != CUSTOM) return prefix
            var p = customMirror.trim()
            if (p.isEmpty()) return null
            if (!p.startsWith("http://") && !p.startsWith("https://")) p = "https://$p"
            if (!p.endsWith("/")) p += "/"
            return p
        }
    }

    data class Release(
        val tagName: String,
        val name: String,
        val body: String,
        /** Release 页面地址（浏览器打开） */
        val htmlUrl: String,
        /** APK 资产直链（已加镜像前缀，国内可下载；无资产时为 null） */
        val apkDownloadUrl: String?,
        /** 经哪个镜像检查成功（供界面显示） */
        val via: UpdateMirror,
    )

    sealed interface CheckResult {
        data class UpToDate(val currentVersion: String, val via: UpdateMirror?) : CheckResult
        data class Available(val release: Release) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    /** 最近一次检查结果（自动检查为内存态：进程被杀即丢，手动检查总会刷新） */
    @Volatile
    var lastResult: CheckResult? = null

    fun check(currentVersionName: String, preferredMirror: UpdateMirror, customMirror: String = ""): CheckResult {
        val ordered = listOf(preferredMirror) + UpdateMirror.entries.filter { it != preferredMirror }
        val errors = StringBuilder()

        // ① API（镜像前缀 + api.github.com），按顺序尝试；错误详情收集进 errors 供失败时展示/诊断
        for (mirror in ordered) {
            val prefix = mirror.resolvePrefix(customMirror) ?: continue
            val url = prefix + "https://api.github.com/repos/$REPO/releases/latest"
            val (json, err) = fetchApiJson(url)
            if (json != null) return judge(json, currentVersionName, mirror, prefix)
            if (err != null) errors.appendLine("[${mirror.name}] $err")
        }

        // ② 302 重定向解析兜底（API 全挂时）：releases/latest → /releases/tag/vX.Y.Z
        for (mirror in ordered) {
            val prefix = mirror.resolvePrefix(customMirror) ?: continue
            val tag = fetchRedirectTag(prefix + "https://github.com/$REPO/releases/latest")
            if (tag != null) {
                val latest = tag.removePrefix("v")
                return if (isNewer(latest, currentVersionName)) {
                    CheckResult.Available(
                        Release(
                            tagName = tag,
                            name = "",
                            body = "",
                            htmlUrl = "https://github.com/$REPO/releases/tag/$tag",
                            // 下载直链同样用解析后的前缀（CUSTOM 镜像的前缀在设置里，mirror.prefix 为空）
                            apkDownloadUrl = prefix +
                                "https://github.com/$REPO/releases/download/$tag/FrostHush-$latest.apk",
                            via = mirror,
                        )
                    )
                } else {
                    CheckResult.UpToDate(currentVersionName, mirror)
                }
            }
        }
        return CheckResult.Failed(errors.toString().ifBlank { "all endpoints unreachable" })
    }

    private fun judge(
        json: JSONObject,
        currentVersionName: String,
        mirror: UpdateMirror,
        prefix: String,
    ): CheckResult {
        val tag = json.optString("tag_name").removePrefix("v")
        val assets = json.optJSONArray("assets")
        var apkUrl: String? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                if (name.startsWith("FrostHush-") && name.endsWith(".apk")) {
                    // 资产直链加镜像前缀，国内浏览器可直接下载（CUSTOM 用解析后的前缀）
                    apkUrl = prefix + url
                    break
                }
            }
        }
        val release = Release(
            tagName = json.optString("tag_name"),
            name = json.optString("name"),
            body = json.optString("body"),
            htmlUrl = json.optString("html_url"),
            apkDownloadUrl = apkUrl,
            via = mirror,
        )
        return if (isNewer(tag, currentVersionName)) {
            CheckResult.Available(release)
        } else {
            CheckResult.UpToDate(currentVersionName, mirror)
        }
    }

    /** 版本号比较：按 '.' 分段数值比较（如 1.10.0 > 1.9.2）；解析失败按字符串比较 */
    internal fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.trim().toIntOrNull() }
        val c = current.split('.').map { it.trim().toIntOrNull() }
        // 任一段非数字（预发布 tag、脏数据、空 tag_name）：保守按"无更新"处理，
        // 避免字符串不等就误报有更新（如 rc 后缀、缺失 tag_name 的镜像响应）
        if (r.any { it == null } || c.any { it == null }) return false
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrNull(i) ?: 0
            val cv = c.getOrNull(i) ?: 0
            if (rv != cv) return rv > cv
        }
        return false
    }

    /** @return (解析结果, 失败原因；成功时为 null) */
    private fun fetchApiJson(url: String): Pair<JSONObject?, String?> = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            when (conn.responseCode) {
                200 -> {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    try {
                        JSONObject(text) to null
                    } catch (e: Exception) {
                        null to "JSON parse failed: ${e.message}"
                    }
                }
                else -> null to "HTTP ${conn.responseCode}"
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null to "${e.javaClass.simpleName}: ${e.message}"
    }

    /** 解析 releases/latest 的 302 Location（…/releases/tag/vX.Y.Z）取 tag */
    private fun fetchRedirectTag(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val location = conn.getHeaderField("Location")
            val tag = location?.substringAfter("/releases/tag/", "")?.takeIf { it.isNotBlank() }
            tag?.let { URLDecoder.decode(it.substringBefore('?'), "UTF-8") }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}
