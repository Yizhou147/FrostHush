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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
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
    implementation(libs.pinyin4j)
    implementation(libs.hiddenapibypass)
    implementation(libs.kotlinx.coroutines.android)
    // reorderable 3.x 传递依赖 Compose Multiplatform(org.jetbrains.compose 1.7.0)，
    // 会与 BOM 的 androidx.compose 形成双套类导致编译期符号解析崩坏，故排除之，
    // 使其直接复用 BOM 的 androidx.compose（API 二进制兼容）。
    implementation(libs.reorderable) {
        exclude(group = "org.jetbrains.compose")
    }
}
