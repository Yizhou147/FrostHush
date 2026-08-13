// 与 Hail 一致：GitHub Actions 等 CI 环境网络正常，直接使用官方仓库；
// 仅本地构建（无 GITHUB_ACTIONS 环境变量）时优先使用国内镜像加速。
pluginManagement {
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == null) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == null) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
        // libxposed 现代 Xposed API（内置超级岛解锁模块编译期依赖）
        maven("https://jitpack.io")
    }
}
rootProject.name = "FrostHush"
include(":app")
