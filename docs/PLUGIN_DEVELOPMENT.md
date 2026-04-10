# Kime 插件开发指南

## 插件架构概述

Kime 采用类似 Mihon/Tachiyomi 的插件化架构，支持动态加载独立 APK 插件。

### 核心组件

- **plugin-api**: 定义插件接口，所有插件必须依赖此模块
- **ExtensionLoader**: 动态加载插件 APK
- **ExtensionManager**: 管理插件生命周期和调用

## 插件类型

```kotlin
enum class ExtensionType {
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

```kotlin
class MyPredictionExtension : KimeExtension {
    override val id = "my_prediction"           // 唯一标识
    override val name = "我的联想引擎"           // 显示名称
    override val type = ExtensionType.PREDICTION // 插件类型
    override val version = "1.0.0"              // 版本号
    
    override fun initialize(context: Context): Boolean {
        // 初始化逻辑（加载模型、准备资源等）
        return true
    }
    
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        // 处理输入并返回结果
        val text = input.text ?: return ExtensionResult.Error("No text")
        val candidates = predict(text, input.topK)
        return ExtensionResult.Text(candidates)
    }
    
    override fun release() {
        // 释放资源
    }
    
    private fun predict(text: String, topK: Int): List<String> {
        // 实现预测逻辑
        return emptyList()
    }
}
```

### 3. 实现工厂接口

```kotlin
class MyExtensionFactory : KimeExtensionFactory {
    override fun createExtensions(): List<KimeExtension> {
        return listOf(MyPredictionExtension())
    }
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
            android:name="com.kingzcheung.kime.extension.factory.class"
            android:value="com.example.MyExtensionFactory" />
    </application>
</manifest>
```

### 5. 构建插件 APK

构建 APK 后，可以：
- 安装到系统（所有 Kime 实例共享）
- 放入 `{应用私有目录}/extensions/` 目录（仅当前应用可用）

## 语音转文字插件示例

```kotlin
class MySpeechExtension : KimeExtension {
    override val id = "my_speech"
    override val name = "离线语音识别"
    override val type = ExtensionType.SPEECH
    override val version = "1.0.0"
    
    private var recognizer: SpeechRecognizer? = null
    
    override fun initialize(context: Context): Boolean {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        return true
    }
    
    override suspend fun process(input: ExtensionInput): ExtensionResult {
        val audioData = input.audioData ?: return ExtensionResult.Error("No audio")
        
        // 处理音频数据
        val result = recognizeAudio(audioData, input.audioSampleRate)
        return ExtensionResult.Text(listOf(result))
    }
    
    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }
    
    private suspend fun recognizeAudio(data: ByteArray, sampleRate: Int): String {
        // 实现语音识别逻辑
        return ""
    }
}
```

## 输入输出数据结构

### ExtensionInput

```kotlin
data class ExtensionInput(
    val text: String? = null,           // 文本输入
    val audioData: ByteArray? = null,   // 音频数据
    val audioSampleRate: Int = 16000,   // 音频采样率
    val topK: Int = 5,                  // 返回候选数
    val context: Map<String, Any> = emptyMap()
)
```

### ExtensionResult

```kotlin
sealed class ExtensionResult {
    // 文本结果（联想词、语音识别）
    data class Text(val candidates: List<String>) : ExtensionResult()
    
    // 表情结果
    data class Emojis(val items: List<EmojiItem>) : ExtensionResult()
    
    // 错误
    data class Error(val message: String, val exception: Exception? = null) : ExtensionResult()
}

data class EmojiItem(
    val id: String,
    val displayText: String,    // 显示文本
    val insertText: String,     // 插入文本
    val imageUrl: String? = null, // 图片 URL（可选）
    val category: String? = null
)
```

## 在主应用中使用插件

```kotlin
// 初始化插件系统
ExtensionManager.initialize(context)

// 获取插件
val extensions = ExtensionManager.getExtensionsByType(ExtensionType.PREDICTION)

// 调用插件
val result = ExtensionManager.processFirst(
    ExtensionType.PREDICTION,
    ExtensionInput(text = "你好", topK = 5)
)

