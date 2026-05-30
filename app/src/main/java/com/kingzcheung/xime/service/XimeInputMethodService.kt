package com.kingzcheung.xime.service

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.kingzcheung.xime.ui.LocalStretchFactor
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.ui.KeyboardResizeOverlay
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.association.AssociationService
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.SchemaConfigHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.KeyboardView
import com.kingzcheung.xime.ui.KeysConfigHelper
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class XimeInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "XimeInputMethodService"
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
    private val clipboardItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    private val quickSendItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    private val recentClipboardItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    
    private var isTrackingVoiceButtons = false
    private var voiceRecordingStarted = false
    private var lastClearedText: String = ""
    
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
        val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        uiState.value = uiState.value.copy(
            darkMode = SettingsPreferences.getDarkMode(this),
            themeId = SettingsPreferences.getKeyboardTheme(this),
            showBottomButtons = SettingsPreferences.showBottomButtons(this),
            keyboardHeightDp = SettingsPreferences.getKeyboardHeightDp(this, isLandscape),
            keyboardBottomPaddingDp = SettingsPreferences.getKeyboardBottomPaddingDp(this)
        )
    }
    
    private fun registerSharedPrefsListener() {
        val prefs = SettingsPreferences.getPrefsPublic(this)
        sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "dark_mode", "keyboard_theme", "show_bottom_buttons", "keyboard_height_dp", "keyboard_bottom_padding_dp" -> {
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
        // 允许 IME 窗口绘制到摄像头挖孔/刘海区域（横屏时背景覆盖全屏）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.window?.attributes?.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        savedStateRegistryController.performRestore(null)
        window.window?.decorView?.setViewTreeLifecycleOwner(this)
        window.window?.decorView?.setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        
        FileLogger.init(this)
        FileLogger.i(TAG, "XimeInputMethodService created")
        
        feedbackManager.initialize()
        
        loadDarkModePreference()
        registerSharedPrefsListener()
        
        initRimeEngine()
        
        serviceScope.launch(Dispatchers.IO) {
            try {
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
        
        // 必须在任何异步操作之前同步加载键盘按键配置，
        // 否则 KeyboardLayout 组合时 swipeUp/swipeDown 配置可能尚未就绪，
        // 导致按键上的符号不显示、上滑/下滑手势不触发。
        KeysConfigHelper.loadConfig(this)
        
        RimeEngine.setDeploymentCallback { isDeploying, message ->
            serviceScope.launch(Dispatchers.Main) {
                uiState.value = uiState.value.copy(
                    isDeploying = isDeploying,
                    deploymentMessage = message
                )
            }
        }
        
        val initJob = serviceScope.launch(Dispatchers.IO) {
            try {
                notifyDeploymentStatus(true, "正在初始化...")
                
                val (userDataDir, sharedDataDir) = RimeConfigHelper.initializeRimeDataAsync(this@XimeInputMethodService)
                
                notifyDeploymentStatus(true, "正在加载输入法引擎...")
                rimeEngine.initialize(userDataDir, sharedDataDir)

                // 检查词库是否已部署（prism.bin 文件是否存在）
                val deploymentDone = SettingsPreferences.isDeploymentDone(this@XimeInputMethodService)
                val needsDeployment = !deploymentDone || !RimeConfigHelper.isDeploymentComplete(this@XimeInputMethodService)

                if (needsDeployment) {
                    // 首次部署：需要完整编译词库
                    notifyDeploymentStatus(true, "正在编译词库...")
                    val maintenanceStarted = rimeEngine.startMaintenance(true)
                    if (!maintenanceStarted) {
                        Log.w(TAG, "initRimeEngine: startMaintenance returned false! " +
                                "Deployment may not have started. Trying deploy() as fallback...")
                        val deployed = rimeEngine.deploy()
                        if (deployed) {
                            Log.i(TAG, "initRimeEngine: deploy() succeeded as fallback")
                        } else {
                            Log.e(TAG, "initRimeEngine: both startMaintenance and deploy() failed")
                        }
                    }

                    // 诊断：检查 maintenance 是否真的进入了维护模式
                    val maintaining = rimeEngine.isMaintaining()
                    Log.d(TAG, "initRimeEngine: startMaintenance returned $maintenanceStarted, isMaintaining=$maintaining")

                    // 等待编译完成（最多 120 秒），startMaintenance 是异步的，
                    // 不等待的话 ensureSession 读到的是空 schema 列表
                    if (maintaining) {
                        var maintenanceWaited = 0L
                        val maintenanceTimeoutMs = 120_000L
                        while (rimeEngine.isMaintaining() && maintenanceWaited < maintenanceTimeoutMs) {
                            Thread.sleep(100)
                            maintenanceWaited += 100
                            if (maintenanceWaited % 5000 == 0L) {
                                Log.d(TAG, "initRimeEngine: waiting for maintenance... (${maintenanceWaited / 1000}s)")
                            }
                        }
                        if (rimeEngine.isMaintaining()) {
                            Log.w(TAG, "initRimeEngine: maintenance still running after timeout, continuing anyway")
                        } else {
                            Log.d(TAG, "initRimeEngine: maintenance completed in ${maintenanceWaited}ms")
                        }
                    }
                } else {
                    // 词库已存在：快速刷新 schema 注册表，不显示"编译"提示
                    rimeEngine.startMaintenance(false)
                }

                val sessionReady = rimeEngine.ensureSession()
                if (sessionReady) {
                    Log.d(TAG, "initRimeEngine: Session ready")
                    // 确保部署成功后才标记完成，避免首次部署超时后误标记
                    if (needsDeployment) {
                        SettingsPreferences.setDeploymentDone(this@XimeInputMethodService, true)
                    }
                } else {
                    Log.w(TAG, "initRimeEngine: Session not ready after 60s, continuing in background")
                }
                notifyDeploymentStatus(false, "")

                withContext(Dispatchers.Main) {
                    val savedSchema = SettingsPreferences.getCurrentSchema(this@XimeInputMethodService)
                    val availableSchemas = rimeEngine.getAvailableSchemas()
                    Log.d(TAG, "initRimeEngine: availableSchemas=${availableSchemas.joinToString()}")
                    
                    val currentSchema = rimeEngine.getCurrentSchema()
                    Log.d(TAG, "initRimeEngine: currentSchema=$currentSchema, savedSchema=$savedSchema")
                    
                    if (savedSchema in availableSchemas && currentSchema != savedSchema) {
                        Log.d(TAG, "initRimeEngine: Switching to saved schema: $savedSchema")
                        rimeEngine.switchSchema(savedSchema)
                    }
                    
                    updateSchemaName()
                    Log.d(TAG, "initRimeEngine: Rime engine initialized successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "initRimeEngine: Failed to initialize Rime engine", e)
                notifyDeploymentStatus(false, "初始化失败")
            }
        }
        
        // Watchdog: force-clear loading state after 190s
        // withTimeout cannot cancel native JNI calls; if rimeEngine.initialize() hangs
        // in librime, the IO coroutine would block forever. This watchdog ensures the
        // user is never permanently stuck on the loading screen.
        // 首次编译最多等 120s + ensureSession 60s + 10s 缓冲
        serviceScope.launch(Dispatchers.Main) {
            delay(190_000L)
            if (uiState.value.isDeploying) {
                Log.w(TAG, "initRimeEngine: Watchdog triggered - native init appears stuck, forcing loading state cleared")
                uiState.value = uiState.value.copy(
                    isDeploying = false,
                    deploymentMessage = "初始化超时，请重启输入法"
                )
            }
        }
    }
    
    private fun notifyDeploymentStatus(isDeploying: Boolean, message: String) {
        serviceScope.launch(Dispatchers.Main) {
            uiState.value = uiState.value.copy(
                isDeploying = isDeploying,
                deploymentMessage = message
            )
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

    private fun ensureClipboardManagerInitialized() {
        if (!::clipboardManager.isInitialized) {
            Log.d(TAG, "ensureClipboardManagerInitialized: Initializing clipboard manager synchronously")
            try {
                clipboardManager = ClipboardManager.getInstance(this)
                clipboardItemsState.value = clipboardManager.clipboardItems.value
                quickSendItemsState.value = clipboardManager.quickSendItems.value
                Log.d(TAG, "ensureClipboardManagerInitialized: Clipboard manager initialized")
            } catch (e: Exception) {
                Log.e(TAG, "ensureClipboardManagerInitialized: Failed to initialize clipboard manager", e)
            }
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
                val maxHeightDp = (screenHeightDp * 3) / 5
                val isLandscape = resources.configuration.screenWidthDp > screenHeightDp
                val orientationHeight = SettingsPreferences.getKeyboardHeightDp(this@XimeInputMethodService, isLandscape)
                val displayHeight = minOf(orientationHeight, maxHeightDp)
                val keyboardHeight = if (state.showKeyboardResize) {
                    if (isLandscape) (screenHeightDp * 7) / 10 else maxHeightDp + 100
                } else {
                    displayHeight
                }
                
                XimeTheme(darkTheme = isDarkTheme, themeId = state.themeId) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (state.showKeyboardResize)
                                    (maxHeightDp + 100).dp
                                else
                                    (keyboardHeight + state.keyboardBottomPaddingDp).dp
                            )
                    ) {
                        Surface(
                            modifier = Modifier
                                .align(androidx.compose.ui.Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 0.dp)
                                .height(if (state.showKeyboardResize) (state.resizePreviewHeightDp + state.resizePreviewBottomPaddingDp).dp else (keyboardHeight + state.keyboardBottomPaddingDp).dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                        CompositionLocalProvider(LocalStretchFactor provides state.stretchFactor) {
                            KeyboardView(
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
                            keyboardHeightDp = keyboardHeight,
                            keyboardBottomPaddingDp = state.keyboardBottomPaddingDp,
                             clipboardItems = clipboardItemsState.value,
                             quickSendItems = quickSendItemsState.value,
                             recentClipboardItems = recentClipboardItemsState.value,
                            candidateComments = state.candidateComments,
                            isVoiceMode = state.isVoiceMode,
                            voiceBottomActive = state.voiceButtonState.bottomActive,
                            voiceLeftActive = state.voiceButtonState.leftActive,
                            voiceRightActive = state.voiceButtonState.rightActive,
                            voicePluginName = state.voicePluginName,
                            voiceRecognitionState = state.voiceRecognitionState,
                            voiceRecognizedText = state.voiceRecognizedText,
                            voiceAmplitude = state.voiceAmplitude,
                            uiStateProvider = { uiState.value },
                            onKeyPress = { key, isShifted ->
                                handleKeyPress(key, isShifted)
                            },
                            onKeyPressDown = { key ->
                                feedbackManager.performKeyPressDownEffect(key)
                            },
                            onCursorMove = { direction ->
                                serviceScope.launch(Dispatchers.Main) {
                                    val keyCode = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                                    sendDownUpKeyEvents(keyCode)
                                }
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
                                val config = resources.configuration
                                val isLandscape = config.screenWidthDp > config.screenHeightDp
                                val currentHeight = SettingsPreferences.getKeyboardHeightDp(this@XimeInputMethodService, isLandscape)
                                val currentPadding = uiState.value.keyboardBottomPaddingDp
                                val maxHeightDp = (config.screenHeightDp * 3) / 5
                                val displayHeight = minOf(currentHeight, maxHeightDp)
                                uiState.value = uiState.value.copy(
                                    showKeyboardResize = true,
                                    keyboardHeightDp = currentHeight,
                                    resizePreviewHeightDp = displayHeight,
                                    resizePreviewBottomPaddingDp = currentPadding,
                                    originalKeyboardHeightDp = displayHeight,
                                    originalKeyboardBottomPaddingDp = currentPadding,
                                    stretchFactor = ((displayHeight - 126f) / (SettingsPreferences.getDefaultKeyboardHeightDp() - 126f)).coerceAtLeast(0f)
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
                            onPageDown = { pageDown() },
                            onPageUp = { pageUp() },
                            onCommitImage = { imagePath ->
                                val success = commitImage(imagePath)
                                if (!success) {
                                    android.widget.Toast.makeText(
                                        this@XimeInputMethodService,
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
                                    voiceRecordingStarted = true
                                    voiceRecognitionHandler.startRecognition()
                                    Log.d("VoiceButtons", "Speech recognition starting...")
                                } else {
                                    isTrackingVoiceButtons = false
}
 },
                              isDeploying = state.isDeploying,
                              deploymentMessage = state.deploymentMessage,
                              onDismissDeploying = {
                                  notifyDeploymentStatus(false, "")
                              }
                               )
                         }
                     }
                     
                         if (state.showKeyboardResize) {
                            KeyboardResizeOverlay(
                                initialHeightDp = state.resizePreviewHeightDp,
                                initialBottomPaddingDp = state.resizePreviewBottomPaddingDp,
                                defaultHeightDp = SettingsPreferences.getOrientationDefaultKeyboardHeightDp(this@XimeInputMethodService, isLandscape),
                                 defaultBottomPaddingDp = SettingsPreferences.getDefaultKeyboardBottomPaddingDp(),
                               maxContainerHeightDp = keyboardHeight,
                              onHeightChange = { newHeight ->
                                  uiState.value = uiState.value.copy(
                                      resizePreviewHeightDp = newHeight
                                  )
                              },
                              onBottomPaddingChange = { newPadding ->
                                  uiState.value = uiState.value.copy(
                                      resizePreviewBottomPaddingDp = newPadding,
                                      keyboardBottomPaddingDp = newPadding
                                  )
                              },
                              onStretchChange = { stretchFactor ->
                                  uiState.value = uiState.value.copy(
                                      stretchFactor = stretchFactor
                                  )
                              },
                              onReset = { defaultHeight, defaultPadding ->
                                  uiState.value = uiState.value.copy(
                                      resizePreviewHeightDp = defaultHeight,
                                      resizePreviewBottomPaddingDp = defaultPadding,
                                      keyboardBottomPaddingDp = defaultPadding,
                                      stretchFactor = 1f
                                  )
                              },
                              onConfirm = { newHeight, newPadding ->
                                  setKeyboardHeight(newHeight)
                                  SettingsPreferences.setKeyboardBottomPaddingDp(this@XimeInputMethodService, newPadding)
                                  uiState.value = uiState.value.copy(
                                      showKeyboardResize = false,
                                      keyboardHeightDp = newHeight,
                                      keyboardBottomPaddingDp = newPadding
                                  )
                              },
                              onCancel = {
                                  val originalHeight = state.originalKeyboardHeightDp
                                  val cancelStretchFactor = ((originalHeight - 126f) / (SettingsPreferences.getDefaultKeyboardHeightDp() - 126f)).coerceAtLeast(0f)
                                  uiState.value = uiState.value.copy(
                                      showKeyboardResize = false,
                                      keyboardHeightDp = originalHeight,
                                      keyboardBottomPaddingDp = state.originalKeyboardBottomPaddingDp,
                                      resizePreviewHeightDp = originalHeight,
                                      resizePreviewBottomPaddingDp = state.originalKeyboardBottomPaddingDp,
                                      stretchFactor = cancelStretchFactor
                                  )
                              },
                               modifier = Modifier
                                   .align(androidx.compose.ui.Alignment.BottomCenter)
                                   .fillMaxWidth()
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

        predictionManager.clearCommittedText()
        Log.d(TAG, "onStartInput: cleared lastCommittedText")

        checkAndInitializeAssociationEngine()
        
        if (RimeEngine.isInitialized()) {
            val savedSchema = SettingsPreferences.getCurrentSchema(this)
            val currentSchema = rimeEngine.getCurrentSchema()
            if (savedSchema != currentSchema) {
                Log.d(TAG, "onStartInput: schema mismatch, saved=$savedSchema, current=$currentSchema")
                val availableSchemas = rimeEngine.getAvailableSchemas()
                if (savedSchema in availableSchemas) {
                    rimeEngine.switchSchema(savedSchema)
                    updateSchemaName()
                }
            }
        }

        // 标记新一轮输入会话，用于 KeyboardView 重置导航状态
        uiState.value = uiState.value.copy(inputSessionId = System.nanoTime())

        // 获取最近30秒的剪切板内容
        ensureClipboardManagerInitialized()
        try {
            recentClipboardItemsState.value = clipboardManager.getRecentItems(30)
            // 将最近剪切板内容显示在候选栏
            uiState.value = uiState.value.copy(
                candidates = recentClipboardItemsState.value.map { it.text }.toTypedArray(),
                candidateComments = emptyArray(),
                isShowingRecentClipboard = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent clipboard items", e)
        }

        // 监听clipboardItems变化，更新候选栏
        serviceScope.launch {
            clipboardManager.clipboardItems.collect { _ ->
                val items = clipboardManager.getRecentItems(30)
                recentClipboardItemsState.value = items
                if (items.isNotEmpty()) {
                    // 清空Rime联想词等
                    rimeEngine.clearComposition()
                    uiState.value = uiState.value.copy(
                        candidates = items.map { it.text.take(8) + if (it.text.length > 8) "..." else "" }.toTypedArray(),
                        candidateComments = emptyArray(),
                        inputText = "",
                        isComposing = false,
                        associationCandidates = emptyArray(),
                        isShowingRecentClipboard = true
                    )
                } else if (uiState.value.isShowingRecentClipboard) {
                    // 如果没有recent items，清空候选栏
                    uiState.value = uiState.value.copy(
                        candidates = emptyArray(),
                        candidateComments = emptyArray(),
                        isShowingRecentClipboard = false
                    )
                }
            }
        }

        attribute?.let { updateEnterKeyText(it) }
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 作为 onStartInput 的补充，某些 ROM/Android 版本可能不保证 onStartInput 中 EditorInfo 完整
        info?.let { updateEnterKeyText(it) }
    }
    
    private fun updateEnterKeyText(editorInfo: EditorInfo) {
        val imeOptions = editorInfo.imeOptions
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        Log.d(TAG, "updateEnterKeyText: imeOptions=0x${imeOptions.toString(16)}, action=0x${action.toString(16)}, noEnterAction=$noEnterAction")
        val enterText = when {
            noEnterAction -> "换行"
            action == EditorInfo.IME_ACTION_GO -> "前往"
            action == EditorInfo.IME_ACTION_SEARCH -> "搜索"
            action == EditorInfo.IME_ACTION_SEND -> "发送"
            action == EditorInfo.IME_ACTION_NEXT -> "下一项"
            action == EditorInfo.IME_ACTION_DONE -> "完成"
            else -> "换行"
        }
        uiState.value = uiState.value.copy(enterKeyText = enterText)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        clearInputState()
        recentClipboardItemsState.value = emptyList()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        clearInputState()
        recentClipboardItemsState.value = emptyList()
    }
    
    private fun clearInputState() {
        rimeEngine.clearComposition()
        uiState.value = uiState.value.copy(
            candidates = emptyArray(),
            candidateComments = emptyArray(),
            inputText = "",
            isComposing = false,
            isShowingRecentClipboard = false
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefsListener?.let {
            SettingsPreferences.getPrefsPublic(this).unregisterOnSharedPreferenceChangeListener(it)
        }
        feedbackManager.release()
        rimeEngine.destroy()
        voiceRecognitionHandler.release()
        ExtensionManager.release()
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    
    private fun hideKeyboard() {
        uiState.value = uiState.value.copy(associationCandidates = emptyArray())
        requestHideSelf(0)
    }
    
    private fun updateUI() {
        val inputText = rimeEngine.getInput()
        val candidatesWithComments = rimeEngine.getCandidatesWithComments()
        val isAsciiMode = rimeEngine.isAsciiMode()
        val hasNextPage = rimeEngine.hasNextPage()
        val hasPrevPage = rimeEngine.hasPrevPage()
        
        val pendingEnglish = uiState.value.pendingEnglishText
        
        val (filteredTexts, filteredComments) = if (isAsciiMode) {
            val filtered = candidatesWithComments.filterNot { candidate ->
                candidate.text.any { it.code in 0x4E00..0x9FFF }
            }
            filtered.map { it.text }.toTypedArray() to filtered.map { it.comment }.toTypedArray()
        } else {
            candidatesWithComments.map { it.text }.toTypedArray() to candidatesWithComments.map { it.comment }.toTypedArray()
        }
        
        uiState.value = uiState.value.copy(
            inputText = inputText,
            candidates = filteredTexts,
            candidateComments = filteredComments,
            isComposing = inputText.isNotEmpty(),
            isAsciiMode = isAsciiMode,
            associationCandidates = if (isAsciiMode && pendingEnglish.isEmpty()) emptyArray() else uiState.value.associationCandidates,
            isShowingRecentClipboard = false,
            hasNextPage = hasNextPage,
            hasPrevPage = hasPrevPage
        )
        
        if (pendingEnglish.isNotEmpty()) {
            serviceScope.launch {
                val candidates = predictionManager.getEnglishAssociations(pendingEnglish, 5)
                Log.d(TAG, "English association for pending '$pendingEnglish': ${candidates.joinToString()}")
                withContext(Dispatchers.Main) {
                    uiState.value = uiState.value.copy(associationCandidates = candidates)
                }
            }
        }
    }
    
    private fun updateSchemaName() {
        val availableSchemaIds = if (RimeEngine.isInitialized()) {
            rimeEngine.getAvailableSchemas().toList()
        } else {
            emptyList()
        }

        val enabledIds = SchemaManager.getEnabledSchemas(this)
        val allSchemas = SchemaManager.discoverSchemas(this)

        // 键盘菜单栏只显示：已启用 + 已编译的方案
        val schemas = allSchemas
            .filter { meta -> meta.schemaId in enabledIds && meta.schemaId in availableSchemaIds }
            .map { meta ->
                com.kingzcheung.xime.settings.SchemaInfo(
                    schemaId = meta.schemaId,
                    name = meta.name,
                    version = meta.version,
                    author = meta.author,
                    description = meta.description,
                    isDownloaded = true
                )
            }

        val currentSchemaId = rimeEngine.getCurrentSchema()
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
                        predictionManager.deleteLastChar()
                        Log.d(TAG, "Delete committed text, remaining: '${predictionManager.lastCommittedText}'")
                        
                        if (!state.isAsciiMode && SettingsPreferences.isSmartPredictionEnabled(this@XimeInputMethodService) && predictionManager.lastCommittedText.isNotEmpty()) {
                            val candidates = predictionManager.getChineseAssociations(predictionManager.lastCommittedText, 20)
                            uiState.value = uiState.value.copy(associationCandidates = candidates)
                        } else {
                            uiState.value = uiState.value.copy(
                                candidates = emptyArray(),
                                candidateComments = emptyArray(),
                                associationCandidates = emptyArray(),
                                isShowingRecentClipboard = false
                            )
                        }
                        
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
                        pendingEnglishText = "",
                        isShowingRecentClipboard = false
                    )
                    needsUIUpdate = true
                    Log.d(TAG, "Clear composition: cleared all")
                }
                "clear_all" -> {
                    // 记录当前输入框中的文本以便撤回
                    val inputFieldText = withContext(Dispatchers.Main) {
                        currentInputConnection?.getTextBeforeCursor(Int.MAX_VALUE, 0)?.toString() ?: ""
                    }
                    lastClearedText = inputFieldText + state.inputText
                    rimeEngine.clearComposition()
                    uiState.value = uiState.value.copy(
                        candidates = emptyArray(),
                        candidateComments = emptyArray(),
                        associationCandidates = emptyArray(),
                        pendingEnglishText = "",
                        inputText = "",
                        isComposing = false,
                        isShowingRecentClipboard = false
                    )
                    withContext(Dispatchers.Main) {
                        currentInputConnection?.let {
                            it.finishComposingText()
                            // 删除输入框中所有文字
                            val textLen = inputFieldText.length
                            if (textLen > 0) {
                                it.deleteSurroundingText(textLen, 0)
                            }
                        }
                    }
                    needsUIUpdate = true
                    Log.d(TAG, "Clear all: saved='$lastClearedText'")
                }
                "undo_clear" -> {
                    val text = lastClearedText
                    if (text.isNotEmpty()) {
                        lastClearedText = ""
                        withContext(Dispatchers.Main) {
                            val ic = currentInputConnection
                            if (ic != null) {
                                ic.commitText(text, text.length)
                            }
                        }
                    }
                    needsUIUpdate = true
                    Log.d(TAG, "Undo clear: restored='$text'")
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
                        rimeEngine.clearComposition()
                        withContext(Dispatchers.Main) {
                            val imeOptions = currentInputEditorInfo?.imeOptions ?: 0
                            val action = imeOptions and EditorInfo.IME_MASK_ACTION
                            when (action) {
                                EditorInfo.IME_ACTION_GO,
                                EditorInfo.IME_ACTION_SEARCH,
                                EditorInfo.IME_ACTION_SEND,
                                EditorInfo.IME_ACTION_NEXT,
                                EditorInfo.IME_ACTION_DONE -> {
                                    currentInputConnection?.performEditorAction(action)
                                }
                                else -> {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        uiState.value = uiState.value.copy(
                            inputText = "",
                            pendingEnglishText = "",
                            candidates = emptyArray(),
                            candidateComments = emptyArray(),
                            associationCandidates = emptyArray(),
                            isComposing = false
                        )
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
                                    val charToCommit = if (isShifted) char.uppercase() else char.lowercase()
                                    val currentPending = uiState.value.pendingEnglishText
                                    val newPending = currentPending + charToCommit
                                    
                                    withContext(Dispatchers.Main) {
                                        commitText(charToCommit)
                                        uiState.value = uiState.value.copy(
                                            pendingEnglishText = newPending,
                                            associationCandidates = emptyArray()
                                        )
                                    }
                                    
                                    needsUIUpdate = true
                                    Log.d(TAG, "English mode: committed '$charToCommit', pending text '$newPending'")
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
        if (uiState.value.isShowingRecentClipboard && index >= 0 && index < recentClipboardItemsState.value.size) {
            val text = recentClipboardItemsState.value[index].text
            selectClipboardItem(text)
            uiState.value = uiState.value.copy(
                isShowingRecentClipboard = false,
                candidates = emptyArray(),
                candidateComments = emptyArray()
            )
        } else {
            serviceScope.launch(Dispatchers.Default) {
                selectCandidateAsync(index)
            }
        }
    }
    
    private fun pageDown() {
        serviceScope.launch(Dispatchers.Default) {
            if (rimeEngine.pageDown()) {
                withContext(Dispatchers.Main) {
                    updateUI()
                }
            }
        }
    }
    
    private fun pageUp() {
        serviceScope.launch(Dispatchers.Default) {
            if (rimeEngine.pageUp()) {
                withContext(Dispatchers.Main) {
                    updateUI()
                }
            }
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
    
    private fun downloadSchema(schemaId: String) {
        Log.d(TAG, "Downloading schema: $schemaId")
        serviceScope.launch(Dispatchers.IO) {
            notifyDeploymentStatus(true, "正在下载 $schemaId...")
            
            val success = SchemaConfigHelper.downloadSchema(this@XimeInputMethodService, schemaId)
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@XimeInputMethodService, "$schemaId 下载成功，请点击部署", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@XimeInputMethodService, "$schemaId 下载失败", Toast.LENGTH_SHORT).show()
                }
                notifyDeploymentStatus(false, "")
            }
        }
    }
    
    private fun deploy() {
        Log.d(TAG, "Deploying schemas")
        serviceScope.launch(Dispatchers.IO) {
            notifyDeploymentStatus(true, "正在部署...")
            
            val success = rimeEngine.deploy()
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(this@XimeInputMethodService, "部署成功", Toast.LENGTH_SHORT).show()
                    updateUI()
                } else {
                    Toast.makeText(this@XimeInputMethodService, "部署失败", Toast.LENGTH_SHORT).show()
                }
                notifyDeploymentStatus(false, "")
            }
        }
    }
    
    private fun updateKeyboardHeightPreview(heightDp: Int) {
        Log.d(TAG, "Preview keyboard height: $heightDp")
        keyboardContainer.updateHeight(heightDp)
    }
    
    private fun setKeyboardHeight(heightDp: Int) {
        Log.d(TAG, "Setting keyboard height to: $heightDp")
        val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        SettingsPreferences.setKeyboardHeightDp(this, heightDp, isLandscape)
        uiState.value = uiState.value.copy(keyboardHeightDp = heightDp)
        Toast.makeText(this, "键盘高度已调整", Toast.LENGTH_SHORT).show()
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        predictionManager.appendCommittedText(text)
        
        predictionManager.recordInput(text)
        
        if (!uiState.value.isAsciiMode) {
            getPredictionFromPlugin(predictionManager.lastCommittedText)
        }
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