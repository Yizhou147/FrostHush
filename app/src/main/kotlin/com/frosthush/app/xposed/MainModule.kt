package com.frosthush.app.xposed

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.frosthush.app.BuildConfig
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.Collections
import java.util.WeakHashMap

/**
 * HyperOS 超级岛焦点通知白名单解锁（霜息内置，LSPosed 现代 Xposed API 101 / libxposed）。
 *
 * HyperOS 对"焦点通知/超级岛"有白名单、签名检查与 xms 焦点授权限制，普通应用即使
 * 按官方规范在通知中注入 miui.focus.param 也不会被放行。本模块**仅对霜息自身包名
 * 放行**（其他应用行为不变），使霜息的专注通知以超级岛形态展示：
 * - SystemUI 作用域：绕过白名单（canShowFocus/canCustomFocus）与签名检查
 *   （SignatureChecker.checkSignatures），仅霜息包名返回 true；
 * - XMSF 作用域：绕过 xms 焦点授权（AuthSession 失败回调强制成功）。
 *   （XMSF 端无法按包名区分，全局绕过；但 SystemUI 端已按包名严格过滤，
 *   最终只有霜息能上岛，其他应用仍被白名单/签名检查拦截。）
 *
 * 实现参考 focus-unlock-module（GPL-3.0，其参考 Hail/HyperIsland）。
 * 入口由 META-INF/xposed/java_init.list 声明，作用域见 META-INF/xposed/scope.list
 * （com.android.systemui + com.xiaomi.xmsf）。
 * 前提：LSPosed 中启用霜息，作用域勾选「系统界面」与「XMSF」。
 */
