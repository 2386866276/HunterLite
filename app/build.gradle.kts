import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hunterlite.app"
    compileSdk = 34 

    defaultConfig {
        applicationId = "com.hunterlite.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.2.0"
        
        // 生成构建 UUID (取前8位，大写)
        buildConfigField("String", "BUILD_UUID", "\"${UUID.randomUUID().toString().take(8).uppercase()}\"")
        // 生成编译时间
        buildConfigField("String", "BUILD_TIME", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") 
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true // 【关键】启用 BuildConfig，否则无法读取 BUILD_UUID
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" 
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2") 

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}