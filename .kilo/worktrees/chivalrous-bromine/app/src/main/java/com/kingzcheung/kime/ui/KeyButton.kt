package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SwipeState(
    val isSwiping: Boolean = false,
    val swipeText: String? = null
)

@Composable
fun KeyButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    swipeText: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState) -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var hasTriggeredSwipe by remember { mutableStateOf(false) }
    var isSwiping by remember { mutableStateOf(false) }
    
    val swipeThreshold = -50f
    
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPressed) backgroundColor.copy(alpha = 0.7f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isPressed = true
                        dragOffsetY = 0f
                        hasTriggeredSwipe = false
                        isSwiping = false
                        onSwipeStateChange?.invoke(SwipeState(false, null))
                    },
                    onDragEnd = {
                        if (!hasTriggeredSwipe && dragOffsetY > swipeThreshold) {
                            onClick()
                        }
                        isPressed = false
                        dragOffsetY = 0f
                        hasTriggeredSwipe = false
                        isSwiping = false
                        onSwipeStateChange?.invoke(SwipeState(false, null))
                    },
                    onDragCancel = {
                        isPressed = false
                        dragOffsetY = 0f
                        hasTriggeredSwipe = false
                        isSwiping = false
                        onSwipeStateChange?.invoke(SwipeState(false, null))
                    },
                    onDrag = { change, dragAmount ->
                        dragOffsetY += dragAmount.y
                        
                        val shouldShowBubble = dragOffsetY < swipeThreshold * 0.5f && swipeText != null
                        if (shouldShowBubble != isSwiping) {
                            isSwiping = shouldShowBubble
                            onSwipeStateChange?.invoke(SwipeState(shouldShowBubble, swipeText))
                        }
                        
                        if (dragOffsetY < swipeThreshold && !hasTriggeredSwipe && swipeText != null && onSwipe != null) {
                            hasTriggeredSwipe = true
                            onSwipe(swipeText)
                        }
                    }
                )
            }
            .clickable { if (!hasTriggeredSwipe) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (text.length > 2) 14.sp else 18.sp,
            fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        
        if (swipeText != null && swipeText.isNotEmpty()) {
            Text(
                text = swipeText,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = (-14).dp)
            )
        }
    }
}

@Composable
fun SwipeableKeyButton(
    text: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    swipeText: String? = null,
    onSwipe: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var hasTriggeredSwipe by remember { mutableStateOf(false) }
    var isSwiping by remember { mutableStateOf(false) }
    var buttonBounds by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    
    val swipeThreshold = -50f
    
    Box(
        modifier = modifier
            .height(44.dp)
            .onGloballyPositioned { coordinates ->
                buttonBounds = coordinates.boundsInRoot()
            }
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPressed) backgroundColor.copy(alpha = 0.7f)
                else if (isHighlighted) backgroundColor.copy(alpha = 0.8f)
                else backgroundColor
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isPressed = true
                        dragOffsetY = 0f
                        hasTriggeredSwipe = false
                        isSwiping = false
                        onSwipeStateChange?.invoke(SwipeState(false, null), buttonBounds)
                    },
                    onDragEnd = {
                        if (!hasTriggeredSwipe && dragOffsetY > swipeThreshold) {
                            onClick()
                        }
                        isPressed = false
                        dragOffsetY = 0f
                        hasTriggeredSwipe = false
                        isSwiping = false
                        onSwipeStateChange?.invoke(SwipeState(false, null), buttonBounds)
                    },
                    onDragCancel = {
                        isPressed = false
                        dragOffsetY = 0f
                        hasTriggeredSwipe = false
                        isSwiping = false
                        onSwipeStateChange?.invoke(SwipeState(false, null), buttonBounds)
                    },
                    onDrag = { change, dragAmount ->
                        dragOffsetY += dragAmount.y
                        
                        val shouldShowBubble = dragOffsetY < swipeThreshold * 0.5f && swipeText != null
                        if (shouldShowBubble != isSwiping) {
                            isSwiping = shouldShowBubble
                            onSwipeStateChange?.invoke(SwipeState(shouldShowBubble, swipeText), buttonBounds)
                        }
                        
                        if (dragOffsetY < swipeThreshold && !hasTriggeredSwipe && swipeText != null && onSwipe != null) {
                            hasTriggeredSwipe = true
                            onSwipe(swipeText)
                        }
                    }
                )
            }
            .clickable { if (!hasTriggeredSwipe) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (text.length > 2) 14.sp else 18.sp,
            fontWeight = if (text.length > 2) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        
        if (swipeText != null && swipeText.isNotEmpty()) {
            Text(
                text = swipeText,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.offset(y = (-14).dp)
            )
        }
    }
}

@Composable
fun KeyboardRow(
    keys: List<String>,
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    isShifted: Boolean,
    modifier: Modifier = Modifier,
    swipeKeys: List<String>? = null,
    onSwipeKey: ((String) -> Unit)? = null,
    onSwipeStateChange: ((SwipeState, Rect) -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keys.forEachIndexed { index, key ->
            val swipeText = swipeKeys?.getOrNull(index)
            SwipeableKeyButton(
                text = if (isShifted) key.uppercase() else key,
                onClick = { onKeyPress(key) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = swipeText,
                onSwipe = onSwipeKey,
                onSwipeStateChange = onSwipeStateChange
            )
        }
    }
}