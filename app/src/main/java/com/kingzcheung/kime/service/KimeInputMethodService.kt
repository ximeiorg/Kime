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
import kotlinx.coroutines.withContext
import java.io.File

data class InputUIState(
    val candidates: Array<String> = emptyArray(),
    val candidateComments: Array<String> = emptyArray(),
    val inputText: String = "",
    val isComposing: Boolean = false,
    val isAsciiMode: Boolean = false,
    val schemaName: String = "",
    val enterKeyText: String = "发送",
    val darkMode: Int = 0,
    val themeId: String = "ocean_blue",
    val showBottomButtons: Boolean = false
)

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
    
    // UI 状态 - 合并为单一状态对象，减少Compose重组
    private val uiState = mutableStateOf(InputUIState())
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
    
    private fun playKeySound(keyType: String = "standard") {
        if (!SettingsPreferences.isSoundEnabled(this)) return
        
        val volume = SettingsPreferences.getSoundVolume(this) / 100f
        val soundVolume = (volume * 100).toInt()
        
        val effectType = when (keyType) {
            "delete" -> AudioManager.FX_KEYPRESS_DELETE
            "enter" -> AudioManager.FX_KEYPRESS_RETURN
            "space" -> AudioManager.FX_KEYPRESS_SPACEBAR
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        
        audioManager.playSoundEffect(effectType, soundVolume / 100f)
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
    
    private fun performKeyPressEffect(keyType: String = "standard") {
        playKeySound(keyType)
        performVibration()
    }
    
    private fun loadDarkModePreference() {
        uiState.value = uiState.value.copy(
            darkMode = SettingsPreferences.getDarkMode(this),
            themeId = SettingsPreferences.getKeyboardTheme(this),
            showBottomButtons = SettingsPreferences.showBottomButtons(this)
        )
    }
    
    private fun saveDarkModePreference(mode: Int) {
        SettingsPreferences.setDarkMode(this, mode)
        uiState.value = uiState.value.copy(darkMode = mode)
    }
    
    fun toggleDarkMode() {
        val newMode = if (uiState.value.darkMode == DARK_MODE_LIGHT) DARK_MODE_DARK else DARK_MODE_LIGHT
        saveDarkModePreference(newMode)
    }
    
    fun isDarkTheme(): Boolean {
        return uiState.value.darkMode == DARK_MODE_DARK
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
                val state = uiState.value
                val isDarkTheme = isDarkTheme()
                KimeTheme(darkTheme = isDarkTheme) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(290.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        KeyboardView(
                            candidates = state.candidates,
                            inputText = state.inputText,
                            isComposing = state.isComposing,
                            isAsciiMode = state.isAsciiMode,
                            schemaName = state.schemaName,
                            enterKeyText = state.enterKeyText,
                            isDarkTheme = isDarkTheme,
                            themeId = state.themeId,
                            showBottomButtons = state.showBottomButtons,
                            clipboardItems = clipboardItemsState.value,
                            quickSendItems = quickSendItemsState.value,
                            candidateComments = state.candidateComments,
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
                                Log.d(TAG, "Handwriting clicked")
                            },
                            onEmoji = {
                                commitText("😊")
                            },
                            onReloadConfig = {
                                reloadConfig()
                            },
                            onSettings = {
                                openSettings()
                            },
                            onMixedInput = {
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
        val enterText = when (action) {
            EditorInfo.IME_ACTION_GO -> "前往"
            EditorInfo.IME_ACTION_SEARCH -> "搜索"
            EditorInfo.IME_ACTION_SEND -> "发送"
            EditorInfo.IME_ACTION_NEXT -> "下一项"
            EditorInfo.IME_ACTION_DONE -> "完成"
            else -> "换行"
        }
        uiState.value = uiState.value.copy(enterKeyText = enterText)
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
     * 收起键盘
     */
    private fun hideKeyboard() {
        requestHideSelf(0)
    }
    
    /**
     * 更新 UI 状态 - 合并所有状态更新，减少Compose重组次数
     */
    private fun updateUI() {
        val inputText = rimeEngine.getInput()
        val candidatesWithComments = rimeEngine.getCandidatesWithComments()
        
        uiState.value = uiState.value.copy(
            inputText = inputText,
            candidates = candidatesWithComments.map { it.text }.toTypedArray(),
            candidateComments = candidatesWithComments.map { it.comment }.toTypedArray(),
            isComposing = inputText.isNotEmpty(),
            isAsciiMode = rimeEngine.isAsciiMode()
        )
    }
    
    private fun updateSchemaName() {
        val currentSchemaId = rimeEngine.getCurrentSchema()
        val schemas = SchemaConfigHelper.loadSchemas(this)
        val schemaInfo = schemas.find { it.schemaId == currentSchemaId }
        uiState.value = uiState.value.copy(schemaName = schemaInfo?.name ?: currentSchemaId)
    }

    private fun handleKeyPress(key: String, isShifted: Boolean) {
        val keyType = when (key) {
            "delete", "clear_composition" -> "delete"
            "enter" -> "enter"
            "space" -> "space"
            else -> "standard"
        }
        performKeyPressEffect(keyType)
        
        serviceScope.launch(Dispatchers.Default) {
            val state = uiState.value
            var needsUIUpdate = false
            
            when (key) {
                "delete" -> {
                    if (state.isComposing || state.inputText.isNotEmpty()) {
                        rimeEngine.processKey(0xff08, 0)
                        needsUIUpdate = true
                        
                        if (!rimeEngine.getInput().isNotEmpty()) {
                            rimeEngine.clearComposition()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        }
                    }
                }
                "clear_composition" -> {
                    rimeEngine.clearComposition()
                    needsUIUpdate = true
                }
                "enter" -> {
                    if (state.isComposing) {
                        val committedText = rimeEngine.commit()
                        if (committedText.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                commitText(committedText)
                            }
                        }
                        rimeEngine.clearComposition()
                        needsUIUpdate = true
                    } else {
                        withContext(Dispatchers.Main) {
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
                }
                "space" -> {
                    if (state.isComposing) {
                        if (state.candidates.isNotEmpty()) {
                            selectCandidateAsync(0)
                        } else {
                            val input = state.inputText
                            if (input.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    commitText(input)
                                }
                                rimeEngine.clearComposition()
                                needsUIUpdate = true
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            commitText(" ")
                        }
                    }
                }
                "shift" -> {
                }
                "mode_change" -> {
                }
                "ime_switch" -> {
                    withContext(Dispatchers.Main) {
                        switchInputMethod()
                    }
                }
                "abc" -> {
                }
                "emoji" -> {
                    withContext(Dispatchers.Main) {
                        commitText("😊")
                    }
                }
                else -> {
                    if (key.matches(Regex("[0-9]")) ||
                        key in listOf("-", "/", ":", ";", "(", ")", "@", "\"", "'", "#", ".", ",", "!", "?", "，", "。")) {
                        if (state.isComposing) {
                            val committedText = rimeEngine.commit()
                            if (committedText.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    commitText(committedText)
                                }
                            }
                            rimeEngine.clearComposition()
                            needsUIUpdate = true
                        }
                        withContext(Dispatchers.Main) {
                            commitText(key)
                        }
                    } else {
                        val char = if (isShifted) key.uppercase() else key
                        val keyCode = key.lowercase()[0].code
                        val mask = if (isShifted) KeyEvent.META_SHIFT_ON else 0
                        
                        val processed = rimeEngine.processKey(keyCode, mask)
                        
                        if (processed) {
                            needsUIUpdate = true
                            
                            val committedText = rimeEngine.commit()
                            if (committedText.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    commitText(committedText)
                                }
                                needsUIUpdate = true
                            }
                        } else {
                            if (!state.isComposing) {
                                withContext(Dispatchers.Main) {
                                    commitText(char)
                                }
                            }
                        }
                    }
                }
            }
            
            if (needsUIUpdate) {
                withContext(Dispatchers.Main) {
                    updateUI()
                }
            }
        }
    }
    
    private suspend fun selectCandidateAsync(index: Int) {
        if (rimeEngine.selectCandidate(index)) {
            val committedText = rimeEngine.commit()
            if (committedText.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    commitText(committedText)
                }
            }
            withContext(Dispatchers.Main) {
                updateUI()
            }
        }
    }
    
    private fun selectCandidate(index: Int) {
        serviceScope.launch(Dispatchers.Default) {
            selectCandidateAsync(index)
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
        if (uiState.value.isComposing) {
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