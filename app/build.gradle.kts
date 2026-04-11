import java.text.SimpleDateFormat
import java.util.Date
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 获取 Git 提交哈希
fun getGitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .directory(rootDir)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

// 获取构建时间
fun getBuildTime(): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
}

android {
    namespace = "com.kingzcheung.kime"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingzcheung.kime"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // NDK 配置
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        
        // 构建信息
        buildConfigField("String", "GIT_HASH", "\"${getGitHash()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true  // 启用代码混淆和压缩
            isShrinkResources = true  // 启用资源压缩
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    // NDK 构建配置
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    // 打包配置
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    ndkVersion = "28.2.13676358"
    
    // 分架构打包
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

android.applicationVariants.all {
    val appName = "Kime"
    outputs.all {
        val abi = filters.find { it.filterType.toString() == "ABI" }?.identifier ?: "universal"
        (this as BaseVariantOutputImpl).outputFileName = "$appName-$versionName-$abi.apk"
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    
    // Material Icons
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // SavedState
    implementation(libs.androidx.savedstate)
    
    // Coil (Image Loading with SVG support)
    implementation(libs.coil)
    implementation(libs.coil.svg)
    
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}