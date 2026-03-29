package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SpaceKeyButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    schemaName: String = "",
    modifier: Modifier = Modifier,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var hasTriggeredSwipeLeft by remember { mutableStateOf(false) }
    var hasTriggeredSwipeRight by remember { mutableStateOf(false) }
    
    val swipeThreshold = 80f
    
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isPressed) backgroundColor.copy(alpha = 0.7f)
                else backgroundColor
            )
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isPressed = true
                        dragOffsetX = 0f
                        hasTriggeredSwipeLeft = false
                        hasTriggeredSwipeRight = false
                    },
                    onDragEnd = {
                        if (!hasTriggeredSwipeLeft && !hasTriggeredSwipeRight && 
                            dragOffsetX > -swipeThreshold && dragOffsetX < swipeThreshold) {
                            onClick()
                        }
                        isPressed = false
                        dragOffsetX = 0f
                        hasTriggeredSwipeLeft = false
                        hasTriggeredSwipeRight = false
                    },
                    onDragCancel = {
                        isPressed = false
                        dragOffsetX = 0f
                        hasTriggeredSwipeLeft = false
                        hasTriggeredSwipeRight = false
                    },
                    onDrag = { change, dragAmount ->
                        dragOffsetX += dragAmount.x
                        
                        if (dragOffsetX < -swipeThreshold && !hasTriggeredSwipeLeft && onSwipeLeft != null) {
                            hasTriggeredSwipeLeft = true
                            onSwipeLeft()
                        } else if (dragOffsetX > swipeThreshold && !hasTriggeredSwipeRight && onSwipeRight != null) {
                            hasTriggeredSwipeRight = true
                            onSwipeRight()
                        }
                    }
                )
            }
            .clickable { 
                if (!hasTriggeredSwipeLeft && !hasTriggeredSwipeRight) onClick() 
            },
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