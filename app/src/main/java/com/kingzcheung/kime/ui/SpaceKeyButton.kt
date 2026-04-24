package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kingzcheung.kime.ui.LocalStretchFactor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SpaceKeyButton(
    onClick: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    schemaName: String = "",
    modifier: Modifier = Modifier,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    isVoiceMode: Boolean = false,
    onVoiceModeChange: ((Boolean) -> Unit)? = null
) {
    // 使用 remember 保存手势相关状态
    var isPressed by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    var hasTriggeredSwipeLeft by remember { mutableStateOf(false) }
    var hasTriggeredSwipeRight by remember { mutableStateOf(false) }
    
    val swipeThreshold = 80f
    val longPressTimeout = 400L
    val scope = rememberCoroutineScope()
    
    Box(
        modifier = modifier
            .height((44 * LocalStretchFactor.current).dp)
            .shadow(1.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x80000000), spotColor = Color(0x80000000))
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPressed) backgroundColor.copy(alpha = 0.7f)
                else backgroundColor
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    dragOffsetX = 0f
                    hasTriggeredSwipeLeft = false
                    hasTriggeredSwipeRight = false
                    onPress?.invoke()
                    
                    // 启动长按检测
                    var longPressTriggered = false
                    val longPressJob = scope.launch {
                        delay(longPressTimeout)
                        longPressTriggered = true
                        onVoiceModeChange?.invoke(true)
                    }
                    
                    var dragTriggered = false
                    
                    drag(down.id) { change ->
                        dragTriggered = true
                        longPressJob.cancel()
                        val dx = change.position.x - change.previousPosition.x
                        dragOffsetX += dx
                        
                        if (dragOffsetX < -swipeThreshold && !hasTriggeredSwipeLeft && onSwipeLeft != null) {
                            hasTriggeredSwipeLeft = true
                            onSwipeLeft()
                        } else if (dragOffsetX > swipeThreshold && !hasTriggeredSwipeRight && onSwipeRight != null) {
                            hasTriggeredSwipeRight = true
                            onSwipeRight()
                        }
                    }
                    
                    // 等待手指抬起 - 这是关键
                    val up = waitForUpOrCancellation()
                    
                    // 取消长按检测
                    longPressJob.cancel()
                    
                    // 处理结果
                    if (longPressTriggered) {
                        // 长按触发后手指抬起，关闭语音模式
                        onVoiceModeChange?.invoke(false)
                    } else if (!dragTriggered && 
                        !hasTriggeredSwipeLeft && !hasTriggeredSwipeRight && 
                        dragOffsetX > -swipeThreshold && dragOffsetX < swipeThreshold) {
                        // 普通点击
                        onClick()
                    }
                    
                    isPressed = false
                    dragOffsetX = 0f
                    hasTriggeredSwipeLeft = false
                    hasTriggeredSwipeRight = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // UI 显示由外部 isVoiceMode 控制
        if (isVoiceMode) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "语音输入",
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
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
}