package com.kingzcheung.xime.ui.keyboard

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.twotone.EmojiEmotions
import androidx.compose.material.icons.twotone.KeyboardCapslock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.DisplayMode
import com.kingzcheung.xime.settings.ButtonLayout
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.keyboard.GestureAction

/** 半角 → 全角标点映射，中文模式下键帽显示用。提交仍走半角由 Rime 处理。 */
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.keyboard.KeyboardDimensions
import com.kingzcheung.xime.util.PermissionHelper
import com.kingzcheung.xime.util.CharInfo
import com.kingzcheung.xime.util.SubcharHelper
import com.kingzcheung.xime.viewmodel.KeyboardUiState
import com.kingzcheung.xime.viewmodel.KeyboardViewModel
import com.kingzcheung.xime.viewmodel.ShiftMode
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.ui.theme.KeyboardThemes

import androidx.compose.material.icons.twotone.KeyboardControlKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.TextUnit



@Composable
fun KeyboardLayout(
    onKeyPress: (String) -> Unit,
    viewModel: KeyboardViewModel,
    callbacks: KeyboardCallbacks,
    uiState: KeyboardUiState,
    isAsciiMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val isShifted by viewModel.isShifted.collectAsStateWithLifecycle()
    val shiftMode by viewModel.shiftMode.collectAsStateWithLifecycle()

    var visualIsShifted by remember { mutableStateOf(false) }
    LaunchedEffect(isShifted) {
        visualIsShifted = isShifted
    }
    var visualShiftMode by remember { mutableStateOf(ShiftMode.OFF) }
    LaunchedEffect(shiftMode) {
        visualShiftMode = shiftMode
    }

    val context = LocalContext.current
    val kbColors = KeysConfigHelper.getKeyboardColors()
    val longToColor: (Long) -> Color = { Color(0xFF000000 or it) }
    val keyboardBackgroundColor = if (uiState.isDarkTheme) longToColor(kbColors.keyboardBgColorDark) else longToColor(kbColors.keyboardBgColor)
    val themeSpecialKeyColor = KeyboardThemes.getSpecialKeyColor(uiState.themeId, uiState.isDarkTheme)
    val keyBackgroundColor = if (uiState.isDarkTheme) longToColor(kbColors.keyBgColorDark) else longToColor(kbColors.keyBgColor)
    val keyTextColor = if (uiState.isDarkTheme) longToColor(kbColors.keyTextColorDark) else longToColor(kbColors.keyTextColor)
    val specialKeyBackgroundColor = if (uiState.isDarkTheme) kbColors.specialKeyBgColorDark?.let { longToColor(it) }
        ?: themeSpecialKeyColor else kbColors.specialKeyBgColor?.let { longToColor(it) } ?: themeSpecialKeyColor
    val specialKeyTextColor = if (uiState.isDarkTheme) Color.White
        else KeyboardThemes.getSpecialKeyTextColor(uiState.themeId, false)
    val kbShadow = KeysConfigHelper.getKeyboardShadow()
    val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
    val shadowEnabled = kbShadow.enabled
    val shadowElevation = kbShadow.elevation.dp
    val shadowShapeRadius = kbShadow.shapeRadius.dp
    val schemaName = uiState.schemaName
    val isMicrosoftDoublePinyin = schemaName.contains("微软双拼")
    val enterKeyText = uiState.enterKeyText
    val isDarkTheme = uiState.isDarkTheme
    val isSttEnabled = uiState.isSttEnabled
    val isVoiceMode = uiState.isVoiceMode
    val onKeyPressDown = callbacks.onKeyPressDown
    val onKeyRelease = callbacks.onKeyRelease
    val onVoiceModeChange = callbacks.onVoiceModeChange
    val onCommitText = callbacks.onCommitText
    val onGestureAction: (GestureAction, String) -> Unit = { action, value ->
        when (action) {
            GestureAction.SWITCH_ROUTE -> {
                val overlayRoute = when (value) {
                    "emoji" -> OverlayRoute.Emoji
                    "symbol" -> OverlayRoute.Symbol
                    else -> null
                }
                overlayRoute?.let { viewModel.showOverlay(it) }
            }
            GestureAction.TOGGLE_ASCII -> {
                viewModel.resetShift()
                callbacks.onKeyPress("ime_switch", uiState.isAsciiMode)
            }
            GestureAction.DELETE -> {
                callbacks.onKeyPress("delete", false)
            }
            GestureAction.TOGGLE_SYMBOLS -> {
                callbacks.onKeyPress("mode_change", false)
            }
            else -> callbacks.onGestureAction?.invoke(action, value) ?: Unit
        }
    }
    val suppressCursorMove = LocalSuppressCursorMove.current
    var swipeUpHintsEnabled by remember {
        mutableStateOf(
            SettingsPreferences.isSwipeUpHintsEnabled(
                context
            )
        )
    }
    var swipeDownHintsEnabled by remember {
        mutableStateOf(
            SettingsPreferences.isSwipeDownHintsEnabled(
                context
            )
        )
    }
    val effectiveSwipeDownHintsEnabled = if (isAsciiMode) false else swipeDownHintsEnabled

    // 监听设置变化
    DisposableEffect(context) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                when (key) {
                    SettingsPreferences.KEY_SWIPE_UP_HINTS_ENABLED ->
                        swipeUpHintsEnabled = SettingsPreferences.isSwipeUpHintsEnabled(context)

                    SettingsPreferences.KEY_SWIPE_DOWN_HINTS_ENABLED ->
                        swipeDownHintsEnabled = SettingsPreferences.isSwipeDownHintsEnabled(context)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    LaunchedEffect(Unit) {
        SubcharHelper.init(context)
    }

    var swipeState by remember { mutableStateOf(SwipeState()) }
    var keyboardBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var lastKeyBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }

    // 监听手势配置版本号，部署后强制刷新键帽显示
    val cfgVer by KeysConfigHelper.configVersion.collectAsState()

    fun processSwipeState(state: SwipeState, bounds: Rect) {
        val newState = if (state.isSwipeDown && state.swipeText != null) {
            state.copy(charInfos = SubcharHelper.parseSwipeDownText(state.swipeText))
        } else {
            state
        }
        swipeState = newState

        lastKeyBounds = Rect(
            left = bounds.left - keyboardBounds.left,
            top = bounds.top - keyboardBounds.top,
            right = bounds.right - keyboardBounds.left,
            bottom = bounds.bottom - keyboardBounds.top
        )
    }

    val bubbleData = rememberSwipeBubbleDrawData(
        swipeState = swipeState,
        keyBounds = lastKeyBounds,
        keyBackgroundColor = keyBackgroundColor,
        keyTextColor = keyTextColor,
        accentColor = specialKeyTextColor,
        keyWidth = if (swipeState.isSwiping || swipeState.isPressed) lastKeyBounds.width else 0f,
        keyboardWidth = keyboardBounds.width
    )

    val isLandscape = !uiState.isFloatingMode && LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp

    CompositionLocalProvider(LocalKeyCornerRadius provides kbKey.cornerRadius.dp) {
    Box(
        modifier = modifier
            .background(keyboardBackgroundColor)
            .onGloballyPositioned { coordinates ->
                keyboardBounds = coordinates.boundsInRoot()
            }
            .drawWithContent {
                drawContent()
                bubbleData?.let { drawSwipeBubble(it) }
            }
            .padding(bottom = if (uiState.isFloatingMode || isLandscape) {0.dp} else {10.dp})
    ) {
            if (isLandscape) {
            LandscapeKeyboardContent(
                onKeyPress = onKeyPress,
                viewModel = viewModel,
                callbacks = callbacks,
                uiState = uiState,
                swipeUpHintsEnabled = swipeUpHintsEnabled,
                swipeDownHintsEnabled = effectiveSwipeDownHintsEnabled,
                isAsciiMode = isAsciiMode,
                onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(keyboardBackgroundColor)
                    .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.Top,
                ) {
                    // 第一行
                    if (isVoiceMode) {
                        Box(modifier = Modifier.weight(1f)) {
                            DummyKeyboardRow(
                                keysCount = 10,
                                keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                keyboardBackgroundColor = keyboardBackgroundColor
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            KeyboardRowWithConfig(
                                keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                                onKeyPress = onKeyPress,
                                config = KeyboardRowConfig(
                                    keyBackgroundColor = keyBackgroundColor,
                                    keyTextColor = keyTextColor,
                                    keyboardBackgroundColor = keyboardBackgroundColor,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                ),
                                isShifted = visualIsShifted,
                                isAsciiMode = isAsciiMode,
                                onSwipeStateChange = { state, bounds ->
                                    processSwipeState(
                                        state,
                                        bounds
                                    )
                                },
                                onKeyPressDown = onKeyPressDown,
                                onKeyRelease = onKeyRelease,
                                swipeDownHintsEnabled = effectiveSwipeDownHintsEnabled,
                                swipeUpHintsEnabled = swipeUpHintsEnabled,
                                onCommitText = onCommitText,
                                onGestureAction = onGestureAction,
                                configVersion = cfgVer,
                            )
                        }
                    }

                    // 第二行
                    if (isVoiceMode) {
                        Box(
                            modifier =
                                if (isMicrosoftDoublePinyin)
                                    Modifier.weight(1f)
                                else
                                    Modifier.weight(1f).padding(horizontal = 16.dp)
                        ) {
                            DummyKeyboardRow(
                                keysCount = if (isMicrosoftDoublePinyin) 10 else 9,
                                keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                keyboardBackgroundColor = keyboardBackgroundColor
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            KeyboardRowWithConfig(
                                keys =
                                    if (isMicrosoftDoublePinyin)
                                        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", ";")
                                    else
                                        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                                onKeyPress = onKeyPress,
                                config = KeyboardRowConfig(
                                    keyBackgroundColor = keyBackgroundColor,
                                    keyTextColor = keyTextColor,
                                    keyboardBackgroundColor = keyboardBackgroundColor,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                ),
                                isShifted = visualIsShifted,
                                isAsciiMode = isAsciiMode,
                                modifier = if (isMicrosoftDoublePinyin) Modifier else Modifier.padding(horizontal = 16.dp),
                                onSwipeStateChange = { state, bounds ->
                                    processSwipeState(
                                        state,
                                        bounds
                                    )
                                },
                                onKeyPressDown = onKeyPressDown,
                                onKeyRelease = onKeyRelease,
                                swipeDownHintsEnabled = effectiveSwipeDownHintsEnabled,
                                swipeUpHintsEnabled = swipeUpHintsEnabled,
                                onCommitText = onCommitText,
                                onGestureAction = onGestureAction,
                                configVersion = cfgVer,
                            )
                        }
                    }

                    // 第三行
                    if (isVoiceMode) {
                        Box(modifier = Modifier.weight(1f)) {
                            DummyBottomRow(
                                keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                specialKeyBackgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                keyboardBackgroundColor = keyboardBackgroundColor
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .fillMaxHeight()
                                .background(keyboardBackgroundColor),
                        ) {
                            ShiftCapsKeyButton(
                                shiftMode = visualShiftMode,
                                onKeyPress = onKeyPress,
                                onKeyPressDown = onKeyPressDown,
                                backgroundColor = specialKeyBackgroundColor,
                                iconColor = specialKeyTextColor,
                                modifier = Modifier
                                    .padding(2.dp,4.dp)
                                    .weight(1.4f)
                                    .fillMaxHeight(),
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                            )

                                Row(
                                    modifier = Modifier
                                        .weight(7f)
                                        .fillMaxHeight()
                                        .background(keyboardBackgroundColor),
                                ) {
                                val bottomKeys = listOf("z", "x", "c", "v", "b", "n", "m")
                                bottomKeys.forEach { key ->
                                    val rawSwipeUpLabel = KeysConfigHelper.getSwipeUpLabel(key, isAsciiMode)
                                    val swipeUpText =
                                        if (swipeUpHintsEnabled) rawSwipeUpLabel else null
                                    val swipeUpAction = KeysConfigHelper.getSwipeUpAction(key, isAsciiMode)
                                    val swipeUpDisplay = KeysConfigHelper.getSwipeUpDisplay(key, isAsciiMode)
                                    val swipeUpKeyLabel =
                                        if (swipeUpDisplay != DisplayMode.BUBBLE && swipeUpHintsEnabled) swipeUpText else null
                                    val swipeUpCommitValue = KeysConfigHelper.getSwipeUpCommitValue(key, isAsciiMode)
                                    val swipeDownRaw =
                                        KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.swipeDown
                                    val swipeDownLabel =
                                        swipeDownRaw?.label?.takeIf { it.isNotEmpty() }
                                    val swipeDownAction = swipeDownRaw?.action
                                    val swipeDownValue = swipeDownRaw?.value
                                    val swipeDownDisplay = swipeDownRaw?.display ?: DisplayMode.BOTH
                                    val swipeDownBubbleText = if (!isAsciiMode && swipeDownDisplay != DisplayMode.KEY) swipeDownLabel else null
                                    val longPressConfig =
                                        KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.longPress
                                    val longPressDisplay = longPressConfig?.display ?: "key"
                                    val longPressLabels = if (longPressDisplay == "bubble") {
                                        longPressConfig?.values?.map { it.label }
                                            ?.filter { it.isNotEmpty() }?.ifEmpty { null }
                                    } else null
                                    val longPressGestureMap = if (longPressDisplay == "bubble") {
                                        longPressConfig?.values?.associateBy { it.label }
                                    } else null

                                    val rawCommitValue = KeysConfigHelper.getKeyCommitValue(key, isAsciiMode)
                                    val commitValue = if (visualIsShifted) rawCommitValue.uppercase() else rawCommitValue
                                    val displayText = if (isAsciiMode) {
                                        commitValue
                                    } else {
                                        KeysConfigHelper.getKeyDisplayLabel(key, isAsciiMode)
                                    }

                                    val onClick = remember(key, commitValue, onKeyPress) { { onKeyPress(commitValue) } }
                                    val onPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
                                    val onRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
                                    val onSwipeDown = if (!isAsciiMode && swipeDownAction != null && swipeDownLabel != null) {
                                        remember(key, onKeyPress, onGestureAction, onCommitText, swipeDownAction, swipeDownValue, swipeDownLabel) {
                                            val label = swipeDownLabel
                                            { _: String ->
                                                if (swipeDownAction == GestureAction.COMMIT) {
                                                    (onCommitText ?: onKeyPress)(swipeDownValue?.ifEmpty { label } ?: label)
                                                } else {
                                                    onGestureAction?.invoke(
                                                        swipeDownAction,
                                                        swipeDownValue?.ifEmpty { label } ?: label)
                                                }
                                                Unit
                                            }
                                        }
                                    } else null
                                    val onSwipeStateChange = remember(key) { { state: SwipeState, bounds: Rect -> processSwipeState(state, bounds) } }
                                    val onLongPressSelect: ((String) -> Unit)? = remember(key, longPressGestureMap, onGestureAction, onCommitText, onKeyPress) { { selectedLabel: String ->
                                        val gesture = longPressGestureMap?.get(selectedLabel)
                                        if (gesture != null && gesture.action != GestureAction.COMMIT) {
                                            onGestureAction?.invoke(
                                                gesture.action!!,
                                                gesture.value.ifEmpty { selectedLabel })
                                        } else {
                                            (onCommitText ?: onKeyPress)(selectedLabel)
                                        }
                                        Unit
                                    } }

                                    SwipeableKeyButton(
                                        layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                                        text = displayText,
                                        onClick = onClick,
                                        backgroundColor = keyBackgroundColor,
                                        textColor = keyTextColor,
                                        modifier = Modifier.weight(1f),
                                        swipeText = swipeUpText,
                                        swipeDownText = swipeDownBubbleText,
                                        swipeUpKeyLabel = swipeUpKeyLabel,
                                        swipeDownKeyLabel = if (!isAsciiMode && (swipeDownDisplay == DisplayMode.KEY || swipeDownDisplay == DisplayMode.BOTH)) swipeDownLabel else null,
                                        onSwipe = if (swipeUpCommitValue != null && swipeUpAction != GestureAction.NONE) { { onKeyPress(swipeUpCommitValue) } } else null,
                                        onSwipeDown = onSwipeDown,
                                        onSwipeStateChange = onSwipeStateChange,
                                        onPress = onPress,
                                        onRelease = onRelease,
                                        onLongPressSelect = onLongPressSelect,
                                        longPressItems = longPressLabels,
                                        shadowEnabled = shadowEnabled,
                                        shadowElevation = shadowElevation,
                                        shadowShapeRadius = shadowShapeRadius,
                                    )
                                }
                            }

                            SwipeableIconKeyButton(
                                icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                                onClick = { onKeyPress("delete") },
                                backgroundColor = specialKeyBackgroundColor,
                                iconColor = specialKeyTextColor,
                                modifier = Modifier
                                    .padding(2.dp,0.dp)
                                    .weight(1.4f)
                                    .fillMaxHeight(),
                                swipeText = "清空",
                                onSwipe = { onKeyPress("clear_composition") },
                                onLongClick = { onKeyPress("delete") },
                                onPress = { onKeyPressDown?.invoke("delete") },
                                onRelease = { onKeyRelease?.invoke("delete") },
                                swipeUpLabel = "上滑清空",
                                swipeDownLabel = "下滑撤回",
                                onSwipeUp = { onKeyPress("clear_all") },
                                onSwipeDown = { onKeyPress("undo_clear") },
                                onSwipeLeft = { suppressCursorMove.value = true; onKeyPress("clear_composition") },
                                onSwipeStateChange = { state, bounds ->
                                    processSwipeState(
                                        state,
                                        bounds
                                    )
                                },
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                            )
                        }
                    }

                    // 第四行（控制行）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(keyboardBackgroundColor),
                    ) {
                        if (isVoiceMode) {
                            DummyKeyButton(
                                backgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1.2f)
                            )
                            DummyKeyButton(
                                backgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(0.8f)
                            )
                        } else {
                            // ?123 — 硬编码（长按弹出 t9/t26 图标）
                            SwipeableKeyButton(
                                text = "?123",
                                onClick = { onKeyPress("mode_change") },
                                backgroundColor = specialKeyBackgroundColor,
                                textColor = specialKeyTextColor,
                                modifier = Modifier.weight(1.2f),
                                onPress = { onKeyPressDown?.invoke("mode_change") },
                                onRelease = { onKeyRelease?.invoke("mode_change") },
                                onLongPressSelect = { label -> onKeyPress(if (label == "number") "mode_change_number" else "mode_change_common_symbol") },
                                longPressItems = listOf("number", "common_symbol"),
                                longPressDrawableIds = listOf(
                                    com.kingzcheung.xime.R.drawable.t9,
                                    com.kingzcheung.xime.R.drawable.t26
                                ),
                                onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                            )

                            // 逗号 — 从配置读取 "'"
                            val k2KeyGesture = KeysConfigHelper.getKeyGesture("'", isAsciiMode)
                            val k2TapAction = k2KeyGesture?.tap?.action
                            val k2TapValue = k2KeyGesture?.tap?.value?.takeIf { it.isNotEmpty() }
                                ?: k2KeyGesture?.tap?.label?.takeIf { it.isNotEmpty() }
                                ?: if (isAsciiMode) "," else "，"
                            val k2TapLabel = k2KeyGesture?.tap?.label?.takeIf { it.isNotEmpty() } ?: k2TapValue
                            val k2SwipeUpRaw = k2KeyGesture?.swipeUp
                            val k2SwipeUpLabel = if (isAsciiMode)
                                (k2SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                                else (k2SwipeUpRaw?.label?.takeIf { it.isNotEmpty() } ?: k2SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                            val k2SwipeUpValue = k2SwipeUpRaw?.value?.takeIf { it.isNotEmpty() }
                                ?: k2SwipeUpRaw?.label?.takeIf { it.isNotEmpty() }
                            val k2SwipeUpDisplay = k2SwipeUpRaw?.display ?: DisplayMode.BOTH
                            val k2SwipeUpCommitValue = k2SwipeUpValue
                            val k2SwipeDownRaw = k2KeyGesture?.swipeDown
                            val k2SwipeDownLabel = k2SwipeDownRaw?.label?.takeIf { it.isNotEmpty() }
                            val k2SwipeDownAction = k2SwipeDownRaw?.action
                            val k2SwipeDownValue = k2SwipeDownRaw?.value
                            val k2SwipeDownDisplay = k2SwipeDownRaw?.display ?: DisplayMode.BOTH
                            val k2SwipeDownBubbleText = if (!isAsciiMode && k2SwipeDownDisplay != DisplayMode.KEY) k2SwipeDownLabel else null
                            val k2LongPressConfig = k2KeyGesture?.longPress
                            val k2LongPressDisplay = k2LongPressConfig?.display ?: "key"
                            val k2LongPressLabels = if (k2LongPressDisplay == "bubble") {
                                k2LongPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                                    ?.ifEmpty { null }
                            } else null
                            val k2LongPressGestureMap = if (k2LongPressDisplay == "bubble") {
                                k2LongPressConfig?.values?.associateBy { it.label }
                            } else null
                            val k2OnClick: () -> Unit = remember(k2TapAction, k2TapValue, onKeyPress, onGestureAction) {
                                {
                                    if (k2TapAction != null && k2TapAction != GestureAction.COMMIT) {
                                        onGestureAction?.invoke(k2TapAction, k2TapValue)
                                    } else {
                                        onKeyPress(k2TapValue)
                                    }
                                }
                            }
                            val k2OnSwipeDown: ((String) -> Unit)? = if (k2SwipeDownAction != null && k2SwipeDownLabel != null) {
                                remember(k2SwipeDownAction, k2SwipeDownValue, k2SwipeDownLabel, onKeyPress, onGestureAction, onCommitText) {
                                    val label = k2SwipeDownLabel
                                    { _: String ->
                                        if (k2SwipeDownAction == GestureAction.COMMIT) {
                                            (onCommitText ?: onKeyPress)(k2SwipeDownValue?.ifEmpty { label } ?: label)
                                        } else {
                                            onGestureAction?.invoke(k2SwipeDownAction, k2SwipeDownValue?.ifEmpty { label } ?: label)
                                        }
                                        Unit
                                    }
                                }
                            } else null
                            val k2OnLongPressSelect: ((String) -> Unit)? = remember(k2LongPressGestureMap, onGestureAction, onCommitText, onKeyPress) {
                                { selectedLabel: String ->
                                    val gesture = k2LongPressGestureMap?.get(selectedLabel)
                                    if (gesture != null && gesture.action != GestureAction.COMMIT) {
                                        onGestureAction?.invoke(gesture.action!!, gesture.value.ifEmpty { selectedLabel })
                                    } else {
                                        (onCommitText ?: onKeyPress)(selectedLabel)
                                    }
                                    Unit
                                }
                            }
                            if (k2TapAction == GestureAction.TOGGLE_ASCII) {
                                IconKeyButton(
                                    icon = rememberVectorPainter(Icons.Default.Language),
                                    onClick = k2OnClick,
                                    backgroundColor = keyBackgroundColor,
                                    iconColor = keyTextColor,
                                    modifier = Modifier.weight(0.8f),
                                    onPress = { onKeyPressDown?.invoke(k2TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k2TapValue) },
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            } else {
                                SwipeableKeyButton(
                                    layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                                    text = k2TapLabel,
                                    onClick = k2OnClick,
                                    backgroundColor = keyBackgroundColor,
                                    textColor = keyTextColor,
                                    modifier = Modifier.weight(0.8f),
                                    swipeText = k2SwipeUpLabel,
                                    swipeDownText = k2SwipeDownBubbleText,
                                    swipeDownKeyLabel = if (!isAsciiMode && (k2SwipeDownDisplay == DisplayMode.KEY || k2SwipeDownDisplay == DisplayMode.BOTH)) k2SwipeDownLabel else null,
                                    onSwipe = if (k2SwipeUpCommitValue != null) { { onKeyPress(k2SwipeUpCommitValue) } } else null,
                                    onSwipeDown = k2OnSwipeDown,
                                    onSwipeStateChange = { state, bounds ->
                                        processSwipeState(state, bounds)
                                    },
                                    onPress = { onKeyPressDown?.invoke(k2TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k2TapValue) },
                                    onLongPressSelect = k2OnLongPressSelect,
                                    longPressItems = k2LongPressLabels,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            }
                        }

                        SpaceKey(
                            schemaName = schemaName,
                            isAsciiMode = isAsciiMode,
                            isSttEnabled = isSttEnabled,
                            isVoiceMode = isVoiceMode,
                            keyBackgroundColor = keyBackgroundColor,
                            keyTextColor = keyTextColor,
                            shadowEnabled = shadowEnabled,
                            shadowElevation = shadowElevation,
                            shadowShapeRadius = shadowShapeRadius,
                            modifier = Modifier.weight(3f),
                            onKeyPress = onKeyPress,
                            onKeyPressDown = onKeyPressDown,
                            onKeyRelease = onKeyRelease,
                            onVoiceModeChange = onVoiceModeChange,
                        )

                        // 中/英切换 + 回车（硬编码 + 配置驱动）
                        if (isVoiceMode) {
                            DummyKeyButton(
                                backgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(0.8f)
                            )
                            DummyKeyButton(
                                backgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.weight(1.2f)
                            )
                        } else {
                            // earth — 从配置读取
                            val k4KeyGesture = KeysConfigHelper.getKeyGesture("earth", isAsciiMode)
                            val k4TapAction = k4KeyGesture?.tap?.action
                            val k4TapValue = k4KeyGesture?.tap?.value?.takeIf { it.isNotEmpty() } ?: ""
                            val k4TapLabel = k4KeyGesture?.tap?.label?.takeIf { it.isNotEmpty() } ?: ""
                            val k4Icon: Painter? = k4KeyGesture?.tap?.icon?.takeIf { it.isNotEmpty() }?.let { iconName ->
                                val iv = when (iconName) {
                                    "language", "globe" -> Icons.Default.Language
                                    else -> null
                                }
                                iv?.let { rememberVectorPainter(it) }
                            }
                            val k4SwipeUpRaw = k4KeyGesture?.swipeUp
                            val k4SwipeUpLabel = if (isAsciiMode)
                                (k4SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                                else (k4SwipeUpRaw?.label?.takeIf { it.isNotEmpty() } ?: k4SwipeUpRaw?.value?.takeIf { it.isNotEmpty() } ?: "")
                            val k4SwipeUpValue = k4SwipeUpRaw?.value?.takeIf { it.isNotEmpty() }
                                ?: k4SwipeUpRaw?.label?.takeIf { it.isNotEmpty() }
                            val k4SwipeUpAction = k4SwipeUpRaw?.action
                            val k4SwipeDownRaw = k4KeyGesture?.swipeDown
                            val k4SwipeDownLabel = k4SwipeDownRaw?.label?.takeIf { it.isNotEmpty() }
                            val k4SwipeDownAction = k4SwipeDownRaw?.action
                            val k4SwipeDownValue = k4SwipeDownRaw?.value
                            val k4SwipeDownDisplay = k4SwipeDownRaw?.display ?: DisplayMode.BOTH
                            val k4SwipeDownBubbleText = if (!isAsciiMode && k4SwipeDownDisplay != DisplayMode.KEY) k4SwipeDownLabel else null
                            val k4LongPressConfig = k4KeyGesture?.longPress
                            val k4LongPressDisplay = k4LongPressConfig?.display ?: "key"
                            val k4LongPressLabels = if (k4LongPressDisplay == "bubble") {
                                k4LongPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                                    ?.ifEmpty { null }
                            } else null
                            val k4LongPressGestureMap = if (k4LongPressDisplay == "bubble") {
                                k4LongPressConfig?.values?.associateBy { it.label }
                            } else null
                            val k4OnClick: () -> Unit = remember(k4TapAction, k4TapValue, onKeyPress, onGestureAction) {
                                {
                                    if (k4TapAction != null && k4TapAction != GestureAction.COMMIT) {
                                        onGestureAction?.invoke(k4TapAction, k4TapValue)
                                    } else {
                                        onKeyPress(k4TapValue)
                                    }
                                }
                            }
                            val k4OnSwipe: ((String) -> Unit)? = if (k4SwipeUpValue != null && k4SwipeUpAction != GestureAction.NONE) {
                                remember(k4SwipeUpValue, onKeyPress) { { onKeyPress(k4SwipeUpValue) } }
                            } else null
                            val k4OnSwipeDown: ((String) -> Unit)? = if (k4SwipeDownAction != null && k4SwipeDownLabel != null) {
                                remember(k4SwipeDownAction, k4SwipeDownValue, k4SwipeDownLabel, onKeyPress, onGestureAction, onCommitText) {
                                    val label = k4SwipeDownLabel
                                    { _: String ->
                                        if (k4SwipeDownAction == GestureAction.COMMIT) {
                                            (onCommitText ?: onKeyPress)(k4SwipeDownValue?.ifEmpty { label } ?: label)
                                        } else {
                                            onGestureAction.invoke(k4SwipeDownAction, k4SwipeDownValue?.ifEmpty { label } ?: label)
                                        }
                                        Unit
                                    }
                                }
                            } else null
                            val k4OnLongPressSelect: ((String) -> Unit)? = remember(k4LongPressGestureMap, onGestureAction, onCommitText, onKeyPress) {
                                { selectedLabel: String ->
                                    val gesture = k4LongPressGestureMap?.get(selectedLabel)
                                    if (gesture != null && gesture.action != GestureAction.COMMIT) {
                                        onGestureAction.invoke(gesture.action!!, gesture.value.ifEmpty { selectedLabel })
                                    } else {
                                        (onCommitText ?: onKeyPress)(selectedLabel)
                                    }
                                    Unit
                                }
                            }
                            if (k4TapAction == GestureAction.TOGGLE_ASCII && k4LongPressLabels == null && k4Icon != null) {
                                IconKeyButton(
                                    icon = k4Icon,
                                    onClick = k4OnClick,
                                    backgroundColor = keyBackgroundColor,
                                    iconColor = keyTextColor,
                                    modifier = Modifier.weight(0.8f),
                                    onPress = { onKeyPressDown?.invoke(k4TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k4TapValue) },
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            } else {
                                SwipeableKeyButton(
                                    layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                                    text = k4TapLabel,
                                    onClick = k4OnClick,
                                    backgroundColor = keyBackgroundColor,
                                    textColor = keyTextColor,
                                    modifier = Modifier.weight(0.8f),
                                    icon = k4Icon,
                                    swipeText = k4SwipeUpLabel.takeIf { it.isNotEmpty() },
                                    swipeDownText = k4SwipeDownBubbleText,
                                    swipeDownKeyLabel = if (!isAsciiMode && (k4SwipeDownDisplay == DisplayMode.KEY || k4SwipeDownDisplay == DisplayMode.BOTH)) k4SwipeDownLabel else null,
                                    onSwipe = k4OnSwipe,
                                    onSwipeDown = k4OnSwipeDown,
                                    onSwipeStateChange = { state, bounds ->
                                        processSwipeState(state, bounds)
                                    },
                                    onPress = { onKeyPressDown?.invoke(k4TapValue) },
                                    onRelease = { onKeyRelease?.invoke(k4TapValue) },
                                    onLongPressSelect = k4OnLongPressSelect,
                                    longPressItems = k4LongPressLabels,
                                    shadowEnabled = shadowEnabled,
                                    shadowElevation = shadowElevation,
                                    shadowShapeRadius = shadowShapeRadius,
                                )
                            }

                            // 回车 — 硬编码
                            KeyButton(
                                text = enterKeyText,
                                onClick = { onKeyPress("enter") },
                                backgroundColor = specialKeyBackgroundColor,
                                textColor = specialKeyTextColor,
                                modifier = Modifier.weight(1.2f),
                                onPress = { onKeyPressDown?.invoke("enter") },
                                onRelease = { onKeyRelease?.invoke("enter") },
                                shadowEnabled = shadowEnabled,
                                shadowElevation = shadowElevation,
                                shadowShapeRadius = shadowShapeRadius,
                            )
                        }
                    }
                }

            }
        }

        // 语音模式中央麦克风图标
        if (isVoiceMode) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音输入",
                    tint = keyTextColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }

    }
}

@Composable
private fun DummyKeyboardRow(
    keysCount: Int,
    keyBackgroundColor: Color,
    keyboardBackgroundColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(keyboardBackgroundColor),
    ) {
        repeat(keysCount) {
            DummyKeyButton(
                backgroundColor = keyBackgroundColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DummyBottomRow(
    keyBackgroundColor: Color,
    specialKeyBackgroundColor: Color,
    keyboardBackgroundColor: Color = Color.Transparent
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(keyboardBackgroundColor),
    ) {
        DummyKeyButton(
            backgroundColor = specialKeyBackgroundColor,
            modifier = Modifier.weight(1.2f)
        )
        Row(
            modifier = Modifier
                .weight(7f)
                .fillMaxHeight(),
        ) {
            repeat(7) {
                DummyKeyButton(
                    backgroundColor = keyBackgroundColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        DummyKeyButton(
            backgroundColor = specialKeyBackgroundColor,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun DummyKeyButton(
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(LocalKeyCornerRadius.current))
            .background(backgroundColor)
    )
}

@Composable
fun KeyboardRowWithConfig(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    config: KeyboardRowConfig,
    isShifted: Boolean,
    isAsciiMode: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onKeyPressDown: ((String) -> Unit)? = null,
    onKeyRelease: ((String) -> Unit)? = null,
    swipeDownHintsEnabled: Boolean = true,
    swipeUpHintsEnabled: Boolean = true,
    onCommitText: ((String) -> Unit)? = null,
    onGestureAction: ((GestureAction, String) -> Unit)? = null,
    configVersion: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(config.keyboardBackgroundColor),
    ) {
        keys.forEach { key ->
            val rawSwipeUpLabel = KeysConfigHelper.getSwipeUpLabel(key, isAsciiMode)
            val swipeUpText = if (swipeUpHintsEnabled) rawSwipeUpLabel else null
            val swipeUpAction = KeysConfigHelper.getSwipeUpAction(key, isAsciiMode)
            val swipeUpDisplay = KeysConfigHelper.getSwipeUpDisplay(key, isAsciiMode)
            val swipeUpKeyLabel =
                if (swipeUpDisplay != DisplayMode.BUBBLE && swipeUpHintsEnabled) swipeUpText else null
            val swipeUpCommitValue = KeysConfigHelper.getSwipeUpCommitValue(key, isAsciiMode)
            val swipeDownRaw = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.swipeDown
            val swipeDownLabel = swipeDownRaw?.label?.takeIf { it.isNotEmpty() }
            val swipeDownAction = swipeDownRaw?.action
            val swipeDownValue = swipeDownRaw?.value
            val swipeDownDisplay = swipeDownRaw?.display ?: DisplayMode.BOTH
            val swipeDownBubbleText =
                if (swipeDownDisplay != DisplayMode.KEY && swipeDownHintsEnabled) swipeDownLabel else null

            // 长按选项
            val longPressConfig = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.longPress
            val longPressDisplay = longPressConfig?.display ?: "key"
            val longPressLabels = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                    ?.ifEmpty { null }
            } else null
            val longPressGestureMap = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.associateBy { it.label }
            } else null

            // 键帽显示文本
            val rawCommitValue = KeysConfigHelper.getKeyCommitValue(key, isAsciiMode)
            val commitValue = if (isShifted) rawCommitValue.uppercase() else rawCommitValue
            val displayText = if (isShifted && key == ";") {
                if (isAsciiMode) ":" else "："
            } else if (isAsciiMode) {
                commitValue
            } else {
                KeysConfigHelper.getKeyDisplayLabel(key, isAsciiMode)
            }

            val onClick = remember(key, commitValue, onKeyPress) { { onKeyPress(commitValue) } }
            val onPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
            val onRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
            val onSwipeDown: ((String) -> Unit)? = if (swipeDownAction != null && swipeDownHintsEnabled && swipeDownLabel != null) {
                remember(key, onKeyPress, onGestureAction, onCommitText, swipeDownAction, swipeDownValue, swipeDownLabel) {
                    val label = swipeDownLabel
                    { _: String ->
                        if (swipeDownAction == GestureAction.COMMIT) {
                            (onCommitText ?: onKeyPress)(swipeDownValue?.ifEmpty { label } ?: label)
                        } else {
                            onGestureAction?.invoke(
                                swipeDownAction,
                                swipeDownValue?.ifEmpty { label } ?: label)
                        }
                        Unit
                    }
                }
            } else null
            val onLongPressSelect: ((String) -> Unit)? = remember(key, longPressGestureMap, onGestureAction, onCommitText, onKeyPress) { { selectedLabel: String ->
                val gesture = longPressGestureMap?.get(selectedLabel)
                if (gesture != null && gesture.action != GestureAction.COMMIT) {
                    onGestureAction?.invoke(
                        gesture.action!!,
                        gesture.value.ifEmpty { selectedLabel })
                } else {
                    (onCommitText ?: onKeyPress)(selectedLabel)
                }
                Unit
            } }

            SwipeableKeyButton(
                layoutMode = KeysConfigHelper.getButtonLayout(isAsciiMode),
                text = displayText,
                onClick = onClick,
                backgroundColor = config.keyBackgroundColor,
                textColor = config.keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = swipeUpText,
                swipeDownText = swipeDownBubbleText,
                swipeUpKeyLabel = swipeUpKeyLabel,
                swipeDownKeyLabel = if ((swipeDownDisplay == DisplayMode.KEY || swipeDownDisplay == DisplayMode.BOTH) && swipeDownHintsEnabled) swipeDownLabel else null,
                onSwipe = if (swipeUpCommitValue != null && swipeUpAction != GestureAction.NONE) { { onKeyPress(swipeUpCommitValue) } } else null,
                onSwipeDown = onSwipeDown,
                onSwipeStateChange = onSwipeStateChange,
                onPress = onPress,
                onRelease = onRelease,
                onLongPressSelect = onLongPressSelect,
                longPressItems = longPressLabels,
                fontSize = config.fontSize,
                swipeFontSize = config.swipeFontSize,
                shadowEnabled = config.shadowEnabled,
                shadowElevation = config.shadowElevation,
                shadowShapeRadius = config.shadowShapeRadius,
            )
        }
    }
}

@Composable
private fun ShiftCapsKeyButton(
    shiftMode: ShiftMode,
    onKeyPress: (String) -> Unit,
    onKeyPressDown: ((String) -> Unit)?,
    backgroundColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }

    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha)
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    onKeyPressDown?.invoke("shift")
                    onKeyPress("shift_single")

                    val firstUp = waitForUpOrCancellation()
                    if (firstUp != null) {
                        val secondDown = withTimeoutOrNull(
                            viewConfiguration.doubleTapTimeoutMillis
                        ) {
                            awaitFirstDown(requireUnconsumed = false)
                        }
                        if (secondDown != null) {
                            onKeyPress("shift_caps")
                            waitForUpOrCancellation()
                        }
                    }
                    isPressed = false
                }
            }
            .then(shadowModifier)
            .clip(keyClipShape)
            .background(
                if (isPressed) darkenColor(backgroundColor, 0.1f)
                else if (shiftMode == ShiftMode.CAPS) darkenColor(backgroundColor, 0.2f)
                else if (shiftMode == ShiftMode.SINGLE) darkenColor(backgroundColor, 0.1f)
                else backgroundColor
            ),
        contentAlignment = Alignment.Center
    ) {
        val painter = when (shiftMode) {
            ShiftMode.OFF -> rememberVectorPainter(Icons.TwoTone.KeyboardControlKey)
            else -> rememberVectorPainter(Icons.TwoTone.KeyboardCapslock)
        }
        Icon(
            painter = painter,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        if (shiftMode == ShiftMode.CAPS) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(iconColor)
            )
        }
    }
}

/**
 * 横屏分体键盘内容 — 当 [KeyboardLayout.isLandscape] 为 true 时渲染。
 * 将键盘拆分为左右两个面板，紧贴屏幕左右边缘，中间留空方便双手持机拇指操作。
 */
@Composable
private fun LandscapeKeyboardContent(
    onKeyPress: (String) -> Unit,
    viewModel: KeyboardViewModel,
    callbacks: KeyboardCallbacks,
    uiState: KeyboardUiState,
    swipeUpHintsEnabled: Boolean,
    swipeDownHintsEnabled: Boolean,
    isAsciiMode: Boolean,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
) {
    val isShifted by viewModel.isShifted.collectAsStateWithLifecycle()
    val shiftMode by viewModel.shiftMode.collectAsStateWithLifecycle()

    var visualIsShifted by remember { mutableStateOf(false) }
    LaunchedEffect(isShifted) {
        if (isShifted) {
            delay(250L)
            visualIsShifted = isShifted
        } else {
            visualIsShifted = isShifted
        }
    }
    var visualShiftMode by remember { mutableStateOf(ShiftMode.OFF) }
    LaunchedEffect(shiftMode) {
        if (shiftMode == ShiftMode.SINGLE) {
            delay(250L)
            visualShiftMode = shiftMode
        } else {
            visualShiftMode = shiftMode
        }
    }

    val suppressCursorMove = LocalSuppressCursorMove.current
    val staggerStep = 10.dp
    val landscapeFontSize = 12.sp
    val landscapeSwipeFontSize = 7.sp

    val kbColors = KeysConfigHelper.getKeyboardColors()
    val longToColor: (Long) -> Color = { Color(0xFF000000 or it) }
    val keyboardBackgroundColor = if (uiState.isDarkTheme) longToColor(kbColors.keyboardBgColorDark) else longToColor(kbColors.keyboardBgColor)
    val themeSpecialKeyColor = KeyboardThemes.getSpecialKeyColor(uiState.themeId, uiState.isDarkTheme)
    val keyBackgroundColor = if (uiState.isDarkTheme) longToColor(kbColors.keyBgColorDark) else longToColor(kbColors.keyBgColor)
    val keyTextColor = if (uiState.isDarkTheme) longToColor(kbColors.keyTextColorDark) else longToColor(kbColors.keyTextColor)
    val specialKeyBackgroundColor = if (uiState.isDarkTheme) kbColors.specialKeyBgColorDark?.let { longToColor(it) }
        ?: themeSpecialKeyColor else kbColors.specialKeyBgColor?.let { longToColor(it) } ?: themeSpecialKeyColor
    val specialKeyTextColor = if (uiState.isDarkTheme) Color.White
        else KeyboardThemes.getSpecialKeyTextColor(uiState.themeId, false)
    val kbShadow = KeysConfigHelper.getKeyboardShadow()
    val kbKey = KeysConfigHelper.getKeyboardKeyConfig()
    val shadowEnabled = kbShadow.enabled
    val shadowElevation = kbShadow.elevation.dp
    val shadowShapeRadius = kbShadow.shapeRadius.dp
    val schemaName = uiState.schemaName
    val isMicrosoftDoublePinyin = schemaName.contains("微软双拼")
    val enterKeyText = uiState.enterKeyText
    val onKeyPressDown = callbacks.onKeyPressDown
    val onKeyRelease = callbacks.onKeyRelease
    val onCommitText = callbacks.onCommitText
    val onGestureAction: (GestureAction, String) -> Unit = { action, value ->
        when (action) {
            GestureAction.SWITCH_ROUTE -> {
                val overlayRoute = when (value) {
                    "emoji" -> OverlayRoute.Emoji
                    "symbol" -> OverlayRoute.Symbol
                    else -> null
                }
                overlayRoute?.let { viewModel.showOverlay(it) }
            }
            GestureAction.TOGGLE_ASCII -> {
                viewModel.resetShift()
                callbacks.onKeyPress("ime_switch", uiState.isAsciiMode)
            }
            GestureAction.DELETE -> {
                callbacks.onKeyPress("delete", false)
            }
            GestureAction.TOGGLE_SYMBOLS -> {
                callbacks.onKeyPress("mode_change", false)
            }
            else -> callbacks.onGestureAction?.invoke(action, value) ?: Unit
        }
    }

    CompositionLocalProvider(
        LocalKeyVisualPadding provides PaddingValues(horizontal = 1.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 2.dp, horizontal = 50.dp)
        ) {
        // ========== 左面板 ==========
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.42f)
                .padding(start = 4.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompactKeyboardRowWithConfig(
                    keys = listOf("q", "w", "e", "r", "t"),
                    onKeyPress = onKeyPress,
                    config = KeyboardRowConfig(
                        keyBackgroundColor = keyBackgroundColor,
                        keyTextColor = keyTextColor,
                        keyboardBackgroundColor = keyboardBackgroundColor,
                        fontSize = landscapeFontSize,
                        swipeFontSize = landscapeSwipeFontSize,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    ),
                    isShifted = visualIsShifted,
                    onKeyPressDown = onKeyPressDown,
                    onKeyRelease = onKeyRelease,
                    swipeDownHintsEnabled = swipeDownHintsEnabled,
                    swipeUpHintsEnabled = swipeUpHintsEnabled,
                    onCommitText = onCommitText,
                    onGestureAction = onGestureAction,
                    onSwipeStateChange = onSwipeStateChange,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = staggerStep)
            ) {
                CompactKeyboardRowWithConfig(
                    keys = listOf("a", "s", "d", "f", "g"),
                    onKeyPress = onKeyPress,
                    config = KeyboardRowConfig(
                        keyBackgroundColor = keyBackgroundColor,
                        keyTextColor = keyTextColor,
                        keyboardBackgroundColor = keyboardBackgroundColor,
                        fontSize = landscapeFontSize,
                        swipeFontSize = landscapeSwipeFontSize,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    ),
                    isShifted = visualIsShifted,
                    onKeyPressDown = onKeyPressDown,
                    onKeyRelease = onKeyRelease,
                    swipeDownHintsEnabled = swipeDownHintsEnabled,
                    swipeUpHintsEnabled = swipeUpHintsEnabled,
                    onCommitText = onCommitText,
                    onGestureAction = onGestureAction,
                    onSwipeStateChange = onSwipeStateChange,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = staggerStep * 2)
            ) {
                CompactKeyboardRowWithConfig(
                    keys = listOf("z", "x", "c", "v"),
                    onKeyPress = onKeyPress,
                    config = KeyboardRowConfig(
                        keyBackgroundColor = keyBackgroundColor,
                        keyTextColor = keyTextColor,
                        keyboardBackgroundColor = keyboardBackgroundColor,
                        fontSize = landscapeFontSize,
                        swipeFontSize = landscapeSwipeFontSize,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    ),
                    isShifted = visualIsShifted,
                    onKeyPressDown = onKeyPressDown,
                    onKeyRelease = onKeyRelease,
                    swipeDownHintsEnabled = swipeDownHintsEnabled,
                    swipeUpHintsEnabled = swipeUpHintsEnabled,
                    onCommitText = onCommitText,
                    onGestureAction = onGestureAction,
                    onSwipeStateChange = onSwipeStateChange,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ShiftCapsKeyButton(
                    shiftMode = visualShiftMode,
                    onKeyPress = onKeyPress,
                    onKeyPressDown = onKeyPressDown,
                    backgroundColor = specialKeyBackgroundColor,
                    iconColor = specialKeyTextColor,
                    modifier = Modifier.padding(1.dp,2.dp).weight(1.2f),
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    )
                    val k2Gesture = KeysConfigHelper.getKeyGesture("'")
                    val k2Action = k2Gesture?.tap?.action
                    val k2Tap = k2Gesture?.tap?.value?.takeIf { it.isNotEmpty() }
                        ?: k2Gesture?.tap?.label?.takeIf { it.isNotEmpty() }
                        ?: "，"
                    val k2SwipeValue = k2Gesture?.swipeUp?.value?.takeIf { it.isNotEmpty() } ?: "。"
                    val k2SwipeLabel = if (isAsciiMode) k2SwipeValue
                        else (k2Gesture?.swipeUp?.label?.takeIf { it.isNotEmpty() } ?: k2SwipeValue)
                    val k2Swipe = k2SwipeValue
                    if (k2Action == GestureAction.TOGGLE_ASCII) {
                        IconKeyButton(
                            icon = rememberVectorPainter(Icons.Default.Language),
                            onClick = {
                                onGestureAction.invoke(k2Action, k2Tap)
                            },
                            backgroundColor = keyBackgroundColor,
                            iconColor = keyTextColor,
                            modifier = Modifier.weight(0.8f),
                            onPress = { onKeyPressDown?.invoke(k2Swipe) },
                            shadowEnabled = shadowEnabled,
                            shadowElevation = shadowElevation,
                            shadowShapeRadius = shadowShapeRadius,
                        )
                    } else {
                        SwipeableKeyButtonLandscape(
                            text = k2Tap,
                            onClick = {
                                if (k2Action != null && k2Action != GestureAction.COMMIT) {
                                    onGestureAction?.invoke(k2Action, k2Tap)
                                } else {
                                    onKeyPress(k2Tap)
                                }
                            },
                            backgroundColor = keyBackgroundColor,
                            textColor = keyTextColor,
                            modifier = Modifier.weight(0.8f),
                            swipeText = k2Swipe,
                            swipeFontSize = landscapeSwipeFontSize,
                            onSwipe = { onKeyPress(it) },
                            onPress = { onKeyPressDown?.invoke(k2Swipe) },
                            shadowEnabled = shadowEnabled,
                            shadowElevation = shadowElevation,
                            shadowShapeRadius = shadowShapeRadius,
                        )
                    }
                SplitSpaceKey(
                    onClick = { onKeyPress("space") },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    schemaName = if (isAsciiMode) "English" else schemaName,
                    modifier = Modifier.weight(3f),
                    onPress = { onKeyPressDown?.invoke("space") },
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                )
            }
        }

        // 中间留空
        Spacer(modifier = Modifier.weight(0.16f))

        // ========== 右面板 ==========
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.42f)
                .padding(end = 4.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                CompactKeyboardRowWithConfig(
                    keys = listOf("y", "u", "i", "o", "p"),
                    onKeyPress = onKeyPress,
                    config = KeyboardRowConfig(
                        keyBackgroundColor = keyBackgroundColor,
                        keyTextColor = keyTextColor,
                        keyboardBackgroundColor = keyboardBackgroundColor,
                        fontSize = landscapeFontSize,
                        swipeFontSize = landscapeSwipeFontSize,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    ),
                    isShifted = visualIsShifted,
                    onKeyPressDown = onKeyPressDown,
                    onKeyRelease = onKeyRelease,
                    swipeDownHintsEnabled = swipeDownHintsEnabled,
                    swipeUpHintsEnabled = swipeUpHintsEnabled,
                    onCommitText = onCommitText,
                    onGestureAction = onGestureAction,
                    onSwipeStateChange = onSwipeStateChange,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = staggerStep)
            ) {
                CompactKeyboardRowWithConfig(
                    keys = if (isMicrosoftDoublePinyin) listOf("h", "j", "k", "l", ";") else listOf("g", "h", "j", "k", "l"),
                    onKeyPress = onKeyPress,
                    config = KeyboardRowConfig(
                        keyBackgroundColor = keyBackgroundColor,
                        keyTextColor = keyTextColor,
                        keyboardBackgroundColor = keyboardBackgroundColor,
                        fontSize = landscapeFontSize,
                        swipeFontSize = landscapeSwipeFontSize,
                    ),
                    isShifted = visualIsShifted,
                    onKeyPressDown = onKeyPressDown,
                    onKeyRelease = onKeyRelease,
                    swipeDownHintsEnabled = swipeDownHintsEnabled,
                    swipeUpHintsEnabled = swipeUpHintsEnabled,
                    onCommitText = onCommitText,
                    onGestureAction = onGestureAction,
                    onSwipeStateChange = onSwipeStateChange,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(end = staggerStep * 2),
            ) {
                Box(modifier = Modifier.weight(4f)) {
                    CompactKeyboardRowWithConfig(
                        keys = listOf("v", "b", "n", "m"),
                        onKeyPress = onKeyPress,
                        config = KeyboardRowConfig(
                            keyBackgroundColor = keyBackgroundColor,
                            keyTextColor = keyTextColor,
                            keyboardBackgroundColor = keyboardBackgroundColor,
                            fontSize = landscapeFontSize,
                            swipeFontSize = landscapeSwipeFontSize,
                        ),
                        isShifted = visualIsShifted,
                        onKeyPressDown = onKeyPressDown,
                        onKeyRelease = onKeyRelease,
                        swipeDownHintsEnabled = swipeDownHintsEnabled,
                        swipeUpHintsEnabled = swipeUpHintsEnabled,
                        onCommitText = onCommitText,
                        onGestureAction = onGestureAction,
                        onSwipeStateChange = onSwipeStateChange,
                    )
                }
                SwipeableIconKeyButton(
                    icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                    onClick = { onKeyPress("delete") },
                    backgroundColor = specialKeyBackgroundColor,
                    iconColor = specialKeyTextColor,
                    modifier = Modifier
                        .padding(1.dp)
                        .width(48.dp)
                        .fillMaxHeight(),
                    onLongClick = { onKeyPress("delete") },
                    onPress = { onKeyPressDown?.invoke("delete") },
                    onRelease = { onKeyRelease?.invoke("delete") },
                    swipeUpLabel = "上滑清空",
                    swipeDownLabel = "下滑撤回",
                    onSwipeUp = { onKeyPress("clear_all") },
                    onSwipeDown = { onKeyPress("undo_clear") },
                    onSwipeLeft = { suppressCursorMove.value = true; onKeyPress("clear_composition") },
                    onSwipeStateChange = onSwipeStateChange,
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SplitSpaceKey(
                    onClick = { onKeyPress("space") },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    schemaName = if (isAsciiMode) "English" else "",
                    modifier = Modifier.weight(2f),
                    onPress = { onKeyPressDown?.invoke("space") },
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                )
                SwipeableKeyButton(
                    text = "?123",
                    onClick = { onKeyPress("mode_change") },
                    backgroundColor = specialKeyBackgroundColor,
                    textColor = specialKeyTextColor,
                    modifier = Modifier.weight(1.2f),
                    onPress = { onKeyPressDown?.invoke("mode_change") },
                    onRelease = { onKeyRelease?.invoke("mode_change") },
                    onLongPressSelect = { label -> onKeyPress(if (label == "number") "mode_change_number" else "mode_change_common_symbol") },
                    longPressItems = listOf("number", "common_symbol"),
                    longPressDrawableIds = listOf(
                        com.kingzcheung.xime.R.drawable.t9,
                        com.kingzcheung.xime.R.drawable.t26
                    ),
                    onSwipeStateChange = onSwipeStateChange,
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                )
                val k4Gesture = KeysConfigHelper.getKeyGesture("earth")
                val k4Action = k4Gesture?.tap?.action
                val k4Value = k4Gesture?.tap?.value?.takeIf { it.isNotEmpty() } ?: k4Gesture?.tap?.label?.takeIf { it.isNotEmpty() } ?: "ime_switch"
                val k4Label = k4Gesture?.tap?.label?.takeIf { it.isNotEmpty() } ?: "中"
                if (k4Action == GestureAction.TOGGLE_ASCII) {
                    IconKeyButton(
                        icon = rememberVectorPainter(Icons.Default.Language),
                        onClick = {
                            if (k4Action != null && k4Action != GestureAction.COMMIT) {
                                onGestureAction?.invoke(k4Action, k4Value)
                            } else {
                                onKeyPress(k4Value)
                            }
                        },
                        backgroundColor = keyBackgroundColor,
                        iconColor = keyTextColor,
                        modifier = Modifier.weight(0.8f),
                        onPress = { onKeyPressDown?.invoke(k4Value) },
                        onRelease = { onKeyRelease?.invoke(k4Value) },
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    )
                } else {
                    val k4SwipeValue = k4Gesture?.swipeUp?.value?.takeIf { it.isNotEmpty() }
                    val k4SwipeLabel = if (isAsciiMode) (k4SwipeValue ?: "")
                        else (k4Gesture?.swipeUp?.label?.takeIf { it.isNotEmpty() } ?: k4SwipeValue ?: "")
                    SwipeableKeyButtonLandscape(
                        text = k4Label,
                        onClick = {
                            if (k4Action != null && k4Action != GestureAction.COMMIT) {
                                onGestureAction?.invoke(k4Action, k4Value)
                            } else {
                                onKeyPress(k4Value)
                            }
                        },
                        backgroundColor = keyBackgroundColor,
                        textColor = keyTextColor,
                        modifier = Modifier.weight(0.8f),
                        swipeText = k4SwipeLabel,
                        onSwipe = if (k4SwipeValue != null) { { onKeyPress(k4SwipeValue) } } else null,
                        onPress = { onKeyPressDown?.invoke(k4Value) },
                        onRelease = { onKeyRelease?.invoke(k4Value) },
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                    )
                }
                KeyButton(
                    text = enterKeyText,
                    onClick = { onKeyPress("enter") },
                    backgroundColor = specialKeyBackgroundColor,
                    textColor = specialKeyTextColor,
                    modifier = Modifier.weight(1.2f),
                    onPress = { onKeyPressDown?.invoke("enter") },
                    onRelease = { onKeyRelease?.invoke("enter") },
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                )
            }
        }
    }
    }
}

/**
 * 横屏紧凑版按键 — 主字符和上滑字符垂直堆叠居中
 */
@Composable
fun SwipeableKeyButtonLandscape(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    swipeText: String? = null,
    swipeDownText: String? = null,
    swipeUpKeyLabel: String? = null,
    swipeDownKeyLabel: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeDown: ((String) -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    onLongPressSelect: ((String) -> Unit)? = null,
    longPressItems: List<String>? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    swipeFontSize: androidx.compose.ui.unit.TextUnit = 8.sp,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var hasTriggeredSwipeUp by remember { mutableStateOf(false) }
    var hasTriggeredSwipeDown by remember { mutableStateOf(false) }
    var isSwiping by remember { mutableStateOf(false) }
    var isSwipeDown by remember { mutableStateOf(false) }
    var buttonBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var dragActivated by remember { mutableStateOf(false) }

    val currentText by rememberUpdatedState(text)
    val currentSwipeText by rememberUpdatedState(swipeText)
    val currentSwipeDownText by rememberUpdatedState(swipeDownText)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentOnSwipeDown by rememberUpdatedState(onSwipeDown)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnLongPressSelect by rememberUpdatedState(onLongPressSelect)
    val currentLongPressItems by rememberUpdatedState(longPressItems)
    val currentOnSwipeStateChange by rememberUpdatedState(onSwipeStateChange)
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current
    val chaiPuaFontFamily = remember {
        FontFamily(Font("ChaiPUA-0.2.7-snow.ttf", context.assets))
    }

    val density = LocalDensity.current
    val swipeUpThreshold = with(density) { (-15).dp.toPx() }
    val swipeDownThreshold = with(density) { 15.dp.toPx() }
    val bubbleShowThresholdUp = swipeUpThreshold * 0.3f
    val bubbleShowThresholdDown = swipeDownThreshold * 0.3f

    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }

    fun darkenColor(color: Color, factor: Float = 0.15f): Color {
        return Color(
            red = (color.red * (1 - factor)).coerceIn(0f, 1f),
            green = (color.green * (1 - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1 - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .pointerInput(currentText, currentLongPressItems.isNullOrEmpty(), currentOnLongPressSelect != null) {
                if (currentLongPressItems.isNullOrEmpty() || currentOnLongPressSelect == null) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            currentOnSwipeStateChange?.invoke(SwipeState(isPressed = true, pressedText = currentText), buttonBounds)
                            currentOnPress?.invoke()
                            tryAwaitRelease()
                            isPressed = false
                            currentOnRelease?.invoke()
                            currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                        },
                        onTap = {
                            if (!dragActivated && !hasTriggeredSwipeUp && !hasTriggeredSwipeDown) currentOnClick()
                        }
                    )
                } else {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        var localLongPressTriggered = false
                        var selectedIdx = 0
                        val downX = down.position.x
                        val items = currentLongPressItems ?: return@awaitEachGesture

                        currentOnSwipeStateChange?.invoke(SwipeState(isPressed = true, pressedText = currentText), buttonBounds)
                        currentOnPress?.invoke()

                        val longPressJob = scope.launch {
                            delay(400L)
                            localLongPressTriggered = true
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            currentOnSwipeStateChange?.invoke(
                                SwipeState(
                                    isPressed = true,
                                    isLongPress = true,
                                    longPressItems = items,
                                    selectedLongPressIndex = 0
                                ),
                                buttonBounds
                            )
                        }

                        val cancelThresholdPx = with(density) { 5.dp.toPx() }
                        val downY = down.position.y
                        var swipeDetected = false

                        try {
                            var lastReportedIdx = -1
                            var completed = false
                            while (!completed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (change.isConsumed) continue

                                if (!localLongPressTriggered) {
                                    val deltaX = change.position.x - downX
                                    val deltaY = change.position.y - downY
                                    if (kotlin.math.abs(deltaX) > cancelThresholdPx || kotlin.math.abs(deltaY) > cancelThresholdPx) {
                                        swipeDetected = true
                                        longPressJob.cancel()
                                    }
                                }

                                if (localLongPressTriggered) {
                                    val deltaX = change.position.x - downX
                                    val itemWidth = buttonBounds.width / items.size
                                    selectedIdx = ((deltaX / itemWidth) + if (items.size > 1) 0.5f else 0f).toInt()
                                        .coerceIn(0, items.size - 1)

                                    if (selectedIdx != lastReportedIdx) {
                                        lastReportedIdx = selectedIdx
                                        currentOnSwipeStateChange?.invoke(
                                            SwipeState(
                                                isPressed = true,
                                                isLongPress = true,
                                                longPressItems = items,
                                                selectedLongPressIndex = selectedIdx
                                            ),
                                            buttonBounds
                                        )
                                    }
                                    change.consume()
                                }

                                if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Release) {
                                    completed = true
                                    if (localLongPressTriggered) {
                                        val selected = items.getOrNull(selectedIdx)
                                        if (selected != null) {
                                            currentOnLongPressSelect?.invoke(selected)
                                        }
                                    } else if (!swipeDetected && !dragActivated) {
                                        currentOnClick()
                                    }
                                }
                            }
                        } finally {
                            longPressJob.cancel()
                            isPressed = false
                            currentOnRelease?.invoke()
                            currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                        }
                    }
                }
            }
            .then(
                if (swipeText != null || swipeDownText != null) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragActivated = true
                                isPressed = true
                                dragOffsetY = 0f
                                hasTriggeredSwipeUp = false
                                hasTriggeredSwipeDown = false
                                isSwiping = false
                                isSwipeDown = false
                                currentOnSwipeStateChange?.invoke(SwipeState(isPressed = true, pressedText = currentText), buttonBounds)
                            },
                            onDragEnd = {
                                if (!hasTriggeredSwipeUp && !hasTriggeredSwipeDown && dragOffsetY > swipeUpThreshold && dragOffsetY < swipeDownThreshold) {
                                    onClick()
                                }
                                dragActivated = false
                                isPressed = false
                                dragOffsetY = 0f
                                hasTriggeredSwipeUp = false
                                hasTriggeredSwipeDown = false
                                isSwiping = false
                                isSwipeDown = false
                                currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                            },
                            onDragCancel = {
                                dragActivated = false
                                isPressed = false
                                dragOffsetY = 0f
                                hasTriggeredSwipeUp = false
                                hasTriggeredSwipeDown = false
                                isSwiping = false
                                isSwipeDown = false
                                currentOnSwipeStateChange?.invoke(SwipeState(), buttonBounds)
                            },
                            onDrag = { _: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Offset ->
                                dragOffsetY += dragAmount.y

                                val swipeTextValue = currentSwipeText
                                val swipeDownTextValue = currentSwipeDownText
                                val onSwipeAction = currentOnSwipe
                                val onSwipeDownAction = currentOnSwipeDown
                                val onSwipeStateChangeAction = currentOnSwipeStateChange

                                if (dragOffsetY < 0) {
                                    val shouldShowBubble = swipeTextValue != null && dragOffsetY < bubbleShowThresholdUp
                                    if (shouldShowBubble != isSwiping) {
                                        isSwiping = shouldShowBubble
                                        isSwipeDown = false
                                        onSwipeStateChangeAction?.invoke(
                                            SwipeState(isSwiping = shouldShowBubble, swipeText = swipeTextValue, isSwipeDown = false),
                                            buttonBounds
                                        )
                                    }
                                } else if (dragOffsetY > 0) {
                                    val shouldShowBubble = swipeDownTextValue != null && dragOffsetY > bubbleShowThresholdDown
                                    if (shouldShowBubble != isSwipeDown) {
                                        isSwipeDown = shouldShowBubble
                                        isSwiping = shouldShowBubble
                                        onSwipeStateChangeAction?.invoke(
                                            SwipeState(isSwiping = shouldShowBubble, swipeText = swipeDownTextValue, isSwipeDown = true),
                                            buttonBounds
                                        )
                                    }
                                }

                                if (dragOffsetY < 0 && !hasTriggeredSwipeUp && swipeTextValue != null && onSwipeAction != null) {
                                    if (dragOffsetY < swipeUpThreshold) {
                                        hasTriggeredSwipeUp = true
                                        onSwipeAction(swipeTextValue)
                                    }
                                } else if (dragOffsetY > 0 && !hasTriggeredSwipeDown && swipeDownTextValue != null && onSwipeDownAction != null) {
                                    if (dragOffsetY > swipeDownThreshold) {
                                        hasTriggeredSwipeDown = true
                                        onSwipeDownAction(swipeDownTextValue)
                                    }
                                }
                            }
                        )
                    }
                } else Modifier
            )
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
            }
            .padding(LocalKeyVisualPadding.current)
            .then(shadowModifier)
            .clip(keyClipShape)
            .background(if (isPressed) darkenColor(backgroundColor) else backgroundColor),
        contentAlignment = Alignment.TopStart
    ) {

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = textColor,
                fontSize = if (fontSize != androidx.compose.ui.unit.TextUnit.Unspecified) fontSize else 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                lineHeight = TextUnit.Unspecified
            )
        }

        val keyLabel = swipeUpKeyLabel ?: swipeText
        if (keyLabel != null) {
            Text(
                text = keyLabel,
                color = textColor.copy(alpha = 0.5f),
                fontSize = swipeFontSize,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.End,
                maxLines = 1,
                lineHeight = 8.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
            )
        }
        if (swipeDownText != null) {
            Text(
                text = swipeDownText,
                color = textColor.copy(alpha = 0.5f),
                fontSize = swipeFontSize,
                fontWeight = FontWeight.Normal,
                fontFamily = chaiPuaFontFamily,
                textAlign = TextAlign.Start,
                maxLines = 1,
                lineHeight = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 4.dp, bottom = 2.dp)
            )
        }
    }
}

