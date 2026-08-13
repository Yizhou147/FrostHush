package com.frosthush.app.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.frosthush.app.BuildConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * HyperOS 超级岛焦点通知白名单解锁（霜息内置，LSPosed 现代 Xposed API 101 / libxposed）。
 *
 * HyperOS 对"焦点通知/超级岛"有应用白名单限制，普通应用即使按官方规范在
 * 通知中注入 miui.focus.param 也不会被放行。本模块在 SystemUI 进程中把
 * NotificationSettingsManager.canShowFocus / canCustomFocus 的返回值替换为 true，
 * **仅对霜息自身包名放行**（其他应用行为不变），使霜息的专注通知以超级岛形态展示。
 *
 * 实现参考 focus-unlock-module（GPL-3.0，其参考 Hail/HyperIsland）。
 * 入口由 META-INF/xposed/java_init.list 声明，作用域见 META-INF/xposed/scope.list。
 * 前提：LSPosed 中启用霜息，作用域勾选「系统界面 (com.android.systemui)」。
 */
@SuppressLint("PrivateApi", "BlockedPrivateApi")
class MainModule : XposedModule() {

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        // 仅在 SystemUI 进程生效
        if (param.packageName != PACKAGE_SYSTEMUI) return
        try {
            val clazz = param.classLoader.loadClass(TARGET_CLASS)
            // canShowFocus(Context, String)：包名是否在「展示」白名单内
            try {
                val method = clazz.getMethod("canShowFocus", Context::class.java, String::class.java)
                hook(method).intercept(FrostHushOnlyHooker())
                log(Log.INFO, TAG, "hooked canShowFocus")
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "hook canShowFocus failed", t)
            }
            // canCustomFocus(String)：包名是否在「自定义内容」白名单内
            try {
                val method = clazz.getMethod("canCustomFocus", String::class.java)
                hook(method).intercept(FrostHushOnlyHooker())
                log(Log.INFO, TAG, "hooked canCustomFocus")
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "hook canCustomFocus failed", t)
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "load $TARGET_CLASS failed", t)
        }
    }

    /**
     * 拦截器：仅当被查询的包名是霜息自身时才返回 true，否则放行原始逻辑。
     * canShowFocus 的包名参数在 index 1，canCustomFocus 在 index 0，按参数个数区分。
     */
    private class FrostHushOnlyHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any {
            val args = chain.args
            val packageName = (if (args.size >= 2) args[1] else args[0]) as String
            return if (packageName == BuildConfig.APPLICATION_ID) true else chain.proceed()
        }
    }

    private companion object {
        const val TAG = "FrostHushXposed"
        const val PACKAGE_SYSTEMUI = "com.android.systemui"
        const val TARGET_CLASS = "miui.systemui.notification.NotificationSettingsManager"
    }
}
