package com.kingzcheung.kime.service

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.kingzcheung.kime.ui.theme.KimeTheme
import com.kingzcheung.kime.ui.KeyboardView

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
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // Rime 引擎实例
    private val rimeEngine = RimeEngine()
    
    // UI 状态
    private val candidatesState = mutableStateOf<Array<String>>(emptyArray())
    private val inputTextState = mutableStateOf("")
    private val isComposingState = mutableStateOf(false)
    private val isAsciiModeState = mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        // 关键：在 window 的 decorView 上设置 LifecycleOwner 和 SavedStateRegistryOwner
        // 这样 ComposeView 就能找到它们
        window.window?.decorView?.setViewTreeLifecycleOwner(this)
        window.window?.decorView?.setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        // 初始化 Rime 引擎
        initRimeEngine()
    }
    
    /**
     * 初始化 Rime 引擎
     */
    private fun initRimeEngine() {
        Log.d(TAG, "initRimeEngine: Starting initialization...")
        try {
            // 使用 RimeConfigHelper 初始化数据目录和配置文件
            Log.d(TAG, "initRimeEngine: Calling RimeConfigHelper.initializeRimeData...")
            val (userDataDir, sharedDataDir) = RimeConfigHelper.initializeRimeData(this)
            
            Log.d(TAG, "initRimeEngine: userDataDir=$userDataDir, sharedDataDir=$sharedDataDir")
            
            Log.d(TAG, "initRimeEngine: Calling rimeEngine.initialize...")
            rimeEngine.initialize(userDataDir, sharedDataDir)
            Log.d(TAG, "initRimeEngine: Rime engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initRimeEngine: Failed to initialize Rime engine", e)
        }
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setContent {
                KimeTheme {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        KeyboardView(
                            candidates = candidatesState.value,
                            inputText = inputTextState.value,
                            isComposing = isComposingState.value,
                            isAsciiMode = isAsciiModeState.value,
                            onKeyPress = { key, isShifted ->
                                handleKeyPress(key, isShifted)
                            },
                            onCandidateSelect = { index ->
                                selectCandidate(index)
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

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }
}