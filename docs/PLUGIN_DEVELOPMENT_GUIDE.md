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
│  │   MyPluginFactory                    │   │
│  │   - createPredictionPlugin()         │   │
│  │   - createEmojiPlugin()              │   │
│  │   - createSpeechPlugin()             │   │
│  └─────────────────────────────────────┘   │
│                    │                         │
│                    ▼                         │
│  ┌─────────────────────────────────────┐   │
│  │   MyPredictionPlugin                 │   │
│  │   - id, name, type, version          │   │
│  │   - initialize()                     │   │
│  │   - predict()                        │   │
│  │   - learn()                          │   │
│  │   - release()                        │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

## 插件类型

Kime 插件系统支持三种独立插件类型：

| 类型 | 接口 | 用途 |
|------|------|------|
| PREDICTION | PredictionPlugin | 联想词预测 |
| SPEECH | SpeechPlugin | 语音转文字 |
| EMOJI | EmojiPlugin | 表情输入 |

```kotlin
enum class PluginType {
    PREDICTION,  // 联想词预测
    SPEECH,      // 语音转文字
    EMOJI        // 表情输入
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
    │   ├── MyPredictionPlugin.kt
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
    implementation(project(":plugin-api"))
    
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

### 3. 配置 AndroidManifest.xml（关键）

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true">
        
        <!-- 声明这是一个 Kime 插件（通过 Intent Filter） -->
        <activity
            android:name=".PluginDeclaration"
            android:exported="true">
            <intent-filter>
                <action android:name="com.kingzcheung.kime.plugin.EXTENSION" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>
        
        <!-- 插件设置界面（可选） -->
        <activity
            android:name=".PluginSettingsActivity"
            android:exported="true"
            android:label="插件设置"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
        </activity>
        
        <!-- 指定插件工厂类 -->
        <meta-data
            android:name="com.kingzcheung.kime.plugin.factory.class"
            android:value="com.example.plugin.MyPluginFactory" />
        
    </application>

</manifest>
```

### 4. 实现联想词插件

```kotlin
package com.example.plugin

import android.content.Context
import android.util.Log
import com.kingzcheung.kime.plugin.api.*

class MyPredictionPlugin : PredictionPlugin {
    
    override val id = "my_prediction_plugin"
    override val name = "我的联想引擎"
    override val description = "基于xxx的联想插件"
    override val version = "1.0.0"
    override val type = PluginType.PREDICTION
    
    private var isInitialized = false
    
    companion object {
        private const val TAG = "MyPredictionPlugin"
    }
    
    override fun initialize(context: Context): Boolean {
        if (isInitialized) return true
        
        Log.d(TAG, "Initializing plugin...")
        
        try {
            // 加载模型、准备资源
            isInitialized = true
            Log.d(TAG, "Plugin initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
            return false
        }
    }
    
    override suspend fun predict(inputText: String, topK: Int): List<PredictionCandidate> {
        if (!isInitialized) return emptyList()
        if (inputText.isEmpty()) return emptyList()
        
        return try {
            // 实现预测逻辑
            val results = doPredict(inputText, topK)
            results.map { PredictionCandidate(it, score = 1.0f) }
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            emptyList()
        }
    }
    
    override fun learn(text: String) {
        // 学习用户输入
    }
    
    override suspend fun saveLearnedData() {
        // 保存学习数据
    }
    
    override fun release() {
        isInitialized = false
    }
    
    private fun doPredict(text: String, topK: Int): List<String> {
        // 实现预测逻辑
        return listOf("候选1", "候选2", "候选3").take(topK)
    }
}
```

### 5. 实现表情插件

```kotlin
package com.example.plugin

import android.content.Context
import com.kingzcheung.kime.plugin.api.*

class MyEmojiPlugin : EmojiPlugin {
    
    override val id = "my_emoji_plugin"
    override val name = "自定义表情包"
    override val description = "我的表情集合"
    override val version = "1.0.0"
    override val type = PluginType.EMOJI
    
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
            imageUrl = "file:///android_asset/emoji/happy.png",
            category = "图片表情"
        )
    )
    
    override fun initialize(context: Context): Boolean = true
    
    override suspend fun getEmojis(category: String?, searchText: String?, topK: Int = 100): List<EmojiItem> {
        var result = emojis
        
        category?.let { cat ->
            result = result.filter { it.category == cat }
        }
        
        searchText?.let { text ->
            if (text.isNotEmpty()) {
                result = result.filter { 
                    it.displayText.contains(text) || 
                    it.insertText.contains(text)
                }
            }
        }
        
        return result.take(topK)
    }
    
    override suspend fun getCategories(): List<String> {
        return emojis.mapNotNull { it.category }.distinct()
    }
    
    override fun release() {}
}
```

### 6. 实现语音插件

