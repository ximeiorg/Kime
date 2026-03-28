package com.kingzcheung.kime.service

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kingzcheung.kime.rime.RimeConfigHelper
import com.kingzcheung.kime.rime.RimeEngine
import com.kingzcheung.kime.settings.SettingsPreferences
import com.kingzcheung.kime.ui.KeysConfigHelper
import com.kingzcheung.kime.ui.theme.KimeTheme
import com.kingzcheung.kime.ui.KeyboardView
import java.io.File

/**
 * Kime 输入法服务
 * 使用 Jetpack Compose 构建输入法 UI
 * 集成 Rime 引擎实现五笔输入
 * 
 * 参考 trime 的 LifecycleInputMethodService 实现
 */
class KimeInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "KimeInputMethodService"
        private const val DARK_MODE_LIGHT = 0
        private const val DARK_MODE_DARK = 1
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // Rime 引擎实例
    private val rimeEngine = RimeEngine()
    
    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // UI 状态
    private val candidatesState = mutableStateOf<Array<String>>(emptyArray())
    private val inputTextState = mutableStateOf("")
    private val isComposingState = mutableStateOf(false)
    private val isAsciiModeState = mutableStateOf(false)
    private val darkModeState = mutableStateOf(DARK_MODE_LIGHT)
    
    // 音频和振动
    private val audioManager: AudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    private fun playKeySound() {
        if (!SettingsPreferences.isSoundEnabled(this)) return
        
        val volume = SettingsPreferences.getSoundVolume(this) / 100f
        val soundVolume = (volume * 100).toInt()
        
        audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, soundVolume / 100f)
    }
    
    private fun performVibration() {
        if (!SettingsPreferences.isVibrationEnabled(this)) return
        if (!vibrator.hasVibrator()) return
        
        val intensity = SettingsPreferences.getVibrationIntensity(this)
        val duration = 10L + (intensity * 0.4).toLong()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = (intensity * 2.55).toInt().coerceIn(1, 255)
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
    
    private fun performKeyPressEffect() {
        playKeySound()
        performVibration()
    }
    
    private fun loadDarkModePreference() {
        darkModeState.value = SettingsPreferences.getDarkMode(this)
    }
    
    private fun saveDarkModePreference(mode: Int) {
        SettingsPreferences.setDarkMode(this, mode)
        darkModeState.value = mode
    }
    
    fun toggleDarkMode() {
        val newMode = if (darkModeState.value == DARK_MODE_LIGHT) DARK_MODE_DARK else DARK_MODE_LIGHT
        saveDarkModePreference(newMode)
    }
    
    fun isDarkTheme(): Boolean {
        return darkModeState.value == DARK_MODE_DARK
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        window.window?.decorView?.setViewTreeLifecycleOwner(this)
        window.window?.decorView?.setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        loadDarkModePreference()
        initRimeEngine()
    }
    
    /**
     * 初始化 Rime 引擎
     */
    private fun initRimeEngine() {
        Log.d(TAG, "initRimeEngine: Starting initialization...")
        try {
            KeysConfigHelper.loadConfig(this)
            
            val (userDataDir, sharedDataDir) = RimeConfigHelper.initializeRimeData(this)
            
            Log.d(TAG, "initRimeEngine: userDataDir=$userDataDir, sharedDataDir=$sharedDataDir")
            
            Log.d(TAG, "initRimeEngine: Calling rimeEngine.initialize...")
            rimeEngine.initialize(userDataDir, sharedDataDir)
            
            val currentSchema = rimeEngine.getCurrentSchema()
            val savedSchema = SettingsPreferences.getCurrentSchema(this)
            Log.d(TAG, "initRimeEngine: currentSchema=$currentSchema, savedSchema=$savedSchema")
            
            if (currentSchema != savedSchema) {
                Log.d(TAG, "initRimeEngine: Switching to saved schema: $savedSchema")
                rimeEngine.switchSchema(savedSchema)
            }
            
            Log.d(TAG, "initRimeEngine: Rime engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initRimeEngine: Failed to initialize Rime engine", e)
        }
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setContent {
                val isDarkTheme = isDarkTheme()
                KimeTheme(darkTheme = isDarkTheme) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        KeyboardView(
                            candidates = candidatesState.value,
                            inputText = inputTextState.value,
                            isComposing = isComposingState.value,
                            isAsciiMode = isAsciiModeState.value,
                            isDarkTheme = isDarkTheme,
                            onKeyPress = { key, isShifted ->
                                handleKeyPress(key, isShifted)
                            },
                            onCandidateSelect = { index ->
                                selectCandidate(index)
                            },
                            onToggleDarkMode = {
                                toggleDarkMode()
                            },
                            onClipboard = {
                                // TODO: 实现剪贴板功能
                                Log.d(TAG, "Clipboard clicked")
                            },
                            onQuickSend = {
                                // TODO: 实现快捷发送功能
                                Log.d(TAG, "QuickSend clicked")
                            },
                            onHandwriting = {
                                // TODO: 实现手写找字功能
                                Log.d(TAG, "Handwriting clicked")
                            },
                            onEmoji = {
                                // 表情功能
                                commitText("😊")
                            },
                            onReloadConfig = {
                                // 重载配置
                                reloadConfig()
                            },
                            onSettings = {
                                // 打开输入法设置
                                openSettings()
                            },
                            onMixedInput = {
                                // 切换五笔拼音混输
                                toggleMixedInput()
                            },
                            onHideKeyboard = {
                                // 收起键盘
                                hideKeyboard()
                            }
                        )
                    }
                }
            }
        }
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        loadDarkModePreference()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        rimeEngine.destroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    
    /**
     * 选择候选词
     */
    private fun selectCandidate(index: Int) {
        if (rimeEngine.selectCandidate(index)) {
            // 获取提交的文本
            val committedText = rimeEngine.commit()
            if (committedText.isNotEmpty()) {
                commitText(committedText)
            }
            updateUI()
        }
    }
    
    /**
     * 收起键盘
     */
    private fun hideKeyboard() {
        requestHideSelf(0)
    }
    
    /**
     * 更新 UI 状态
     */
    private fun updateUI() {
        // 获取当前输入编码
        inputTextState.value = rimeEngine.getInput()
        
        // 获取候选词列表
        candidatesState.value = rimeEngine.getCandidates()
        
        // 更新组合状态
        isComposingState.value = inputTextState.value.isNotEmpty()
        
        // 更新中英文模式状态
        isAsciiModeState.value = rimeEngine.isAsciiMode()
    }

    private fun handleKeyPress(key: String, isShifted: Boolean) {
        performKeyPressEffect()
        when (key) {
            "delete" -> {
                if (isComposingState.value) {
                    // 如果正在组合中，发送退格键给 Rime 处理
                    rimeEngine.processKey(KeyEvent.KEYCODE_DEL, 0)
                    updateUI()
                    
                    // 如果组合已清空，不需要额外处理
                    if (!isComposingState.value) {
                        rimeEngine.clearComposition()
                    }
                } else {
                    // 否则直接删除文本
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            }
            "enter" -> {
                if (isComposingState.value) {
                    // 如果正在组合中，提交当前输入
                    val committedText = rimeEngine.commit()
                    if (committedText.isNotEmpty()) {
                        commitText(committedText)
                    }
                    rimeEngine.clearComposition()
                    updateUI()
                } else {
                    // 否则发送回车键
                    val action = currentInputEditorInfo?.imeOptions ?: 0
                    when (action and EditorInfo.IME_MASK_ACTION) {
                        EditorInfo.IME_ACTION_GO,
                        EditorInfo.IME_ACTION_SEARCH,
                        EditorInfo.IME_ACTION_SEND,
                        EditorInfo.IME_ACTION_NEXT,
                        EditorInfo.IME_ACTION_DONE -> {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                        }
                        else -> {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                        }
                    }
                }
            }
            "space" -> {
                if (isComposingState.value) {
                    // 如果正在组合中，空格选择第一个候选词
                    if (candidatesState.value.isNotEmpty()) {
                        selectCandidate(0)
                    }
                } else {
                    commitText(" ")
                }
            }
            "shift" -> {
                // 切换大小写状态（暂不实现）
            }
            "mode_change" -> {
                // 切换输入模式（中文/英文）- 现在由 KeyboardView 处理
            }
            "ime_switch" -> {
                // 切换输入法模式（英文/五笔）
                switchInputMethod()
            }
            "abc" -> {
                // 返回全键盘 - 由 KeyboardView 处理
            }
            "emoji" -> {
                // 表情键 - 暂时输入一个默认表情
                commitText("😊")
            }
            else -> {
                // 处理数字、符号和字母键输入
                // 数字和符号直接输入，不经过 Rime
                if (key.matches(Regex("[0-9]")) ||
                    key in listOf("-", "/", ":", ";", "(", ")", "@", "\"", "'", "#", ".", ",", "!", "?", "，", "。")) {
                    // 直接输入数字和符号
                    if (isComposingState.value) {
                        // 如果正在组合中，先提交当前输入
                        val committedText = rimeEngine.commit()
                        if (committedText.isNotEmpty()) {
                            commitText(committedText)
                        }
                        rimeEngine.clearComposition()
                        updateUI()
                    }
                    commitText(key)
                } else {
                    // 处理字母键输入
                    val char = if (isShifted) key.uppercase() else key
                    
                    // 将按键发送给 Rime 引擎处理
                    // Rime 使用 ASCII keycode（a=97, b=98, ...）
                    // 对于小写字母，使用 ASCII 值；对于大写字母，也使用小写字母的 ASCII 值 + shift mask
                    val keyCode = key.lowercase()[0].code  // 使用小写字母的 ASCII 码
                    val mask = if (isShifted) KeyEvent.META_SHIFT_ON else 0
                    
                    Log.d(TAG, "Processing key: char=$char, keyCode=$keyCode, mask=$mask")
                    
                    val processed = rimeEngine.processKey(keyCode, mask)
                    
                    Log.d(TAG, "Rime processed: $processed, input=${rimeEngine.getInput()}")
                    
                    if (processed) {
                        // Rime 处理了按键，更新 UI
                        updateUI()
                        
                        // 检查是否有提交的文本
                        val committedText = rimeEngine.commit()
                        if (committedText.isNotEmpty()) {
                            commitText(committedText)
                            updateUI()
                        }
                    } else {
                        // Rime 没有处理按键，直接输入字符
                        if (!isComposingState.value) {
                            commitText(char)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 切换输入法模式（中文/英文）
     */
    private fun switchInputMethod() {
        Log.d(TAG, "Toggling ascii mode")
        rimeEngine.toggleAsciiMode()
        updateUI()
    }
    
    /**
     * 重载配置
     */
    private fun reloadConfig() {
        Log.d(TAG, "Reloading config...")
        Thread {
            try {
                KeysConfigHelper.loadConfig(this)
                
                // 清理 build 目录强制重新部署
                val userDataDir = File(filesDir, "rime/user")
                val buildDir = File(userDataDir, "build")
                if (buildDir.exists()) {
                    Log.d(TAG, "Cleaning build directory")
                    buildDir.deleteRecursively()
                }
                
                // 写入 default.custom.yaml 设置默认方案
                val savedSchema = SettingsPreferences.getCurrentSchema(this)
                Log.d(TAG, "Saved schema: $savedSchema")
                
                val customYaml = """# Rime default.custom.yaml
patch:
  "schema_list":
    - schema: wubi86
    - schema: wubi86_pinyin
"""
                
                val customFile = File(userDataDir, "default.custom.yaml")
                customFile.writeText(customYaml)
                Log.d(TAG, "Wrote default.custom.yaml")
                
                // 部署
                Log.d(TAG, "Starting deployment...")
                val deployResult = rimeEngine.deploy()
                Log.d(TAG, "Deploy result: $deployResult")
                
                // 获取可用方案列表
                val availableSchemas = rimeEngine.getAvailableSchemas()
                Log.d(TAG, "Available schemas: ${availableSchemas.joinToString()}")
                
                // 切换方案
                if (savedSchema in availableSchemas) {
                    val switchResult = rimeEngine.switchSchema(savedSchema)
                    Log.d(TAG, "Switch schema result: $switchResult")
                } else {
                    Log.w(TAG, "Schema $savedSchema not found in available schemas")
                }
                
                // 在主线程更新 UI
                mainHandler.post {
                    updateUI()
                    Log.d(TAG, "Config reloaded successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload config", e)
            }
        }.start()
    }
    
    /**
     * 部署方案
     */
    private fun deploySchema() {
        Log.d(TAG, "Deploying schema...")
        try {
            rimeEngine.deploy()
            val savedSchema = SettingsPreferences.getCurrentSchema(this)
            rimeEngine.switchSchema(savedSchema)
            updateUI()
            Log.d(TAG, "Schema deployed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy schema", e)
        }
    }
    
    /**
     * 打开输入法设置
     */
    private fun openSettings() {
        Log.d(TAG, "Opening settings...")
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open settings", e)
        }
    }
    
    /**
     * 切换五笔拼音混输
     */
    private fun toggleMixedInput() {
        Log.d(TAG, "Toggling mixed input...")
        try {
            val currentSchema = rimeEngine.getCurrentSchema()
            val newSchema = if (currentSchema.contains("pinyin")) {
                "wubi86"
            } else {
                "wubi86_pinyin"
            }
            SettingsPreferences.setCurrentSchema(this, newSchema)
            rimeEngine.switchSchema(newSchema)
            rimeEngine.deploy()
            updateUI()
            Log.d(TAG, "Switched to schema: $newSchema")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle mixed input", e)
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
}