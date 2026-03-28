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

private val topRowSwipeKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
private val middleRowSwipeKeys = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(")
private val bottomRowSwipeKeys = listOf("`", "~", "\\", "|", "_", "-", "+", "=")

private val BubbleSize = 48.dp
private val BubbleOffsetY = -70.dp

@Composable
fun KeyboardLayout(
    onKeyPress: (String) -> Unit,
    isShifted: Boolean,
    isAsciiMode: Boolean = false,
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
            KeyboardRow(
                keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                swipeKeys = topRowSwipeKeys,
                onKeyPress = onKeyPress,
                onSwipeKey = onKeyPress,
                keyBackgroundColor = keyBackgroundColor,
                keyTextColor = keyTextColor,
                isShifted = isShifted,
                onSwipeStateChange = { state, bounds ->
                    swipeState = state
                    if (state.isSwiping && keyboardBounds.width > 0) {
                        val relativeX = bounds.left - keyboardBounds.left
                        val relativeY = bounds.top - keyboardBounds.top
                        val keyCenterX = relativeX + bounds.width / 2
                        val bubbleLeft = keyCenterX - bubbleSizePx / 2
                        val bubbleTop = relativeY + bubbleOffsetYPx
                        bubblePosition = IntOffset(bubbleLeft.roundToInt(), bubbleTop.roundToInt())
                    }
                }
            )
            
            KeyboardRow(
                keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                swipeKeys = middleRowSwipeKeys,
                onKeyPress = onKeyPress,
                onSwipeKey = onKeyPress,
                keyBackgroundColor = keyBackgroundColor,
                keyTextColor = keyTextColor,
                isShifted = isShifted,
                modifier = Modifier.padding(horizontal = 16.dp),
                onSwipeStateChange = { state, bounds ->
                    swipeState = state
                    if (state.isSwiping && keyboardBounds.width > 0) {
                        val relativeX = bounds.left - keyboardBounds.left
                        val relativeY = bounds.top - keyboardBounds.top
                        val keyCenterX = relativeX + bounds.width / 2
                        val bubbleLeft = keyCenterX - bubbleSizePx / 2
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
                    bottomKeys.forEachIndexed { index, key ->
                        SwipeableKeyButton(
                            text = if (isShifted) key.uppercase() else key,
                            onClick = { onKeyPress(key) },
                            backgroundColor = keyBackgroundColor,
                            textColor = keyTextColor,
                            modifier = Modifier.weight(1f),
                            swipeText = bottomRowSwipeKeys.getOrNull(index),
                            onSwipe = onKeyPress,
                            onSwipeStateChange = { state, bounds ->
                                swipeState = state
                                if (state.isSwiping && keyboardBounds.width > 0) {
                                    val relativeX = bounds.left - keyboardBounds.left
                                    val relativeY = bounds.top - keyboardBounds.top
                                    val keyCenterX = relativeX + bounds.width / 2
                                    val bubbleLeft = keyCenterX - bubbleSizePx / 2
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
                Box(
                    modifier = Modifier
                        .offset { bubblePosition }
                        .shadow(8.dp, RoundedCornerShape(8.dp))
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .size(BubbleSize),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentSwipeText,
                        color = Color.Black,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}