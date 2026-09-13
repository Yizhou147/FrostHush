package com.frosthush.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 界面风格：`Miuix`（HyperOS 设计语言）/ `Material`（项目原有主题）。
 *
 * 迁移期说明：全量页面双 UI 化完成前，[SettingsStore][com.frosthush.app.data.SettingsStore]
 * 的默认值为 `material`，以保证中间态构建的观感与旧版一致；全部页面改造完成后再把默认值
 * 切换为 `miuix`（见实施计划 Phase 5）。
 */
enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String?): UiMode = when (value) {
            Miuix.value -> Miuix
            else -> Material
        }
    }
}

/** 当前界面风格 */
val LocalUiMode = staticCompositionLocalOf { UiMode.Material }

/** 顶栏 / 底栏模糊效果（仅在 API 33+ 真正生效） */
val LocalEnableBlur = staticCompositionLocalOf { true }

/** 悬浮底栏 */
val LocalEnableFloatingBottomBar = staticCompositionLocalOf { false }

/** 悬浮底栏液态玻璃效果 */
val LocalEnableFloatingBottomBarBlur = staticCompositionLocalOf { false }

/** 预测性返回手势 */
val LocalEnablePredictiveBack = staticCompositionLocalOf { false }
