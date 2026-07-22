plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kingzcheung.xime.plugin.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    constraints {
        implementation("org.jetbrains:annotations:23.0.0")
    }
    
    api(kotlin("stdlib"))
    api(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)

    api(platform(libs.androidx.compose.bom))
    api("androidx.compose.runtime:runtime")
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.activity.compose)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.lifecycle.runtime.compose)

    api("com.android.tools.smali:smali-dexlib2:3.0.9")
    
    testImplementation("junit:junit:4.13.2")
}
