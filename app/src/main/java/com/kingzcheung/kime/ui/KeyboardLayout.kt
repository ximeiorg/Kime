package com.kingzcheung.kime.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

private val BubbleSize = 50.dp
private val BubbleWidthDown = 160.dp
private val BubbleHeightDown = 50.dp
private val BubbleOffsetY = -45.dp

@Composable
fun KeyboardLayout(
    onKeyPress: (String) -> Unit,
    isShifted: Boolean,
    isAsciiMode: Boolean = false,
    isDarkTheme: Boolean = false,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    var swipeState by remember { mutableStateOf(SwipeState()) }
    var bubblePosition by remember { mutableStateOf(IntOffset(0, 0)) }
    var keyboardBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect(0f, 0f, 0f, 0f)) }
    
    val density = LocalDensity.current
    val bubbleSizePx = with(density) { BubbleSize.toPx() }
    val bubbleWidthDownPx = with(density) { BubbleWidthDown.toPx() }
    val bubbleOffsetYPx = with(density) { BubbleOffsetY.toPx() }
    
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (swipeState.isSwiping) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "bubbleAlpha"
    )
    
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
                onSwipeStateChange = { state, bounds ->
                    swipeState = state
                    if (state.isSwiping && keyboardBounds.width > 0) {
                        val relativeX = bounds.left - keyboardBounds.left
                        val relativeY = bounds.top - keyboardBounds.top
                        val keyCenterX = relativeX + bounds.width / 2
                        val currentBubbleWidthPx = if (state.isSwipeDown) bubbleWidthDownPx else bubbleSizePx
                        val bubbleLeft = keyCenterX - currentBubbleWidthPx / 2
                        val bubbleTop = relativeY + bubbleOffsetYPx
                        bubblePosition = IntOffset(bubbleLeft.roundToInt(), bubbleTop.roundToInt())
                    }
                }
            )
            
            KeyboardRowWithConfig(
                keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                onKeyPress = onKeyPress,
                keyBackgroundColor = keyBackgroundColor,
                keyTextColor = keyTextColor,
                isShifted = isShifted,
                isAsciiMode = isAsciiMode,
                modifier = Modifier.padding(horizontal = 16.dp),
                onSwipeStateChange = { state, bounds ->
                    swipeState = state
                    if (state.isSwiping && keyboardBounds.width > 0) {
                        val relativeX = bounds.left - keyboardBounds.left
                        val relativeY = bounds.top - keyboardBounds.top
                        val keyCenterX = relativeX + bounds.width / 2
                        val currentBubbleWidthPx = if (state.isSwipeDown) bubbleWidthDownPx else bubbleSizePx
                        val bubbleLeft = keyCenterX - currentBubbleWidthPx / 2
                        val bubbleTop = relativeY + bubbleOffsetYPx
                        bubblePosition = IntOffset(bubbleLeft.roundToInt(), bubbleTop.roundToInt())
                    }
                }
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton(
                    text = "⇧",
                    onClick = { onKeyPress("shift") },
                    backgroundColor = specialKeyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(1.2f),
                    isHighlighted = isShifted
                )
                
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
                            text = if (isShifted) key.uppercase() else key,
                            onClick = { onKeyPress(key) },
                            backgroundColor = keyBackgroundColor,
                            textColor = keyTextColor,
                            modifier = Modifier.weight(1f),
                            swipeText = swipeUpText,
                            swipeDownText = swipeDownText,
                            onSwipe = if (swipeUpText != null) onKeyPress else null,
                            onSwipeDown = if (isAsciiMode && swipeDownText != null) onKeyPress else null,
                            onSwipeStateChange = { state, bounds ->
                                swipeState = state
                                if (state.isSwiping && keyboardBounds.width > 0) {
                                    val relativeX = bounds.left - keyboardBounds.left
                                    val relativeY = bounds.top - keyboardBounds.top
                                    val keyCenterX = relativeX + bounds.width / 2
                                    val currentBubbleWidthPx = if (state.isSwipeDown) bubbleWidthDownPx else bubbleSizePx
                                    val bubbleLeft = keyCenterX - currentBubbleWidthPx / 2
                                    val bubbleTop = relativeY + bubbleOffsetYPx
                                    bubblePosition = IntOffset(bubbleLeft.roundToInt(), bubbleTop.roundToInt())
                                }
                            }
                        )
                    }
                }
                
                KeyButton(
                    text = "⌫",
                    onClick = { onKeyPress("delete") },
                    backgroundColor = specialKeyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(1.2f)
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
                    modifier = Modifier.weight(1.2f)
                )
                
                KeyButton(
                    text = if (isAsciiMode) "英" else "中",
                    onClick = { onKeyPress("ime_switch") },
                    backgroundColor = specialKeyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(0.8f)
                )
                
                KeyButton(
                    text = "空格",
                    onClick = { onKeyPress("space") },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(3f)
                )
                
                KeyButton(
                    text = "。",
                    onClick = { onKeyPress(".") },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(0.8f)
                )
                
                KeyButton(
                    text = "发送",
                    onClick = { onKeyPress("enter") },
                    backgroundColor = specialKeyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
        
        if (swipeState.isSwiping && bubbleAlpha > 0f) {
            val currentSwipeText = swipeState.swipeText
            if (currentSwipeText != null) {
                val bubbleBgColor = if (isDarkTheme) Color(0xFF45474A) else Color(0xFFF0F1F2)
                val bubbleTextColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF202124)
                
                if (swipeState.isSwipeDown) {
                    Box(
                        modifier = Modifier
                            .offset { bubblePosition }
                            .shadow(6.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
                            .background(bubbleBgColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSwipeText,
                            color = bubbleTextColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .offset { bubblePosition }
                            .shadow(6.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
                            .background(bubbleBgColor, RoundedCornerShape(8.dp))
                            .size(BubbleSize),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentSwipeText,
                            color = bubbleTextColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
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
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null
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
                text = if (isShifted) key.uppercase() else key,
                onClick = { onKeyPress(key) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = swipeUpText,
                swipeDownText = swipeDownText,
                onSwipe = if (swipeUpText != null) onKeyPress else null,
                onSwipeDown = if (isAsciiMode && swipeDownText != null) onKeyPress else null,
                onSwipeStateChange = onSwipeStateChange
            )
        }
    }
}