@SuppressLint("PrivateApi", "BlockedPrivateApi")
class MainModule : XposedModule() {

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        when (param.packageName) {
            PACKAGE_SYSTEMUI -> hookSystemUi(param)
            PACKAGE_XMSF -> hookXmsf(param)
        }
    }

    // ---------------- SystemUI：白名单 + 签名检查（仅霜息放行） ----------------

    private fun hookSystemUi(param: XposedModuleInterface.PackageReadyParam) {
        // 主 APK 直接 hook（旧系统或新类名在主 APK 的情况）
        hookFocusWhitelist(param, param.classLoader)
        // 新版 HyperOS 的白名单/签名类在插件（MIUISystemUIPlugin）里，
        // 通过 PluginInstance$PluginFactory.createPluginContext 拿插件 classloader 后再 hook
        hookPluginClassLoader(param, param.classLoader)
    }

    private fun hookPluginClassLoader(param: XposedModuleInterface.PackageReadyParam, classLoader: ClassLoader) {
        try {
            val factoryClass = classLoader.loadClass(PLUGIN_FACTORY_CLASS)
            val methods = factoryClass.declaredMethods.filter { it.name == "createPluginContext" }
            if (methods.isEmpty()) {
                log(Log.INFO, TAG, "createPluginContext not found in $PLUGIN_FACTORY_CLASS")
                return
            }
            methods.forEach { method ->
                hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any {
                        val result = chain.proceed()
                        val pluginClassLoader = (result as? Context)?.classLoader
                        if (pluginClassLoader != null) {
                            hookFocusWhitelist(param, pluginClassLoader)
                        }
                        return result
                    }
                })
            }
            log(Log.INFO, TAG, "waiting for focus notification plugin class loader")
        } catch (t: Throwable) {
            log(Log.INFO, TAG, "failed to hook plugin class loader: ${t.message}")
        }
    }

    /** 尝试 hook 白名单方法与签名检查（仅霜息放行）；成功返回 true */
    private fun hookFocusWhitelist(param: XposedModuleInterface.PackageReadyParam, classLoader: ClassLoader): Boolean {
        if (hookedClassLoaders.contains(classLoader)) return true
        var hooked = false
        // 白名单：canShowFocus / canCustomFocus（任意签名，返回 boolean 即 hook）。
        // 类名随 HyperOS 版本迁移：旧版 miui.systemui（插件），新版 com.miui.systemui（主 APK）。
        for (className in WHITELIST_CLASSES) {
            try {
                val clazz = classLoader.loadClass(className)
                clazz.declaredMethods
                    .filter {
                        (it.name == "canShowFocus" || it.name == "canCustomFocus") &&
                            it.returnType == Boolean::class.javaPrimitiveType
                    }
                    .forEach { method ->
                        hook(method).intercept(FrostHushOnlyHooker())
                        log(Log.INFO, TAG, "hooked ${method.name}(${method.parameterTypes.joinToString { it.simpleName }}) in $className")
                        hooked = true
                    }
            } catch (_: ClassNotFoundException) {
                // 该 classloader 下无此类（可能在插件里，等插件注入；或旧/新系统二选一）
            } catch (t: Throwable) {
                log(Log.INFO, TAG, "failed to hook focus whitelist: ${t.message}")
            }
        }
        // 签名检查：SignatureChecker.checkSignatures
        try {
            val clazz = classLoader.loadClass(SIGNATURE_CHECKER_CLASS)
            clazz.declaredMethods
                .filter { it.name == "checkSignatures" && it.returnType == Boolean::class.javaPrimitiveType }
                .forEach { method ->
                    hook(method).intercept(FrostHushOnlyHooker())
                    log(Log.INFO, TAG, "hooked SignatureChecker.${method.name}")
                    hooked = true
                }
        } catch (_: ClassNotFoundException) {
            // 仅新版焦点通知插件存在
        } catch (t: Throwable) {
            log(Log.INFO, TAG, "failed to hook focus signature checker: ${t.message}")
        }
        if (hooked) hookedClassLoaders.add(classLoader)
        return hooked
    }

    // ---------------- XMSF：焦点授权绕过 ----------------

    private fun hookXmsf(param: XposedModuleInterface.PackageReadyParam) {
        try {
            val authSessionClass = param.classLoader.loadClass(AUTH_SESSION_CLASS)
            val targetMethod = authSessionClass.declaredMethods
                .filter { it.name == "b" && it.parameterCount == 1 }
                .firstOrNull()
            if (targetMethod == null) {
                log(Log.INFO, TAG, "method 'b(error)' not found in $AUTH_SESSION_CLASS")
                return
            }
            hook(targetMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any {
                    val error = chain.args[0]
                    if (error == null) return chain.proceed()
                    return try {
                        val originalCode = getIntField(error, "a")
                        Log.i(TAG, "auth error intercepted, original errorCode=$originalCode, forcing to 0")
                        setField(error, "a", 0)
                        val successResult = callNoArgMethod(chain.thisObject, "h")
                        Log.i(TAG, "auth bypassed successfully")
                        successResult ?: true // skip original
                    } catch (t: Throwable) {
                        Log.e(TAG, "bypass failed: ${t.message}")
                        chain.proceed()
                    }
                }
            })
            log(Log.INFO, TAG, "hooked AuthSession.b(error)")
        } catch (t: Throwable) {
            log(Log.INFO, TAG, "failed to hook $AUTH_SESSION_CLASS: ${t.message}")
        }
    }

    private fun getIntField(instance: Any, fieldName: String): Int {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                return (f.get(instance) as? Int) ?: 0
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return 0
    }

    private fun setField(instance: Any, fieldName: String, value: Any?) {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(instance, value)
                return
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
    }

    private fun callNoArgMethod(instance: Any, methodName: String): Any? {
        var c: Class<*>? = instance.javaClass
        while (c != null) {
            try {
                val m = c.getDeclaredMethod(methodName)
                m.isAccessible = true
                return m.invoke(instance)
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        return null
    }

    /**
     * 拦截器：仅当被查询的包名是霜息自身时才返回 true，否则放行原始逻辑。
     * 统一取参数中第一个 String 作为包名（canShowFocus 在 index 1、canCustomFocus 在 index 0、
     * checkSignatures 在 index 1）。
     */
    private class FrostHushOnlyHooker : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any {
            val args = chain.args
            val pkg = args.firstOrNull { it is String }
            Log.i(TAG, "intercept args=" + args.joinToString { it?.toString() ?: "null" })
            return if (pkg == BuildConfig.APPLICATION_ID) true else chain.proceed()
        }
    }

    private companion object {
        const val TAG = "FrostHushXposed"
        const val PACKAGE_SYSTEMUI = "com.android.systemui"
        const val PACKAGE_XMSF = "com.xiaomi.xmsf"
        val WHITELIST_CLASSES = arrayOf(
            "miui.systemui.notification.NotificationSettingsManager",
            "com.miui.systemui.notification.NotificationSettingsManager",
        )
        const val SIGNATURE_CHECKER_CLASS = "miui.systemui.notification.focus.SignatureChecker"
        const val PLUGIN_FACTORY_CLASS =
            "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory"
        const val AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession"

        val hookedClassLoaders: MutableSet<ClassLoader> = Collections.synchronizedSet(
            Collections.newSetFromMap(WeakHashMap<ClassLoader, Boolean>())
        )
    }
}