when (result) {
    is ExtensionResult.Text -> {
        // 处理候选词
        val candidates = result.candidates
    }
    is ExtensionResult.Error -> {
        // 处理错误
        Log.e(TAG, result.message)
    }
    else -> {}
}
```

## 插件加载顺序

1. 内置插件（BuiltinExtensionFactory）
2. 系统已安装插件（PackageManager 扫描）
3. 私有目录插件（`filesDir/extensions/*.apk`）

## 注意事项

1. **性能**: 插件在同一进程内运行，无需 IPC 开销
2. **类型安全**: 使用密封类确保返回类型明确
3. **生命周期**: 插件初始化和释放由 ExtensionManager 管理
4. **唯一标识**: 每个插件必须有唯一的 `id`
5. **Assets 访问问题**: 插件无法直接访问主应用的 filesDir，需要使用硬编码路径或复制资源
6. **设置界面**: createSettingsIntent 必须使用 setClassName 明确指定插件包名和类名

### Assets 资源处理

插件中的 Assets 资源需要特殊处理，因为插件的 Context 是主应用创建的，无法直接访问插件的assets。

**关键问题**：
- `context.assets` 访问的是主应用的assets，不是插件的assets
- 需要使用 `createPackageContext` 创建插件的Context来访问插件的assets

```kotlin
override fun initialize(context: Context): Boolean {
    // 使用主应用的filesDir路径存储资源
    val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
    if (!mainAppFilesDir.exists()) {
        mainAppFilesDir.mkdirs()
    }
    
    // 从插件assets复制资源到主应用filesDir
    copyAssetsToFilesDir(context, mainAppFilesDir)
    return true
}

private fun copyAssetsToFilesDir(context: Context, filesDir: File) {
    val targetDir = File(filesDir, "my_resources")
    targetDir.mkdirs()
    
    // 关键：使用createPackageContext获取插件的Context
    val pluginContext = context.createPackageContext(
        "com.kingzcheung.kime.plugin.yourplugin",  // 插件包名
        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
    )
    
    // 现在可以访问插件的assets了
    val assetManager = pluginContext.assets
    val files = assetManager.list("my_assets") ?: emptyArray()
    
    for (fileName in files) {
        assetManager.open("my_assets/$fileName").use { input ->
            File(targetDir, fileName).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}
```

**完整示例**（参考 prediction-onnx 和 emoji-sticker 插件）：

```kotlin
class MyPlugin : KimeExtension {
    override fun initialize(context: Context): Boolean {
        try {
            val mainAppFilesDir = File("/data/data/com.kingzcheung.kime/files")
            mainAppFilesDir.mkdirs()
            
            val pluginContext = context.createPackageContext(
                "com.kingzcheung.kime.plugin.myplugin",
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
            
            copyAssets(pluginContext, mainAppFilesDir)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            return false
        }
    }
    
    private fun copyAssets(pluginContext: Context, targetDir: File) {
        val resourcesDir = File(targetDir, "resources")
        resourcesDir.mkdirs()
        
        pluginContext.assets.list("resources")?.forEach { fileName ->
            pluginContext.assets.open("resources/$fileName").use { input ->
                File(resourcesDir, fileName).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
```

### 设置界面实现

插件的设置Activity需要正确配置：

```kotlin
override fun createSettingsIntent(context: Context): Intent {
    val intent = Intent()
    // 关键：必须使用插件的包名和完整类名
    intent.setClassName(
        "com.kingzcheung.kime.plugin.yourplugin",  // 插件包名
        "com.kingzcheung.kime.plugin.yourplugin.PluginSettingsActivity"  // 完整类名
    )
    return intent
}
```

AndroidManifest.xml配置：

```xml
<activity
    android:name=".PluginSettingsActivity"
    android:exported="true"
    android:label="插件设置"
    android:theme="@android:style/Theme.Material.Light.NoActionBar">
</activity>
```

## 内置插件 vs 外部插件

| 特性 | 内置插件 | 外部插件 |
|------|---------|---------|
| 打包方式 | 主 APK 内 | 独立 APK |
| 加载时机 | 应用启动 | 按需加载 |
| 更新方式 | 更新应用 | 更新插件 APK |
| 适用场景 | 核心功能 | 可选功能 |

## 未来扩展

插件系统支持新增类型，只需：
1. 在 `ExtensionType` 添加新枚举值
2. 在 `ExtensionResult` 添加新返回类型（如需要）
3. 实现新插件接口