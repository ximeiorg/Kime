import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.nio.ByteBuffer
import java.nio.ByteOrder

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

val buildSherpaOnnx by tasks.registering {
    val jniLibsDir = file("src/main/jniLibs")
    val sherpaOnnxSoArm64 = file("src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so")
    val sherpaOnnxSoArmV7 = file("src/main/jniLibs/armeabi-v7a/libsherpa-onnx-jni.so")
    
    outputs.file(sherpaOnnxSoArm64)
    outputs.file(sherpaOnnxSoArmV7)
    
    dependsOn(downloadOnnx)
    
    doLast {
        if (sherpaOnnxSoArm64.exists() && sherpaOnnxSoArmV7.exists()) {
            println("sherpa-onnx JNI libraries already exist, skipping build")
            return@doLast
        }
        
        println("Building sherpa-onnx JNI libraries...")
        
        val buildScript = File(rootDir, "build-sherpa-onnx.sh")
        if (!buildScript.exists()) {
            println("ERROR: build script not found: ${buildScript.absolutePath}")
            return@doLast
        }
        
        val process = ProcessBuilder("bash", buildScript.absolutePath)
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        
        val output = process.inputStream.bufferedReader().readText()
        println(output)
        
        if (process.waitFor() != 0) {
            println("WARNING: sherpa-onnx build failed. ASR will use online mode only.")
        }
    }
}

val buildTrie by tasks.registering {
    val inputFile = file("src/main/assets/english.txt")
    val outputFile = file("src/main/assets/english_trie.bin")
    
    inputs.file(inputFile)
    outputs.file(outputFile)
    
    doLast {
        val words = inputFile.readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        
        println("Loaded ${words.size} words from ${inputFile.name}")
        
        val nodes = mutableListOf<MutableMap<Char, Int>>()
        val nodeWords = mutableListOf<String?>()
        val nodeFreqs = mutableListOf<Int>()
        nodes.add(mutableMapOf())
        nodeWords.add(null)
        nodeFreqs.add(0)
        
        fun getOrCreateChild(parentIndex: Int, char: Char): Int {
            val existing = nodes[parentIndex][char]
            if (existing != null) return existing
            
            val newIndex = nodes.size
            nodes.add(mutableMapOf())
            nodeWords.add(null)
            nodeFreqs.add(0)
            nodes[parentIndex][char] = newIndex
            return newIndex
        }
        
        words.forEachIndexed { lineNum, word ->
            var current = 0
            for (char in word) {
                current = getOrCreateChild(current, char)
            }
            if (nodeWords[current] == null) {
                nodeWords[current] = word
                nodeFreqs[current] = lineNum + 1
            }
        }
        
        println("Built trie with ${nodes.size} nodes")
        
        val buffer = ByteBuffer.allocate(512 * 1024)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        buffer.put("TRIE".toByteArray())
        buffer.put(1)
        buffer.putInt(nodes.size)
        
        for (i in nodes.indices) {
            val children = nodes[i]
            buffer.put(children.size.toByte())
            for ((char, childIndex) in children) {
                buffer.put(char.code.toByte())
                buffer.putInt(childIndex)
            }
            
            val word = nodeWords[i]
            buffer.put(if (word != null) 1 else 0)
            if (word != null) {
                val bytes = word.toByteArray(Charsets.UTF_8)
                buffer.put(bytes.size.toByte())
                buffer.put(bytes)
                buffer.putInt(nodeFreqs[i])
            }
        }
        
        val data = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(data)
        outputFile.writeBytes(data)
        
        println("Wrote ${data.size} bytes (${data.size / 1024}KB) to ${outputFile.name}")
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadOnnx)
    dependsOn(buildSherpaOnnx)
    dependsOn(buildTrie)
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
        val packageName = "com.kingzcheung.xime"
        val pluginsDir = "/data/data/$packageName/files/plugins"
        
        println("=== Clearing Xime plugin data ===")
        
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
            println("Please restart Xime app to reload plugins")
        }
    }
}

tasks.register("uninstallApp", DefaultTask::class) {
    group = "plugin-dev"
    description = "Completely uninstall Xime app (clear all data)"
    
    doLast {
        val packageName = "com.kingzcheung.xime"
        
        println("=== Completely uninstalling Xime app ===")
        
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
    namespace = "com.kingzcheung.xime"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kingzcheung.xime"
        minSdk = 28
        targetSdk = 35
        versionCode = 21
        versionName = "2.2.1-beta2"

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
    val appName = "Xime"
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
    
    // OkHttp for WebSocket and model download
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    
    // Apache Commons Compress for tar.bz2 extraction
    implementation("org.apache.commons:commons-compress:1.26.0")
    
    // Kaml for YAML parsing
    implementation(libs.kaml)

    // ZXing for QR code generation
    implementation("com.google.zxing:core:3.5.3")

    // Ktor embedded server for wireless import
    implementation("io.ktor:ktor-server-core:3.1.2")
    implementation("io.ktor:ktor-server-cio:3.1.2")
    
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
    androidTestImplementation("androidx.concurrent:concurrent-futures:1.2.0")
}
