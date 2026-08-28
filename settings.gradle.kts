pluginManagement {
    repositories {
        // 1. 阿里云 Gradle 插件镜像 (加速 AGP、Kotlin 插件下载)
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        
        // 2. 阿里云 Google 镜像 (加速 AndroidX、Compose 依赖下载)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        
        // 3. 阿里云公共镜像 (加速 Maven Central、JCenter 依赖下载)
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        
        // 官方源作为兜底（防止镜像站偶尔缺失最新发布的包）
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 1. 阿里云公共镜像 (核心依赖)
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        
        // 2. 阿里云 Google 镜像 (AndroidX 核心依赖)
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        
        // 官方源作为兜底
        google()
        mavenCentral()
        
        // 3. JitPack (常用于下载 GitHub 上的开源库，国内直连有时较慢，但通常可用)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "HunterLite"
include(":app")