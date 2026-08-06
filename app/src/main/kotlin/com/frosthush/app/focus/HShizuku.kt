package com.frosthush.app.focus

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import androidx.annotation.RequiresApi
import com.frosthush.app.BuildConfig
import com.frosthush.app.FrostHushApp.Companion.app
import com.frosthush.app.R
import com.frosthush.app.util.Targets
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku 系统接口封装（复用 Hail 的 HShizuku 实现）：
 * 专注模式通过 IPackageManager.setPackagesSuspendedAsUser 暂停应用，
 * 暂停弹窗文案定制为「xx已暂停，请保持专注。」（SuspendDialogInfo.setMessage）。
 */
object HShizuku {
    val isRoot get() = Shizuku.getUid() == 0
    private val callerPackage get() = if (isRoot) BuildConfig.APPLICATION_ID else "com.android.shell"

    private fun asInterface(className: String, original: IBinder): Any {
        val stub = Class.forName("$className\$Stub")
        return if (Targets.P) HiddenApiBypass.invoke(stub, null, "asInterface", ShizukuBinderWrapper(original))
        else stub.getMethod("asInterface", IBinder::class.java).invoke(null, ShizukuBinderWrapper(original))
    }

    private fun asInterface(className: String, serviceName: String): Any =
        asInterface(className, SystemServiceHelper.getSystemService(serviceName))

    /** 强制停止应用（暂停前调用，使暂停立即生效） */
    fun forceStopApp(packageName: String): Boolean = runCatching {
        val am = asInterface("android.app.IActivityManager", Context.ACTIVITY_SERVICE)
        if (Targets.P) HiddenApiBypass.invoke(
            am::class.java, am, "forceStopPackage", packageName, Process.myUserHandle().hashCode()
        ) else am::class.java.getMethod(
            "forceStopPackage", String::class.java, Int::class.java
        ).invoke(am, packageName, Process.myUserHandle().hashCode())
        true
    }.getOrElse { false }

