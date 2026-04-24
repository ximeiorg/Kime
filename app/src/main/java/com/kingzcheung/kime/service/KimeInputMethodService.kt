package com.kingzcheung.kime.service

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.kingzcheung.kime.ui.KeyboardResizeOverlay
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kingzcheung.kime.MainActivity
import com.kingzcheung.kime.association.AssociationManager
import com.kingzcheung.kime.association.AssociationService
import com.kingzcheung.kime.clipboard.ClipboardManager
import com.kingzcheung.kime.plugin.ExtensionManager
import com.kingzcheung.kime.speech.RecognitionState
import com.kingzcheung.kime.rime.RimeConfigHelper
import com.kingzcheung.kime.rime.RimeEngine
import com.kingzcheung.kime.settings.SchemaConfigHelper
import com.kingzcheung.kime.settings.SettingsPreferences
import com.kingzcheung.kime.ui.KeyboardView
import com.kingzcheung.kime.ui.KeysConfigHelper
import com.kingzcheung.kime.ui.theme.KimeTheme
import com.kingzcheung.kime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

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

    private val rimeEngine = RimeEngine.getInstance()
    
    private lateinit var clipboardManager: ClipboardManager
    
    private lateinit var keyboardContainer: VoiceKeyboardContainer
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val uiState = mutableStateOf(InputUIState())
    private val clipboardItemsState = mutableStateOf<List<com.kingzcheung.kime.clipboard.ClipboardItem>>(emptyList())
    private val quickSendItemsState = mutableStateOf<List<com.kingzcheung.kime.clipboard.ClipboardItem>>(emptyList())
    
    private var isTrackingVoiceButtons = false
    private var voiceRecordingStarted = false
    
    private val predictionManager = PredictionManager(
        context = this,
        serviceScope = serviceScope,
        onStateChanged = { newState -> uiState.value = newState },
        getState = { uiState.value }
    )
    
    private val voiceRecognitionHandler = VoiceRecognitionHandler(
        context = this,
        onStateChanged = { newState -> uiState.value = newState },
        getState = { uiState.value },
        getInputConnection = { currentInputConnection }
    )
    
    private var sharedPrefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    
    private val feedbackManager = FeedbackManager(this)
    
    private fun loadDarkModePreference() {
        uiState.value = uiState.value.copy(
            darkMode = SettingsPreferences.getDarkMode(this),
            themeId = SettingsPreferences.getKeyboardTheme(this),
            showBottomButtons = SettingsPreferences.showBottomButtons(this),
            keyboardHeightDp = SettingsPreferences.getKeyboardHeightDp(this)
        )
    }
    
    private fun registerSharedPrefsListener() {
        val prefs = SettingsPreferences.getPrefsPublic(this)
        sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "dark_mode", "keyboard_theme", "show_bottom_buttons", "keyboard_height_dp" -> {
                    loadDarkModePreference()
                    Log.d(TAG, "Settings changed: $key, updated UI state")
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(sharedPrefsListener)
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
        
        FileLogger.init(this)
        FileLogger.i(TAG, "KimeInputMethodService created")
        
        loadDarkModePreference()
        registerSharedPrefsListener()
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                initRimeEngine()
                initClipboardManager()
                initAssociationEngine()
                initSpeechRecognition()
                
                withContext(Dispatchers.Main) {
                    FileLogger.i(TAG, "Service initialization completed")
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Initialization failed: ${e.message}")
            }
        }
    }
    
    private fun initSpeechRecognition() {
        voiceRecognitionHandler.initialize()
    }
    
    private fun initAssociationEngine() {
        predictionManager.initialize()
    }
    
    private fun checkAndInitializeAssociationEngine() {
        predictionManager.checkAndInitialize()
    }
    
    private fun getPredictionFromPlugin(contextText: String) {
        predictionManager.getPrediction(contextText)
    }
    
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
            
            val availableSchemas = rimeEngine.getAvailableSchemas()
            Log.d(TAG, "initRimeEngine: availableSchemas=${availableSchemas.joinToString()}")
            
            if (savedSchema in availableSchemas && currentSchema != savedSchema) {
                Log.d(TAG, "initRimeEngine: Switching to saved schema: $savedSchema")
                rimeEngine.switchSchema(savedSchema)
            }
            
            updateSchemaName()
            
            Log.d(TAG, "initRimeEngine: Rime engine initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "initRimeEngine: Failed to initialize Rime engine", e)
        }
    }
    
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
        keyboardContainer = VoiceKeyboardContainer(
            context = this,
            uiStateProvider = { uiState.value },
            onUiStateChanged = { newState -> uiState.value = newState },
            onPerformVibration = { feedbackManager.performVibration() },
            onPerformUndo = { performUndo() },
            onPerformSearch = { performSearch() },
            onStopRecognition = { voiceRecognitionHandler.stopRecognition() },
            isRecording = { voiceRecordingStarted },
            setRecording = { voiceRecordingStarted = it }
        )
        
        val composeView = ComposeView(this).apply {
            setContent {
                val state = uiState.value
                val isDarkTheme = isDarkTheme()
                val screenHeightDp = resources.configuration.screenHeightDp
                val maxHeightDp = screenHeightDp / 2
                
                KimeTheme(darkTheme = isDarkTheme, themeId = state.themeId) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (state.showKeyboardResize) maxHeightDp.dp else state.keyboardHeightDp.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(if (state.showKeyboardResize) state.resizePreviewHeightDp.dp else state.keyboardHeightDp.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                        val isResizing = state.showKeyboardResize
                        val isStretching = isResizing && state.stretchFactor > 1f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (isStretching) {
                                        Modifier.graphicsLayer {
                                            scaleY = state.stretchFactor
                                            transformOrigin = TransformOrigin(0.5f, 1f)
                                        }
                                    } else Modifier
                                ),
                            contentAlignment = if (isStretching) Alignment.BottomCenter else Alignment.TopStart
                        ) {
                            KeyboardView(
                                modifier = if (isStretching) Modifier.height(state.keyboardHeightDp.dp) else Modifier,
                                candidates = state.candidates,
                                inputText = state.inputText,
                                isComposing = state.isComposing,
                                isAsciiMode = state.isAsciiMode,
                                schemaName = state.schemaName,
                                currentSchemaId = state.currentSchemaId,
                                schemas = state.schemas,
                                enterKeyText = state.enterKeyText,
                            isDarkTheme = isDarkTheme,
                            themeId = state.themeId,
                            showBottomButtons = state.showBottomButtons,
                            keyboardHeightDp = state.keyboardHeightDp,
                            clipboardItems = clipboardItemsState.value,
                            quickSendItems = quickSendItemsState.value,
                            candidateComments = state.candidateComments,
                            isVoiceMode = state.isVoiceMode,
                            voiceBottomActive = state.voiceButtonState.bottomActive,
                            voiceLeftActive = state.voiceButtonState.leftActive,
                            voiceRightActive = state.voiceButtonState.rightActive,
                            voicePluginName = state.voicePluginName,
                            voiceRecognitionState = state.voiceRecognitionState,
                            voiceRecognizedText = state.voiceRecognizedText,
                            voiceAmplitude = state.voiceAmplitude,
                            onKeyPress = { key, isShifted ->
                                handleKeyPress(key, isShifted)
                            },
                            onKeyPressDown = { key ->
                                feedbackManager.performKeyPressDownEffect(key)
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
                            onAddToQuickSend = { id ->
                                addToQuickSend(id)
                            },
                            onRemoveFromQuickSend = { id ->
                                removeFromQuickSend(id)
                            },
                            onQuickSend = {
                                Log.d(TAG, "QuickSend clicked")
                            },
                            onKeyboardResize = {
                                val currentHeight = uiState.value.keyboardHeightDp
                                uiState.value = uiState.value.copy(
                                    showKeyboardResize = true,
                                    resizePreviewHeightDp = currentHeight,
                                    originalKeyboardHeightDp = currentHeight
                                )
                            },
                            onReloadConfig = {
                                reloadConfig()
                            },
                            onSettings = {
                                openSettings()
                            },
                            onSwitchSchema = { schemaId ->
                                switchSchema(schemaId)
                            },
                            onHideKeyboard = {
                                hideKeyboard()
                            },
                            onSwitchKeyboard = {
                                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                @Suppress("DEPRECATION")
                                imm.showInputMethodPicker()
                            },
                            associationCandidates = if (state.pendingEnglishText.isNotEmpty()) {
                                arrayOf(state.pendingEnglishText) + state.associationCandidates
                            } else {
                                state.associationCandidates
                            },
                            onAssociationSelect = { index ->
                                val adjustedCandidates = if (state.pendingEnglishText.isNotEmpty()) {
                                    arrayOf(state.pendingEnglishText) + state.associationCandidates
                                } else {
                                    state.associationCandidates
                                }
                                
                                if (index >= 0 && index < adjustedCandidates.size) {
                                    val text = adjustedCandidates[index]
                                    val pendingEnglish = state.pendingEnglishText
                                    
                                    if (pendingEnglish.isNotEmpty()) {
                                        if (index == 0 && text == pendingEnglish) {
                                            uiState.value = uiState.value.copy(
                                                pendingEnglishText = "",
                                                associationCandidates = emptyArray()
                                            )
                                            Log.d(TAG, "Confirmed pending English: '$text'")
                                        } else {
                                            currentInputConnection?.deleteSurroundingText(pendingEnglish.length, 0)
                                            commitText(text)
                                            uiState.value = uiState.value.copy(
                                                pendingEnglishText = "",
                                                associationCandidates = emptyArray()
                                            )
                                            Log.d(TAG, "Replaced '$pendingEnglish' with association: '$text'")
                                        }
                                    } else {
                                        commitText(text)
                                        updateUI()
                                    }
                                }
                            },
                            onCommitImage = { imagePath ->
                                val success = commitImage(imagePath)
                                if (!success) {
                                    android.widget.Toast.makeText(
                                        this@KimeInputMethodService,
                                        "发送失败，已复制到剪贴板",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    clipboardManager.copyImageToSystemClipboard(imagePath)
                                }
                            },
                            onVoiceModeChange = { enabled ->
                                Log.d("VoiceButtons", "onVoiceModeChange called: enabled=$enabled")
                                uiState.value = uiState.value.copy(
                                    isVoiceMode = enabled,
                                    voiceButtonState = if (enabled) VoiceButtonState(bottomActive = true) else VoiceButtonState(),
                                    voiceRecognizedText = ""
                                )
                                if (enabled) {
                                    feedbackManager.performVibration()
                                    isTrackingVoiceButtons = true
                                    val started = voiceRecognitionHandler.startRecognition()
                                    voiceRecordingStarted = started
                                    Log.d("VoiceButtons", "Speech recognition started: $started")
                                    if (!started) {
                                        Log.e(TAG, "Failed to start speech recognition")
                                        uiState.value = uiState.value.copy(
                                            isVoiceMode = false,
                                            voiceRecognitionState = RecognitionState.ERROR
                                        )
                                        isTrackingVoiceButtons = false
                                    }
                                } else {
                                    isTrackingVoiceButtons = false
                                }
}
                             )
                        }
                     }
                     
                     if (state.showKeyboardResize) {
                         val screenHeightDp = resources.configuration.screenHeightDp
                         val maxContainerHeightDp = screenHeightDp / 2
                         
                         KeyboardResizeOverlay(
                             initialHeightDp = state.resizePreviewHeightDp,
                             defaultHeightDp = SettingsPreferences.getDefaultKeyboardHeightDp(),
                             maxContainerHeightDp = maxContainerHeightDp,
                              onHeightChange = { newHeight, isStretch ->
                                  val currentState = uiState.value
                                  val newStretchFactor = if (isStretch && currentState.keyboardHeightDp > 0) {
                                      newHeight.toFloat() / currentState.keyboardHeightDp.toFloat()
                                  } else {
                                      1f
                                  }
                                  uiState.value = currentState.copy(
                                      resizePreviewHeightDp = newHeight,
                                      stretchFactor = newStretchFactor
                                  )
                              },
                             onHeightChangeEnd = { newHeight ->
                                 uiState.value = uiState.value.copy(
                                     keyboardHeightDp = newHeight
                                 )
                             },
                              onReset = { defaultHeight ->
                                  uiState.value = uiState.value.copy(
                                      resizePreviewHeightDp = defaultHeight,
                                      stretchFactor = 1f
                                  )
                              },
                             onConfirm = { newHeight ->
                                 setKeyboardHeight(newHeight)
                                 uiState.value = uiState.value.copy(
                                     showKeyboardResize = false
                                 )
                             },
                             onCancel = {
                                 uiState.value = uiState.value.copy(
                                     showKeyboardResize = false,
                                     keyboardHeightDp = state.originalKeyboardHeightDp,
                                     resizePreviewHeightDp = state.originalKeyboardHeightDp
                                 )
                             },
                             modifier = Modifier.fillMaxWidth()
                         )
                     }
                    }
                }
            }
        }
        
        keyboardContainer.addView(composeView)
        
        val initialHeightDp = SettingsPreferences.getKeyboardHeightDp(this)
        keyboardContainer.updateHeight(initialHeightDp)
        
        return keyboardContainer
    }
    
    private fun performUndo() {
        val currentTextBeforeCursor = currentInputConnection?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
        val currentLength = currentTextBeforeCursor.length
        
        val charsToDelete = currentLength - voiceRecognitionHandler.textLengthBeforeVoiceInput
        
        Log.d("VoiceButtons", "Undo: currentLength=$currentLength, savedLength=${voiceRecognitionHandler.textLengthBeforeVoiceInput}, charsToDelete=$charsToDelete")
        
        if (charsToDelete > 0) {
            for (i in 0 until charsToDelete) {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            Log.d("VoiceButtons", "Deleted $charsToDelete characters")
        } else {
            Log.d("VoiceButtons", "No characters to delete")
        }
        
        voiceRecognitionHandler.textBeforeVoiceInput = ""
        voiceRecognitionHandler.textLengthBeforeVoiceInput = 0
    }
    
    private fun performSearch() {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        loadDarkModePreference()
        
        predictionManager.lastCommittedText = ""
        Log.d(TAG, "onStartInput: cleared lastCommittedText")
        
        checkAndInitializeAssociationEngine()
        
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
        clearInputState()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        clearInputState()
    }
    
    private fun clearInputState() {
        rimeEngine.clearComposition()
        uiState.value = uiState.value.copy(
            candidates = emptyArray(),
            candidateComments = emptyArray(),
            inputText = "",
            isComposing = false
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefsListener?.let {
            SettingsPreferences.getPrefsPublic(this).unregisterOnSharedPreferenceChangeListener(it)
        }
        rimeEngine.destroy()
        voiceRecognitionHandler.release()
        ExtensionManager.release()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    
    private fun hideKeyboard() {
        requestHideSelf(0)
    }
    
    private fun updateUI() {
        val inputText = rimeEngine.getInput()
        val candidatesWithComments = rimeEngine.getCandidatesWithComments()
        
        uiState.value = uiState.value.copy(
            inputText = inputText,
            candidates = candidatesWithComments.map { it.text }.toTypedArray(),
            candidateComments = candidatesWithComments.map { it.comment }.toTypedArray(),
            isComposing = inputText.isNotEmpty(),
            isAsciiMode = rimeEngine.isAsciiMode(),
            associationCandidates = emptyArray()
        )
        
        val pendingEnglish = uiState.value.pendingEnglishText
        
        if (pendingEnglish.isNotEmpty()) {
            serviceScope.launch {
                val candidates = predictionManager.getEnglishAssociations(pendingEnglish, 5)
                Log.d(TAG, "English association for pending '$pendingEnglish': ${candidates.joinToString()}")
                withContext(Dispatchers.Main) {
                    uiState.value = uiState.value.copy(associationCandidates = candidates)
                }
            }
        } else if (SettingsPreferences.isSmartPredictionEnabled(this) && inputText.isEmpty() && predictionManager.lastCommittedText.isNotEmpty()) {
            val isAscii = rimeEngine.isAsciiMode()
            if (!isAscii) {
                serviceScope.launch {
                    val candidates = predictionManager.getChineseAssociations(predictionManager.lastCommittedText, 5)
                    Log.d(TAG, "Chinese association candidates: ${candidates.joinToString()}")
                    withContext(Dispatchers.Main) {
                        uiState.value = uiState.value.copy(associationCandidates = candidates)
                    }
                }
            }
        }
    }
    
    private fun updateSchemaName() {
        val currentSchemaId = rimeEngine.getCurrentSchema()
        val schemas = SchemaConfigHelper.loadSchemas(this)
        val schemaInfo = schemas.find { it.schemaId == currentSchemaId }
        uiState.value = uiState.value.copy(
            schemaName = schemaInfo?.name ?: currentSchemaId,
            currentSchemaId = currentSchemaId,
            schemas = schemas
        )
    }

    private fun handleKeyPress(key: String, isShifted: Boolean) {
        serviceScope.launch(Dispatchers.Default) {
            val state = uiState.value
            var needsUIUpdate = false
            
            when (key) {
                "delete" -> {
                    val pendingEnglish = state.pendingEnglishText
                    
                    if (pendingEnglish.isNotEmpty()) {
                        val newPending = pendingEnglish.dropLast(1)
                        withContext(Dispatchers.Main) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                            uiState.value = uiState.value.copy(
                                pendingEnglishText = newPending,
                                associationCandidates = emptyArray()
                            )
                        }
                        needsUIUpdate = true
                        Log.d(TAG, "Delete English pending: '$newPending'")
                    } else if (state.isComposing || state.inputText.isNotEmpty()) {
                        rimeEngine.processKey(0xff08, 0)
                        
                        val currentInput = rimeEngine.getInput()
                        if (currentInput.isEmpty()) {
                            rimeEngine.clearComposition()
                            Log.d(TAG, "Delete: encoding cleared, cleared composition and candidates")
                        }
                        
                        needsUIUpdate = true
                    } else {
                        if (predictionManager.lastCommittedText.isNotEmpty()) {
                            predictionManager.lastCommittedText = predictionManager.lastCommittedText.dropLast(1)
                            Log.d(TAG, "Delete committed text, remaining: '${predictionManager.lastCommittedText}'")
                        }
                        
                        uiState.value = uiState.value.copy(
                            candidates = emptyArray(),
                            candidateComments = emptyArray(),
                            associationCandidates = emptyArray()
                        )
                        
                        withContext(Dispatchers.Main) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        }
                    }
                }
                "clear_composition" -> {
                    rimeEngine.clearComposition()
                    uiState.value = uiState.value.copy(
                        candidates = emptyArray(),
                        candidateComments = emptyArray(),
                        associationCandidates = emptyArray(),
                        pendingEnglishText = ""
                    )
                    needsUIUpdate = true
                    Log.d(TAG, "Clear composition: cleared all")
                }
                "enter" -> {
                    if (state.isComposing) {
                        val input = state.inputText
                        if (input.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                commitText(input)
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
                    val pendingEnglish = state.pendingEnglishText
                    
                    if (pendingEnglish.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            commitText(" ")
                            uiState.value = uiState.value.copy(
                                pendingEnglishText = "",
                                associationCandidates = emptyArray()
                            )
                        }
                        Log.d(TAG, "Space: added space after pending English '$pendingEnglish'")
                    } else if (state.isComposing) {
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
                    val pendingEnglish = state.pendingEnglishText
                    
                    if (key.matches(Regex("[0-9]")) ||
                        key in listOf("-", "/", ":", ";", "(", ")", "@", "\"", "'", "#", ".", ",", "!", "?", "，", "。")) {
                        if (pendingEnglish.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                commitText(key)
                                uiState.value = uiState.value.copy(
                                    pendingEnglishText = "",
                                    associationCandidates = emptyArray()
                                )
                            }
                            Log.d(TAG, "Symbol: added '$key' after pending English '$pendingEnglish'")
                        } else if (state.isComposing) {
                            val committedText = rimeEngine.commit()
                            if (committedText.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    commitText(committedText)
                                }
                            }
                            rimeEngine.clearComposition()
                            needsUIUpdate = true
                            withContext(Dispatchers.Main) {
                                commitText(key)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                commitText(key)
                            }
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
                            val isAscii = state.isAsciiMode
                            if (!state.isComposing) {
                                if (isAscii) {
                                    val currentPending = uiState.value.pendingEnglishText
                                    val newPending = currentPending + char.lowercase()
                                    
                                    withContext(Dispatchers.Main) {
                                        commitText(char.lowercase())
                                        uiState.value = uiState.value.copy(
                                            pendingEnglishText = newPending,
                                            associationCandidates = emptyArray()
                                        )
                                    }
                                    
                                    needsUIUpdate = true
                                    Log.d(TAG, "English mode: committed '$char', pending text '$newPending'")
                                } else {
                                    withContext(Dispatchers.Main) {
                                        commitText(char)
                                    }
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
        val selectedCandidate = if (index < uiState.value.candidates.size) {
            uiState.value.candidates[index]
        } else null
        
        if (rimeEngine.selectCandidate(index)) {
            val committedText = rimeEngine.commit()
            if (committedText.isNotEmpty()) {
                if (SettingsPreferences.isSmartPredictionEnabled(this) && selectedCandidate != null && AssociationManager.isInitialized()) {
                    if (predictionManager.lastCommittedText.isNotEmpty()) {
                        val lastChar = predictionManager.lastCommittedText.last().toString()
                        predictionManager.recordInputPair(lastChar, selectedCandidate)
                        Log.d(TAG, "Learned: '$lastChar' + '$selectedCandidate'")
                    }
                }
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
    
    private fun switchInputMethod() {
        Log.d(TAG, "Toggling ascii mode")
        rimeEngine.toggleAsciiMode()
        updateUI()
    }
    
    private fun reloadConfig() {
        Log.d(TAG, "Deploying schema...")
        
        mainHandler.post {
            requestHideSelf(0)
            android.widget.Toast.makeText(this, "方案部署中...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        Thread {
            try {
                KeysConfigHelper.loadConfig(this)
                
                val userDataDir = File(filesDir, "rime/user")
                
                val customFile = File(userDataDir, "default.custom.yaml")
                if (customFile.exists()) {
                    Log.d(TAG, "Removing old default.custom.yaml")
                    customFile.delete()
                }
                
                val buildDir = File(userDataDir, "build")
                if (buildDir.exists()) {
                    Log.d(TAG, "Cleaning build directory")
                    buildDir.deleteRecursively()
                }
                
                Log.d(TAG, "Starting deployment...")
                val deployResult = rimeEngine.deploy()
                Log.d(TAG, "Deploy result: $deployResult")
                
                val availableSchemas = rimeEngine.getAvailableSchemas()
                Log.d(TAG, "Available schemas: ${availableSchemas.joinToString()}")
                
                val savedSchema = SettingsPreferences.getCurrentSchema(this)
                Log.d(TAG, "Saved schema: $savedSchema")
                if (savedSchema in availableSchemas) {
                    val switchResult = rimeEngine.switchSchema(savedSchema)
                    Log.d(TAG, "Switch schema result: $switchResult")
                } else {
                    Log.w(TAG, "Schema $savedSchema not found in available schemas")
                }
                
                mainHandler.post {
                    updateSchemaName()
                    updateUI()
                    android.widget.Toast.makeText(this, "方案部署完成", android.widget.Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Schema deployed successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload config", e)
            }
        }.start()
    }
    
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
    
    private fun switchSchema(schemaId: String) {
        Log.d(TAG, "Switching schema to: $schemaId")
        try {
            SettingsPreferences.setCurrentSchema(this, schemaId)
            rimeEngine.switchSchema(schemaId)
            updateSchemaName()
            updateUI()
            Toast.makeText(this, "已切换输入方案", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Switched to schema: $schemaId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch schema", e)
        }
    }
    
    private fun updateKeyboardHeightPreview(heightDp: Int) {
        Log.d(TAG, "Preview keyboard height: $heightDp")
        keyboardContainer.updateHeight(heightDp)
    }
    
    private fun setKeyboardHeight(heightDp: Int) {
        Log.d(TAG, "Setting keyboard height to: $heightDp")
        SettingsPreferences.setKeyboardHeightDp(this, heightDp)
        uiState.value = uiState.value.copy(keyboardHeightDp = heightDp)
        Toast.makeText(this, "键盘高度已调整", Toast.LENGTH_SHORT).show()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        predictionManager.lastCommittedText = text
        
        predictionManager.recordInput(text)
        
        getPredictionFromPlugin(text)
    }
    
    private fun commitImage(imagePath: String, mimeType: String = "image/jpeg"): Boolean {
        return try {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file not found: $imagePath")
                return false
            }
            
            val cacheDir = File(cacheDir, "emoji_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val cacheFile = File(cacheDir, imageFile.name)
            FileInputStream(imageFile).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                cacheFile
            )
            
            val inputContentInfo = InputContentInfo(
                uri,
                android.content.ClipDescription("emoji_image", arrayOf(mimeType)),
                null
            )
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            } else {
                0
            }
            
            currentInputConnection?.commitContent(inputContentInfo, flags, null) ?: false
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to commit image", e)
            false
        }
    }
    
    private fun selectClipboardItem(text: String) {
        if (uiState.value.isComposing) {
            rimeEngine.clearComposition()
            updateUI()
        }
        commitText(text)
        clipboardManager.copyToSystemClipboard(text)
    }
    
    private fun removeClipboardItem(id: Long) {
        clipboardManager.removeItem(id)
    }
    
    private fun toggleClipboardPin(id: Long) {
        clipboardManager.togglePin(id)
    }
    
    private fun clearClipboard() {
        clipboardManager.clearAll()
    }
    
    private fun addToQuickSend(id: Long) {
        clipboardManager.addToQuickSend(id)
    }
    
    private fun removeFromQuickSend(id: Long) {
        clipboardManager.removeFromQuickSend(id)
    }
}