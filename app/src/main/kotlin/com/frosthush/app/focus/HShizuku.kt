package com.frosthush.app.focus

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import androidx.annotation.RequiresApi
import com.frosthush.app.BuildConfig
import com.frosthush.app.FrostHushApp.Companion.app
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.util.DebugLog
import com.frosthush.app.util.Targets
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku 系统接口封装（复用 Hail 的 HShizuku 实现）：
 * 专注模式通过 IPackageManager.setPackagesSuspendedAsUser 暂停应用，
 * 暂停弹窗文案定制为「xx已暂停，请保持专注。」（SuspendDialogInfo.setMessage）。
 * 支持应用分身：userId 参数指定目标用户空间（分身如小米 user 999），默认当前用户。
 */
object HShizuku {
    val isRoot get() = Shizuku.getUid() == 0
    private val callerPackage get() = if (isRoot) BuildConfig.APPLICATION_ID else "com.android.shell"
    private val myUserId get() = Process.myUserHandle().hashCode()

    private fun asInterface(className: String, original: IBinder): Any {
        val stub = Class.forName("$className\$Stub")
        return if (Targets.P) HiddenApiBypass.invoke(stub, null, "asInterface", ShizukuBinderWrapper(original))
        else stub.getMethod("asInterface", IBinder::class.java).invoke(null, ShizukuBinderWrapper(original))
    }

    private fun asInterface(className: String, serviceName: String): Any =
        asInterface(className, SystemServiceHelper.getSystemService(serviceName))

    /** 强制停止应用（暂停前调用，使暂停立即生效）；userId 指定目标用户（分身） */
    fun forceStopApp(packageName: String, userId: Int = myUserId): Boolean = runCatching {
        val am = asInterface("android.app.IActivityManager", Context.ACTIVITY_SERVICE)
        if (Targets.P) HiddenApiBypass.invoke(
            am::class.java, am, "forceStopPackage", packageName, userId
        ) else am::class.java.getMethod(
            "forceStopPackage", String::class.java, Int::class.java
        ).invoke(am, packageName, userId)
        true
    }.getOrElse { false }

