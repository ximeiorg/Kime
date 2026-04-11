plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val onnxVersion = "1.20.0"
val onnxAarUrl = "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${onnxVersion}/onnxruntime-android-${onnxVersion}.aar"

val downloadOnnx by tasks.registering {
    val cppDir = file("src/main/cpp/onnxruntime")
    val jniLibsDir = file("src/main/jniLibs")
    
    outputs.dir(cppDir)
    outputs.dir(jniLibsDir)
    
    doLast {
        val tmpDir = temporaryDir
        val aarFile = File(tmpDir, "onnxruntime.aar")
        val zipFile = File(tmpDir, "onnxruntime.zip")
        
        println("Downloading ONNX Runtime ${onnxVersion}...")
        
        ant.invokeMethod("get", mapOf("src" to onnxAarUrl, "dest" to aarFile))
        
        copy {
            from(zipTree(aarFile))
            into(tmpDir)
        }
        
        copy {
            from(File(tmpDir, "headers"))
            into(File(cppDir, "include"))
        }
        
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        abis.forEach { abi ->
            copy {
                from(File(File(tmpDir, "jni"), abi))
                include("libonnxruntime.so")
                into(File(File(cppDir, "lib"), abi))
            }
            copy {
                from(File(File(tmpDir, "jni"), abi))
                include("libonnxruntime.so")
                into(File(jniLibsDir, abi))
            }
        }
        
        println("ONNX Runtime downloaded successfully")
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadOnnx)
}

android {
    namespace = "com.kingzcheung.kime.plugin.prediction"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kingzcheung.kime.plugin.prediction"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

import com.android.build.gradle.internal.api.BaseVariantOutputImpl

android.applicationVariants.all {
    val pluginName = "prediction-onnx"
    outputs.all {
        val abi = filters.find { it.filterType.toString() == "ABI" }?.identifier ?: "universal"
        (this as BaseVariantOutputImpl).outputFileName = "$pluginName-$versionName-$abi.apk"
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
}