    /** 专注模式专用暂停：系统弹窗文案为「xx已暂停，请保持专注。」且不显示"取消暂停应用"按钮 */
    fun setAppSuspendedForFocus(packageName: String, suspended: Boolean): Boolean {
        if (getApplicationInfoOrNull(packageName) == null) return false
        if (Targets.P) setAppRestricted(packageName, suspended)
        if (suspended) forceStopApp(packageName)
        return runCatching {
            val pm = asInterface("android.content.pm.IPackageManager", "package")
            (when {
                // Android 14+：新增 flags / suspendingUserId / targetUserId 参数
                Targets.U -> runCatching {
                    HiddenApiBypass.invoke(
                        pm::class.java,
                        pm,
                        "setPackagesSuspendedAsUser",
                        arrayOf(packageName),
                        suspended,
                        null,
                        null,
                        if (suspended) focusSuspendDialogInfo else null,
                        0,
                        callerPackage,
                        Process.myUserHandle().hashCode(), /*suspendingUserId*/
                        Process.myUserHandle().hashCode()  /*targetUserId*/
                    )
                }.getOrElse {
                    if (it is NoSuchMethodException) setPackagesSuspendedAsUserSinceQ(pm, packageName, suspended)
                    else throw it
                }

                // Android 10-13：带 SuspendDialogInfo 的 7 参数版本
                Targets.Q -> runCatching {
                    setPackagesSuspendedAsUserSinceQ(pm, packageName, suspended)
                }.getOrElse {
                    if (it is NoSuchMethodException) setPackagesSuspendedAsUserSinceP(pm, packageName, suspended)
                    else throw it
                }

                // Android 9：带 dialogMessage（CharSequence）的 7 参数版本
                Targets.P -> setPackagesSuspendedAsUserSinceP(pm, packageName, suspended)

                // Android 7-8：3 参数版本（无自定义弹窗）
                Targets.N -> pm::class.java.getMethod(
                    "setPackagesSuspendedAsUser", Array<String>::class.java, Boolean::class.java, Int::class.java
                ).invoke(pm, arrayOf(packageName), suspended, Process.myUserHandle().hashCode())

                else -> return false // Android 6 及以下不支持暂停
            } as Array<*>).isEmpty()
        }.getOrElse { false }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setPackagesSuspendedAsUserSinceQ(pm: Any, packageName: String, suspended: Boolean): Any =
        HiddenApiBypass.invoke(
            pm::class.java,
            pm,
            "setPackagesSuspendedAsUser",
            arrayOf(packageName),
            suspended,
            null,
            null,
            if (suspended) focusSuspendDialogInfo else null,
            callerPackage,
            Process.myUserHandle().hashCode()
        )

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setPackagesSuspendedAsUserSinceP(pm: Any, packageName: String, suspended: Boolean): Any =
        HiddenApiBypass.invoke(
            pm::class.java,
            pm,
            "setPackagesSuspendedAsUser",
            arrayOf(packageName),
            suspended,
            null,
            null,
            null /*dialogMessage*/,
            callerPackage,
            Process.myUserHandle().hashCode()
        )

    /** 原版暂停弹窗信息：不显示"取消暂停应用"按钮，仅保留"确定" */
    private val suspendDialogInfo: Any
        @RequiresApi(Build.VERSION_CODES.Q) @SuppressLint("PrivateApi") get() = runCatching {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/SuspendDialogInfo;")
            val builderClass = Class.forName("android.content.pm.SuspendDialogInfo\$Builder")
            val builder = builderClass.getConstructor().newInstance()
            // BUTTON_ACTION_NONE = 0：不显示"取消暂停应用"按钮
            HiddenApiBypass.invoke(builderClass, builder, "setNeutralButtonAction", 0)
            HiddenApiBypass.invoke(builderClass, builder, "build")
        }.getOrThrow()

    /** 专注模式专用弹窗信息：定制文案（方法名为 setMessage）+ 无"取消暂停应用"按钮 */
    private val focusSuspendDialogInfo: Any
        @RequiresApi(Build.VERSION_CODES.Q) @SuppressLint("PrivateApi") get() {
            // SuspendDialogInfo 为 @SystemApi，加入隐藏 API 豁免（幂等）保证反射可见
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/SuspendDialogInfo;")
            return runCatching {
                val builderClass = Class.forName("android.content.pm.SuspendDialogInfo\$Builder")
                val builder = builderClass.getConstructor().newInstance()
                // 消息方法名是 setMessage(String)，%1$s 会被系统替换为应用名
                HiddenApiBypass.invoke(builderClass, builder, "setMessage", app.getString(R.string.focus_suspended_dialog))
                HiddenApiBypass.invoke(builderClass, builder, "setNeutralButtonAction", 0)
                HiddenApiBypass.invoke(builderClass, builder, "build")
            }.getOrElse { suspendDialogInfo }
        }

    @RequiresApi(Build.VERSION_CODES.P)
    fun setAppRestricted(packageName: String, restricted: Boolean): Boolean = runCatching {
        val appops = asInterface("com.android.internal.app.IAppOpsService", Context.APP_OPS_SERVICE)
        HiddenApiBypass.invoke(
            appops::class.java,
            appops,
            "setMode",
            HiddenApiBypass.invoke(AppOpsManager::class.java, null, "strOpToOp", "android:run_any_in_background"),
            packageUid(packageName),
            packageName,
            if (restricted) AppOpsManager.MODE_IGNORED else AppOpsManager.MODE_ALLOWED
        )
        true
    }.getOrElse { false }

    fun packageUid(packageName: String): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        app.packageManager.getPackageUid(
            packageName, PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        app.packageManager.getPackageUid(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
    }

    fun getApplicationInfoOrNull(packageName: String): android.content.pm.ApplicationInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.packageManager.getApplicationInfo(
                packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            app.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }
    }.getOrNull()
}
