import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.frosthush.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.frosthush.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 6
        versionName = "1.2.0"
        // 编译时间（精确到秒）：关于页展示 + 诊断日志导出头部
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
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
