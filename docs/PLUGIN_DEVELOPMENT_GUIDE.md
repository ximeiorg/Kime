# Kime 插件开发完整指南

## 插件系统架构

Kime 采用类似 Mihon/Tachiyomi 的动态加载插件架构：

```
┌─────────────────────────────────────────────┐
│          主应用 (Kime APK)                   │
│                                              │
│  ┌─────────────────────────────────────┐   │
│  │   ExtensionManager                   │   │
│  │   - 扫描已安装插件APK                 │   │
│  │   - 动态 ClassLoader 加载            │   │
│  │   - 管理插件生命周期                  │   │
│  └─────────────────────────────────────┘   │
│                    │                         │
│                    │ PathClassLoader        │
│                    ▼                         │
└─────────────────────────────────────────────┘
         │
         │ 加载插件APK
         ▼
┌─────────────────────────────────────────────┐
│       插件 APK (独立安装)                    │
│                                              │
│  ┌─────────────────────────────────────┐   │
│  │   OnnxPluginFactory                  │   │
│  │   - createExtensions()               │   │
│  │   - 返回插件实例列表                  │   │
│  └─────────────────────────────────────┘   │
│                    │                         │
│                    ▼                         │
│  ┌─────────────────────────────────────┐   │
│  │   OnnxPredictionPlugin               │   │
│  │   - id, name, type, version          │   │
│  │   - initialize()                     │   │
│  │   - process()                        │   │
│  │   - release()                        │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

## 插件类型

```kotlin
enum class ExtensionType {
    PREDICTION,  // 联想词预测
    SPEECH,      // 语音转文字
    EMOJI        // 表情输入（支持文本和图片）
}
```

## 开发独立插件 APK

### 1. 创建插件项目

创建一个新的 Android 项目（Application），结构如下：

```
my-kime-plugin/
├── build.gradle.kts
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    ├── java/com/example/plugin/
    │   ├── MyPlugin.kt
    │   └── MyPluginFactory.kt
    └── res/
        └── values/strings.xml
```

### 2. 配置 build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.kime.plugin"
    compileSdk = 35

    defaultConfig {
        // 应用ID必须唯一
        applicationId = "com.example.kime.plugin.myplugin"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // 必须依赖 plugin-api（需要发布到 Maven 或本地引用）
    implementation("com.kingzcheung.kime:plugin-api:1.0.0")
    
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    
    // 如果需要 ONNX Runtime
    implementation("com.microsoft.onnxruntime:onnxruntime-android:latest.release")
}
```

### 3. 配置 AndroidManifest.xml（关键）

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- 声明这是一个 Kime 插件（必须有） -->
    <uses-feature android:name="com.kingzcheung.kime.extension" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name">
        
        <!-- 指定插件工厂类（必须有） -->
        <meta-data
            android:name="com.kingzcheung.kime.extension.factory.class"
            android:value="com.example.plugin.MyPluginFactory" />
        
    </application>

