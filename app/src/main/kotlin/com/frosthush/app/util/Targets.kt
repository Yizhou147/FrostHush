package com.frosthush.app.util

import android.os.Build

/** 各 Android 版本 API 级别快捷判断 */
object Targets {
    val N get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    val P get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val Q get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val U get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