    /** 专注模式专用暂停：系统弹窗文案为「xx已暂停，请保持专注。」且不显示"取消暂停应用"按钮 */
    fun setAppSuspendedForFocus(packageName: String, suspended: Boolean, userId: Int = myUserId): Boolean {
        // 硬防御：绝不允许暂停自身（黑名单历史数据/剪贴板导入可能误带本应用）
        if (packageName == BuildConfig.APPLICATION_ID) return false
        if (getApplicationInfoOrNull(packageName, userId) == null) {
            // 记录具体原因：应用不存在 vs 跨用户查询被系统拒绝（区别于权限拒绝）
            DebugLog.d("Suspend", "getApplicationInfo 为 null pkg=$packageName user=$userId suspended=$suspended")
            return false
        }
        if (Targets.P) setAppRestricted(packageName, userId, suspended)
        if (suspended) forceStopApp(packageName, userId)
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
                        myUserId, /*suspendingUserId*/
                        userId      /*targetUserId*/
                    )
                }.getOrElse {
                    if (it is NoSuchMethodException) setPackagesSuspendedAsUserSinceQ(pm, packageName, suspended, userId)
                    else throw it
                }

                // Android 10-13：带 SuspendDialogInfo 的 7 参数版本
                Targets.Q -> runCatching {
                    setPackagesSuspendedAsUserSinceQ(pm, packageName, suspended, userId)
                }.getOrElse {
                    if (it is NoSuchMethodException) setPackagesSuspendedAsUserSinceP(pm, packageName, suspended, userId)
                    else throw it
                }

                // Android 9：带 dialogMessage（CharSequence）的 7 参数版本
                Targets.P -> setPackagesSuspendedAsUserSinceP(pm, packageName, suspended, userId)

                // Android 7-8：3 参数版本（无自定义弹窗）
                Targets.N -> pm::class.java.getMethod(
                    "setPackagesSuspendedAsUser", Array<String>::class.java, Boolean::class.java, Int::class.java
                ).invoke(pm, arrayOf(packageName), suspended, userId)

                else -> return false // Android 6 及以下不支持暂停
            } as Array<*>).isEmpty()
        }.getOrElse {
            DebugLog.e("Suspend", "setAppSuspendedForFocus 失败 pkg=$packageName user=$userId suspended=$suspended", it)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setPackagesSuspendedAsUserSinceQ(pm: Any, packageName: String, suspended: Boolean, userId: Int): Any =
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
            userId
        )

    @RequiresApi(Build.VERSION_CODES.P)
    private fun setPackagesSuspendedAsUserSinceP(pm: Any, packageName: String, suspended: Boolean, userId: Int): Any =
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
            userId
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
    fun setAppRestricted(packageName: String, userId: Int = myUserId, restricted: Boolean): Boolean = runCatching {
        val appops = asInterface("com.android.internal.app.IAppOpsService", Context.APP_OPS_SERVICE)
        HiddenApiBypass.invoke(
            appops::class.java,
            appops,
            "setMode",
            HiddenApiBypass.invoke(AppOpsManager::class.java, null, "strOpToOp", "android:run_any_in_background"),
            packageUid(packageName, userId),
            packageName,
            if (restricted) AppOpsManager.MODE_IGNORED else AppOpsManager.MODE_ALLOWED
        )
        true
    }.getOrElse { false }

    /** 通过 Shizuku 服务端（shell 权限）执行命令，返回 stdout；失败返回 null */
    fun execute(command: String): String? = runCatching {
        val service: IShizukuService = IShizukuService.Stub.asInterface(Shizuku.getBinder()) ?: return null
        val process = service.newProcess(arrayOf("sh", "-c", command), null, null) ?: return null
        val pfd = process.inputStream ?: return null
        ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }.getOrElse {
        android.util.Log.e("FrostHush", "Shizuku 执行命令失败: $command", it)
        null
    }

    /**
     * 冻结/暂停指定用户的应用（专注入口）。
     * 优先 setPackagesSuspendedAsUser（体验好：系统「已暂停」弹窗 + 定制文案）；
     * 失败时按设置「强制冻结」模式（SettingsStore.suspendFallbackMode）回退
     * `pm disable-user --user <id>`：仅分身或所有用户应用（禁用更彻底，但桌面图标会暂时消失）。
     */
    fun freezeForFocus(packageName: String, userId: Int = myUserId): Boolean {
        if (setAppSuspendedForFocus(packageName, true, userId)) {
            DebugLog.d("Suspend", "freeze suspend 成功 pkg=$packageName user=$userId")
            return true
        }
        val mode = SettingsStore.cache.suspendFallbackMode
        val isClone = userId != myUserId
        val allowed = when (mode) {
            SettingsStore.FALLBACK_ALL -> true
            SettingsStore.FALLBACK_CLONE_ONLY -> isClone
            else -> false
        }
        if (!allowed) {
            DebugLog.d("Suspend", "freeze suspend 失败且兜底未启用 pkg=$packageName user=$userId mode=$mode")
            return false
        }
        DebugLog.d("Suspend", "freeze suspend 失败，回退 disable-user pkg=$packageName user=$userId mode=$mode")
        return setAppDisabledForUser(packageName, true, userId)
    }

    /**
     * 解除冻结/恢复（专注结束/休息段）。
     * 始终先 unsuspend；是否 enable 由「强制冻结」模式决定（避免误启用用户手动禁用的应用）。
     */
    fun restoreForFocus(packageName: String, userId: Int = myUserId): Boolean {
        val a = setAppSuspendedForFocus(packageName, false, userId)
        val mode = SettingsStore.cache.suspendFallbackMode
        val isClone = userId != myUserId
        val enableAllowed = when (mode) {
            SettingsStore.FALLBACK_ALL -> true
            SettingsStore.FALLBACK_CLONE_ONLY -> isClone
            else -> false
        }
        val b = if (enableAllowed) setAppDisabledForUser(packageName, false, userId) else false
        DebugLog.d("Suspend", "restore suspend=$a enable=$b pkg=$packageName user=$userId mode=$mode")
        return a || b
    }

    /**
     * 禁用/启用指定用户的应用（`pm disable-user` / `pm enable` 命令，走 Shizuku shell 通道）。
     * disabled 后该用户的应用进程被杀且无法启动，效果等同冻结；enable 完整恢复。
     * 跨用户不需要 suspend 权限（实测 HyperOS 2 分身用户可用）。
     */
    fun setAppDisabledForUser(packageName: String, disabled: Boolean, userId: Int = myUserId): Boolean = runCatching {
        val cmd = if (disabled) "pm disable-user --user $userId $packageName"
                  else "pm enable --user $userId $packageName"
        val out = execute(cmd)
        // 成功输出 "Package xxx new state: disabled-user/enabled"；失败输出 Exception/Error
        out != null && out.contains("new state:")
    }.getOrDefault(false)

    /**
     * 指定用户已安装包名列表（分身/XSpace 等用户空间），返回 (包名, 是否系统分区路径)。
     * 执行 `pm list packages --user <id> -f`（shell 有跨用户权限）；-f 带 APK 路径，
     * 系统应用在 /system|/product|/vendor|/system_ext 等分区，用户应用在 /data/app。
     * 注：HyperOS 的 IPackageManager AIDL 接口被精简，无法走 binder 反射，只能靠 shell 命令。
     */
    fun listPackagesForUser(userId: Int): List<Pair<String, Boolean>> = runCatching {
        val out = execute("pm list packages --user $userId -f") ?: return emptyList()
        out.lineSequence().mapNotNull { line ->
            if (!line.startsWith("package:")) return@mapNotNull null
            val body = line.removePrefix("package:")
            val eq = body.lastIndexOf('=')
            if (eq <= 0) return@mapNotNull null
            val path = body.substring(0, eq)
            val pkg = body.substring(eq + 1)
            if (pkg.isBlank()) return@mapNotNull null
            val sysByPath = path.startsWith("/system/") || path.startsWith("/system_ext/") ||
                path.startsWith("/product/") || path.startsWith("/vendor/") || path.startsWith("/odm/")
            pkg to sysByPath
        }.toList()
    }.getOrElse { emptyList() }

    /**
     * 构造 UserHandle：本地 android.jar 为裁剪版，缺 UserHandle(int) 构造器与 of()，
     * 运行时系统均支持（API 17+），用反射构造。
     */
    private fun userHandleOf(userId: Int): Any =
        UserHandle::class.java.getConstructor(Int::class.java).newInstance(userId)

    /** 指定用户的包 uid；分身（其他用户）用反射调 getPackageUid(String, int, UserHandle)（API 24+） */
    fun packageUid(packageName: String, userId: Int = myUserId): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (userId == myUserId) {
                return app.packageManager.getPackageUid(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
                )
            }
        } else if (userId == myUserId) {
            @Suppress("DEPRECATION")
            return app.packageManager.getPackageUid(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }
        @Suppress("DEPRECATION")
        return PackageManager::class.java
            .getMethod("getPackageUid", String::class.java, Int::class.java, UserHandle::class.java)
            .invoke(app.packageManager, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES, userHandleOf(userId)) as Int
    }

    /**
     * 指定用户的应用信息；分身（其他用户）用反射调 getApplicationInfoAsUser(String, int, UserHandle)（API 24+）。
     * 本地 android.jar 裁剪缺失这些重载，运行时系统均支持。
     */
    fun getApplicationInfoOrNull(packageName: String, userId: Int = myUserId): android.content.pm.ApplicationInfo? = runCatching {
        if (userId == myUserId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.packageManager.getApplicationInfo(
                    packageName, PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                app.packageManager.getApplicationInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
            }
        } else {
            @Suppress("DEPRECATION")
            PackageManager::class.java
                .getMethod("getApplicationInfoAsUser", String::class.java, Int::class.java, UserHandle::class.java)
                .invoke(app.packageManager, packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES, userHandleOf(userId)) as android.content.pm.ApplicationInfo
        }
    }.getOrNull()
}