```kotlin
package com.example.plugin

import android.content.Context
import com.kingzcheung.kime.plugin.api.*

class MySpeechPlugin : SpeechPlugin {
    
    override val id = "my_speech_plugin"
    override val name = "离线语音识别"
    override val description = "基于xxx的语音识别"
    override val version = "1.0.0"
    override val type = PluginType.SPEECH
    
    override val supportsRealtime = true
    override val requiresNetwork = false
    
    private var state = RecognitionState.IDLE
    
    override fun initialize(context: Context): Boolean = true
    
    override fun startRecognition(config: AudioConfig, onResult: (SpeechResult) -> Unit): Boolean {
        state = RecognitionState.LISTENING
        // 启动识别
        return true
    }
    
    override fun sendAudioChunk(data: ByteArray) {
        // 发送音频数据
    }
    
    override fun stopRecognition() {
        state = RecognitionState.IDLE
    }
    
    override fun cancelRecognition() {
        state = RecognitionState.IDLE
    }
    
    override suspend fun recognizeOnce(data: ByteArray, config: AudioConfig): String? {
        // 一次性识别
        return "识别结果"
    }
    
    override fun getState(): RecognitionState = state
    
    override fun release() {
        state = RecognitionState.IDLE
    }
}
```

### 7. 实现工厂接口

```kotlin
package com.example.plugin

import com.kingzcheung.kime.plugin.api.*

class MyPluginFactory : PluginFactory {
    
    override fun createPredictionPlugin(): PredictionPlugin? {
        return MyPredictionPlugin()
    }
    
    override fun createEmojiPlugin(): EmojiPlugin? {
        return null // 或返回 MyEmojiPlugin()
    }
    
    override fun createSpeechPlugin(): SpeechPlugin? {
        return null // 或返回 MySpeechPlugin()
    }
}
```

### 8. 构建插件 APK

```bash
./gradlew assembleRelease

# 输出位置
# my-kime-plugin/build/outputs/apk/release/my-kime-plugin-release.apk
```

## 安装插件

### 方式1：系统安装（推荐）

```bash
adb install my-kime-plugin-release.apk
```

安装后，所有 Kime 实例都可以使用此插件。

### 方式2：私有目录安装

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
   - 读取 `com.kingzcheung.kime.plugin.factory.class` 元数据

3. **实例化阶段**：
   - 加载工厂类并调用对应的 createXxxPlugin() 方法
   - 调用每个插件的 initialize(context)

4. **运行阶段**：
   - 根据插件类型调用对应方法
   - 联想: predict(), learn(), saveLearnedData()
   - 表情: getEmojis(), getCategories()
   - 语音: startRecognition(), sendAudioChunk(), stopRecognition() 等

5. **卸载阶段**：
   - 调用 release() 释放资源

## 数据结构

### PredictionPlugin 接口

```kotlin
interface PredictionPlugin : PluginMetadata {
    suspend fun predict(inputText: String, topK: Int = 5): List<PredictionCandidate>
    
    fun learn(text: String) {}
    
    suspend fun saveLearnedData() {}
}

data class PredictionCandidate(
    val text: String,
    val score: Float = 1.0f
)
```

### EmojiPlugin 接口

```kotlin
interface EmojiPlugin : PluginMetadata {
    suspend fun getEmojis(category: String?, searchText: String?, topK: Int = 100): List<EmojiItem>
    suspend fun getCategories(): List<String>
}

data class EmojiItem(
    val id: String,
    val displayText: String,
    val insertText: String,
    val imageUrl: String? = null,
    val category: String? = null
)
```

### SpeechPlugin 接口

```kotlin
interface SpeechPlugin : PluginMetadata {
    val supportsRealtime: Boolean
    val requiresNetwork: Boolean
    
    fun startRecognition(config: AudioConfig, onResult: (SpeechResult) -> Unit): Boolean
    fun sendAudioChunk(data: ByteArray)
    fun stopRecognition()
    fun cancelRecognition()
    suspend fun recognizeOnce(data: ByteArray, config: AudioConfig): String?
    fun getState(): RecognitionState
}

data class AudioConfig(
    val sampleRate: Int = 16000,
    val encoding: AudioEncoding = AudioEncoding.PCM16,
    val channels: Int = 1
)

enum class AudioEncoding { PCM16, OPUS, SPEEX }

data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val durationMs: Long = 0
)

enum class RecognitionState { IDLE, LISTENING, PROCESSING, ERROR }
```

## 注意事项

### 1. Context 使用

插件获得的是**主应用的 Context**，可以访问：
- `context.filesDir` - 主应用私有目录
- `context.cacheDir` - 主应用缓存目录

### 2. ClassLoader 隔离

插件使用独立的 PathClassLoader，与主应用类隔离：
- 插件可以有自己的依赖库版本
- 插件类不会污染主应用类空间

### 3. 权限

插件 APK 不需要特殊权限，使用主应用的权限。

### 4. 性能

- 插件在同一进程内运行，无 IPC 开销
- 使用 suspend 函数支持异步处理
- 建议使用 `Dispatchers.Default` 处理耗时任务

### 5. 插件更新

- 更新插件 APK 后，需要重启主应用
- 主应用会重新扫描和加载插件

### 6. meta-data 名称

**重要**: AndroidManifest.xml 中的 meta-data 名称已更改：
- 旧: `com.kingzcheung.kime.extension.factory.class`
- 新: `com.kingzcheung.kime.plugin.factory.class`

## 示例项目

查看现有插件实现：
- `plugins/prediction-onnx/` - 联想词插件（ONNX）
- `plugins/kaomoji/` - 颜文字插件
- `plugins/emoji-sticker/` - 表情贴纸插件

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