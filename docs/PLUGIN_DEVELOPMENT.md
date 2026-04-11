# Kime 插件开发指南

## 插件架构概述

Kime 采用类似 Mihon/Tachiyomi 的插件化架构，支持动态加载独立 APK 插件。

### 核心组件

- **plugin-api**: 定义插件接口，所有插件必须依赖此模块
- **ExtensionLoader**: 动态加载插件 APK
- **ExtensionManager**: 管理插件生命周期和调用

## 插件类型

```kotlin
enum class PluginType {
    PREDICTION,  // 联想词预测
    SPEECH,      // 语音转文字
    EMOJI        // 表情输入
}
```

## 开发独立插件

### 1. 创建插件项目

创建一个新的 Android 项目，添加对 `plugin-api` 的依赖：

```gradle
dependencies {
    implementation(project(":plugin-api"))
}
```

### 2. 实现插件接口

根据插件类型实现对应接口：

#### 联想词插件 (PredictionPlugin)

```kotlin
class MyPredictionPlugin : PredictionPlugin {
    override val id = "my_prediction"           // 唯一标识
    override val name = "我的联想引擎"             // 显示名称
    override val description = "描述信息"
    override val version = "1.0.0"              // 版本号
    override val type = PluginType.PREDICTION    // 插件类型
    
    private var isInitialized = false
    
    override fun initialize(context: Context): Boolean {
        // 初始化逻辑（加载模型、准备资源等）
        isInitialized = true
        return true
    }
    
    override suspend fun predict(inputText: String, topK: Int): List<Candidate> {
        // 实现预测逻辑
        return emptyList()
    }
    
    override suspend fun learn(text: String) {
        // 学习用户输入
    }
    
    override suspend fun saveLearnedData() {
        // 保存学习数据
    }
    
    override fun release() {
        // 释放资源
    }
}
```

#### 表情插件 (EmojiPlugin)

```kotlin
class MyEmojiPlugin : EmojiPlugin {
    override val id = "my_emoji"
    override val name = "我的表情包"
    override val description = "自定义表情"
    override val version = "1.0.0"
    override val type = PluginType.EMOJI
    
    override fun initialize(context: Context): Boolean = true
    
    override suspend fun getEmojis(category: String?, searchText: String?, topK: Int): List<EmojiItem> {
        // 返回表情列表
        return emptyList()
    }
    
    override suspend fun getCategories(): List<String> {
        // 返回分类列表
        return emptyList()
    }
    
    override fun release() {}
}
```

#### 语音插件 (SpeechPlugin)

```kotlin
class MySpeechPlugin : SpeechPlugin {
    override val id = "my_speech"
    override val name = "离线语音识别"
    override val description = "描述"
    override val version = "1.0.0"
    override val type = PluginType.SPEECH
    override val supportsRealtime = false
    override val requiresNetwork = false
    
    override fun initialize(context: Context): Boolean = true
    
    override fun startRecognition(config: AudioConfig, onResult: (SpeechResult) -> Unit): Boolean = false
    
    override fun sendAudioChunk(data: ByteArray) {}
    
    override fun stopRecognition() {}
    
    override fun cancelRecognition() {}
    
    override suspend fun recognizeOnce(data: ByteArray, config: AudioConfig): String? = null
    
    override fun getState(): RecognitionState = RecognitionState.IDLE
    
    override fun release() {}
}
```

### 3. 实现工厂接口

```kotlin
class MyPluginFactory : PluginFactory {
    override fun createPredictionPlugin(): PredictionPlugin? = MyPredictionPlugin()
    
    override fun createEmojiPlugin(): EmojiPlugin? = null
    
    override fun createSpeechPlugin(): SpeechPlugin? = null
}
```

### 4. 配置 AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- 声明插件特性 -->
    <uses-feature android:name="com.kingzcheung.kime.extension" />
    
    <application>
        <!-- 指定工厂类 -->
        <meta-data
            android:name="com.kingzcheung.kime.plugin.factory.class"
            android:value="com.example.MyPluginFactory" />
    </application>
