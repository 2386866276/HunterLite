plugins {
    id("base")
    // 完美兼容 Gradle 8.11.1 的 AGP 版本
    id("com.android.application") version "8.7.3" apply false
    // 必须与上面的 composeOptions 1.5.14 匹配的 Kotlin 版本
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}