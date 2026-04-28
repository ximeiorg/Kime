import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val onnxVersion = "1.20.0"
val onnxAarUrl = "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${onnxVersion}/onnxruntime-android-${onnxVersion}.aar"

val downloadOnnx by tasks.registering {
    val cppDir = file("src/main/jni/onnxruntime")
    val jniLibsDir = file("src/main/jniLibs")
    
    outputs.dir(cppDir)
    outputs.dir(jniLibsDir)
    
    doLast {
        val tmpDir = temporaryDir
        val aarFile = File(tmpDir, "onnxruntime.aar")
        
        val universalSo = file("src/main/jniLibs/arm64-v8a/libonnxruntime.so")
        if (universalSo.exists()) {
            println("ONNX Runtime files already exist, skipping download")
            return@doLast
        }
        
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

tasks.register("copyPluginsToAssets", Copy::class) {
    group = "plugin-dev"
    description = "Manually copy plugin APKs to assets for debugging"
    
    val pluginProjects = listOf(
        ":plugins:meme-bunny",
        ":plugins:kaomoji"
    )
    
    pluginProjects.forEach { pluginPath ->
        dependsOn(project(pluginPath).tasks.getByName("assembleDebug"))
        from(project(pluginPath).layout.buildDirectory.dir("outputs/apk/debug")) {
            include("*universal*.apk")
        }
    }
    
    into(layout.projectDirectory.dir("src/main/assets/plugins"))
    
    doFirst {
        layout.projectDirectory.dir("src/main/assets/plugins").asFile.mkdirs()
    }
}

tasks.register("clearPlugins", DefaultTask::class) {
    group = "plugin-dev"
    description = "Clear all plugin data from device (requires connected device with adb)"
    
    doLast {
        val packageName = "com.kingzcheung.kime"
        val pluginsDir = "/data/data/$packageName/files/plugins"
        
        println("=== Clearing Kime plugin data ===")
        
        val devicesCheck = executeCommand("adb devices")
        if (!devicesCheck.contains("device")) {
            println("ERROR: No connected device detected")
        } else {
            println("Clearing plugin directory...")
            executeCommand("adb shell rm -rf $pluginsDir")
            
            println("Clearing plugin config...")
            executeCommand("adb shell rm -rf /data/data/$packageName/shared_prefs/plugin_*.xml")
            executeCommand("adb shell rm -rf /data/data/$packageName/shared_prefs/plugins.xml")
            
            println("=== Done ===")
            println("Please restart Kime app to reload plugins")
        }
    }
}

tasks.register("uninstallApp", DefaultTask::class) {
    group = "plugin-dev"
    description = "Completely uninstall Kime app (clear all data)"
    
    doLast {
        val packageName = "com.kingzcheung.kime"
        
        println("=== Completely uninstalling Kime app ===")
        
        val devicesCheck = executeCommand("adb devices")
        if (!devicesCheck.contains("device")) {
            println("ERROR: No connected device detected")
        } else {
            println("Uninstalling $packageName...")
            val result = executeCommand("adb uninstall $packageName")
            println(result)
            
            println("=== Done ===")
            println("All app data cleared. Reinstall to start fresh.")
        }
    }
}

fun executeCommand(command: String): String {
    return try {
        val parts = command.split(" ")
        val process = ProcessBuilder(parts)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText()
    } catch (e: Exception) {
        ""
    }
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

// 加载签名配置
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.kingzcheung.kime"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kingzcheung.kime"
        minSdk = 28
        targetSdk = 35
        versionCode = 11
        versionName = "2.0.0-beta5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // NDK 配置
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        
        // 构建信息
        buildConfigField("String", "GIT_HASH", "\"${getGitHash()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 只在本地有 keystore.properties 时才使用签名配置
            // GitHub Actions 使用自己的签名方式
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    
    // 分架构打�?
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
    implementation(project(":plugin-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Kotlin stdlib - CRITICAL for plugin compatibility
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    implementation(libs.kotlinx.coroutines.core)
    
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
    
    // OkHttp for WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    debugImplementation(libs.androidx.compose.ui.tooling)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
