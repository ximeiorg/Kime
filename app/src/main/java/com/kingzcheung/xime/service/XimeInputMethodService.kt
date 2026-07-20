package com.kingzcheung.xime.service

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.kingzcheung.xime.ui.keyboard.LocalStretchFactor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.ui.keyboard.KeyboardResizeOverlay
import com.kingzcheung.xime.ui.keyboard.HardwareKeyboardCandidateBar
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.asCoroutineDispatcher
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.association.AssociationManager
import com.kingzcheung.xime.ui.keyboard.KeyboardCallbacks
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.association.AssociationService
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.T9InputController
import com.kingzcheung.xime.rime.convertT9PreeditToPinyin
import com.kingzcheung.xime.rime.buildT9DisplayState
import com.kingzcheung.xime.rime.resolveRimeCandidateIndex

import com.kingzcheung.xime.settings.SchemaConfigHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.keyboard.KeyboardView
import com.kingzcheung.xime.ui.keyboard.isT9Schema
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import kotlin.math.abs
import kotlin.math.roundToInt
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.theme.XimeTheme
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.util.PreeditMergeHelper
import com.kingzcheung.xime.keyboard.ActionExecutor
import com.kingzcheung.xime.keyboard.HANDWRITING_SCHEMA_ID
import com.kingzcheung.xime.keyboard.OverlayRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

object QuickSendFormEditTextHolder {
    var editText: android.widget.EditText? = null
}

class XimeInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner, ActionExecutor {

