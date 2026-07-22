import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kingzcheung.xime.plugin.emoji"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kingzcheung.xime.plugin.emoji"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

android.applicationVariants.all {
    val pluginName = "meme-bunny"
    outputs.all {
        (this as BaseVariantOutputImpl).outputFileName = "$pluginName-$versionName.apk"
    }
}

dependencies {
    constraints {
        implementation("org.jetbrains:annotations:23.0.0")
    }
    compileOnly(project(":plugin-core"))
    implementation("io.coil-kt:coil-compose:2.7.0")
}