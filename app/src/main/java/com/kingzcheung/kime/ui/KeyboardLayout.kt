package com.kingzcheung.kime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



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
    showBottomButtons: Boolean = false,
    onHideKeyboard: (() -> Unit)? = null,
    onSwitchKeyboard: (() -> Unit)? = null,
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
        modifier = modifier.onGloballyPositioned { coordinates ->
            keyboardBounds = coordinates.boundsInRoot()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            KeyboardRowWithConfig(
                keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                onKeyPress = onKeyPress,
                keyBackgroundColor = keyBackgroundColor,
                keyTextColor = keyTextColor,
                isShifted = isShifted,
                isAsciiMode = isAsciiMode,
                onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                onKeyPressDown = onKeyPressDown
            )
            
            KeyboardRowWithConfig(
                keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                onKeyPress = onKeyPress,
                keyBackgroundColor = keyBackgroundColor,
                keyTextColor = keyTextColor,
                isShifted = isShifted,
                isAsciiMode = isAsciiMode,
                modifier = Modifier.padding(horizontal = 16.dp),
                onSwipeStateChange = { state, bounds -> processSwipeState(state, bounds) },
                onKeyPressDown = onKeyPressDown
            )
            
Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isAsciiMode) {
                    IconKeyButton(
                        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.ArrowUpward),
                        onClick = { onKeyPress("shift") },
                        backgroundColor = specialKeyBackgroundColor,
                        iconColor = keyTextColor,
                        modifier = Modifier.weight(1.2f),
                        isHighlighted = isShifted,
                        onPress = { onKeyPressDown?.invoke("shift") }
                    )
                } else {
                    IconKeyButton(
                        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.EmojiEmotions),
                        onClick = { onKeyPress("emoji") },
                        backgroundColor = specialKeyBackgroundColor,
                        iconColor = keyTextColor,
                        modifier = Modifier.weight(1.2f),
                        onPress = { onKeyPressDown?.invoke("emoji") }
                    )
                }
                
                Row(
                    modifier = Modifier.weight(7f),
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
                    icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
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
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                
                SpaceKeyButton(
                    onClick = { onKeyPress("space") },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    schemaName = schemaName,
                    modifier = Modifier.weight(3f),
                    onPress = { onKeyPressDown?.invoke("space") }
                )
                
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
            
            if (showBottomButtons) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
fun KeyboardRowWithConfig(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    isShifted: Boolean,
    isAsciiMode: Boolean,
    modifier: Modifier = Modifier,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null,
    onKeyPressDown: ((String) -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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