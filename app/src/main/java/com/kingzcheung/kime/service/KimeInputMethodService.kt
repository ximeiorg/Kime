package com.kingzcheung.kime.service

import android.content.Intent
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
import com.kingzcheung.kime.MainActivity
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kingzcheung.kime.clipboard.ClipboardManager
import com.kingzcheung.kime.rime.RimeConfigHelper
import com.kingzcheung.kime.rime.RimeEngine
import com.kingzcheung.kime.settings.SchemaConfigHelper
import com.kingzcheung.kime.settings.SettingsPreferences
import com.kingzcheung.kime.ui.KeysConfigHelper
import com.kingzcheung.kime.ui.theme.KimeTheme
import com.kingzcheung.kime.ui.KeyboardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
    
    // 剪切板管理器
    private lateinit var clipboardManager: ClipboardManager
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // UI 状态
    private val candidatesState = mutableStateOf<Array<String>>(emptyArray())
    private val candidateCommentsState = mutableStateOf<Array<String>>(emptyArray())
    private val inputTextState = mutableStateOf("")
    private val isComposingState = mutableStateOf(false)
    private val isAsciiModeState = mutableStateOf(false)
    private val schemaNameState = mutableStateOf("")
    private val enterKeyTextState = mutableStateOf("发送")
    private val darkModeState = mutableStateOf(DARK_MODE_LIGHT)
    private val themeIdState = mutableStateOf("ocean_blue")
    private val clipboardItemsState = mutableStateOf<List<com.kingzcheung.kime.clipboard.ClipboardItem>>(emptyList())
    private val quickSendItemsState = mutableStateOf<List<com.kingzcheung.kime.clipboard.ClipboardItem>>(emptyList())
    
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
        themeIdState.value = SettingsPreferences.getKeyboardTheme(this)
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
        initClipboardManager()
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
            
            updateSchemaName()
            
            Log.d(TAG, "initRimeEngine: Rime engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initRimeEngine: Failed to initialize Rime engine", e)
        }
    }
    
    /**
     * 初始化剪切板管理器
     */
    private fun initClipboardManager() {
        Log.d(TAG, "initClipboardManager: Starting initialization...")
        try {
            clipboardManager = ClipboardManager.getInstance(this)
            clipboardItemsState.value = clipboardManager.clipboardItems.value
            quickSendItemsState.value = clipboardManager.quickSendItems.value
            
            serviceScope.launch {
                clipboardManager.clipboardItems.collect { items ->
                    clipboardItemsState.value = items
                }
            }
            
            serviceScope.launch {
                clipboardManager.quickSendItems.collect { items ->
                    quickSendItemsState.value = items
                }
            }
            Log.d(TAG, "initClipboardManager: Clipboard manager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initClipboardManager: Failed to initialize clipboard manager", e)
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
                            .height(300.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        KeyboardView(
                            candidates = candidatesState.value,
                            inputText = inputTextState.value,
                            isComposing = isComposingState.value,
                            isAsciiMode = isAsciiModeState.value,
                            schemaName = schemaNameState.value,
                            enterKeyText = enterKeyTextState.value,
                            isDarkTheme = isDarkTheme,
                            themeId = themeIdState.value,
                            clipboardItems = clipboardItemsState.value,
                            quickSendItems = quickSendItemsState.value,
                            candidateComments = candidateCommentsState.value,
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
                                Log.d(TAG, "Clipboard clicked")
                            },
                            onClipboardSelect = { text ->
                                selectClipboardItem(text)
                            },
                            onClipboardRemove = { id ->
                                removeClipboardItem(id)
                            },
                            onClipboardTogglePin = { id ->
                                toggleClipboardPin(id)
                            },
                            onClipboardClearAll = {
                                clearClipboard()
                            },
                            onAddToQuickSend = { id ->
                                addToQuickSend(id)
                            },
                            onRemoveFromQuickSend = { id ->
                                removeFromQuickSend(id)
                            },
                            onQuickSend = {
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
                                hideKeyboard()
                            },
                            onSwitchKeyboard = {
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                @Suppress("DEPRECATION")
                                imm.showInputMethodPicker()
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
        
        // 更新 Enter 键文字
        attribute?.let { updateEnterKeyText(it) }
    }
    
    private fun updateEnterKeyText(editorInfo: EditorInfo) {
        val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        enterKeyTextState.value = when (action) {
            EditorInfo.IME_ACTION_GO -> "前往"
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_NEXT -> "下一项"
            EditorInfo.IME_ACTION_DONE -> "完成"
            else -> "换行"
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        super.onDestroy()
        rimeEngine.destroy()
        serviceScope.cancel()
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
        inputTextState.value = rimeEngine.getInput()
        val candidatesWithComments = rimeEngine.getCandidatesWithComments()
        candidatesState.value = candidatesWithComments.map { it.text }.toTypedArray()
        candidateCommentsState.value = candidatesWithComments.map { it.comment }.toTypedArray()
        isComposingState.value = inputTextState.value.isNotEmpty()
        isAsciiModeState.value = rimeEngine.isAsciiMode()
        Log.d(TAG, "updateUI: inputText='${inputTextState.value}', isComposing=${isComposingState.value}, candidates=${candidatesState.value.size}")
    }
    
    private fun updateSchemaName() {
        val currentSchemaId = rimeEngine.getCurrentSchema()
        val schemas = SchemaConfigHelper.loadSchemas(this)
        val schemaInfo = schemas.find { it.schemaId == currentSchemaId }
        schemaNameState.value = schemaInfo?.name ?: currentSchemaId
    }

    private fun handleKeyPress(key: String, isShifted: Boolean) {
        performKeyPressEffect()
        when (key) {
            "delete" -> {
                Log.d(TAG, "delete key: isComposing=${isComposingState.value}, inputText='${inputTextState.value}'")
                if (isComposingState.value || inputTextState.value.isNotEmpty()) {
                    // 如果正在组合中或有输入编码，发送退格键给 Rime 处理
                    // Rime使用X11 KeySym键码：BackSpace = 0xff08 (65288)
                    rimeEngine.processKey(0xff08, 0)  // X11 KeySym for BackSpace
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
            "clear_composition" -> {
                Log.d(TAG, "clear_composition: inputText='${inputTextState.value}'")
                // 清空正在输入的编码
                rimeEngine.clearComposition()
                updateUI()
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
                    // 如果正在组合中
                    if (candidatesState.value.isNotEmpty()) {
                        // 有候选词，选择第一个
                        selectCandidate(0)
                    } else {
                        // 没有候选词，输入当前编码的字母
                        val input = inputTextState.value
                        if (input.isNotEmpty()) {
                            commitText(input)
                            rimeEngine.clearComposition()
                            updateUI()
                        }
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
                    updateSchemaName()
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
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
            updateSchemaName()
            updateUI()
            Log.d(TAG, "Switched to schema: $newSchema")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle mixed input", e)
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
    
    /**
     * 选择剪切板项
     */
    private fun selectClipboardItem(text: String) {
        // 如果正在组合中，先清除
        if (isComposingState.value) {
            rimeEngine.clearComposition()
            updateUI()
        }
        commitText(text)
        clipboardManager.copyToSystemClipboard(text)
    }
    
    /**
     * 删除剪切板项
     */
    private fun removeClipboardItem(id: Long) {
        clipboardManager.removeItem(id)
    }
    
    /**
     * 切换剪切板项置顶状态
     */
    private fun toggleClipboardPin(id: Long) {
        clipboardManager.togglePin(id)
    }
    
    /**
     * 清空剪切板
     */
    private fun clearClipboard() {
        clipboardManager.clearAll()
    }
    
    /**
     * 添加到快捷发送
     */
    private fun addToQuickSend(id: Long) {
        clipboardManager.addToQuickSend(id)
    }
    
    /**
     * 从快捷发送移除
     */
    private fun removeFromQuickSend(id: Long) {
        clipboardManager.removeFromQuickSend(id)
    }
}