    companion object {
        private const val TAG = "XimeInputMethodService"
        private const val DARK_MODE_LIGHT = 0
        private const val DARK_MODE_DARK = 1
        private const val DARK_MODE_SYSTEM = 2
        private const val HARDWARE_CANDIDATE_BAR_HEIGHT = 72
        private const val SAFE_TEXT_LIMIT = 262144

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
    private val keyProcessingDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "key-process").also { it.isDaemon = true }
    }.asCoroutineDispatcher()
    
    private val keyJobs = Channel<Job>(Channel.UNLIMITED)
    private val uiEventChannel = Channel<suspend () -> Unit>(Channel.CONFLATED)

    init {
        serviceScope.launch {
            keyJobs.consumeEach { job ->
                job.join()
            }
        }
        serviceScope.launch(Dispatchers.Main) {
            uiEventChannel.consumeEach { work -> work() }
        }
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private val uiState = mutableStateOf(InputUIState())
    private val candidateState = mutableStateOf(CandidateState())
    private val clipboardItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    private val voiceAmplitudeState = mutableFloatStateOf(0f)
    private val quickSendItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    private val recentClipboardItemsState = mutableStateOf<List<com.kingzcheung.xime.clipboard.ClipboardItem>>(emptyList())
    private var hasHardwareKeyboard = false
    private var floatingWinX = 100
    private var floatingWinY = 300
    
    private var isTrackingVoiceButtons = false
    private var voiceRecordingStarted = false
    private var pendingVoiceAction: (() -> Unit)? = null
    private var composeViewRef: View? = null
    private var lastClearedText: String = ""
    /** 累积的 partial commit 文本列表（多段选词场景下逐段追加） */
    private val t9PartialCommitTexts = mutableListOf<String>()
    /** 键盘回调引用，用于在 RIME selectCandidate 前同步通知 T9 控制器 */
    private var keyboardCallbacks: KeyboardCallbacks? = null
    private var isChineseMode = true
    private var currentEffectiveKeyboardHeight: Int = 0
    private var currentFloatingCardHeightDp: Int = 0
    private var previousSchemaId: String = ""
    
    private val calculatorEngine = com.kingzcheung.xime.calculator.CalculatorEngine()

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private val keyboardViewModel: KeyboardViewModel by lazy {
        ViewModelProvider(
            _viewModelStore,
            androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(applicationContext as android.app.Application)
        ).get(KeyboardViewModel::class.java)
    }
    
    private val predictionManager = PredictionManager(
        context = this,
        serviceScope = serviceScope,
        onPredictionResult = { candidates ->
            candidateState.value = candidateState.value.copy(
                associationCandidates = candidates
            )
        },
    )
    
    private val voiceRecognitionHandler = VoiceRecognitionHandler(
        context = this,
        onStateChanged = { newState -> uiState.value = newState },
        getState = { uiState.value },
        getInputConnection = { currentInputConnection },
        onVoiceComplete = {
            val action = pendingVoiceAction
            pendingVoiceAction = null
            action?.invoke()

            voiceAmplitudeState.floatValue = 0f
            uiState.value = uiState.value.copy(
                isVoiceMode = false,
                voiceButtonState = VoiceButtonState(),
                voiceRecognitionState = RecognitionState.IDLE,
                voiceRecognizedText = "",
                voiceAmplitude = 0f
            )
            isTrackingVoiceButtons = false
            keyboardViewModel.exitVoice()
        },
        onAmplitudeChanged = { amplitude ->
            voiceAmplitudeState.floatValue = amplitude
        }
    )
    
    private var sharedPrefsListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var clipboardCollectorJob: kotlinx.coroutines.Job? = null
    
    private val feedbackManager = FeedbackManager(this)
    
    private fun loadDarkModePreference() {
        val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        val isFloatingMode = SettingsPreferences.isFloatingMode(this, isLandscape)
        SettingsPreferences.setFloatingMode(this, isFloatingMode, !isLandscape)
        val loadedX = SettingsPreferences.getFloatingOffsetX(this, isLandscape)
        val loadedY = SettingsPreferences.getFloatingOffsetY(this, isLandscape)
        SettingsPreferences.setFloatingOffsetX(this, loadedX, !isLandscape)
        SettingsPreferences.setFloatingOffsetY(this, loadedY, !isLandscape)
        val screenW = resources.configuration.screenWidthDp
        val screenH = resources.configuration.screenHeightDp
        val portraitWidth = minOf(screenW, screenH)
        val cardWidth = (portraitWidth * 0.85f).roundToInt()
        val halfMargin = maxOf(0, (screenW - cardWidth) / 2)
        val kbH = SettingsPreferences.getKeyboardHeightDp(this, isLandscape)
        val cappedKbH = kbH.coerceAtMost((screenH * 8) / 10)
        val cardH = (cappedKbH * 0.85f).roundToInt() + 18
        val navBarDp = tryGetNavBarHeightDp()
        val minY = if (isFloatingMode) navBarDp else 0
        val effectiveH = if (isFloatingMode) screenH - tryGetStatusBarHeightDp() else screenH
        val maxY = maxOf(minY, effectiveH - cardH - 20)
        val clampedX = loadedX.coerceIn(-halfMargin, halfMargin)
        val clampedY = loadedY.coerceIn(minY, maxY)
        if (clampedX != loadedX || clampedY != loadedY) {
            SettingsPreferences.setFloatingOffsetX(this, clampedX, isLandscape)
            SettingsPreferences.setFloatingOffsetY(this, clampedY, isLandscape)
        }
        uiState.value = uiState.value.copy(
            darkMode = SettingsPreferences.getDarkMode(this),
            themeId = SettingsPreferences.getKeyboardTheme(this),
            isSttEnabled = SettingsPreferences.isSttEnabled(this@XimeInputMethodService),
            keyboardHeightDp = SettingsPreferences.getKeyboardHeightDp(this, isLandscape),
            keyboardBottomPaddingDp = SettingsPreferences.getKeyboardBottomPaddingDp(this),
            toolbarButtons = SettingsPreferences.getToolbarButtons(this),
            isFloatingMode = isFloatingMode,
            floatingOffsetX = clampedX,
            floatingOffsetY = clampedY,
            isGlassEffectEnabled = SettingsPreferences.isGlassEffectEnabled(this@XimeInputMethodService),
        )
    }
    
    private fun tryGetNavBarHeightDp(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val decorView = window.window?.decorView
                if (decorView != null) {
                    val insets = decorView.rootWindowInsets
                    if (insets != null) {
                        return (insets.getInsetsIgnoringVisibility(
                            android.view.WindowInsets.Type.navigationBars()
                        ).bottom / resources.displayMetrics.density).roundToInt()
                    }
                }
            }
            val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId > 0) (resources.getDimensionPixelSize(resId) / resources.displayMetrics.density).roundToInt() else 0
        } catch (e: Exception) { 0 }
    }

    private fun tryGetVisibleNavBarHeightDp(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val decorView = window.window?.decorView
                if (decorView != null) {
                    val insets = decorView.rootWindowInsets
                    if (insets != null) {
                        val px = insets.getInsets(
                            android.view.WindowInsets.Type.navigationBars()
                        ).bottom
                        if (px > 0) return (px / resources.displayMetrics.density).roundToInt()
                    }
                }
            }
            0
        } catch (e: Exception) { 0 }
    }

    private fun tryGetStatusBarHeightDp(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val decorView = window.window?.decorView
                if (decorView != null) {
                    val insets = decorView.rootWindowInsets
                    if (insets != null) {
                        val px = insets.getInsetsIgnoringVisibility(
                            android.view.WindowInsets.Type.statusBars()
                        ).top
                        if (px > 0) return (px / resources.displayMetrics.density).roundToInt()
                    }
                }
            }
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) (resources.getDimensionPixelSize(resId) / resources.displayMetrics.density).roundToInt() else 0
        } catch (e: Exception) { 0 }
    }

    private fun registerSharedPrefsListener() {
        val prefs = SettingsPreferences.getPrefsPublic(this)
        sharedPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "dark_mode", "keyboard_theme", "show_bottom_buttons", "keyboard_height_dp", "keyboard_bottom_padding_dp" -> {
                    loadDarkModePreference()
                    Log.d(TAG, "Settings changed: $key, updated UI state")
                }
                "floating_mode", "floating_mode_landscape" -> {
                    loadDarkModePreference()
                    applyFloatingWindowBackground()
                    Log.d(TAG, "Floating mode changed: $key")
                }
                "stt_enabled" -> {
                    uiState.value = uiState.value.copy(isSttEnabled = SettingsPreferences.isSttEnabled(this@XimeInputMethodService))
                    Log.d(TAG, "STT setting changed: $key -> ${SettingsPreferences.isSttEnabled(this@XimeInputMethodService)}")
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
        val currentMode = uiState.value.darkMode
        val newMode = when (currentMode) {
            DARK_MODE_LIGHT -> DARK_MODE_DARK
            DARK_MODE_DARK -> DARK_MODE_LIGHT
            else -> { // DARK_MODE_SYSTEM: 切换到当前系统主题的反面
                if (isDarkTheme()) DARK_MODE_LIGHT else DARK_MODE_DARK
            }
        }
        saveDarkModePreference(newMode)
    }
    
    fun isDarkTheme(): Boolean {
        return when (uiState.value.darkMode) {
            DARK_MODE_DARK -> true
            DARK_MODE_SYSTEM -> {
                val nightModeFlags = resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
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
    
    
    private fun getPredictionFromPlugin(contextText: String) {
        predictionManager.getPrediction(contextText)
    }
    
    private fun initRimeEngine() {
        Log.d(TAG, "initRimeEngine: Starting initialization...")
        
        // 必须在任何异步操作之前同步加载键盘按键配置，
        // 否则 KeyboardLayout 组合时 swipeUp/swipeDown 配置可能尚未就绪，
        // 导致按键上的符号不显示、上滑/下滑手势不触发。
        runBlocking(Dispatchers.IO) {
            KeysConfigHelper.loadConfig(this@XimeInputMethodService)
        }
        
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

                    // 如果所有方案已编译完成，只是 deploymentDone 标记没设（例如从设置页部署的），
                    // 用增量刷新即可，避免不必要的全量扫描
                    val alreadyCompiled = RimeConfigHelper.isDeploymentComplete(this@XimeInputMethodService)
                    val maintenanceStarted = rimeEngine.startMaintenance(!alreadyCompiled)
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
                        val maintenanceTimeoutMs = 300_000L
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
                            rimeEngine.updateLastBuildTime()
                        }
                    }
                } else {
                    Log.d(TAG, "initRimeEngine: Already deployed, creating session directly")
                }

                // 创建 session（已部署时跳过 maintenance 直接创建）
                val sessionReady = rimeEngine.ensureSession(180_000L)
                if (sessionReady) {
                    Log.d(TAG, "initRimeEngine: Session ready")
                    // 确保部署成功后才标记完成，避免首次部署超时后误标记
                    if (needsDeployment) {
                        SettingsPreferences.setDeploymentDone(this@XimeInputMethodService, true)
                        RimeConfigHelper.storeDeploymentHash(this@XimeInputMethodService)
                    }
                } else {
                    Log.w(TAG, "initRimeEngine: Session not ready after 60s, continuing in background")
                }
                notifyDeploymentStatus(false, "")

                withContext(Dispatchers.Main) {
                    val savedSchema = SettingsPreferences.getCurrentSchema(this@XimeInputMethodService)
                    val availableSchemas = rimeEngine.getAvailableSchemas()
                    val currentSchema = rimeEngine.getCurrentSchema()
                    Log.d(TAG, "initRimeEngine: currentSchema=$currentSchema, savedSchema=$savedSchema, availableSchemas=${availableSchemas.joinToString()}")
                    
                    when {
                        savedSchema == HANDWRITING_SCHEMA_ID -> {
                            // 手写方案：不要调 rimeEngine.switchSchema（Rime 没有手写引擎），
                            // 也不要覆盖 savedSchema（由 onStartInput 恢复 UI）
                            Log.d(TAG, "initRimeEngine: savedSchema is handwriting, keeping current Rime schema")
                        }
                        savedSchema in availableSchemas -> {
                            // 即使 savedSchema == currentSchema 也要调用 switchSchema，
                            // 因为 nativeCreateSession 后 schema 的 processor/translator 等
                            // 可能未完全初始化，switchSchema 会触发完整的初始化流程
                            Log.d(TAG, "initRimeEngine: Switching to saved schema: $savedSchema")
                            applyPageSizeSetting(savedSchema)
                            rimeEngine.switchSchema(savedSchema)
                        }
                        SchemaManager.isSchemaCompiled(this@XimeInputMethodService, savedSchema) -> {
                            Log.d(TAG, "initRimeEngine: Schema compiled but not in get_schema_list, switching anyway")
                            applyPageSizeSetting(savedSchema)
                            rimeEngine.switchSchema(savedSchema)
                        }
                        availableSchemas.isNotEmpty() -> {
                            // savedSchema 不可用且未编译，退而求其次用第一个可用方案
                            val fallbackSchema = availableSchemas.first()
                            Log.d(TAG, "initRimeEngine: savedSchema '$savedSchema' not available, falling back to '$fallbackSchema'")
                            applyPageSizeSetting(fallbackSchema)
                            rimeEngine.switchSchema(fallbackSchema)
                            SettingsPreferences.setCurrentSchema(this@XimeInputMethodService, fallbackSchema)
                        }
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
            onPerformVibration = { view -> feedbackManager.hapticFeedback(view) },
            onPerformUndo = { pendingVoiceAction = { performUndo() } },
            onPerformSearch = { pendingVoiceAction = { performSearch() } },
            onStopRecognition = { voiceRecognitionHandler.stopRecognition() },
            isRecording = { voiceRecordingStarted },
            setRecording = { voiceRecordingStarted = it },
            onVoiceDismiss = {
                val action = pendingVoiceAction
                pendingVoiceAction = null
                action?.invoke()
                uiState.value = uiState.value.copy(
                    isVoiceMode = false,
                    voiceButtonState = VoiceButtonState(),
                    voiceRecognizedText = ""
                )
                keyboardViewModel.exitVoice()
                isTrackingVoiceButtons = false
            }
        )
        
        val composeView = ComposeView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            composeViewRef = this
            setContent {
                val cand = candidateState.value
                val state = uiState.value
                val page by keyboardViewModel.page.collectAsState(com.kingzcheung.xime.keyboard.KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.FULL))
                val isHandwritingMode = (page as? com.kingzcheung.xime.keyboard.KeyboardPage.Main)?.type == com.kingzcheung.xime.keyboard.MainType.HANDWRITING
                val isDarkTheme = isDarkTheme()
                val screenHeightDp = resources.configuration.screenHeightDp
                val physicalScreenDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).roundToInt()
                val statusBarHeightDp = tryGetStatusBarHeightDp()
                val navBarHeightDp = tryGetNavBarHeightDp()
                val visibleNavBarHeightDp = tryGetVisibleNavBarHeightDp()
                // 用物理屏幕高度减去状态栏，保证不同 Android 版本一致
                val effectiveScreenH = if (state.isFloatingMode) physicalScreenDp - statusBarHeightDp else screenHeightDp
                val windowVisibleHeightDp = effectiveScreenH
                // 检测 config.screenHeightDp 是否已排除导航栏（非全屏 + 3按钮导航）
                val navBarAlreadyExcluded = (physicalScreenDp - screenHeightDp) >= (navBarHeightDp + statusBarHeightDp - 3)
                val floatingMinY = if (navBarAlreadyExcluded) 0 else visibleNavBarHeightDp

                val screenWidthDp = resources.configuration.screenWidthDp
                val screenIsLandscape = screenWidthDp > screenHeightDp
                val portraitScreenHeightDp = if (screenIsLandscape) screenWidthDp else screenHeightDp
                val isLandscape = !state.isFloatingMode && screenIsLandscape
                val orientationHeight = if (state.isFloatingMode) {
                    val prefs = SettingsPreferences.getPrefsPublic(this@XimeInputMethodService)
                    val storedPortrait = prefs.getInt("keyboard_height_dp", -1)
                    if (storedPortrait > 0) storedPortrait else {
                        val storedLandscape = prefs.getInt("keyboard_height_dp_landscape", -1)
                        if (storedLandscape > 0) storedLandscape else portraitScreenHeightDp * SettingsPreferences.DEFAULT_KEYBOARD_HEIGHT_PERCENT / 100
                    }
                } else {
                    SettingsPreferences.getKeyboardHeightDp(this@XimeInputMethodService, screenIsLandscape)
                }
                val displayHeight = orientationHeight.coerceAtMost((if (state.isFloatingMode) portraitScreenHeightDp else screenHeightDp) * 8 / 10)
                val keyboardHeight = if (state.showKeyboardResize) {
                    if (screenIsLandscape) (screenHeightDp * 7) / 10 else displayHeight.coerceAtLeast(screenHeightDp / 2)
                } else if (isHandwritingMode) {
                    screenHeightDp / 2
                } else {
                    displayHeight
                }
                val floatScale = if (state.isFloatingMode) 0.85f else 1f
                val effectiveKeyboardHeight = (keyboardHeight * floatScale).toInt()
                val floatingDragBarHeight = if (state.isFloatingMode) 18 else 0
                val floatingCardContentHeight = effectiveKeyboardHeight + floatingDragBarHeight
                Log.d(TAG, "ComposeHeight: showResize=${state.showKeyboardResize} orientHeight=$orientationHeight displayHeight=$displayHeight keyboardHeight=$keyboardHeight floatScale=$floatScale effectiveHeight=$effectiveKeyboardHeight isFloatingMode=${state.isFloatingMode} isLandscape=$isLandscape")
                
                val density = LocalDensity.current
                val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)
                val navBarInsetDp = if (navBarInsetPx > 0) {
                    with(density) { navBarInsetPx.toDp().value.toInt() }
                } else 0
                val navBarDp = navBarInsetDp.dp
                val hasNavBar = navBarDp > 0.dp

                val quickSendFormExtra = if (state.showQuickSendForm) 200 else 0

                XimeTheme(darkTheme = isDarkTheme, themeId = state.themeId) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (state.isCompact || state.isFloatingMode) effectiveScreenH.dp
                                else if (state.showKeyboardResize) ((screenHeightDp * 7) / 10 + 100).dp
                                else (keyboardHeight + state.keyboardBottomPaddingDp + quickSendFormExtra).dp + (if (hasNavBar) navBarDp else 0.dp)
                            )
                    ) {
                        // Sync FrameLayout height with Compose content height
                        val contentHeight = if (state.showKeyboardResize) state.resizePreviewHeightDp else floatingCardContentHeight + quickSendFormExtra
                        val totalDp = if (state.isCompact || state.isFloatingMode) effectiveScreenH
                            else contentHeight + state.keyboardBottomPaddingDp + navBarInsetDp
                        Log.d(TAG, "HeightSync: mode=${if (state.showKeyboardResize) "resize" else "normal"} height=$contentHeight navBarDp=${navBarDp.value} padding=${state.keyboardBottomPaddingDp} hasNavBar=$hasNavBar totalDp=$totalDp")
                        SideEffect {
                            keyboardContainer.updateHeight(totalDp)
                            currentEffectiveKeyboardHeight = if (state.isFloatingMode) keyboardHeight + floatingDragBarHeight + 50 + state.keyboardBottomPaddingDp
                                else if (state.isCompact) HARDWARE_CANDIDATE_BAR_HEIGHT
                                else effectiveKeyboardHeight + quickSendFormExtra
                        }
                        val kbColors = KeysConfigHelper.getKeyboardColors()
                        val longToColor: (Long) -> androidx.compose.ui.graphics.Color = { if (it == 0L)  { androidx.compose.ui.graphics.Color(0xE61E1E1E) } else { androidx.compose.ui.graphics.Color(0xFF000000 or it) } }
                        val isDark = isDarkTheme
                        val cardBg = if (isDark) longToColor(kbColors.keyboardBgColorDark) else longToColor(kbColors.keyboardBgColor)
                        val candidateTextCol = if (isDark) longToColor(kbColors.candidateTextColorDark) else longToColor(kbColors.candidateTextColor)
                        val accentCol = com.kingzcheung.xime.ui.theme.KeyboardThemes.getAccentColor(state.themeId, isDark)
                        if (state.isCompact && (cand.candidates.isNotEmpty() || cand.isShowingRecentClipboard || cand.inputText.isNotEmpty())) {
                            HardwareKeyboardCandidateBar(
                                inputText = cand.inputText,
                                preeditText = cand.preeditText,
                                candidates = cand.candidates,
                                hasNextPage = cand.hasNextPage,
                                hasPrevPage = cand.hasPrevPage,
                                cursorX = state.cursorX,
                                cursorY = state.cursorY,
                                cursorVisible = state.cursorVisible,
                                highlightIndex = highlightIndex.intValue,
                                cardBackgroundColor = cardBg,
                                candidateTextColor = candidateTextCol,
                                activeColor = accentCol,
                            )
                        } else if (state.isCompact) {
                            Box(modifier = Modifier.fillMaxSize())
                        } else {
                        val keyboardBgColor = cardBg
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (!state.isFloatingMode) Modifier.background(keyboardBgColor) else Modifier)
                    ) {
                        Box(
                            modifier = Modifier

                                .fillMaxWidth()
                                .height(if (state.showKeyboardResize) (state.resizePreviewHeightDp + state.keyboardBottomPaddingDp).dp else (floatingCardContentHeight + state.keyboardBottomPaddingDp + quickSendFormExtra).dp)
                                .align(if (state.isFloatingMode) androidx.compose.ui.Alignment.BottomCenter else androidx.compose.ui.Alignment.TopStart)
                        ) {
                        CompositionLocalProvider(LocalStretchFactor provides state.stretchFactor) {
                            val kbState = KeyboardUiState(
                                candidates = cand.candidates,
                                candidateComments = cand.candidateComments,
                                inputText = cand.inputText,
                                preeditText = cand.preeditText,
                                isComposing = cand.isComposing,
                                associationCandidates = if (cand.pendingEnglishText.isNotEmpty()) {
                                    listOf(cand.pendingEnglishText) + cand.associationCandidates
                                } else {
                                    cand.associationCandidates
                                },
                                hasNextPage = cand.hasNextPage,
                                hasPrevPage = cand.hasPrevPage,
                                isAsciiMode = state.isAsciiMode,
                                schemaName = state.schemaName,
                                currentSchemaId = state.currentSchemaId,
                                schemas = state.schemas,
                                enterKeyText = state.enterKeyText,
                                isDarkTheme = isDarkTheme,
                                darkMode = state.darkMode,
                                themeId = state.themeId,
                                keyboardHeightDp = effectiveKeyboardHeight,
                                keyboardBottomPaddingDp = state.keyboardBottomPaddingDp,
                                clipboardItems = clipboardItemsState.value,
                                quickSendItems = quickSendItemsState.value,
                                recentClipboardItems = recentClipboardItemsState.value,
                                isVoiceMode = state.isVoiceMode,
                                voiceBottomActive = state.voiceButtonState.bottomActive,
                                voiceLeftActive = state.voiceButtonState.leftActive,
                                voiceRightActive = state.voiceButtonState.rightActive,
                                voicePluginName = state.voicePluginName,
                                voiceRecognitionState = state.voiceRecognitionState,
                                voiceRecognizedText = state.voiceRecognizedText,
                                voiceAmplitude = voiceAmplitudeState.floatValue,
                                isSttEnabled = state.isSttEnabled,
                                toolbarButtons = state.toolbarButtons,
                                isCalculatorMode = calculatorEngine.isActive(),
                                inputSessionId = state.inputSessionId,
                                isShowingRecentClipboard = cand.isShowingRecentClipboard,
                                isFloatingMode = state.isFloatingMode,
                                isHandwritingMode = isHandwritingMode,
                                floatingOffsetX = state.floatingOffsetX,
                                floatingOffsetY = state.floatingOffsetY,
                                floatingMinOffsetY = floatingMinY,
                                t9ResetSignal = state.t9ResetSignal,
                                t9RightCandidateSelectedCount = state.t9RightCandidateSelectedCount,
                                t9SelectedCandidatePinyin = state.t9SelectedCandidatePinyin,
                                showQuickSendForm = state.showQuickSendForm,
                                quickSendFormFocused = state.quickSendFormFocused,
                                quickSendEditingItemId = state.quickSendEditingItemId,
                                quickSendEditingItemText = state.quickSendEditingItemText,
                            )
                            val view = LocalView.current
                            val callbacks = remember(floatingMinY) {
                                KeyboardCallbacks(
                                    onKeyPress = { key, isShifted ->
                                        handleKeyPress(key, isShifted)
                                    },
                                    onKeyPressDown = { key ->
                                        feedbackManager.performKeyPressDownEffect(key, view)
                                    },
                                    onKeyRelease = { key ->
                                        feedbackManager.hapticFeedback(view, keyUp = true)
                                    },
                                    onCandidateSelect = { index ->
                                        selectCandidate(index)
                                    },
                                    onAssociationSelect = { index ->
                                        feedbackManager.performKeyPressEffect(view = view)
                                        val cs = candidateState.value
                                        val adjustedCandidates = if (cs.pendingEnglishText.isNotEmpty()) {
                                            listOf(cs.pendingEnglishText) + cs.associationCandidates
                                        } else {
                                            cs.associationCandidates
                                        }
                                        if (index >= 0 && index < adjustedCandidates.size) {
                                            val text = adjustedCandidates[index]
                                            val pendingEnglish = cs.pendingEnglishText
                                            if (pendingEnglish.isNotEmpty()) {
                                                if (index == 0 && text == pendingEnglish) {
                                                    commitText(text)
                                                    candidateState.value = candidateState.value.copy(
                                                        pendingEnglishText = "",
                                                        associationCandidates = emptyList()
                                                    )
                                                    Log.d(TAG, "Confirmed pending English: '$text'")
                                                } else {
                                                    // 键入时已用 setComposingText 建立 composing region，
                                                    // commitText 自然替换 composing 文本，终端也兼容。
                                                    commitText(text)
                                                    candidateState.value = candidateState.value.copy(
                                                        pendingEnglishText = "",
                                                        associationCandidates = emptyList()
                                                    )
                                                    Log.d(TAG, "Replaced '$pendingEnglish' with association: '$text'")
                                                }
                                            } else {
                                                commitText(text)
                                                updateUI()
                                            }
                                        }
                                    },
                                    onClearAssociation = {
                                        candidateState.value = candidateState.value.copy(associationCandidates = emptyList())
                                    },
                                    onToggleDarkMode = { toggleDarkMode() },
                                    onClipboard = { Log.d(TAG, "Clipboard clicked") },
                                    onClipboardSelect = { text -> selectClipboardItem(text) },
                                    onCommitText = { text -> commitClipboardText(text) },
                                    onDeleteText = { count -> deleteClipboardChars(count) },
                                    onQuickSend = { Log.d(TAG, "QuickSend clicked") },
                                    onKeyboardResize = {
                                        val config = resources.configuration
                                        val isLandscape = config.screenWidthDp > config.screenHeightDp
                                        val currentHeight = SettingsPreferences.getKeyboardHeightDp(this@XimeInputMethodService, isLandscape)
                                        uiState.value = uiState.value.copy(
                                            showKeyboardResize = true,
                                            resizePreviewHeightDp = currentHeight,
                                        )
                                    },
                                    onReloadConfig = { reloadConfig() },
                                    onSettings = { openSettings() },
                                    onSwitchSchema = { schemaId -> switchSchema(schemaId) },
                                    onHideKeyboard = { hideKeyboard() },
                                    onSwitchKeyboard = {
                                        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        @Suppress("DEPRECATION")
                                        imm.showInputMethodPicker()
                                    },
                                    onToolbarEditingAction = { action -> handleToolbarEditingAction(action) },
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
                                            keyboardViewModel.enterVoice()
                                            feedbackManager.performVibration()
                                            isTrackingVoiceButtons = true
                                            keyboardContainer.enableVoiceButtonTracking()
                                            voiceRecordingStarted = true
                                            voiceRecognitionHandler.startRecognition()
                                            Log.d("VoiceButtons", "Speech recognition starting...")
                                        } else {
                                            keyboardViewModel.exitVoice()
                                            isTrackingVoiceButtons = false
                                        }
                                    },
                                    onPageDown = { pageDown() },
                                    onPageUp = { pageUp() },
                                    onCursorMove = { direction ->
                                        val ic = currentInputConnection
                                        if (ic != null && direction != 0) {
                                            var movedBySelection = false
                                            try {
                                                val req = android.view.inputmethod.ExtractedTextRequest()
                                                val extracted = ic.getExtractedText(req, 0)
                                                if (extracted != null && extracted.selectionStart >= 0) {
                                                    val newPos = (extracted.selectionStart + direction)
                                                        .coerceIn(0, extracted.text?.length ?: 0)
                                                    ic.setSelection(newPos, newPos)
                                                    movedBySelection = true
                                                }
                                            } catch (_: Exception) {}
                                            if (!movedBySelection) {
                                                val keyCode = if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                                                repeat(abs(direction)) {
                                                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
                                                    ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
                                                }
                                            }
                                        }
                                    },
                                    onGestureAction = { action, value ->
                                        action.execute(this@XimeInputMethodService, value)
                                    },
                                    onUpdateToolbarButtons = { buttons ->
                                        SettingsPreferences.setToolbarButtons(this@XimeInputMethodService, buttons)
                                        uiState.value = uiState.value.copy(toolbarButtons = buttons)
                                    },
                                    onKeyboardModeChange = { chineseMode ->
                                        if (isChineseMode != chineseMode) {
                                            isChineseMode = chineseMode
                                            if (!chineseMode) {
                                                candidateState.value = candidateState.value.copy(associationCandidates = emptyList())
                                            }
                                        }
                                    },
                                    onDismissDeploying = { notifyDeploymentStatus(false, "") },
                                    onFloatingModeChange = { enabled -> toggleFloatingMode(enabled, floatingMinY) },
                                    onFloatingKeyboardDrag = { dx, dy ->
                                        val s = uiState.value
                                        val screenW = resources.configuration.screenWidthDp
                                        val screenH = if (state.isFloatingMode) effectiveScreenH else resources.configuration.screenHeightDp
                                        val portraitWidth = minOf(screenW, screenH)
                                        val cardWidth = (portraitWidth * 0.85f).roundToInt()
                                        val halfMargin = ((screenW - cardWidth) / 2f).roundToInt()
                                        val newX = (s.floatingOffsetX + dx).roundToInt().coerceIn(-halfMargin, halfMargin)
                                        val newY_raw = (s.floatingOffsetY + dy).roundToInt()
                                        val actualCardH = if (currentFloatingCardHeightDp > 0) currentFloatingCardHeightDp else currentEffectiveKeyboardHeight
                                        val maxOffsetY = (screenH - actualCardH).coerceAtLeast(floatingMinY)
                                        val newY = newY_raw.coerceIn(0, maxOffsetY)
                                        uiState.value = s.copy(
                                            floatingOffsetX = newX,
                                            floatingOffsetY = newY,
                                        )
                                    },
                                    onFloatingKeyboardDragEnd = {
                                        val s = uiState.value
                                        val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
                                        SettingsPreferences.setFloatingOffsetX(this@XimeInputMethodService, s.floatingOffsetX, isLandscape)
                                        SettingsPreferences.setFloatingOffsetY(this@XimeInputMethodService, s.floatingOffsetY, isLandscape)
                                    },
                                    onT9ReplaceFullPinyin = { pinyin ->
                                        val t0 = System.nanoTime()
                                        serviceScope.launch(keyProcessingDispatcher) {
                                            when {
                                                pinyin == T9InputController.CLEAR_COMPOSITION_ONLY -> {
                                                    rimeEngine.clearComposition()
                                                }
                                                pinyin == T9InputController.CLEAR_ALL -> {
                                                    t9PartialCommitTexts.clear()
                                                    rimeEngine.setInput("")
                                                    rimeEngine.clearComposition()
                                                }
                                                pinyin.isEmpty() -> {
                                                    rimeEngine.clearComposition()
                                                }
                                                else -> {
                                                    rimeEngine.setInput(pinyin)
                                                }
                                            }
                                            val composition = rimeEngine.getComposition()
                                            withContext(Dispatchers.Main) {
                                                val totalMs = (System.nanoTime() - t0) / 1_000_000L
                                                if (totalMs > 10) {
                                                    Log.d(TAG, "T9 replaceFullPinyin: '${pinyin.take(20)}' total=${totalMs}ms")
                                                }
                                                mainHandler.post { applyComposition(composition) }
                                            }
                                        }
                                    },
                                    onT9RightCommitUndone = { count ->
                                        currentInputConnection?.deleteSurroundingText(count, 0)
                                        t9PartialCommitTexts.removeLastOrNull()
                                    },
                                    onT9SwitchAway = {
                                        postRimeJob {
                                            commitFirstCandidateAndClearT9()
                                        }
                                    },
                                    onShowQuickSendForm = {
                                        val current = uiState.value
                                        uiState.value = current.copy(
                                            showQuickSendForm = true,
                                            quickSendFormFocused = true,
                                            quickSendEditingItemId = null,
                                            quickSendEditingItemText = "",
                                            enterKeyText = "确定",
                                        )
                                    },
                                    onQuickSendEditItem = { id, text ->
                                        uiState.value = uiState.value.copy(
                                            showQuickSendForm = true,
                                            quickSendFormFocused = true,
                                            quickSendEditingItemId = id,
                                            quickSendEditingItemText = text,
                                            enterKeyText = "确定",
                                        )
                                        QuickSendFormEditTextHolder.editText?.let { et ->
                                            et.setText(text)
                                            et.setSelection(text.length)
                                        }
                                    },
                                    onHideQuickSendForm = {
                                        uiState.value = uiState.value.copy(
                                            showQuickSendForm = false,
                                            quickSendFormFocused = false,
                                            quickSendEditingItemId = null,
                                            quickSendEditingItemText = "",
                                            enterKeyText = "发送",
                                        )
                                        QuickSendFormEditTextHolder.editText = null
                                        keyboardViewModel.showOverlay(OverlayRoute.Clipboard(1))
                                    },
                                    onQuickSendFormFocusChange = { focused: Boolean ->
                                        uiState.value = uiState.value.copy(
                                            quickSendFormFocused = focused,
                                            enterKeyText = if (focused) "确定" else "发送",
                                        )
                                    },
                                )
                            }
                            keyboardCallbacks = callbacks
                            KeyboardView(
                                viewModel = keyboardViewModel,
                                state = kbState,
                                callbacks = callbacks,
                                onCardPositioned = { _: Int, top: Int, _: Int, bottom: Int ->
                                    val cardHeightPx = bottom - top
                                    if (cardHeightPx > 0) {
                                        currentEffectiveKeyboardHeight = (cardHeightPx / density.density).roundToInt()
                                    }
                                },
                            )
                           }
                           if (state.showKeyboardResize) {
                              KeyboardResizeOverlay(
                                     initialHeightDp = state.resizePreviewHeightDp,
                                     defaultHeightDp = SettingsPreferences.getDefaultKeyboardHeightDp(this@XimeInputMethodService, isLandscape),
                                    maxContainerHeightDp = state.resizePreviewHeightDp + state.keyboardBottomPaddingDp,
                                   currentBottomPaddingDp = state.keyboardBottomPaddingDp,
                                  onHeightChange = { newHeight ->
                                       uiState.value = uiState.value.copy(
                                           resizePreviewHeightDp = newHeight
                                       )
                                   },
                                  onBottomPaddingChange = { newPadding ->
                                       uiState.value = uiState.value.copy(
                                           keyboardBottomPaddingDp = newPadding
                                       )
                                   },
                                  onReset = { defaultHeight ->
                                       uiState.value = uiState.value.copy(
                                           resizePreviewHeightDp = defaultHeight,
                                           keyboardBottomPaddingDp = 0,
                                           stretchFactor = 1f
                                       )
                                   },
                                  onConfirm = { newHeight, newPadding ->
                                       Log.d(TAG, "onConfirm: newHeight=$newHeight newPadding=$newPadding")
                                       setKeyboardHeight(newHeight)
                                       SettingsPreferences.setKeyboardBottomPaddingDp(this@XimeInputMethodService, newPadding)
                                       uiState.value = uiState.value.copy(
                                           showKeyboardResize = false,
                                           keyboardHeightDp = newHeight,
                                           keyboardBottomPaddingDp = newPadding,
                                       )
                                    },
                                    onCancel = {
                                        val restoreHeight = SettingsPreferences.getKeyboardHeightDp(this@XimeInputMethodService, isLandscape)
                                        val restorePadding = SettingsPreferences.getKeyboardBottomPaddingDp(this@XimeInputMethodService)
                                        uiState.value = uiState.value.copy(
                                            showKeyboardResize = false,
                                            keyboardHeightDp = restoreHeight,
                                            keyboardBottomPaddingDp = restorePadding,
                                        )
                                    },
                                    modifier = Modifier
                                       .fillMaxSize()
                              )
                          }
                           }
                            if (!state.isFloatingMode && navBarDp > 0.dp) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(navBarDp))
                            }
                       }
                       }
                     }
                }
            }
        }
        
        keyboardContainer.addView(composeView)

        return keyboardContainer
    }
    
    // ── ActionExecutor 实现 ──

    override fun performEditorMenuAction(actionId: Int) {
        when (actionId) {
            android.R.id.undo -> {
                // performContextMenuAction 对 undo 支持不一致，改用 Ctrl+Z 键盘快捷键
                val now = SystemClock.uptimeMillis()
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON)
                )
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON)
                )
            }
            else -> currentInputConnection?.performContextMenuAction(actionId)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val e = event ?: return super.onKeyDown(keyCode, event)
        Log.d(TAG, "onKeyDown: keyCode=$keyCode")
        if (hasHardwareKeyboard && candidateState.value.candidates.isNotEmpty()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (candidateState.value.hasNextPage) { pageDown(); highlightIndex.intValue = 0; return true }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (candidateState.value.hasPrevPage) { pageUp(); highlightIndex.intValue = 0; return true }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val maxIdx = candidateState.value.candidates.size - 1
                    highlightIndex.intValue = (highlightIndex.intValue + 1).coerceAtMost(maxIdx)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    highlightIndex.intValue = (highlightIndex.intValue - 1).coerceAtLeast(0)
                    return true
                }
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> {
                    if (candidateState.value.candidates.isNotEmpty()) {
                        selectCandidate(highlightIndex.intValue)
                        highlightIndex.intValue = 0
                        return true
                    }
                }
                KeyEvent.KEYCODE_1 -> { selectCandidate(0); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_2 -> { selectCandidate(1); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_3 -> { selectCandidate(2); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_4 -> { selectCandidate(3); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_5 -> { selectCandidate(4); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_6 -> { selectCandidate(5); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_7 -> { selectCandidate(6); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_8 -> { selectCandidate(7); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_9 -> { selectCandidate(8); highlightIndex.intValue = 0; return true }
                KeyEvent.KEYCODE_0 -> { selectCandidate(9); highlightIndex.intValue = 0; return true }
            }
        }
        val isShifted = e.isShiftPressed
        val key = keyCodeToKey(keyCode, isShifted)
        if (key != null) {
            handleKeyPress(key, isShifted)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun sendKeyEvent(keyCode: Int) {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun executeCommand(name: String) {
        when (name) {
            "clear_composition" -> {
                postRimeJob {
                    rimeEngine.clearComposition()
                    withContext(Dispatchers.Main) {
                        mainHandler.post { updateUI() }
                    }
                }
            }
            "show_ime_picker" -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                @Suppress("DEPRECATION")
                imm.showInputMethodPicker()
            }
            else -> Log.w(TAG, "Unknown command: $name")
        }
    }

    override fun repeatLastInput() {
        val lastText = predictionManager.lastCommittedText
        if (lastText.isNotEmpty()) {
            currentInputConnection?.commitText(lastText, 1)
        }
    }

    // ── 原有方法 ──

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
        
        if (RimeEngine.isInitialized()) {
            val savedSchema = SettingsPreferences.getCurrentSchema(this)
            val currentSchema = rimeEngine.getCurrentSchema()
            val availableSchemas = rimeEngine.getAvailableSchemas()
            Log.d(TAG, "onStartInput: saved=$savedSchema, current=$currentSchema, available=${availableSchemas.joinToString()}")
            
            // switchSchema 会重置 ASCII 模式为 schema 默认值，先保存以便后续恢复
            val asciiModeBeforeSchemaSwitch = rimeEngine.isAsciiMode()
            
            val actualSchema: String
            when {
                savedSchema == HANDWRITING_SCHEMA_ID -> {
                    Log.d(TAG, "onStartInput: saved schema is handwriting, checking model files")
                    val modelFile = java.io.File(filesDir, "ochwpro.onnx")
                    val charIndexFile = java.io.File(filesDir, "char_index.json")
                    if (!modelFile.exists() || !charIndexFile.exists()) {
                        Log.w(TAG, "Handwriting model not found, falling back to first available schema")
                        android.widget.Toast.makeText(
                            this, "请先下载手写模型", android.widget.Toast.LENGTH_LONG
                        ).show()
                        val fallbackSchema = if (availableSchemas.isNotEmpty()) {
                            availableSchemas.first()
                        } else {
                            savedSchema
                        }
                        applyPageSizeSetting(fallbackSchema)
                        rimeEngine.switchSchema(fallbackSchema)
                        SettingsPreferences.setCurrentSchema(this, fallbackSchema)
                        actualSchema = fallbackSchema
                    } else {
                        Log.d(TAG, "onStartInput: saved schema is handwriting, keeping handwriting mode")
                        keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.HANDWRITING)
                        actualSchema = savedSchema
                    }
                }
                savedSchema in availableSchemas -> {
                    if (savedSchema != currentSchema) {
                        Log.d(TAG, "onStartInput: Switching to saved schema: $savedSchema")
                        applyPageSizeSetting(savedSchema)
                        rimeEngine.switchSchema(savedSchema)
                    } else {
                        // 即使 schema 相同也重新 switch 一下，确保 processor 完全初始化
                        Log.d(TAG, "onStartInput: Schema already matches, re-switching to init processors")
                        applyPageSizeSetting(savedSchema)
                        rimeEngine.switchSchema(savedSchema)
                    }
                    actualSchema = savedSchema
                }
                SchemaManager.isSchemaCompiled(this@XimeInputMethodService, savedSchema) -> {
                    Log.d(TAG, "onStartInput: Schema compiled but not in get_schema_list, switching anyway")
                    applyPageSizeSetting(savedSchema)
                    rimeEngine.switchSchema(savedSchema)
                    actualSchema = savedSchema
                }
                availableSchemas.isNotEmpty() -> {
                    val fallbackSchema = availableSchemas.first()
                    Log.d(TAG, "onStartInput: savedSchema '$savedSchema' not available, falling back to '$fallbackSchema'")
                    applyPageSizeSetting(fallbackSchema)
                    rimeEngine.switchSchema(fallbackSchema)
                    SettingsPreferences.setCurrentSchema(this, fallbackSchema)
                    actualSchema = fallbackSchema
                }
                else -> actualSchema = savedSchema
            }
            updateSchemaName()
            
            // switchSchema 可能会重置 ASCII 模式，恢复之以保持引擎与 UI 状态同步
            if (rimeEngine.isAsciiMode() != asciiModeBeforeSchemaSwitch) {
                rimeEngine.toggleAsciiMode()
            }
        }

        uiState.value = uiState.value.copy(
            inputSessionId = System.nanoTime(),
            isSttEnabled = SettingsPreferences.isSttEnabled(this@XimeInputMethodService),
        )

        // 重置键盘布局到初始状态，避免切换应用后仍残留之前的布局（如英文、数字、符号）。
        // 必须携带当前 schemaId，否则 T9/笔画等专用布局会被错误重置为默认全键盘。
        if (RimeEngine.isInitialized()) {
            keyboardViewModel.resetKeyboard(rimeEngine.isAsciiMode(), uiState.value.currentSchemaId)
        }

        // 先重置候选状态到初始值，避免前一 session 的残留状态影响新输入
        candidateState.value = CandidateState()

        // 获取最近30秒的剪切板内容
        ensureClipboardManagerInitialized()
        try {
            recentClipboardItemsState.value = clipboardManager.getRecentItems(30)
            // 将最近剪切板内容显示在候选栏
            candidateState.value = candidateState.value.copy(
                candidates = recentClipboardItemsState.value.map { it.text },
                candidateComments = emptyList(),
                isShowingRecentClipboard = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recent clipboard items", e)
        }

        // 监听clipboardItems变化，更新候选栏
        clipboardCollectorJob?.cancel()
        clipboardCollectorJob = serviceScope.launch {
            clipboardManager.clipboardItems.collect { _ ->
                val items = clipboardManager.getRecentItems(30)
                recentClipboardItemsState.value = items
                if (items.isNotEmpty()) {
                    // 清空Rime联想词等
                    rimeEngine.clearComposition()
                    candidateState.value = candidateState.value.copy(
                        candidates = items.map { it.text.take(8) + if (it.text.length > 8) "..." else "" },
                        candidateComments = emptyList(),
                        inputText = "",
                        isComposing = false,
                        associationCandidates = emptyList(),
                        isShowingRecentClipboard = true
                    )
                } else if (candidateState.value.isShowingRecentClipboard) {
                    // 如果没有recent items，清空候选栏
                    candidateState.value = candidateState.value.copy(
                        candidates = emptyList(),
                        candidateComments = emptyList(),
                        isShowingRecentClipboard = false
                    )
                }
            }
        }

        attribute?.let { updateEnterKeyText(it) }
    }
    
    private val highlightIndex = mutableIntStateOf(0)

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        info?.let { updateEnterKeyText(it) }
        hasHardwareKeyboard = resources.configuration.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS
        applyCompactMode()
        if (hasHardwareKeyboard) {
            currentInputConnection?.requestCursorUpdates(
                InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE
            )
        }
    }

    private var anchorCoords = floatArrayOf(0f, 0f, 0f, 0f)

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        if (!hasHardwareKeyboard) return
        try {
            val bounds = info.getCharacterBounds(0)
            if (bounds != null) {
                anchorCoords[0] = bounds.left
                anchorCoords[1] = bounds.bottom
                anchorCoords[2] = bounds.left
                anchorCoords[3] = bounds.top
            } else {
                anchorCoords[0] = info.insertionMarkerHorizontal
                anchorCoords[1] = info.insertionMarkerBottom
                anchorCoords[2] = info.insertionMarkerHorizontal
                anchorCoords[3] = info.insertionMarkerTop
            }
            if (anchorCoords.any(Float::isNaN)) return
            info.matrix.mapPoints(anchorCoords)
            val screenY = anchorCoords[1].toInt().coerceIn(0, resources.displayMetrics.heightPixels)
            val screenX = anchorCoords[0].toInt().coerceIn(0, resources.displayMetrics.widthPixels)
            uiState.value = uiState.value.copy(
                cursorX = screenX,
                cursorY = screenY,
                cursorVisible = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "onUpdateCursorAnchorInfo failed", e)
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        return false
    }

    override fun onEvaluateInputViewShown(): Boolean {
        return true
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        return true
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        hasHardwareKeyboard = newConfig.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS
        super.onConfigurationChanged(newConfig)
        applyCompactMode()
        loadDarkModePreference()
        applyFloatingWindowBackground()
        if (hasHardwareKeyboard) {
            currentInputConnection?.requestCursorUpdates(
                InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE
            )
        }
    }

    private fun applyFloatingWindowBackground() {
        if (!uiState.value.isFloatingMode) return
        try {
            window.window?.let { win ->
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                win.setDimAmount(0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyFloatingWindowBackground failed", e)
        }
    }

    private fun applyCompactMode() {
        val current = uiState.value
        val isCompact = hasHardwareKeyboard
        if (current.isCompact != isCompact) {
            uiState.value = current.copy(isCompact = isCompact)
            if (isCompact) {
                window.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
        }
    }

    private fun moveFloatingWindow(dx: Int, dy: Int) {
        window.window?.let { win ->
            val lp = win.attributes
            if (lp.gravity != (android.view.Gravity.TOP or android.view.Gravity.START)) {
                lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
            lp.x = (lp.x + dx).coerceAtLeast(0)
            lp.y = (lp.y + dy).coerceAtLeast(0)
            win.attributes = lp
        }
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
        clipboardCollectorJob?.cancel()
        clipboardCollectorJob = null
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
        calculatorEngine.clear()
        rimeEngine.clearComposition()
        t9PartialCommitTexts.clear()
        uiState.value = uiState.value.copy(
            t9ResetSignal = uiState.value.t9ResetSignal + 1,
            t9RightCandidateSelectedCount = 0,
            t9SelectedCandidatePinyin = ""
        )
        candidateState.value = candidateState.value.copy(
            candidates = emptyList(),
            candidateComments = emptyList(),
            inputText = "",
            isComposing = false,
            isShowingRecentClipboard = false,
            associationCandidates = emptyList(),
            pendingEnglishText = "",
            hasNextPage = false,
            hasPrevPage = false
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPrefsListener?.let {
            SettingsPreferences.getPrefsPublic(this).unregisterOnSharedPreferenceChangeListener(it)
        }
        RimeEngine.setDeploymentCallback { _, _ -> }
        if (::clipboardManager.isInitialized) {
            clipboardManager.release()
        }
        _viewModelStore.clear()
        feedbackManager.release()
        rimeEngine.destroy()
        AssociationManager.release()
        voiceRecognitionHandler.release()
        com.kingzcheung.xime.handwriting.HandwritingEngine.release()
        ExtensionManager.release()
        com.kingzcheung.xime.association.NativeOnnxEngine.releaseSharedEnv()
        serviceScope.cancel()
        keyProcessingDispatcher.close()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
    
    private fun hideKeyboard() {
        clearInputState()
        requestHideSelf(0)
    }
    
    private fun updateUI() {
        applyComposition(rimeEngine.getComposition())
    }

    private fun applyComposition(composition: com.kingzcheung.xime.rime.RimeComposition) {
        val inputText = composition.input
        val preeditText = composition.preedit
        val candidatesWithComments = composition.candidates.toList()
        if (candidatesWithComments.isNotEmpty() || inputText.isNotEmpty()) {
            Log.d(TAG, "updateUI: input='$inputText' preedit='$preeditText' candidates=${candidatesWithComments.joinToString { "'${it.text}'/${it.comment}'" }}")
        }
        val isAsciiMode = composition.isAsciiMode
        val hasNextPage = composition.hasNextPage
        val hasPrevPage = composition.hasPrevPage

        val pendingEnglish = candidateState.value.pendingEnglishText

        val (filteredTexts, filteredComments) = if (isAsciiMode) {
            val filtered = candidatesWithComments.filterNot { candidate ->
                candidate.text.any { it.code in 0x4E00..0x9FFF }
            }
            filtered.map { it.text } to filtered.map { it.comment }
        } else {
            candidatesWithComments.map { it.text } to candidatesWithComments.map { it.comment }
        }

        val isT9Schema = isT9Schema(uiState.value.currentSchemaId)
        // T9 候选词过滤：根据左侧选择历史过滤不匹配的候选词
        val (t9FilteredTexts, t9FilteredComments) = if (isT9Schema) {
            keyboardCallbacks?.onFilterT9Candidates?.invoke(filteredTexts, filteredComments)
                ?: (filteredTexts to filteredComments)
        } else {
            filteredTexts to filteredComments
        }
        val displayText: String
        val displayCandidates: List<String>
        val displayComments: List<String>
        val isComposing: Boolean
        if (isT9Schema) {
            val rawPreedit = if (preeditText.isNotEmpty()) preeditText else inputText
            val convertedPreedit = convertT9PreeditToPinyin(rawPreedit, candidatesWithComments.firstOrNull()?.comment ?: "")
            val display = buildT9DisplayState(
                t9PartialCommitTexts, convertedPreedit, inputText, t9FilteredTexts, t9FilteredComments
            )
            displayText = display.displayText
            displayCandidates = display.displayCandidates
            displayComments = display.displayComments
            isComposing = display.isComposing
        } else {
            displayText = if (preeditText.isNotEmpty()) preeditText else inputText
            displayCandidates = filteredTexts
            displayComments = filteredComments
            isComposing = inputText.isNotEmpty()
        }


        candidateState.value = candidateState.value.copy(
            inputText = displayText,
            preeditText = displayText,
            candidates = displayCandidates,
            candidateComments = displayComments,
            isComposing = isComposing,
            associationCandidates = if ((isAsciiMode || !isChineseMode) && pendingEnglish.isEmpty()) emptyList() else candidateState.value.associationCandidates,
            isShowingRecentClipboard = false,
            hasNextPage = hasNextPage,
            hasPrevPage = hasPrevPage
        )
        uiState.value = uiState.value.copy(isAsciiMode = isAsciiMode)

        if (pendingEnglish.isNotEmpty()) {
            serviceScope.launch {
                val candidates = predictionManager.getEnglishAssociations(pendingEnglish, PredictionManager.MAX_ASSOCIATION_COUNT)
                Log.d(TAG, "English association for pending '$pendingEnglish': ${candidates.joinToString()}")
                withContext(Dispatchers.Main) {
                    candidateState.value = candidateState.value.copy(associationCandidates = candidates)
                }
            }
        }
    }

    private fun updateUIWithResult(result: com.kingzcheung.xime.rime.RimeProcessResult) {
        val t0 = System.nanoTime()
        val isAsciiMode = result.isAsciiMode
        val candidatesWithComments = result.candidates
        if (candidatesWithComments.isNotEmpty() || result.inputText.isNotEmpty()) {
            Log.d(TAG, "updateUIWithResult: input='${result.inputText}' preedit='${result.preeditText}' candidates=${candidatesWithComments.joinToString { "'${it.text}'/${it.comment}'" }}")
        }

        val pendingEnglish = candidateState.value.pendingEnglishText

        val tFilter = System.nanoTime()
        val (filteredTexts, filteredComments) = if (isAsciiMode) {
            val filtered = candidatesWithComments.filterNot { candidate ->
                candidate.text.any { it.code in 0x4E00..0x9FFF }
            }
            filtered.map { it.text } to filtered.map { it.comment }
        } else {
            candidatesWithComments.map { it.text } to candidatesWithComments.map { it.comment }
        }

        // 非 T9 方案（如双拼）使用原始输入文本显示，
        // 避免显示 rime speller 展开后的编码（如双拼 i → ch）
        val isT9Schema = isT9Schema(uiState.value.currentSchemaId)
        // T9 候选词过滤：根据左侧选择历史（全拼/简拼）过滤不匹配的候选词
        val (t9FilteredTexts, t9FilteredComments) = if (isT9Schema) {
            keyboardCallbacks?.onFilterT9Candidates?.invoke(filteredTexts, filteredComments)
                ?: (filteredTexts to filteredComments)
        } else {
            filteredTexts to filteredComments
        }
        val displayText: String
        val displayCandidates: List<String>
        val displayComments: List<String>
        val isComposing: Boolean
        if (isT9Schema) {
            val rawPreedit = if (result.preeditText.isNotEmpty()) result.preeditText else result.inputText
            val convertedPreedit = convertT9PreeditToPinyin(rawPreedit, candidatesWithComments.firstOrNull()?.comment ?: "")
            val display = buildT9DisplayState(
                t9PartialCommitTexts, convertedPreedit, result.inputText, t9FilteredTexts, t9FilteredComments
            )
            displayText = display.displayText
            displayCandidates = display.displayCandidates
            displayComments = display.displayComments
            isComposing = display.isComposing
        } else {
            displayText = if (result.preeditText.isNotEmpty()) result.preeditText else result.inputText
            displayCandidates = filteredTexts
            displayComments = filteredComments
            isComposing = result.inputText.isNotEmpty()
        }

        candidateState.value = candidateState.value.copy(
            inputText = displayText,
            preeditText = displayText,
            candidates = displayCandidates,
            candidateComments = displayComments,
            isComposing = isComposing,
            associationCandidates = if ((isAsciiMode || !isChineseMode) && pendingEnglish.isEmpty()) emptyList() else candidateState.value.associationCandidates,
            isShowingRecentClipboard = false,
            hasNextPage = result.hasNextPage,
            hasPrevPage = result.hasPrevPage
        )
        uiState.value = uiState.value.copy(isAsciiMode = isAsciiMode)
        
        if (pendingEnglish.isNotEmpty()) {
            serviceScope.launch {
                val candidates = predictionManager.getEnglishAssociations(pendingEnglish, PredictionManager.MAX_ASSOCIATION_COUNT)
                Log.d(TAG, "English association for pending '$pendingEnglish': ${candidates.joinToString()}")
                withContext(Dispatchers.Main) {
                    candidateState.value = candidateState.value.copy(associationCandidates = candidates)
                }
            }
        }
        
    }

    private fun updateSchemaName() {
        val context = this@XimeInputMethodService
        serviceScope.launch(Dispatchers.IO) {
            val page = keyboardViewModel.page.value
            val isHandwritingMode = (page as? com.kingzcheung.xime.keyboard.KeyboardPage.Main)?.type == com.kingzcheung.xime.keyboard.MainType.HANDWRITING
            val currentSchemaId = if (isHandwritingMode) {
                HANDWRITING_SCHEMA_ID
            } else {
                rimeEngine.getCurrentSchema()
            }
            val name = SchemaManager.getSchemaDisplayName(context, currentSchemaId)

            val enabledIds = SchemaManager.getEnabledSchemas(context)
            val allSchemas = SchemaManager.discoverSchemas(context)
            val schemas = allSchemas
                .filter { meta -> meta.schemaId in enabledIds && SchemaManager.isSchemaCompiled(context, meta.schemaId) }
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

            withContext(Dispatchers.Main) {
                uiState.value = uiState.value.copy(
                    schemaName = name ?: currentSchemaId,
                    currentSchemaId = currentSchemaId,
                    schemas = schemas
                )
            }
        }
    }

    /**
     * T9 键盘切换离开时：提交右侧候选词列表首位候选词并清理 T9 和 Rime 状态。
     * 运行在 keyProcessingDispatcher 线程。
     */
    private suspend fun commitFirstCandidateAndClearT9() {
        val isT9 = isT9Schema(uiState.value.currentSchemaId)
        if (!isT9) return

        val candState = candidateState.value
        val candidates = candState.candidates

        if (candidates.isNotEmpty()) {
            if (rimeEngine.selectCandidate(0)) {
                val committedText = rimeEngine.commit()
                if (committedText.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        commitText(committedText)
                    }
                }
            }
        }

        rimeEngine.clearComposition()

        withContext(Dispatchers.Main) {
            keyboardCallbacks?.onT9ReplaceFullPinyin?.invoke(T9InputController.CLEAR_ALL)
            uiState.value = uiState.value.copy(
                t9ResetSignal = uiState.value.t9ResetSignal + 1,
                t9RightCandidateSelectedCount = 0,
                t9SelectedCandidatePinyin = ""
            )
            t9PartialCommitTexts.clear()
            candidateState.value = candidateState.value.copy(
                inputText = "",
                preeditText = "",
                candidates = emptyList(),
                candidateComments = emptyList(),
                isComposing = false,
                associationCandidates = emptyList(),
                hasNextPage = false,
                hasPrevPage = false
            )
        }
    }

    private fun handleKeyPress(key: String, isShifted: Boolean) {
        if (uiState.value.quickSendFormFocused) {
            when (key) {
                "enter" -> {
                    val editText = QuickSendFormEditTextHolder.editText
                    val text = editText?.text?.toString() ?: ""
                    val s = uiState.value
                    val editingId = s.quickSendEditingItemId
                    if (text.isNotBlank()) {
                        if (editingId != null) {
                            keyboardViewModel.updateQuickSendItem(editingId, text)
                        } else {
                            keyboardViewModel.addQuickSendText(text)
                        }
                    }
                    uiState.value = s.copy(showQuickSendForm = false, quickSendFormFocused = false, quickSendEditingItemId = null, quickSendEditingItemText = "")
                    QuickSendFormEditTextHolder.editText = null
                    keyboardViewModel.showOverlay(OverlayRoute.Clipboard(1))
                    return
                }
                "delete" -> {
                    QuickSendFormEditTextHolder.editText?.let { et ->
                        val start = et.selectionStart.coerceAtLeast(0)
                        val end = et.selectionEnd.coerceAtLeast(start)
                        if (start == end && start > 0) {
                            et.text?.delete(start - 1, start)
                            try { et.setSelection(start - 1) } catch (_: Exception) {}
                        } else if (end > start) {
                            et.text?.delete(start, end)
                            try { et.setSelection(start) } catch (_: Exception) {}
                        }
                    }
                    return
                }
            }
        }
        val job = serviceScope.launch(keyProcessingDispatcher, start = CoroutineStart.LAZY) {
            val state = uiState.value
            val candState = candidateState.value
            var needsUIUpdate = false
            var pendingResult: com.kingzcheung.xime.rime.RimeProcessResult? = null
            var committedText: String? = null
            
            when (key) {
                "delete" -> {
                    // 计算器模式：追踪退格
                    calculatorEngine.handleDelete()
                    updateCalculatorCandidates()
                    
                    // 数字/符号键盘：直接发送系统退格，不经过 Rime
                    // 防止 T9 残留状态被 Rime 退格修改导致 UI 不一致
                    val layoutState = keyboardViewModel.keyboardState.value
                    if (layoutState is KeyboardLayoutState.Number || layoutState is KeyboardLayoutState.Symbol) {
                        withContext(Dispatchers.Main) {
                            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                        }
                    } else when {
                        // 1. 英文待处理文本：逐个删除字符，重新加载联想
                        candState.pendingEnglishText.isNotEmpty() -> {
                            val pendingLen = candState.pendingEnglishText.length
                            if (pendingLen > 1) {
                                val newPending = candState.pendingEnglishText.dropLast(1)
                                withContext(Dispatchers.Main) {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                                    candidateState.value = candidateState.value.copy(
                                        pendingEnglishText = newPending,
                                        candidates = emptyList(),
                                        candidateComments = emptyList(),
                                        associationCandidates = emptyList()
                                    )
                                }
                                serviceScope.launch {
                                    val candidates = predictionManager.getEnglishAssociations(newPending, PredictionManager.MAX_ASSOCIATION_COUNT)
                                    withContext(Dispatchers.Main) {
                                        candidateState.value = candidateState.value.copy(associationCandidates = candidates)
                                    }
                                }
                                Log.d(TAG, "Delete: one char from pending English, now '$newPending'")
                            } else {
                                withContext(Dispatchers.Main) {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                                    candidateState.value = candidateState.value.copy(
                                        pendingEnglishText = "",
                                        candidates = emptyList(),
                                        candidateComments = emptyList(),
                                        associationCandidates = emptyList(),
                                        isShowingRecentClipboard = false
                                    )
                                }
                                Log.d(TAG, "Delete: last pending English char, cleared")
                            }
                        }
                        
                        // 2. Rime 编码中：让 Rime 处理退格，更新候选
                        candState.isComposing || candState.inputText.isNotEmpty() -> {
                            rimeEngine.processKey(0xff08, 0)
                            val result = rimeEngine.getProcessResult(true)
                            if (result.inputText.isEmpty()) {
                                rimeEngine.clearComposition()
                            }
                            uiEventChannel.trySend {
                                updateUIWithResult(result)
                                if (calculatorEngine.isActive()) updateCalculatorCandidates()
                            }
                            Log.d(TAG, "Delete: processed Rime backspace, remaining='${result.inputText}'")
                        }
                        
                        // 3. 联想词或剪贴板：仅清空候选栏，不回删已上屏字符
                        candState.associationCandidates.isNotEmpty() || candState.isShowingRecentClipboard -> {
                            Log.d(TAG, "Delete: cleared predictions, clipboard=${candState.isShowingRecentClipboard}")
                            
                            candidateState.value = candidateState.value.copy(
                                candidates = emptyList(),
                                candidateComments = emptyList(),
                                associationCandidates = emptyList(),
                                isShowingRecentClipboard = false
                            )
                        }
                        
                        // 4. 无候选也无编码：直接回删已上屏文本
                        else -> {
                            predictionManager.deleteLastChar()
                            Log.d(TAG, "Delete committed text, remaining: '${predictionManager.lastCommittedText}'")
                            
                            withContext(Dispatchers.Main) {
                                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                            }
                            
                            candidateState.value = candidateState.value.copy(
                                candidates = emptyList(),
                                candidateComments = emptyList(),
                                associationCandidates = emptyList(),
                                isShowingRecentClipboard = false
                            )
                        }
                    }
                }
                "clear_composition" -> {
                    calculatorEngine.clear()
                    updateCalculatorCandidates()
                    rimeEngine.clearComposition()
                    candidateState.value = candidateState.value.copy(
                        candidates = emptyList(),
                        candidateComments = emptyList(),
                        associationCandidates = emptyList(),
                        pendingEnglishText = "",
                        isShowingRecentClipboard = false
                    )
                    needsUIUpdate = true
                    Log.d(TAG, "Clear composition: cleared all")
                }
                "clear_all" -> {
                    calculatorEngine.clear()
                    updateCalculatorCandidates()
                    // 记录当前输入框中的文本以便撤回
                    val inputFieldText = withContext(Dispatchers.Main) {
                        currentInputConnection?.getTextBeforeCursor(SAFE_TEXT_LIMIT, 0)?.toString() ?: ""
                    }
                    lastClearedText = inputFieldText + candState.inputText
                    rimeEngine.clearComposition()
                    candidateState.value = candidateState.value.copy(
                        candidates = emptyList(),
                        candidateComments = emptyList(),
                        associationCandidates = emptyList(),
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
                    calculatorEngine.clear()
                    updateCalculatorCandidates()
                    if (candState.isComposing) {
                        val input = candState.inputText
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
                            val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                            when {
                                // 如果设置了 IME_FLAG_NO_ENTER_ACTION，必须插入换行符
                                // 不能走 performEditorAction，否则某些应用收到 Done/Send 等
                                // 动作后会收起键盘，但按键标签显示的是"换行"
                                noEnterAction -> {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                                }
                                action == EditorInfo.IME_ACTION_GO ||
                                action == EditorInfo.IME_ACTION_SEARCH ||
                                action == EditorInfo.IME_ACTION_SEND ||
                                action == EditorInfo.IME_ACTION_NEXT ||
                                action == EditorInfo.IME_ACTION_DONE -> {
                                    currentInputConnection?.performEditorAction(action)
                                }
                                else -> {
                                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        candidateState.value = candidateState.value.copy(
                            inputText = "",
                            pendingEnglishText = "",
                            candidates = emptyList(),
                            candidateComments = emptyList(),
                            associationCandidates = emptyList(),
                            isComposing = false
                        )
                        // T9 模式：同步重置 T9 控制器状态并清空 partial commit 累积文本，
                        // 否则左侧候选区残留、下一轮输入会拼接旧 partial commit。
                        if (isT9Schema(state.currentSchemaId)) {
                            keyboardCallbacks?.onT9ReplaceFullPinyin?.invoke(T9InputController.CLEAR_ALL)
                            uiState.value = uiState.value.copy(
                                t9ResetSignal = uiState.value.t9ResetSignal + 1,
                                t9RightCandidateSelectedCount = 0,
                                t9SelectedCandidatePinyin = ""
                            )
                        }
                    }
                }
                "space" -> {
                    val pendingEnglish = candState.pendingEnglishText

                    if (pendingEnglish.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            commitText(pendingEnglish + " ")
                            candidateState.value = candidateState.value.copy(
                                pendingEnglishText = "",
                                associationCandidates = emptyList()
                            )
                        }
                        Log.d(TAG, "Space: committed '$pendingEnglish '")
                    } else if (candState.isComposing) {
                        if (candState.candidates.isNotEmpty()) {
                            selectCandidateAsync(0)
                        } else {
                            val input = candState.inputText
                            if (input.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    commitText(input)
                                }
                                rimeEngine.clearComposition()
                                // T9模式：清空partialCommit累积文本，避免下一轮输入
                                // preedit中残留上一轮的提交内容（如"看"→下一轮"看jihua"）
                                if (isT9Schema(state.currentSchemaId)) {
                                    withContext(Dispatchers.Main) {
                                        t9PartialCommitTexts.clear()
                                        uiState.value = uiState.value.copy(
                                            t9ResetSignal = uiState.value.t9ResetSignal + 1,
                                            t9RightCandidateSelectedCount = 0,
                                            t9SelectedCandidatePinyin = ""
                                        )
                                    }
                                }
                                needsUIUpdate = true
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            commitText(" ")
                        }
                    }
                }
                "word_separator" -> {
                    if (candState.isComposing || candState.inputText.isNotEmpty()) {
                        val result = rimeEngine.processKeyAndGetResult(0x27, 0)
                        if (result.processed) {
                            uiEventChannel.trySend {
                                updateUIWithResult(result)
                            }
                        } else {
                            needsUIUpdate = true
                        }
                    } else {
                        needsUIUpdate = true
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
                    calculatorEngine.clear()
                    updateCalculatorCandidates()
                }
                "number", "common_symbol" -> {
                    // Number/CommonSymbol 内部切换由 KeyboardView 的 key handler 处理
                }
                "emoji" -> {
                    withContext(Dispatchers.Main) {
                        commitText("😊")
                    }
                }
                else -> {
                    val isNumberKeyboard = keyboardViewModel.keyboardState.value is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.Number
                    val isCommonSymbolKeyboard = keyboardViewModel.keyboardState.value is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.CommonSymbol

                    val routeResult = com.kingzcheung.xime.calculator.routeCalculatorKey(
                        key = key,
                        isNumberKeyboard = isNumberKeyboard,
                        isCommonSymbolKeyboard = isCommonSymbolKeyboard,
                        calculatorEngine = calculatorEngine,
                    )
                    if (routeResult is com.kingzcheung.xime.calculator.CalculatorRouteResult.Handled) {
                        withContext(Dispatchers.Main) { commitText(routeResult.commitText) }
                        if (isNumberKeyboard) updateCalculatorCandidates()
                        needsUIUpdate = true
                        return@launch
                    }

                    val pendingEnglish = candState.pendingEnglishText
                    
                    // 非计算器键清除计算器状态
                    if (!key.matches(Regex("[0-9]")) && key !in listOf("+", "-", "*", "/", ".")) {
                        if (calculatorEngine.isActive() || calculatorEngine.getCandidate() != null) {
                            calculatorEngine.clear()
                            updateCalculatorCandidates()
                        }
                    }
                    
                    // 计算器模式：追踪数字、运算符和小数点
                    if (key.matches(Regex("[0-9]")) || key in listOf("+", "-", "*", "/", ".")) {
                        if (key.matches(Regex("[0-9]")) || key == ".") {
                            calculatorEngine.handleDigit(key)
                        } else {
                            calculatorEngine.handleOperator(key)
                        }
                        updateCalculatorCandidates()
                    }
                    
                    // 所有按键统一经过 Rime 引擎
                    // 字母键不进入此分支（即使 pendingEnglish 非空），需要继续积累编码
                    if (pendingEnglish.isNotEmpty() && !key.matches(Regex("[a-zA-Z]"))) {
                        val finalKey = key
                        withContext(Dispatchers.Main) {
                            commitText(pendingEnglish + finalKey)
                            candidateState.value = candidateState.value.copy(
                                pendingEnglishText = "",
                                associationCandidates = emptyList()
                            )
                        }
                        Log.d(TAG, "Symbol: committed '$pendingEnglish$finalKey'")
                    } else {
                        val isChinese = !state.isAsciiMode
                        val char = key
                        val keyCode = key.lowercase()[0].code
                        val mask = if (isShifted) KeyEvent.META_SHIFT_ON else 0
                        val isLetter = key.matches(Regex("[a-zA-Z]"))
                        val isShiftedChinese = isShifted && isChinese && isLetter

                        // Shifted non-letter keys: send character code to Rime (like soft keyboard does),
                        // avoiding Rime misinterpreting physical keycodes as internal actions.
                        if (isShifted && !isLetter) {
                            if (char.length == 1) {
                                val charCode = char[0].code
                                val processed = rimeEngine.processKey(charCode, 0)
                                if (processed) {
                                    val result = rimeEngine.getProcessResult(processed)
                                    uiEventChannel.trySend {
                                        if (result.committedText.isNotEmpty()) commitText(result.committedText)
                                        updateUIWithResult(result)
                                        if (calculatorEngine.isActive()) updateCalculatorCandidates()
                                    }
                                    Log.d(TAG, "Shift+symbol: Rime processed charCode=$charCode, result='${result.committedText}'")
                                } else {
                                    committedText = char
                                    needsUIUpdate = true
                                    Log.d(TAG, "Shift+symbol: Rime unprocessed, committing '$char' directly")
                                }
                            } else {
                                committedText = char
                                needsUIUpdate = true
                                Log.d(TAG, "Shift+symbol: multi-char '$char' committed directly")
                            }
                        } else {
                            val processed = rimeEngine.processKey(keyCode, mask)
                            if (processed) {
                                val result = rimeEngine.getProcessResult(processed)
                                if (isShiftedChinese && result.committedText != char) {
                                    rimeEngine.clearComposition()
                                    committedText = char
                                    needsUIUpdate = true
                                    Log.d(TAG, "Shift+letter in Chinese mode: Rime consumed key but didn't produce uppercase, committing '$char' directly")
                                } else {
                                    val committed = result.committedText
                                    if (state.isAsciiMode && committed.isNotEmpty() && result.inputText.isEmpty() && result.candidates.isEmpty()) {
                                        val current = candState.pendingEnglishText
                                        val newPending = current + committed
                                        candidateState.value = candidateState.value.copy(pendingEnglishText = newPending)
                                        withContext(Dispatchers.Main) {
                                            currentInputConnection?.setComposingText(newPending, 1)
                                        }
                                        uiEventChannel.trySend {
                                            updateUIWithResult(result)
                                            if (calculatorEngine.isActive()) updateCalculatorCandidates()
                                        }
                                    } else {
                                        uiEventChannel.trySend {
                                            if (committed.isNotEmpty()) commitText(committed)
                                            updateUIWithResult(result)
                                            if (calculatorEngine.isActive()) updateCalculatorCandidates()
                                        }
                                    }
                                }
                            } else {
                                val isAscii = state.isAsciiMode
                                if (!candState.isComposing || isShiftedChinese) {
                                                    if (isAscii) {
                                                        val charToCommit = if (isShifted) char.uppercase() else char.lowercase()
                                                        val currentPending = candState.pendingEnglishText
                                                        val newPending = currentPending + charToCommit
                                                        // 用 setComposingText 建立 composing region，选关联候选时 commitText 自然替换
                                                        withContext(Dispatchers.Main) {
                                                            currentInputConnection?.setComposingText(newPending, 1)
                                                        }
                                                        candidateState.value = candidateState.value.copy(
                                                            pendingEnglishText = newPending,
                                                            associationCandidates = emptyList()
                                                        )
                                                        needsUIUpdate = true
                                                        Log.d(TAG, "English mode: setComposingText '$newPending'")
                                    } else {
                                        committedText = char
                                        needsUIUpdate = true
                                    }
                                } else {
                                    val candidateText = if (rimeEngine.selectCandidate(0)) {
                                        rimeEngine.commit()
                                    } else {
                                        ""
                                    }
                                    committedText = candidateText + char
                                    needsUIUpdate = true
                                }
                            }
                        }
                    }
                }
            }
            
            if (needsUIUpdate) {
                val result = pendingResult
                val textToCommit = committedText
                if (result != null) {
                    uiEventChannel.trySend {
                        if (textToCommit != null) {
                            commitText(textToCommit)
                        }
                        updateUIWithResult(result)
                        if (calculatorEngine.isActive()) {
                            updateCalculatorCandidates()
                        }
                    }
                } else {
                    val capturedInputText = rimeEngine.getInput()
                    val capturedCandidates = rimeEngine.getCandidatesWithComments()
                    val capturedIsAscii = rimeEngine.isAsciiMode()
                    val capturedHasNext = rimeEngine.hasNextPage()
                    val capturedHasPrev = rimeEngine.hasPrevPage()
                    uiEventChannel.trySend {
                        if (textToCommit != null) {
                            commitText(textToCommit)
                        }
                        val pendingEnglish = candidateState.value.pendingEnglishText
                        val (filteredTexts, filteredComments) = if (capturedIsAscii) {
                            val filtered = capturedCandidates.filterNot { candidate ->
                                candidate.text.any { it.code in 0x4E00..0x9FFF }
                            }
                            filtered.map { it.text } to filtered.map { it.comment }
                        } else {
                            capturedCandidates.map { it.text } to capturedCandidates.map { it.comment }
                        }
                        candidateState.value = candidateState.value.copy(
                            inputText = capturedInputText,
                            candidates = filteredTexts,
                            candidateComments = filteredComments,
                            isComposing = capturedInputText.isNotEmpty(),
                            associationCandidates = if ((capturedIsAscii || !isChineseMode) && pendingEnglish.isEmpty()) emptyList() else candidateState.value.associationCandidates,
                            isShowingRecentClipboard = false,
                            hasNextPage = capturedHasNext,
                            hasPrevPage = capturedHasPrev
                        )
                        uiState.value = uiState.value.copy(isAsciiMode = capturedIsAscii)
                        if (pendingEnglish.isNotEmpty()) {
                            serviceScope.launch {
                                val candidates = predictionManager.getEnglishAssociations(pendingEnglish, PredictionManager.MAX_ASSOCIATION_COUNT)
                                withContext(Dispatchers.Main) {
                                    candidateState.value = candidateState.value.copy(associationCandidates = candidates)
                                }
                            }
                        }
                        if (calculatorEngine.isActive()) {
                            updateCalculatorCandidates()
                        }
                    }
                }
            }
        }
        keyJobs.trySend(job)
    }

    /**
     * Posts a rime operation to [keyJobs] for sequential execution.
     * Ensures no interleaving with key processing.
     */
    private fun postRimeJob(block: suspend CoroutineScope.() -> Unit) {
        val job = serviceScope.launch(keyProcessingDispatcher, start = CoroutineStart.LAZY) {
            block()
        }
        keyJobs.trySend(job)
    }

    private suspend fun selectCandidateAsync(index: Int) {
        val selectedCandidate = if (index < candidateState.value.candidates.size) {
            candidateState.value.candidates[index]
        } else null

        val isT9 = isT9Schema(uiState.value.currentSchemaId)
        val candidatePinyin = if (isT9 && index < candidateState.value.candidateComments.size) {
            candidateState.value.candidateComments[index]
        } else null

        // 在 RIME 真正 select/commit 之前，先同步通知 T9 控制器消费数字。
        // 控制器返回 true 表示输入序列已被该候选词完整消费，服务层应视为 full commit。
        val candidateTextLength = selectedCandidate?.length ?: 0
        val fullyConsumed = if (isT9) {
            keyboardCallbacks?.onT9RightCandidateWillBeSelected?.invoke(candidatePinyin, candidateTextLength) ?: false
        } else {
            false
        }

        // T9 模式：UI 候选词列表经过 filterCandidatesBySelectionHistory 过滤/重排序
        // （FULL 在前、PREFIX 在后、NONE 排除），其 index 可能与 RIME 原始候选词
        // index 不对应。通过候选词文本查找 RIME 原始候选词的真实 index，确保
        // selectCandidate 选对词（场景19 后续修复：上屏错词问题）。
        val rimeIndex = if (isT9 && selectedCandidate != null) {
            resolveRimeCandidateIndex(index, selectedCandidate, rimeEngine.getCandidates().toList())
        } else {
            index
        }

        if (rimeEngine.selectCandidate(rimeIndex)) {
            val committedText = rimeEngine.commit()
            // T9 模式下 fullyConsumed 是判断 full/partial commit 的唯一权威：
            // 当控制器明确 partial commit 时，即使 RIME commit() 返回非空文本
            // （RIME 内部做了 partial commit），也不应走 full commit 路径。
            val isFullCommit = if (isT9) {
                fullyConsumed && selectedCandidate != null
            } else {
                committedText.isNotEmpty()
            }
            if (isFullCommit) {
                if (SettingsPreferences.isSmartPredictionEnabled(this) && selectedCandidate != null && AssociationManager.isInitialized()) {
                    if (predictionManager.lastCommittedText.isNotEmpty()) {
                        val lastChar = predictionManager.lastCommittedText.last().toString()
                        predictionManager.recordInputPair(lastChar, selectedCandidate)
                        Log.d(TAG, "Learned: '$lastChar' + '$selectedCandidate'")
                    }
                }
                // T9 full commit：优先使用用户明确选中的候选词文本作为上屏文本。
                // UI 候选词列表经 filterCandidatesBySelectionHistory 过滤/重排序后 index
                // 可能与 RIME 原始候选词 index 不对应。即使已通过 resolveRimeCandidateIndex
                // 修正了 selectCandidate 的 index，仍以用户选中的 selectedCandidate 为权威
                // 上屏文本（双保险），避免 RIME commit() 因任何原因返回错误文本。
                // 非 T9 模式仍保持 RIME committedText 优先（可能含简繁转换等处理）。
                val textToMerge = if (isT9 && fullyConsumed && selectedCandidate != null) {
                    selectedCandidate
                } else if (committedText.isNotEmpty()) {
                    committedText
                } else {
                    selectedCandidate!!
                }
                val fullCommitText = if (isT9) {
                    PreeditMergeHelper.mergePartialCommitText(t9PartialCommitTexts, textToMerge)
                } else {
                    textToMerge
                }
                withContext(Dispatchers.Main) {
                    commitText(fullCommitText)
                    t9PartialCommitTexts.clear()
                    candidateState.value = candidateState.value.copy(
                        inputText = "",
                        candidates = emptyList(),
                        candidateComments = emptyList(),
                        isComposing = false,
                        hasNextPage = false,
                        hasPrevPage = false,
                        isShowingRecentClipboard = false
                    )
                    uiState.value = uiState.value.copy(
                        t9ResetSignal = uiState.value.t9ResetSignal + 1,
                        t9RightCandidateSelectedCount = 0,
                        t9SelectedCandidatePinyin = ""
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    if (isT9) {
                        // partial commit：把本次选中的候选文本追加到累积列表，供后续合并显示
                        if (selectedCandidate != null) {
                            t9PartialCommitTexts.add(selectedCandidate)
                        }
                        // 保留状态字段，供 UI 层感知右侧选词事件
                        uiState.value = uiState.value.copy(
                            t9RightCandidateSelectedCount = uiState.value.t9RightCandidateSelectedCount + 1,
                            t9SelectedCandidatePinyin = candidatePinyin ?: ""
                        )
                        // RIME commit() 已清除 composition，需要控制器重新发送剩余数字到 RIME
                        keyboardCallbacks?.onT9ForceSendToRime?.invoke()
                    }
                    updateUI()
                }
            }
        }
    }
    
    /**
     * 更新计算器候选栏显示
     * 显示两个候选：
     * - index 0: 计算结果（如 "2"），点击直接替换为结果
     * - index 1: 带公式的结果（如 "1+1=2"），点击显示公式和结果
     */
    private fun updateCalculatorCandidates() {
        val candidate = calculatorEngine.getCandidate()
        val result = calculatorEngine.getResult()
        candidateState.value = if (candidate != null && result.isNotEmpty()) {
            candidateState.value.copy(
                candidates = listOf(result, candidate),
                candidateComments = emptyList()
            )
        } else {
            // 如果计算器之前有显示但现在已清除，也要清空候选栏
            if (candidateState.value.candidates.isNotEmpty() && !calculatorEngine.isActive()) {
                candidateState.value.copy(
                    candidates = emptyList(),
                    candidateComments = emptyList()
                )
            } else {
                candidateState.value
            }
        }
    }

    private fun selectCandidate(index: Int) {
        composeViewRef?.let { feedbackManager.performKeyPressEffect(view = it) }

        // 计算器模式（仅在数字/常用符号键盘下生效，防止状态残留导致其他键盘候选词点击异常）
        val layoutState = keyboardViewModel.keyboardState.value
        val isCalculatorKeyboard = layoutState is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.Number
                || layoutState is com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState.CommonSymbol
        if (calculatorEngine.isActive() && isCalculatorKeyboard) {
            val result = calculatorEngine.getResult()
            val expression = calculatorEngine.getExpression()
            val formulaResult = calculatorEngine.getFormulaResult()
            if (result.isNotEmpty() && expression.isNotEmpty()) {
                val textToCommit: String
                // index 0: 纯结果（如 "2"）
                // index 1: 公式结果（如 "1+1=2"）
                textToCommit = when (index) {
                    0 -> result
                    1 -> formulaResult
                    else -> ""
                }
                if (textToCommit.isNotEmpty()) {
                    calculatorEngine.clear()
                    serviceScope.launch(Dispatchers.Main) {
                        val ic = currentInputConnection
                        if (ic != null) {
                            // 删除输入框中已键入的表达式
                            ic.deleteSurroundingText(expression.length, 0)
                            // 提交选中的文本
                            ic.commitText(textToCommit, textToCommit.length)
                        }
                        candidateState.value = CandidateState()
                    }
                }
            }
            return
        }
        
        if (candidateState.value.isShowingRecentClipboard && index >= 0 && index < recentClipboardItemsState.value.size) {
            val text = recentClipboardItemsState.value[index].text
            selectClipboardItem(text)
            candidateState.value = candidateState.value.copy(
                isShowingRecentClipboard = false,
                candidates = emptyList(),
                candidateComments = emptyList()
            )
        } else {
            postRimeJob {
                selectCandidateAsync(index)
            }
        }
    }
    
    private fun pageDown() {
        postRimeJob {
            rimeEngine.pageDown()
            withContext(Dispatchers.Main) {
                updateUI()
            }
        }
    }
    
    private fun pageUp() {
        postRimeJob {
            rimeEngine.pageUp()
            withContext(Dispatchers.Main) {
                updateUI()
            }
        }
    }
    
    private fun switchInputMethod() {
        Log.d(TAG, "Toggling ascii mode")
        rimeEngine.toggleAsciiMode()
        updateUI()
    }
    
    private fun reloadConfig() {
        Log.d(TAG, "========== reloadConfig CALLED ==========")
        Log.d(TAG, "Deploying schema...")
        
        mainHandler.post {
            requestHideSelf(0)
            android.widget.Toast.makeText(this, "方案部署中...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        serviceScope.launch(Dispatchers.IO) {
            try {
                KeysConfigHelper.loadConfig(this@XimeInputMethodService)
                // 重新加载配色方案（用户可能在 xime.custom.yaml 中修改了 color_schemes）
                KeyboardThemes.reload(this@XimeInputMethodService)
                
                val userDataDir = File(filesDir, "rime")
                
                // 清空 build 目录，强制 Rime 全量重新编译
                val buildDir = File(userDataDir, "build")
                if (buildDir.exists()) {
                    Log.d(TAG, "Cleaning build directory")
                    buildDir.deleteRecursively()
                }
                
                Log.d(TAG, "Starting deployment...")
                val deployResult = rimeEngine.deploy()
                Log.d(TAG, "Deploy result: $deployResult")
                
                // 部署完成后重新加载配置（Rime 可能在部署过程中改写文件）
                KeysConfigHelper.loadConfig(this@XimeInputMethodService)
                KeyboardThemes.reload(this@XimeInputMethodService)
                
                val availableSchemas = rimeEngine.getAvailableSchemas()
                Log.d(TAG, "Available schemas: ${availableSchemas.joinToString()}")
                
                val savedSchema = SettingsPreferences.getCurrentSchema(this@XimeInputMethodService)
                Log.d(TAG, "Saved schema: $savedSchema")
                if (savedSchema in availableSchemas) {
                    applyPageSizeSetting(savedSchema)
                    val switchResult = rimeEngine.switchSchema(savedSchema)
                    Log.d(TAG, "Switch schema result: $switchResult")
                } else {
                    Log.w(TAG, "Schema $savedSchema not found in available schemas")
                }
                
                // 直接在 IO 线程同步读取 name，避免嵌套协程的时序问题
                val currentSchemaId = rimeEngine.getCurrentSchema()
                val schemaName = SchemaManager.getSchemaDisplayName(
                    this@XimeInputMethodService, currentSchemaId
                ) ?: currentSchemaId

                withContext(Dispatchers.Main) {
                    uiState.value = uiState.value.copy(
                        schemaName = schemaName,
                        currentSchemaId = currentSchemaId,
                    )
                    updateUI()
                    android.widget.Toast.makeText(this@XimeInputMethodService, "方案部署完成", android.widget.Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Schema deployed successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload config", e)
            }
        }
    }
    
    private fun deploySchema() {
        Log.d(TAG, "Deploying schema...")
        try {
            rimeEngine.deploy()
            val savedSchema = SettingsPreferences.getCurrentSchema(this)
            applyPageSizeSetting(savedSchema)
            rimeEngine.switchSchema(savedSchema)
            val currentSchemaId = rimeEngine.getCurrentSchema()
            uiState.value = uiState.value.copy(
                schemaName = SchemaManager.getSchemaDisplayName(this, currentSchemaId) ?: currentSchemaId,
                currentSchemaId = currentSchemaId,
            )
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
    
    private fun handleToolbarEditingAction(action: String) {
        val ic = currentInputConnection ?: return
        when (action) {
            "select_all" -> ic.performContextMenuAction(android.R.id.selectAll)
            "copy" -> ic.performContextMenuAction(android.R.id.copy)
            "paste" -> ic.performContextMenuAction(android.R.id.paste)
            "home" -> {
                ic.setSelection(0, 0)
            }
            "end" -> {
                val textBefore = ic.getTextBeforeCursor(SAFE_TEXT_LIMIT, 0) ?: ""
                val textAfter = ic.getTextAfterCursor(SAFE_TEXT_LIMIT, 0) ?: ""
                ic.setSelection(textBefore.length + textAfter.length, textBefore.length + textAfter.length)
            }
        }
    }

    private fun applyPageSizeSetting(schemaId: String) {
        val userPageSize = SettingsPreferences.getPageSize(this)
        if (userPageSize > 0) {
            rimeEngine.setPageSize(schemaId, userPageSize)
            Log.d(TAG, "Set page_size=$userPageSize for schema '$schemaId' via schema_open API")
        }
    }

    private fun switchSchema(schemaId: String) {
        Log.d(TAG, "Switching schema to: $schemaId")
        if (schemaId == HANDWRITING_SCHEMA_ID) {
            // 检查手写模型文件是否已下载
            val modelFile = java.io.File(filesDir, "ochwpro.onnx")
            val charIndexFile = java.io.File(filesDir, "char_index.json")
            if (!modelFile.exists() || !charIndexFile.exists()) {
                Log.w(TAG, "Handwriting model not found, redirecting to download")
                android.widget.Toast.makeText(
                    this, "请先下载手写模型", android.widget.Toast.LENGTH_LONG
                ).show()
                val intent = android.content.Intent(
                    this, com.kingzcheung.xime.MainActivity::class.java
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("open_fragment", "model_management")
                }
                startActivity(intent)
                return
            }
            previousSchemaId = rimeEngine.getCurrentSchema()
            Log.d(TAG, "Entering handwriting mode, previous schema: $previousSchemaId")
            SettingsPreferences.setCurrentSchema(this, schemaId)
            keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.HANDWRITING)
            updateSchemaName()
            return
        }
        keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.FULL)
        try {
            SettingsPreferences.setCurrentSchema(this, schemaId)
            // 用户自定义候选词数：先写 custom.yaml 再切方案，Rime 会自动加载
            applyPageSizeSetting(schemaId)
            rimeEngine.switchSchema(schemaId)
            if (!rimeEngine.isAsciiMode()) {
                rimeEngine.setOption("ascii_punct", false)
            }
            updateSchemaName()
            updateUI()
            // 确保键盘布局与方案匹配（如 T9 九键不应被 switchMain 重置为全键盘）
            keyboardViewModel.dispatch(
                com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(
                    rimeEngine.isAsciiMode(), schemaId
                )
            )
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
        Log.d(TAG, "========== deploy() CALLED ==========")
        Log.d(TAG, "Deploying schemas")
        serviceScope.launch(Dispatchers.IO) {
            // 部署前刷新手势配置和配色方案缓存
            KeysConfigHelper.loadConfig(this@XimeInputMethodService)
            KeyboardThemes.reload(this@XimeInputMethodService)
            
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

    private fun toggleFloatingMode(enabled: Boolean, navBarDp: Int = 0) {
        val effectiveNavBarDp = navBarDp
        Log.d(TAG, "toggleFloatingMode: $enabled navBarDp=$navBarDp")
        val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
        SettingsPreferences.setFloatingMode(this, enabled, isLandscape)
        SettingsPreferences.setFloatingMode(this, enabled, !isLandscape)
        val loadedX = SettingsPreferences.getFloatingOffsetX(this, isLandscape)
        val loadedY = SettingsPreferences.getFloatingOffsetY(this, isLandscape)
        val screenW = resources.configuration.screenWidthDp
        val screenH = resources.configuration.screenHeightDp
        val portraitWidth = minOf(screenW, screenH)
        val cardWidth = (portraitWidth * 0.85f).roundToInt()
        val halfMargin = maxOf(0, (screenW - cardWidth) / 2)
        val cappedKbH = SettingsPreferences.getKeyboardHeightDp(this, isLandscape).coerceAtMost((screenH * 8) / 10)
        val clampedX = loadedX.coerceIn(-halfMargin, halfMargin)
        uiState.value = uiState.value.copy(
            isFloatingMode = enabled,
            floatingOffsetX = clampedX,
            floatingOffsetY = 0,
        )
        if (enabled) {
            currentEffectiveKeyboardHeight = cappedKbH + 18 + 50 + uiState.value.keyboardBottomPaddingDp
        }
        window.window?.let { win ->
            if (enabled) {
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                win.setDimAmount(0f)
            } else {
                win.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
                win.setDimAmount(0.2f)
            }
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        val state = uiState.value
        if (state.isCompact) {
            try {
                val decor = window.window?.decorView
                if (decor != null) {
                    val navBarBg = decor.findViewById<View>(android.R.id.navigationBarBackground)
                    val navBarH = navBarBg?.height ?: 0
                    val h = (decor.height - navBarH).coerceAtLeast(0)
                    outInsets.contentTopInsets = h
                    outInsets.visibleTopInsets = h
                    outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
                    return
                }
            } catch (_: Exception) { }
            super.onComputeInsets(outInsets)
        } else if (state.isFloatingMode) {
            outInsets.apply {
                contentTopInsets = resources.displayMetrics.heightPixels
                visibleTopInsets = resources.displayMetrics.heightPixels
                touchableInsets = Insets.TOUCHABLE_INSETS_REGION
                val decor = window.window?.decorView ?: return
                if (currentEffectiveKeyboardHeight <= 0) {
                    val isLandscape = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
                    val kbH = SettingsPreferences.getKeyboardHeightDp(this@XimeInputMethodService, isLandscape)
                        .coerceAtMost((resources.configuration.screenHeightDp * 8) / 10)
                    currentEffectiveKeyboardHeight = kbH + 18 + 50 + state.keyboardBottomPaddingDp
                }
                val density = resources.displayMetrics.density
                val inputViewWidthPx = decor.width
                val statusBarHeightDp = tryGetStatusBarHeightDp()
                val physicalHeightPx = resources.displayMetrics.heightPixels
                val inputViewHeightPx = (physicalHeightPx - (statusBarHeightDp * density).toInt()).coerceAtLeast(1)
                val cardWidthPx = (inputViewWidthPx * 0.85f).toInt()
                val leftPaddingPx = ((inputViewWidthPx - cardWidthPx) / 2f).toInt()
                val offsetXPx = (state.floatingOffsetX * density).toInt()
                val cardHeightPx = (currentEffectiveKeyboardHeight * density).toInt()
                val offsetYPx = (state.floatingOffsetY * density).toInt()
                touchableRegion.set(
                    leftPaddingPx + offsetXPx,
                    inputViewHeightPx - cardHeightPx - offsetYPx,
                    leftPaddingPx + offsetXPx + cardWidthPx,
                    inputViewHeightPx - offsetYPx
                )
            }
        } else {
            super.onComputeInsets(outInsets)
        }
    }

    override fun commitText(text: String) {
        if (uiState.value.quickSendFormFocused) {
            mainHandler.post {
                QuickSendFormEditTextHolder.editText?.let { et ->
                    val start = et.selectionStart.coerceAtLeast(0)
                    val textLen = text.length
                    et.text?.replace(start, et.selectionEnd.coerceAtLeast(start), text)
                    try { et.setSelection(start + textLen) } catch (_: Exception) {}
                }
            }
            return
        }
        currentInputConnection?.commitText(text, 1)

        if (isChineseMode) {
            predictionManager.appendCommittedText(text)
            predictionManager.recordInput(text)

            mainHandler.post {
                if (!uiState.value.isAsciiMode) {
                    getPredictionFromPlugin(predictionManager.lastCommittedText)
                }
            }
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
    

    private fun keyCodeToKey(keyCode: Int, isShifted: Boolean): String? {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> if (isShifted) "A" else "a"
            KeyEvent.KEYCODE_B -> if (isShifted) "B" else "b"
            KeyEvent.KEYCODE_C -> if (isShifted) "C" else "c"
            KeyEvent.KEYCODE_D -> if (isShifted) "D" else "d"
            KeyEvent.KEYCODE_E -> if (isShifted) "E" else "e"
            KeyEvent.KEYCODE_F -> if (isShifted) "F" else "f"
            KeyEvent.KEYCODE_G -> if (isShifted) "G" else "g"
            KeyEvent.KEYCODE_H -> if (isShifted) "H" else "h"
            KeyEvent.KEYCODE_I -> if (isShifted) "I" else "i"
            KeyEvent.KEYCODE_J -> if (isShifted) "J" else "j"
            KeyEvent.KEYCODE_K -> if (isShifted) "K" else "k"
            KeyEvent.KEYCODE_L -> if (isShifted) "L" else "l"
            KeyEvent.KEYCODE_M -> if (isShifted) "M" else "m"
            KeyEvent.KEYCODE_N -> if (isShifted) "N" else "n"
            KeyEvent.KEYCODE_O -> if (isShifted) "O" else "o"
            KeyEvent.KEYCODE_P -> if (isShifted) "P" else "p"
            KeyEvent.KEYCODE_Q -> if (isShifted) "Q" else "q"
            KeyEvent.KEYCODE_R -> if (isShifted) "R" else "r"
            KeyEvent.KEYCODE_S -> if (isShifted) "S" else "s"
            KeyEvent.KEYCODE_T -> if (isShifted) "T" else "t"
            KeyEvent.KEYCODE_U -> if (isShifted) "U" else "u"
            KeyEvent.KEYCODE_V -> if (isShifted) "V" else "v"
            KeyEvent.KEYCODE_W -> if (isShifted) "W" else "w"
            KeyEvent.KEYCODE_X -> if (isShifted) "X" else "x"
            KeyEvent.KEYCODE_Y -> if (isShifted) "Y" else "y"
            KeyEvent.KEYCODE_Z -> if (isShifted) "Z" else "z"
            KeyEvent.KEYCODE_SPACE -> "space"
            KeyEvent.KEYCODE_ENTER -> "enter"
            KeyEvent.KEYCODE_DEL -> "delete"
            KeyEvent.KEYCODE_0 -> "0"
            KeyEvent.KEYCODE_1 -> "1"
            KeyEvent.KEYCODE_2 -> "2"
            KeyEvent.KEYCODE_3 -> "3"
            KeyEvent.KEYCODE_4 -> "4"
            KeyEvent.KEYCODE_5 -> "5"
            KeyEvent.KEYCODE_6 -> "6"
            KeyEvent.KEYCODE_7 -> "7"
            KeyEvent.KEYCODE_8 -> "8"
            KeyEvent.KEYCODE_9 -> "9"
            KeyEvent.KEYCODE_COMMA -> ","
            KeyEvent.KEYCODE_PERIOD -> "."
            KeyEvent.KEYCODE_MINUS -> "-"
            KeyEvent.KEYCODE_EQUALS -> "="
            KeyEvent.KEYCODE_SLASH -> "/"
            KeyEvent.KEYCODE_BACKSLASH -> "\\"
            KeyEvent.KEYCODE_SEMICOLON -> ";"
            KeyEvent.KEYCODE_APOSTROPHE -> "'"
            KeyEvent.KEYCODE_LEFT_BRACKET -> "["
            KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
            KeyEvent.KEYCODE_GRAVE -> "`"
            KeyEvent.KEYCODE_TAB -> "\t"
            else -> null
        }
    }

    private fun selectClipboardItem(text: String) {
        if (candidateState.value.isComposing) {
            postRimeJob {
                rimeEngine.clearComposition()
                withContext(Dispatchers.Main) {
                    updateUI()
                }
            }
        }
        commitText(text)
        clipboardManager.copyToSystemClipboard(text)
    }

    private fun commitClipboardText(text: String) {
        commitText(text)
    }

    private fun deleteClipboardChars(count: Int) {
        currentInputConnection?.deleteSurroundingText(count, 0)
    }
    
}