</manifest>
```

### 4. 实现插件接口

```kotlin
package com.example.plugin

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.plugin.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MyPredictionPlugin : KimeExtension {
    
    // 唯一标识（必须唯一）
    override val id = "my_prediction_plugin"
    
    // 显示名称
    override val name = "我的联想引擎"
    
    // 插件类型
    override val type = ExtensionType.PREDICTION
    
    // 版本号
    override val version = "1.0.0"
    
    private var isInitialized = false
    
    companion object {
        private const val TAG = "MyPredictionPlugin"
    }
    
    /**
     * 初始化插件
     * 加载模型、准备资源等
     */
    override fun initialize(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return true
        }
        
        Log.d(TAG, "Initializing plugin...")
        
        // 在这里加载模型、准备资源
        // 注意：context 是主应用的 Context
        
        try {
            // 示例：加载模型文件
            // val modelFile = File(context.filesDir, "my_model.bin")
            // loadModel(modelFile)
            
            isInitialized = true
            Log.d(TAG, "Plugin initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
            return false
        }
    }
    
    /**
     * 处理输入并返回结果
     */
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        if (!isInitialized) {
            return ExtensionResult.Error("Plugin not initialized")
        }
        
        val text = input.text
        if (text.isNullOrEmpty()) {
            Log.d(TAG, "Empty input")
            return ExtensionResult.Text(emptyList())
        }
        
        return withContext(Dispatchers.Default) {
            try {
                Log.d(TAG, "Processing: '$text', topK=${input.topK}")
                
                // 实现预测逻辑
                val candidates = predict(text, input.topK)
                
                Log.d(TAG, "Result: ${candidates.joinToString()}")
                ExtensionResult.Text(candidates)
            } catch (e: Exception) {
                Log.e(TAG, "Processing failed", e)
                ExtensionResult.Error("Processing failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * 释放资源
     */
    override fun release() {
        if (isInitialized) {
            // 释放模型、清理资源
            isInitialized = false
            Log.d(TAG, "Plugin released")
        }
    }
    
    // 私有方法：实现预测逻辑
    private fun predict(text: String, topK: Int): List<String> {
        // 这里实现实际的预测逻辑
        // 例如调用 ONNX 模型
        
        // 示例返回（实际应替换为真实逻辑）
        return listOf("候选1", "候选2", "候选3").take(topK)
    }
}
```

### 5. 实现工厂接口

```kotlin
package com.example.plugin

import com.kingzcheung.kime.plugin.api.KimeExtension
import com.kingzcheung.kime.plugin.api.KimeExtensionFactory

class MyPluginFactory : KimeExtensionFactory {
    
    /**
     * 创建插件实例列表
     * 一个插件APK可以提供多个插件
     */
    override fun createExtensions(): List<KimeExtension> {
        return listOf(
            MyPredictionPlugin()
            // 可以添加更多插件
            // MySpeechPlugin(),
            // MyEmojiPlugin()
        )
    }
}
```

### 6. 构建插件 APK

```bash
# 构建插件
./gradlew assembleRelease

# 输出位置
# my-kime-plugin/build/outputs/apk/release/my-kime-plugin-release.apk
```

## 安装插件

### 方式1：系统安装（推荐）

直接安装插件 APK 到设备：

```bash
adb install my-kime-plugin-release.apk
```

安装后，所有 Kime 实例都可以使用此插件。

### 方式2：私有目录安装

将插件 APK 放入 Kime 的私有目录：

```bash
adb push my-kime-plugin-release.apk /sdcard/
# 然后在应用内复制到私有目录
```

路径：`/data/data/com.kingzcheung.kime/files/extensions/my-kime-plugin.apk`

## 插件加载流程

1. **扫描阶段**：
   - ExtensionManager 扫描已安装应用
   - 查找声明 `com.kingzcheung.kime.extension` 特性的 APK

2. **加载阶段**：
   - 使用 PathClassLoader 加载插件 APK
   - 读取 `com.kingzcheung.kime.extension.factory.class` 元数据

3. **实例化阶段**：
   - 加载工厂类并调用 `createExtensions()`
   - 调用每个插件的 `initialize(context)`

4. **运行阶段**：
   - ExtensionManager 调用 `process(input)`
   - 插件返回 ExtensionResult

5. **卸载阶段**：
   - 调用 `release()` 释放资源

## 输入输出数据结构

### ExtensionInput

```kotlin
data class ExtensionInput(
    val text: String? = null,           // 文本输入（联想词）
    val audioData: ByteArray? = null,   // 音频数据（语音）
    val audioSampleRate: Int = 16000,   // 采样率
    val topK: Int = 5,                  // 返回候选数
    val context: Map<String, Any> = emptyMap() // 额外上下文
)
```

### ExtensionResult

```kotlin
sealed class ExtensionResult {
    // 文本结果（联想词、语音识别）
    data class Text(val candidates: List<String>) : ExtensionResult()
    
    // 表情结果（支持图片）
    data class Emojis(val items: List<EmojiItem>) : ExtensionResult()
    
    // 错误
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : ExtensionResult()
}

data class EmojiItem(
    val id: String,
    val displayText: String,    // 显示文本（如 "😊"）
    val insertText: String,     // 插入文本
    val imageUrl: String? = null, // 图片 URL（可选）
    val category: String? = null  // 分类
)
```

## 完整示例：语音转文字插件

```kotlin
class MySpeechPlugin : KimeExtension {
    
    override val id = "my_speech_plugin"
    override val name = "离线语音识别"
    override val type = ExtensionType.SPEECH
    override val version = "1.0.0"
    
    private var isInitialized = false
    
    override fun initialize(context: Context): Boolean {
        // 加载语音识别模型
        // 例如 Whisper、Vosk 等
        isInitialized = true
        return true
    }
    
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        val audioData = input.audioData
        if (audioData == null || audioData.isEmpty()) {
            return ExtensionResult.Error("No audio data")
        }
        
        return withContext(Dispatchers.Default) {
            try {
                // 处理音频数据
                val recognizedText = recognizeAudio(
                    audioData,
                    input.audioSampleRate
                )
                
                ExtensionResult.Text(listOf(recognizedText))
            } catch (e: Exception) {
                ExtensionResult.Error("Recognition failed: ${e.message}", e)
            }
        }
    }
    
    override fun release() {
        isInitialized = false
    }
    
    private fun recognizeAudio(data: ByteArray, sampleRate: Int): String {
        // 实现语音识别逻辑
        return "识别结果"
    }
}
```

## 完整示例：表情插件（支持图片）

```kotlin
class MyEmojiPlugin : KimeExtension {
    
    override val id = "my_emoji_plugin"
    override val name = "自定义表情包"
    override val type = ExtensionType.EMOJI
    override val version = "1.0.0"
    
    private val emojis = listOf(
        EmojiItem(
            id = "emoji_1",
            displayText = "😊",
            insertText = "😊",
            category = "表情"
        ),
        EmojiItem(
            id = "emoji_2",
            displayText = "[开心]",
            insertText = "[开心]",
            imageUrl = "https://example.com/happy.png",
            category = "图片表情"
        )
    )
    
    override fun initialize(context: Context): Boolean {
        return true
    }
    
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        // 可以根据 input.text 过滤表情
        val keyword = input.text?.lowercase() ?: ""
        
        val filtered = if (keyword.isEmpty()) {
            emojis
        } else {
            emojis.filter { 
                it.displayText.contains(keyword) || 
                it.category?.contains(keyword) == true
            }
        }
        
        return ExtensionResult.Emojis(filtered)
    }
    
    override fun release() {}
}
```

## 注意事项

### 1. Context 使用

插件获得的是**主应用的 Context**，可以访问：
- `context.filesDir` - 主应用私有目录
- `context.cacheDir` - 主应用缓存目录

插件自己的资源需要打包在插件 APK 中。

### 2. ClassLoader 隔离

插件使用独立的 PathClassLoader，与主应用类隔离：
- 插件可以有自己的依赖库版本
- 插件类不会污染主应用类空间

### 3. 权限

插件 APK 不需要特殊权限，使用主应用的权限。

### 4. 性能

- 插件在同一进程内运行，无 IPC 开销
- 使用 `suspend fun process()` 支持异步处理
- 建议使用 `Dispatchers.Default` 处理耗时任务

### 5. 插件更新

- 更新插件 APK 后，需要重启主应用
- 主应用会重新扫描和加载插件

## 示例项目

查看 `sample-extension-plugin` 目录获取完整的插件示例：

```
sample-extension-plugin/
├── build.gradle.kts           # Gradle 配置
├── proguard-rules.pro         # ProGuard 规则
├── src/main/
│   ├── AndroidManifest.xml    # 插件声明
│   └── java/com/example/kime/plugin/
│       ├── OnnxPredictionPlugin.kt  # 插件实现
│       └── OnnxPluginFactory.kt     # 工厂类
```

## 发布 plugin-api 到 Maven

如果插件开发者需要独立开发，可以将 `plugin-api` 发布到 Maven：

```kotlin
// plugin-api/build.gradle.kts
plugins {
    id("com.android.library")
    id("maven-publish")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                
                groupId = "com.kingzcheung.kime"
                artifactId = "plugin-api"
                version = "1.0.0"
            }
        }
    }
}
```

插件开发者可以这样依赖：

```kotlin
dependencies {
    implementation("com.kingzcheung.kime:plugin-api:1.0.0")
}
```