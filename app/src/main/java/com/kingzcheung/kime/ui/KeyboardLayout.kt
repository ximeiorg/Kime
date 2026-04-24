package com.kingzcheung.kime.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kingzcheung.kime.ui.LocalStretchFactor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.kime.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun KeyboardLayout(
    onKeyPress: (String) -> Unit,
    isShifted: Boolean,
    isAsciiMode: Boolean = false,
    schemaName: String = "",
    enterKeyText: String = "发送",
    isDarkTheme: Boolean = false,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    keyboardBackgroundColor: Color = Color.Transparent,
    showBottomButtons: Boolean = false,
    onHideKeyboard: (() -> Unit)? = null,
    onSwitchKeyboard: (() -> Unit)? = null,
    onVoiceModeChange: ((Boolean) -> Unit)? = null,
    isVoiceMode: Boolean = false,
    modifier: Modifier = Modifier,
    onKeyPressDown: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        SubcharHelper.init(context)
    }
    
    var swipeState by remember { mutableStateOf(SwipeState()) }
    var keyboardBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var lastKeyBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    
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
    
    Box(
        modifier = modifier
            .background(keyboardBackgroundColor)
            .onGloballyPositioned { coordinates ->
                keyboardBounds = coordinates.boundsInRoot()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(keyboardBackgroundColor)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行
            if (isVoiceMode) {
                DummyKeyboardRow(keysCount = 10, keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f), keyboardBackgroundColor = keyboardBackgroundColor)
            } else {
                KeyboardRowWithConfig(
                    keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                    onKeyPress = onKeyPress,
                    keyBackgroundColor = keyBackgroundColor,
                    keyTextColor = keyTextColor,
                    isShifted = isShifted,
                    isAsciiMode = isAsciiMode,
                    keyboardBackgroundColor = keyboardBackgroundColor,
                    onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                    onKeyPressDown = onKeyPressDown
                )
            }
            
            // 第二行
            if (isVoiceMode) {
                DummyKeyboardRow(
                    keysCount = 9, 
                    keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                    keyboardBackgroundColor = keyboardBackgroundColor,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                KeyboardRowWithConfig(
                    keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                    onKeyPress = onKeyPress,
                    keyBackgroundColor = keyBackgroundColor,
                    keyTextColor = keyTextColor,
                    isShifted = isShifted,
                    isAsciiMode = isAsciiMode,
                    keyboardBackgroundColor = keyboardBackgroundColor,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                    onKeyPressDown = onKeyPressDown
                )
            }
            
            // 第三行
            if (isVoiceMode) {
                DummyBottomRow(
                    keyBackgroundColor = keyBackgroundColor.copy(alpha = 0.5f),
                    specialKeyBackgroundColor = specialKeyBackgroundColor.copy(alpha = 0.5f),
                    keyboardBackgroundColor = keyboardBackgroundColor
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(keyboardBackgroundColor),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isAsciiMode) {
                        IconKeyButton(
                            icon = rememberVectorPainter(Icons.Default.ArrowUpward),
                            onClick = { onKeyPress("shift") },
                            backgroundColor = specialKeyBackgroundColor,
                            iconColor = keyTextColor,
                            modifier = Modifier.weight(1.2f),
                            isHighlighted = isShifted,
                            onPress = { onKeyPressDown?.invoke("shift") }
                        )
                    } else {
                        IconKeyButton(
                            icon = rememberVectorPainter(Icons.Default.EmojiEmotions),
                            onClick = { onKeyPress("emoji") },
                            backgroundColor = specialKeyBackgroundColor,
                            iconColor = keyTextColor,
                            modifier = Modifier.weight(1.2f),
                            onPress = { onKeyPressDown?.invoke("emoji") }
                        )
                    }
                    
                    Row(
                        modifier = Modifier
                            .weight(7f)
                            .background(keyboardBackgroundColor),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val bottomKeys = listOf("z", "x", "c", "v", "b", "n", "m")
                        bottomKeys.forEach { key ->
                            val swipeUpText = KeysConfigHelper.getSwipeUpText(key)
                            val swipeDownText = if (isAsciiMode) 
                                KeysConfigHelper.getSwipeDownEnglishText(key) 
                            else 
                                KeysConfigHelper.getSwipeDownWubiText(key)
                            
                            SwipeableKeyButton(
                                text = if (isShifted || !isAsciiMode) key.uppercase() else key,
                                onClick = { onKeyPress(key) },
                                backgroundColor = keyBackgroundColor,
                                textColor = keyTextColor,
                                modifier = Modifier.weight(1f),
                                swipeText = swipeUpText,
                                swipeDownText = swipeDownText,
                                onSwipe = if (swipeUpText != null) onKeyPress else null,
                                onSwipeDown = if (isAsciiMode && swipeDownText != null) onKeyPress else null,
                                onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                                onPress = { onKeyPressDown?.invoke(key) }
                            )
                        }
                    }
                    
                    SwipeableIconKeyButton(
                        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                        onClick = { onKeyPress("delete") },
                        backgroundColor = specialKeyBackgroundColor,
                        iconColor = keyTextColor,
                        modifier = Modifier.weight(1.2f),
                        swipeText = "清空",
                        onSwipe = { onKeyPress("clear_composition") },
                        onLongClick = { onKeyPress("delete") },
                        onPress = { onKeyPressDown?.invoke("delete") }
                    )
                }
            }
            
            // 第四行（控制行）- 包含空格键
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(keyboardBackgroundColor),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 123 / 英中 键
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
                    KeyButton(
                        text = "123",
                        onClick = { onKeyPress("mode_change") },
                        backgroundColor = specialKeyBackgroundColor,
                        textColor = keyTextColor,
                        modifier = Modifier.weight(1.2f),
                        onPress = { onKeyPressDown?.invoke("mode_change") }
                    )
                    
                    KeyButton(
                        text = if (isAsciiMode) "英" else "中",
                        onClick = { onKeyPress("ime_switch") },
                        backgroundColor = specialKeyBackgroundColor,
                        textColor = keyTextColor,
                        modifier = Modifier.weight(0.8f),
                        onPress = { onKeyPressDown?.invoke("ime_switch") }
                    )
                }
                
                // 空格键 - 使用 pointerInput 处理长按
                val scope = rememberCoroutineScope()
                Box(
                    modifier = Modifier
                        .weight(3f)
                        .height((44 * LocalStretchFactor.current).dp)
                        .shadow(1.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
                        .clip(RoundedCornerShape(8.dp))
                        .background(keyBackgroundColor)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                onKeyPressDown?.invoke("space")
                                
                                // 启动长按检测
                                var longPressTriggered = false
                                val longPressJob = scope.launch {
                                    delay(400)
                                    longPressTriggered = true
                                    
                                    // 检查麦克风权限
                                    if (!PermissionHelper.hasRecordAudioPermission(context)) {
                                        Toast.makeText(context, "需要麦克风权限才能使用语音输入", Toast.LENGTH_SHORT).show()
                                        PermissionHelper.requestRecordAudioPermission(context)
                                    } else {
                                        // 触发语音模式切换，外部状态变化后会显示 VoiceKeyboardLayout
                                        onVoiceModeChange?.invoke(true)
                                    }
                                }
                                
                                // 等待手指抬起或取消
                                try {
                                    waitForUpOrCancellation()
                                } catch (e: Exception) {
                                    // 手势取消
                                }
                                
                                // 取消长按检测
                                longPressJob.cancel()
                                
                                // 处理结果
                                if (!longPressTriggered) {
                                    // 普通点击
                                    onKeyPress("space")
                                }
                                // 长按触发后：VoiceKeyboardContainer 处理手指抬起停止录音
                            }
                        },
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
                            text = schemaName,
                            color = keyTextColor,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1
                        )
                        
                        Text(
                            text = "空格",
                            color = keyTextColor.copy(alpha = 0.3f),
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 6.dp, bottom = 2.dp)
                        )
                    }
                }
                
                // 逗号 / 回车 键
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
                    SwipeableKeyButton(
                        text = if (isAsciiMode) "," else "，",
                        onClick = { onKeyPress(if (isAsciiMode) "," else "，") },
                        backgroundColor = keyBackgroundColor,
                        textColor = keyTextColor,
                        modifier = Modifier.weight(0.8f),
                        swipeText = if (isAsciiMode) "." else "。",
                        onSwipe = { onSwipeText -> onKeyPress(onSwipeText) },
                        onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                        onPress = { onKeyPressDown?.invoke(if (isAsciiMode) "." else "。") }
                    )
                    
                    KeyButton(
                        text = enterKeyText,
                        onClick = { onKeyPress("enter") },
                        backgroundColor = specialKeyBackgroundColor,
                        textColor = keyTextColor,
                        modifier = Modifier.weight(1.2f),
                        onPress = { onKeyPressDown?.invoke("enter") }
                    )
                }
            }
            
            // 底部按钮
            if (showBottomButtons && !isVoiceMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(keyboardBackgroundColor),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { onHideKeyboard?.invoke() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "收起键盘",
                            tint = keyTextColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { onSwitchKeyboard?.invoke() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "切换键盘",
                            tint = keyTextColor,
                            modifier = Modifier.size(24.dp)
                        )
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
        
        SwipeBubble(
            swipeState = swipeState,
            keyBounds = lastKeyBounds,
            isDarkTheme = isDarkTheme,
            keyWidth = if (swipeState.isSwiping || swipeState.isPressed) lastKeyBounds.width else 0f,
            keyboardWidth = keyboardBounds.width
        )
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DummyKeyButton(
            backgroundColor = specialKeyBackgroundColor,
            modifier = Modifier.weight(1.2f)
        )
        Row(
            modifier = Modifier.weight(7f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
            .height((44 * LocalStretchFactor.current).dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    )
}

@Composable
fun KeyboardRowWithConfig(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    isShifted: Boolean,
    isAsciiMode: Boolean,
    keyboardBackgroundColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onKeyPressDown: ((String) -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(keyboardBackgroundColor),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEach { key ->
            val swipeUpText = KeysConfigHelper.getSwipeUpText(key)
            val swipeDownText = if (isAsciiMode) 
                KeysConfigHelper.getSwipeDownEnglishText(key) 
            else 
                KeysConfigHelper.getSwipeDownWubiText(key)
            
            SwipeableKeyButton(
                text = if (isShifted || !isAsciiMode) key.uppercase() else key,
                onClick = { onKeyPress(key) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = swipeUpText,
                swipeDownText = swipeDownText,
                onSwipe = if (swipeUpText != null) onKeyPress else null,
                onSwipeDown = if (isAsciiMode && swipeDownText != null) onKeyPress else null,
                onSwipeStateChange = onSwipeStateChange,
                onPress = { onKeyPressDown?.invoke(key) }
            )
        }
    }
}