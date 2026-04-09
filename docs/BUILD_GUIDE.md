# Kime 插件系统构建说明

## 已修复的问题

### 1. plugin-api 模块结构
- ✅ 添加了 AndroidManifest.xml（Android Library 必需）
- ✅ 配置正确的 Gradle 依赖

### 2. ExtensionLoader 加载逻辑
- ✅ 修正了插件检测方式（从 metaData 检测工厂类）
- ✅ 移除了错误的 uses-feature 检测

### 3. sample-extension-plugin 插件示例
- ✅ 简化了 AndroidManifest.xml（移除图标配置）
- ✅ 添加了 ONNX Runtime 依赖
- ✅ 创建了 proguard-rules.pro

### 4. 目录结构
```
Kime/
├── plugin-api/                     # 插件接口库
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml     # 新增
│       └── java/.../
│
├── sample-extension-plugin/        # 插件示例
│   ├── build.gradle.kts
│   ├── proguard-rules.pro          # 新增
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/kime/plugin/
│           ├── OnnxPredictionPlugin.kt
│           └── OnnxPluginFactory.kt
│
└── app/
    └── src/main/java/com/kingzcheung/kime/plugin/
        ├── ExtensionLoader.kt       # 已修正
        ├── ExtensionManager.kt
        └── builtin/
            ├── BuiltinExtensionFactory.kt
            ├── OnnxPredictionExtension.kt
            └── SpeechToTextExtension.kt
```

## 构建步骤

### 1. 构建 plugin-api

```bash
# Windows PowerShell
.\gradlew :plugin-api:assembleRelease

# 或 Linux/Mac
./gradlew :plugin-api:assembleRelease
```

输出：`plugin-api/build/outputs/aar/plugin-api-release.aar`

### 2. 构建主应用

```bash
.\gradlew :app:assembleRelease
```

### 3. 构建插件示例

```bash
.\gradlew :sample-extension-plugin:assembleRelease
```

输出：`sample-extension-plugin/build/outputs/apk/release/sample-extension-plugin-release.apk`

## 安装和测试

### 1. 安装主应用

```bash
adb install app/build/outputs/apk/release/Kime-1.2.1-arm64-v8a.apk
```

### 2. 安装插件

方式 A：系统安装（推荐）
```bash
adb install sample-extension-plugin/build/outputs/apk/release/sample-extension-plugin-release.apk
```

方式 B：私有目录安装
```bash
adb push sample-extension-plugin/build/outputs/apk/release/sample-extension-plugin-release.apk /sdcard/
# 然后在应用内复制到私有目录
```

### 3. 测试插件加载

在主应用中查看日志：
```bash
adb logcat -s ExtensionLoader:* ExtensionManager:* OnnxPredictionExtension:*
```

应该看到类似：
```
ExtensionLoader: Loading extensions from APK: /data/app/...
ExtensionLoader: Found extension factory class: com.example.kime.plugin.OnnxPluginFactory
ExtensionLoader: Created 1 extensions from factory
OnnxPredictionPlugin: ONNX plugin initialized: true
ExtensionManager: ExtensionManager initialized with 2 extensions
ExtensionManager:   - builtin_onnx_prediction: ONNX 联想词引擎 (PREDICTION)
ExtensionManager:   - plugin_onnx_prediction: ONNX 联想词插件 (PREDICTION)
```

## 插件开发工作流

### 1. 创建新插件项目

```bash
# 1. 创建新目录
mkdir my-plugin

# 2. 复制 sample-extension-plugin 作为模板
cp -r sample-extension-plugin/* my-plugin/

# 3. 修改配置
#    - build.gradle.kts: applicationId, namespace
#    - AndroidManifest.xml: factory class 路径
#    - 实现你的插件类
```

### 2. 插件发布

如果想让其他开发者使用 `plugin-api`：

```bash
# 发布到 Maven Local
.\gradlew :plugin-api:publishToMavenLocal

# 其他项目可以这样依赖
dependencies {
    implementation("com.kingzcheung.kime:plugin-api:1.0.0")
}
```

## 可能的构建问题

### 问题：找不到 plugin-api

**解决方案**：确保 `settings.gradle.kts` 包含：
```kotlin
include(":plugin-api")
include(":sample-extension-plugin")
```

### 问题：JAVA_HOME 未设置

**解决方案**：
```powershell
# Windows - 设置环境变量
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# 或使用 Android Studio 内置 JDK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
```

### 问题：Kotlin 编译错误

**解决方案**：确保所有模块使用相同的 Kotlin 和 JVM 版本：
```kotlin
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

## 下一步

1. ✅ 基础插件架构已完成
2. ⏭️ 实现实际的 ONNX 模型加载（需要模型文件）
3. ⏭️ 实现语音转文字插件（可集成系统 SpeechRecognizer）
4. ⏭️ 添加插件管理 UI（查看、启用/禁用插件）

## 插件架构验证

插件系统采用 **ClassLoader 动态加载**，类似 Mihon/Tachiyomi：

```kotlin
// 加载流程
PathClassLoader(apkPath, parentClassLoader)  // 创建插件 ClassLoader
  → loadClass("com.example.kime.plugin.OnnxPluginFactory")  // 加载工厂类
  → newInstance()  // 实例化工厂
  → createExtensions()  // 创建插件实例
  → initialize(context)  // 初始化插件
```

**优势**：
- 无 IPC 开销（同一进程）
- 类型安全（编译时接口检查）
- 灵活扩展（新增插件类型只需添加枚举）