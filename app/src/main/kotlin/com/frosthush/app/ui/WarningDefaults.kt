package com.frosthush.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.frosthush.app.ui.theme.isInDarkTheme

/** 深红警告配色（对齐 KernelSU WarningCard）：miuix `errorContainer` 偏浅，警告观感不够醒目 */
internal object WarningDefaults {
    @Composable
    fun containerColor(): Color = if (isInDarkTheme()) Color(0xFF310808) else Color(0xFFF8E2E2)

    @Composable
    fun contentColor(): Color = Color(0xFFF72727)
}