</manifest>
```

### 5. 构建插件 APK

构建 APK 后，可以：
- 安装到系统（所有 Kime 实例共享）
- 放入 `{应用私有目录}/extensions/` 目录（仅当前应用可用）

## 插件数据结构

### EmojiItem

```kotlin
data class EmojiItem(
    val id: String,
    val displayText: String,    // 显示文本
    val insertText: String,     // 插入文本
    val imageUrl: String? = null, // 图片 URL
    val category: String? = null  // 分类
)
```

### Candidate (预测结果)

```kotlin
data class Candidate(
    val text: String,           // 候选文本
    val score: Float = 0f,      // 置信度
    val source: String = ""     // 来源插件ID
)
```

### AudioConfig (语音配置)

```kotlin
data class AudioConfig(
    val sampleRate: Int = 16000,
    val encoding: AudioEncoding = AudioEncoding.PCM16,
    val channels: Int = 1
)

enum class AudioEncoding {
    PCM16, OPUS, SPEEX
}
```

### SpeechResult (语音结果)

```kotlin
data class SpeechResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val durationMs: Long = 0
)
```

## 在主应用中使用插件

```kotlin
// 初始化插件系统
ExtensionManager.initialize(context)

// 获取插件
val predictionPlugins = ExtensionManager.getPredictionPlugins()
val emojiPlugins = ExtensionManager.getEmojiPlugins()
val speechPlugins = ExtensionManager.getSpeechPlugins()

// 获取启用的插件
val enabledPredictionPlugins = ExtensionManager.getEnabledPredictionPlugins(context)

// 调用预测插件
val candidates = ExtensionManager.predict(context, "你好", topK = 5)

// 调用表情插件
val emojis = ExtensionManager.getEmojis(context, topK = 100)
```

## 插件加载顺序

1. 内置插件（BuiltinExtensionFactory）
2. 系统已安装插件（PackageManager 扫描）
3. 私有目录插件（`filesDir/extensions/*.apk`）

## 注意事项

1. **性能**: 插件在同一进程内运行，无需 IPC 开销
2. **类型安全**: 每种插件类型有独立接口
3. **生命周期**: 插件初始化和释放由 ExtensionManager 管理
4. **唯一标识**: 每个插件必须有唯一的 `id`
5. **Assets 访问问题**: 插件无法直接访问主应用的 filesDir，需要使用 createPackageContext
6. **设置界面**: createSettingsIntent 必须使用 setClassName 明确指定插件包名和类名

### Assets 资源处理

插件中的 Assets 资源需要特殊处理：

```kotlin
override fun initialize(context: Context): Boolean {
    val mainAppFilesDir = File(context.filesDir, "my_resources")
    mainAppFilesDir.mkdirs()
    
    val pluginContext = context.createPackageContext(
        "com.kingzcheung.kime.plugin.yourplugin",
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
    )
    
    pluginContext.assets.list("resources")?.forEach { fileName ->
        pluginContext.assets.open("resources/$fileName").use { input ->
            File(mainAppFilesDir, fileName).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
    return true
}
```

### 设置界面实现

```kotlin
override fun createSettingsIntent(context: Context): Intent {
    val intent = Intent()
    intent.setClassName(
        "com.kingzcheung.kime.plugin.yourplugin",
        "com.kingzcheung.kime.plugin.yourplugin.PluginSettingsActivity"
    )
    return intent
}
```

## 内置插件 vs 外部插件

| 特性 | 内置插件 | 外部插件 |
|------|---------|---------|
| 打包方式 | 主 APK 内 | 独立 APK |
| 加载时机 | 应用启动 | 按需加载 |
| 更新方式 | 更新应用 | 更新插件 APK |
| 适用场景 | 核心功能 | 可选功能 |