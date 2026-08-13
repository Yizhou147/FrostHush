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
    /** 小岛/状态栏小图标引用：秒表图标（ic_stat_focus） */
    private const val PIC_SECOND = "miui.focus.pic_second"

    /**
     * 构建岛通知扩展参数（完整对齐番茄Todo 的 HyperOS 3 param_v2 结构，adb 逆向所得）。
     *
     * 关键点：
     * - enableFloat 恒 true：通知每次更新（阶段切换）时岛自动展开弹出（像普通通知一样滑入动画），
     *   番茄Todo 即此行为；配合"仅阶段切换时 notify"（不走秒），不会每秒弹出
     * - 卡片文案静态（ticker/aodTitle/chatInfo.title），倒计时由系统 timerInfo 原生渲染
     *   + 通知 chronometer 倒计时走秒，无需每秒 notify
     * - islandTimeout=3600：岛展开态超时（秒）
     * - turnAnim 省略（番茄Todo 无此字段，用系统默认翻页动画）
     *
     * @param title 状态标题（如"专注中"/"休息中"）
     * @param contentText 卡片文案（同样静态，与 title 一致即可）
     * @param endMillis 本阶段结束时间戳：岛倒计时由系统根据 timerInfo 原生渲染
     * @param timerSystemCurrent 计时锚点：阶段内固定不变
     */
    fun buildIslandExtras(
        context: Context, title: String, contentText: String,
        endMillis: Long?, timerSystemCurrent: Long?,
    ): Bundle {
        // 番茄结构（大岛倒计时正常）+ 图标槽位统一为秒表：
        // - 小岛图标跟随 imageTextInfoLeft（此前设 logo → 小岛变 logo；设秒表 → 小岛秒表）
        // - 大岛 = 秒表小图标 + sameWidthDigitInfo 原生倒计时 + chatInfo 标题（无 baseInfo/hintInfo，
        //   否则会渲染两行重复标题并挤掉倒计时区）
        // - endMillis/timerSystemCurrent 为 null → 无倒计时（适合结束/结果类一次性通知）
        // - 滑入：enableFloat=true + FocusService 阶段切换换新 ID 重新发布
        val bigIslandArea = JSONObject()
            .put(
                "imageTextInfoLeft", JSONObject()
                    .put("type", 1)
                    .put(
                        "picInfo", JSONObject()
                            .put("type", 1)
                            .put("pic", PIC_SECOND)
                    )
            )
        val chatInfo = JSONObject().put("title", title)
        if (endMillis != null && timerSystemCurrent != null) {
            // 大岛右侧：等宽数字倒计时（系统原生渲染）
            bigIslandArea.put(
                "sameWidthDigitInfo", JSONObject()
                    .put(
                        "timerInfo", JSONObject()
                            .put("timerType", -1)
                            .put("timerWhen", endMillis)
                            .put("timerSystemCurrent", timerSystemCurrent)
                    )
                    .put("showHighlightColor", false)
            )
            chatInfo.put(
                "timerInfo", JSONObject()
                    .put("timerType", -1)
                    .put("timerWhen", endMillis)
                    .put("timerSystemCurrent", timerSystemCurrent)
            )
        }
        val param = JSONObject().put(
            "param_v2", JSONObject()
                .put("protocol", 1)
                .put("business", "frosthush_focus")
                // 通知更新时自动展开（像普通通知一样滑入弹出）
                .put("enableFloat", true)
                .put("updatable", true)
                .put("islandFirstFloat", true)
                .put("ticker", contentText)
                .put("tickerPic", PIC_SECOND)
                .put("aodTitle", contentText)
                .put("aodPic", PIC_SECOND)
                .put(
                    "param_island", JSONObject()
                        .put("islandProperty", 1)
                        .put("islandTimeout", 3600)
                        .put("bigIslandArea", bigIslandArea)
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
                // 不设 picProfile：大岛标题纯文字，无右下角小图标
                .put("chatInfo", chatInfo)
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
