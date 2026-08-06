package com.frosthush.app.util

import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import com.frosthush.app.R
import org.json.JSONObject

/**
 * 小米 HyperOS 超级岛 / 焦点通知接入（参考 Hail 的 MiuiIsland 实现）。
 *
 * 通知以普通方式构建，在 extras 中写入 "miui.focus.param"（岛通知 JSON），
 * 支持岛通知的设备上会以超级岛形态展示，否则退化为普通通知。
 *
 * 注意：HyperOS 对焦点通知有白名单限制，应用需在系统侧获得"实时活动/焦点通知"
 * 权限（hasFocusPermission() 可检测），否则即使注入参数也只显示普通通知。
 */
object MiuiIsland {
    private const val KEY_PARAM = "miui.focus.param"
    private const val KEY_PICS = "miui.focus.pics"
    private const val PIC_SECOND = "miui.focus.pic_second"

    /**
     * 构建岛通知扩展参数（对齐官方 HyperOS 3 param_v2 结构）。
     *
     * @param frontTitle 前置文案（如"专注模式中"）
     * @param remainingText 倒计时正文（如"25:00"）
     * @param contentText 通知内容文案（如"剩余 25:00"）
     */
    fun buildIslandExtras(
        context: Context, frontTitle: String, remainingText: String, contentText: String
    ): Bundle {
        val param = JSONObject().put(
            "param_v2", JSONObject()
                .put("protocol", 1)
                .put("business", "frosthush_focus")
                // 通知更新时是否自动展开为展开态（默认 false，不打扰）
                .put("enableFloat", false)
                // 允许后续更新同一条岛通知
                .put("updatable", true)
                // 首次出现即展示展开态
                .put("islandFirstFloat", true)
                // 状态栏 / 息屏展示文案
                .put("ticker", contentText)
                .put("aodTitle", contentText)
                .put(
                    "param_island", JSONObject()
                        .put("islandProperty", 1)
                        .put(
                            "bigIslandArea", JSONObject()
                                // 大岛 A 区（左侧）：仅秒表图标
                                .put(
                                    "imageTextInfoLeft", JSONObject()
                                        .put("type", 1)
                                        .put(
                                            "picInfo", JSONObject()
                                                .put("type", 1)
                                                .put("pic", PIC_SECOND)
                                        )
                                )
                                // 大岛 B 区（右侧）：仅倒计时文本
                                .apply {
                                    val right = JSONObject()
                                        .put("frontTitle", "")
                                        .put("title", remainingText)
                                        .put("content", "")
                                        .put("useHighLight", false)
                                        // 深浅模式两套文字颜色：岛展开卡片背景固定深色，需用白色文字
                                        .put("colorTitle", "#FF000000")
                                        .put("colorTitleDark", "#FFFFFFFF")
                                        .put("colorContent", "#FF000000")
                                        .put("colorContentDark", "#FFFFFFFF")
                                        .put("colorSubContent", "#FF000000")
                                        .put("colorSubContentDark", "#FFFFFFFF")
                                    // 官方文档文本组件 key 为 "miui.focus.paramtextInfo"（疑似笔误），
                                    // 实际系统版本可能识别无前缀 key，多 key 兜底确保文本渲染。
                                    put("miui.focus.paramtextInfo", right)
                                    put("paramtextInfo", right)
                                    put("textInfo", right)
                                }
                        )
                        // 小岛：秒表图标
                        .put(
                            "smallIslandArea", JSONObject()
                                .put(
                                    "picInfo", JSONObject()
                                        .put("type", 1)
                                        .put("pic", PIC_SECOND)
                                )
                        )
                )
                .put(
                    "baseInfo", JSONObject()
                        .put("type", 2)
                        .put("title", frontTitle)
                        // content 置空：避免与 hintInfo.title 的倒计时在通知卡片上重复显示两行
                        .put("content", "")
                        .put("colorTitle", "#FF000000")
                        .put("colorTitleDark", "#FFFFFFFF")
                )
                .put(
                    "hintInfo", JSONObject()
                        .put("type", 1)
                        .put("title", contentText)
                )
        )
        val bundle = Bundle()
        bundle.putString(KEY_PARAM, param.toString())
        val pics = Bundle()
        pics.putParcelable(
            PIC_SECOND, Icon.createWithResource(context, R.drawable.ic_stat_focus)
        )
        bundle.putBundle(KEY_PICS, pics)
        return bundle
    }

    /**
     * 查询当前应用是否开启了焦点通知/超级岛权限（官方查询接口，耗时操作）。
     * 非 HyperOS 或未授权时返回 false。
     */
    fun hasFocusPermission(context: Context): Boolean {
        return try {
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val extras = Bundle().apply { putString("package", context.packageName) }
            context.contentResolver.call(uri, "canShowFocus", null, extras)
                ?.getBoolean("canShowFocus", false) ?: false
        } catch (e: Exception) {
            false
        }
    }
}
