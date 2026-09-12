import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 内置 Kotlin 支持，无需再应用 kotlin-android（版本见根 build.gradle.kts）
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.frosthush.app"
    compileSdk = 37
    // AGP 9.4 默认 build-tools 为 36.0.0；本机仅装了 36.1.0（ARM64 aapt2），显式钉住
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.frosthush.app"
        minSdk = 23
        targetSdk = 37
        versionCode = 8
        versionName = "1.2.2"
        // 编译时间（精确到分钟）：诊断日志导出头部 + 非正式版关于页展示
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        // 是否「正式版」：仅打正式发布包时带 -PisOfficialBuild=true。
        // 正式版关于页不显示编译时间，其余构建（含本地测试用 release 包）仍显示。
        val isOfficialBuild = (project.findProperty("isOfficialBuild") as String?)?.toBoolean() ?: false
        buildConfigField("boolean", "IS_OFFICIAL_BUILD", isOfficialBuild.toString())
    }

    buildTypes {
        debug {
            // 诊断日志为正式代码（DebugLog 不依赖 DEBUG 门控），测试直接用 release 构建
            // （正式包名 com.frosthush.app + 正式签名，可覆盖安装正式版）。
            // debug 构建同样开 R8 压缩，包体积与 release 一致。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 无签名 secrets 时自动回退 debug 签名，云编译开箱即用
            signingConfig = if (file("../signing.properties").exists()) {
                val props = Properties().apply { load(file("../signing.properties").reader()) }
                signingConfigs.create("release") {
                    storeFile = file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            } else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    packaging {
        // LSPosed 现代 API：入口/作用域/属性文件在 META-INF/xposed 下，需合并进 APK
        resources {
            merges += "META-INF/xposed/*"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.aidl)
    implementation(libs.pinyin4j)
    implementation(libs.hiddenapibypass)
    implementation(libs.kotlinx.coroutines.android)
    // miuix（HyperOS 设计语言）：UI 组件 / 偏好项组件 / 图标 / 模糊
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    // 页面栈导航 + 预测性返回（navigation3 由 miuix-navigation3-ui 提供，与 KernelSU 同款转场）
    implementation(libs.miuix.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigationevent.compose)
    // 长按拖拽排序：Reorderable 3.1.0。org.jetbrains.compose 三组会与 androidx.compose 冲突，排除
    implementation(libs.reorderable) {
        exclude(group = "org.jetbrains.compose.runtime")
        exclude(group = "org.jetbrains.compose.animation")
        exclude(group = "org.jetbrains.compose.foundation")
    }
    // 内置 Xposed 模块（焦点通知白名单解锁）：compileOnly，不打包进 APK，仅编译期引用
    compileOnly(libs.libxposed)
}

// 本地构建产物自动同步到安卓宿主机下载目录（容器内该路径为宿主挂载点）。
// 仅同步 release 版（用户只用 release，避免 debug 产物污染下载目录）。
// 其他环境（如 GitHub Actions CI）不存在该目录时自动跳过，不影响云编译。
gradle.projectsEvaluated {
    tasks.named("assembleRelease") { doLast { syncApkToDownload("release") } }
}

fun syncApkToDownload(variant: String) {
    runCatching {
        val destDir = file("/storage/emulated/0/download")
        if (!destDir.isDirectory) return
        val apk = layout.buildDirectory.file("outputs/apk/$variant/app-$variant.apk").get().asFile
        if (!apk.exists()) return
        val destFile = File(destDir, "FrostHush-${android.defaultConfig.versionName}.apk")
        // 流式截断写入（等价 shell cp，不删除目标）：
        // REPLACE_EXISTING 的 Files.copy 会先 unlink 目标，FUSE 层拒绝删除属主为其他 app
        // 的已存在文件（AccessDenied），而直接 O_TRUNC 写入可成功。
        apk.inputStream().use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        println("APK 已复制到 $destFile")
    }.onFailure { e ->
        println("WARN: 复制 APK 到下载目录失败: ${e::class.java.name}: ${e.message}")
        e.stackTrace.take(6).forEach { println("    at $it") }
    }
}