/**
 * 横屏紧凑版键盘行 — 使用 [SwipeableKeyButtonLandscape] 替代 [SwipeableKeyButton]
 */
@Composable
fun CompactKeyboardRowWithConfig(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    config: KeyboardRowConfig,
    isShifted: Boolean,
    isAsciiMode: Boolean = false,
    modifier: Modifier = Modifier,
    onKeyPressDown: ((String) -> Unit)? = null,
    onKeyRelease: ((String) -> Unit)? = null,
    swipeDownHintsEnabled: Boolean = true,
    swipeUpHintsEnabled: Boolean = true,
    onCommitText: ((String) -> Unit)? = null,
    onGestureAction: ((GestureAction, String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    configVersion: Int = 0,
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(config.keyboardBackgroundColor),
    ) {
        keys.forEach { key ->
            val rawSwipeUpLabel = KeysConfigHelper.getSwipeUpLabel(key, isAsciiMode)
            val swipeUpText = if (swipeUpHintsEnabled) rawSwipeUpLabel else null
            val swipeUpAction = KeysConfigHelper.getSwipeUpAction(key, isAsciiMode)
            val swipeUpDisplay = KeysConfigHelper.getSwipeUpDisplay(key, isAsciiMode)
            val swipeUpKeyLabel =
                if (swipeUpDisplay != DisplayMode.BUBBLE && swipeUpHintsEnabled) swipeUpText else null
            val swipeUpCommitValue = KeysConfigHelper.getSwipeUpCommitValue(key, isAsciiMode)
            val swipeDownRaw = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.swipeDown
            val swipeDownLabel = swipeDownRaw?.label?.takeIf { it.isNotEmpty() }
            val swipeDownAction = swipeDownRaw?.action
            val swipeDownValue = swipeDownRaw?.value
            val swipeDownDisplay = swipeDownRaw?.display ?: DisplayMode.BOTH
            val swipeDownBubbleText =
                if (swipeDownDisplay != DisplayMode.KEY && swipeDownHintsEnabled) swipeDownLabel else null

            val longPressConfig = KeysConfigHelper.getKeyGesture(key, isAsciiMode)?.longPress
            val longPressDisplay = longPressConfig?.display ?: "key"
            val longPressLabels = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.map { it.label }?.filter { it.isNotEmpty() }
                    ?.ifEmpty { null }
            } else null
            val longPressGestureMap = if (longPressDisplay == "bubble") {
                longPressConfig?.values?.associateBy { it.label }
            } else null

            val rawCommitValue = KeysConfigHelper.getKeyCommitValue(key, isAsciiMode)
            val commitValue = if (isShifted) rawCommitValue.uppercase() else rawCommitValue
            val compactDisplayText = if (isShifted && key == ";") {
                if (isAsciiMode) ":" else "："
            } else if (isAsciiMode) commitValue else KeysConfigHelper.getKeyDisplayLabel(key, isAsciiMode)
            val compactOnClick = remember(key, commitValue, onKeyPress) { { onKeyPress(commitValue) } }
            val compactOnPress: (() -> Unit)? = remember(key, onKeyPressDown) { { onKeyPressDown?.invoke(key); Unit } }
            val compactOnRelease: (() -> Unit)? = remember(key, onKeyRelease) { { onKeyRelease?.invoke(key); Unit } }
            val compactOnSwipeDown: ((String) -> Unit)? = if (swipeDownAction != null && swipeDownHintsEnabled && swipeDownLabel != null) {
                remember(key, onKeyPress, onGestureAction, onCommitText, swipeDownAction, swipeDownValue, swipeDownLabel) {
                    val label = swipeDownLabel
                    { _: String ->
                        if (swipeDownAction == GestureAction.COMMIT) {
                            (onCommitText ?: onKeyPress)(swipeDownValue?.ifEmpty { label } ?: label)
                        } else {
                            onGestureAction?.invoke(
                                swipeDownAction,
                                swipeDownValue?.ifEmpty { label } ?: label!!)
                        }
                        Unit
                    }
                }
            } else null
            val compactOnLongPressSelect: ((String) -> Unit)? = remember(key, longPressGestureMap, onGestureAction, onCommitText, onKeyPress) { { selectedLabel: String ->
                val gesture = longPressGestureMap?.get(selectedLabel)
                if (gesture != null && gesture.action != GestureAction.COMMIT) {
                    onGestureAction?.invoke(
                        gesture.action!!,
                        gesture.value.ifEmpty { selectedLabel })
                } else {
                    (onCommitText ?: onKeyPress)(selectedLabel)
                }
                Unit
            } }

            SwipeableKeyButtonLandscape(
                text = compactDisplayText,
                onClick = compactOnClick,
                backgroundColor = config.keyBackgroundColor,
                textColor = config.keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = swipeUpText,
                swipeDownText = swipeDownBubbleText,
                swipeUpKeyLabel = swipeUpKeyLabel,
                onSwipe = if (swipeUpCommitValue != null && swipeUpAction != GestureAction.NONE) { { onKeyPress(swipeUpCommitValue) } } else null,
                onSwipeDown = compactOnSwipeDown,
                onSwipeStateChange = onSwipeStateChange,
                onPress = compactOnPress,
                onRelease = compactOnRelease,
                onLongPressSelect = compactOnLongPressSelect,
                longPressItems = longPressLabels,
                fontSize = config.fontSize,
                swipeFontSize = config.swipeFontSize,
                shadowEnabled = config.shadowEnabled,
                shadowElevation = config.shadowElevation,
                shadowShapeRadius = config.shadowShapeRadius,
            )
        }
    }
}

/**
 * 横屏分体键盘专用空格键（简化版，不支持语音/滑动光标）
 */
@Composable
private fun SplitSpaceKey(
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    schemaName: String = "",
    modifier: Modifier = Modifier,
    onPress: (() -> Unit)? = null,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
) {
    val density = LocalDensity.current
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, backgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(backgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }
    val keyCornerRadius = LocalKeyCornerRadius.current
    val keyClipShape = remember(keyCornerRadius) { RoundedCornerShape(keyCornerRadius) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(LocalKeyVisualPadding.current)
            .then(shadowModifier)
            .clip(keyClipShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = schemaName,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )

        Text(
            text = "空格",
            color = textColor.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Start,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 6.dp, bottom = 2.dp)
        )
    }
}

/** QWERTY 空格键 */
@Composable
private fun SpaceKey(
    schemaName: String,
    isAsciiMode: Boolean,
    isSttEnabled: Boolean,
    isVoiceMode: Boolean,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    shadowEnabled: Boolean,
    shadowElevation: Dp,
    shadowShapeRadius: Dp,
    modifier: Modifier = Modifier,
    onKeyPress: (String) -> Unit,
    onKeyPressDown: ((String) -> Unit)?,
    onKeyRelease: ((String) -> Unit)?,
    onVoiceModeChange: ((Boolean) -> Unit)?,
) {
    val currentOnKeyPress by rememberUpdatedState(onKeyPress)
    val currentOnKeyPressDown by rememberUpdatedState(onKeyPressDown)
    val currentOnKeyRelease by rememberUpdatedState(onKeyRelease)
    val currentOnVoiceModeChange by rememberUpdatedState(onVoiceModeChange)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val density = LocalDensity.current
    val shadowModifier = remember(shadowEnabled, shadowElevation, shadowShapeRadius, density, keyBackgroundColor) {
        if (shadowEnabled) {
            val offsetPx = with(density) { shadowElevation.toPx() }
            val cornerPx = with(density) { shadowShapeRadius.toPx() }
            val color = crispShadowColor(keyBackgroundColor)
            Modifier.drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, offsetPx),
                    size = size,
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
        } else Modifier
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(isSttEnabled) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnKeyPressDown?.invoke("space")

                    var longPressTriggered = false
                    val longPressJob = scope.launch {
                        delay(400)
                        longPressTriggered = true

                        if (isSttEnabled) {
                            if (!PermissionHelper.hasRecordAudioPermission(context)) {
                                Toast.makeText(context, "需要麦克风权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                                PermissionHelper.requestRecordAudioPermission(context)
                            } else {
                                currentOnVoiceModeChange?.invoke(true)
                            }
                        } else {
                            while (true) {
                                currentOnKeyPress("space")
                                delay(80)
                            }
                        }
                    }

                    waitForUpOrCancellation()
                    longPressJob.cancel()
                    currentOnKeyRelease?.invoke("space")

                    if (!longPressTriggered) {
                        currentOnKeyPress("space")
                    }
                }
            }
            .padding(LocalKeyVisualPadding.current)
            .fillMaxWidth()
            .fillMaxHeight()
            .then(shadowModifier)
            .clip(RoundedCornerShape(LocalKeyCornerRadius.current))
            .background(keyBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        if (isVoiceMode) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "语音输入",
                tint = keyTextColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Text(
                text = if (isAsciiMode) "English" else schemaName,
                color = keyTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            if (isSttEnabled) {
                Icon(
                    painter = painterResource(com.kingzcheung.xime.R.drawable.voice),
                    contentDescription = "语音输入",
                    tint = keyTextColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp).align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp)
                )
            } else {
                Text(
                    text = "空格",
                    color = keyTextColor.copy(alpha = 0.3f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 2.dp)
                )
            }
        }
    }
}