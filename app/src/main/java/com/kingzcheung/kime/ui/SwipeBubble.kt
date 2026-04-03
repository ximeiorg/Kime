package com.kingzcheung.kime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val BubbleBodyHeight = KeyboardDimensions.BubbleHeightDown
private val BubblePointerHeight = KeyboardDimensions.BubblePointerHeight
private val BubbleCornerRadius = KeyboardDimensions.BubbleCornerRadius
private val BubbleScreenMargin = 4.dp

private fun DrawScope.drawBubbleShape(
    bodyLeft: Float,
    bodyWidth: Float,
    bodyHeight: Float,
    pointerLeft: Float,
    pointerWidth: Float,
    pointerHeight: Float,
    cornerRadius: Float,
    color: Color
) {
    val path = Path()
    val pointerRadius = cornerRadius.coerceAtMost(pointerWidth / 2f)
    val radius = cornerRadius.coerceAtMost(bodyWidth / 2f).coerceAtMost(bodyHeight / 2f)
    
    val bodyRight = bodyLeft + bodyWidth
    val pointerRight = pointerLeft + pointerWidth
    val bodyBottom = bodyHeight
    val pointerBottom = bodyBottom + pointerHeight
    
    val leftGap = bodyLeft - pointerLeft
    val rightGap = bodyRight - pointerRight
    
    val alignLeft = leftGap >= 0 && leftGap <= radius
    val alignRight = rightGap >= 0 && rightGap <= radius
    
    val actualBodyLeft = if (alignLeft) pointerLeft else bodyLeft
    val actualBodyRight = if (alignRight) pointerRight else bodyRight
    
    path.moveTo(actualBodyLeft + radius, 0f)
    
    // 顶部
    path.lineTo(actualBodyRight - radius, 0f)
    path.quadraticBezierTo(actualBodyRight, 0f, actualBodyRight, radius)
    
    // 右边
    if (alignRight) {
        path.lineTo(actualBodyRight, bodyBottom)
    } else {
        path.lineTo(actualBodyRight, bodyBottom - radius)
        path.quadraticBezierTo(actualBodyRight, bodyBottom, actualBodyRight - radius, bodyBottom)
    }
    
    // 右下角到 pointer
    if (alignRight) {
        // 对齐时直接垂直向下
        path.lineTo(actualBodyRight, pointerBottom - pointerRadius)
        path.quadraticBezierTo(actualBodyRight, pointerBottom, actualBodyRight - pointerRadius, pointerBottom)
    } else if (actualBodyRight > pointerRight) {
        // 主体超出 pointer
        path.lineTo(pointerRight + pointerRadius, bodyBottom)
        path.quadraticBezierTo(pointerRight, bodyBottom, pointerRight, bodyBottom + pointerRadius)
        path.lineTo(pointerRight, pointerBottom - pointerRadius)
        path.quadraticBezierTo(pointerRight, pointerBottom, pointerRight - pointerRadius, pointerBottom)
    } else {
        // pointer 超出主体
        path.quadraticBezierTo(actualBodyRight, bodyBottom, pointerRight, bodyBottom + pointerRadius)
        path.lineTo(pointerRight, pointerBottom - pointerRadius)
        path.quadraticBezierTo(pointerRight, pointerBottom, pointerRight - pointerRadius, pointerBottom)
    }
    
    // pointer 底部
    path.lineTo(pointerLeft + pointerRadius, pointerBottom)
    path.quadraticBezierTo(pointerLeft, pointerBottom, pointerLeft, pointerBottom - pointerRadius)
    
    // 左下角到主体
    if (alignLeft) {
        // 对齐时直接垂直向上
        path.lineTo(pointerLeft, bodyBottom)
    } else if (actualBodyLeft < pointerLeft) {
        // pointer 超出主体
        path.lineTo(pointerLeft, bodyBottom + pointerRadius)
        path.quadraticBezierTo(pointerLeft, bodyBottom, pointerLeft - pointerRadius, bodyBottom)
        path.lineTo(actualBodyLeft + radius, bodyBottom)
        path.quadraticBezierTo(actualBodyLeft, bodyBottom, actualBodyLeft, bodyBottom - radius)
    } else {
        // 主体超出 pointer
        path.lineTo(pointerLeft, bodyBottom + pointerRadius)
        path.quadraticBezierTo(pointerLeft, bodyBottom, actualBodyLeft, bodyBottom - radius)
    }
    
    // 左边
    path.lineTo(actualBodyLeft, radius)
    path.quadraticBezierTo(actualBodyLeft, 0f, actualBodyLeft + radius, 0f)
    
    path.close()
    
    drawPath(path, color)
}

data class BubbleLayoutInfo(
    val boxLeft: Float,
    val boxTop: Float,
    val boxWidth: Float,
    val bodyLeftInBox: Float,
    val pointerLeftInBox: Float
)

fun calculateBubbleLayout(
    keyBounds: Rect,
    bodyWidth: Float,
    bodyHeight: Float,
    pointerHeight: Float,
    keyboardWidth: Float,
    thresholdPx: Float,
    screenMarginPx: Float
): BubbleLayoutInfo {
    val pointerWidth = keyBounds.width
    val pointerLeft = keyBounds.left
    val pointerCenterX = pointerLeft + pointerWidth / 2
    val pointerRight = pointerLeft + pointerWidth
    
    val idealBodyLeft = pointerCenterX - bodyWidth / 2
    val maxBodyLeft = keyboardWidth - bodyWidth - screenMarginPx
    val threshold = thresholdPx
    
    val bodyLeft = when {
        idealBodyLeft < screenMarginPx -> {
            val clampedLeft = screenMarginPx
            val bodyRight = clampedLeft + bodyWidth
            val gap = bodyRight - pointerRight
            android.util.Log.d("SwipeBubble", "Left edge: gap=$gap, threshold=$threshold, pointerLeft=$pointerLeft, pointerRight=$pointerRight, bodyWidth=$bodyWidth, bodyRight=$bodyRight")
            if (gap < threshold) {
                pointerLeft
            } else {
                clampedLeft
            }
        }
        idealBodyLeft > maxBodyLeft -> {
            val clampedLeft = maxBodyLeft
            val bodyRight = clampedLeft + bodyWidth
            val gap = bodyRight - pointerRight
            android.util.Log.d("SwipeBubble", "Right edge: gap=$gap, threshold=$threshold, pointerLeft=$pointerLeft, pointerRight=$pointerRight, bodyWidth=$bodyWidth, bodyRight=$bodyRight")
            if (gap < threshold) {
                pointerRight - bodyWidth
            } else {
                clampedLeft
            }
        }
        else -> {
            android.util.Log.d("SwipeBubble", "Center: idealBodyLeft=$idealBodyLeft, pointerLeft=$pointerLeft, pointerRight=$pointerRight, bodyWidth=$bodyWidth")
            idealBodyLeft
        }
    }
    val bodyRight = bodyLeft + bodyWidth
    
    val boxLeft = minOf(bodyLeft, pointerLeft)
    val boxRight = maxOf(bodyRight, pointerRight)
    val boxWidth = boxRight - boxLeft
    
    val bodyLeftInBox = bodyLeft - boxLeft
    val pointerLeftInBox = pointerLeft - boxLeft
    
    val boxTop = keyBounds.top - bodyHeight
    
    return BubbleLayoutInfo(
        boxLeft = boxLeft,
        boxTop = boxTop,
        boxWidth = boxWidth,
        bodyLeftInBox = bodyLeftInBox,
        pointerLeftInBox = pointerLeftInBox
    )
}

@Composable
fun SwipeBubble(
    swipeState: SwipeState,
    keyBounds: Rect,
    isDarkTheme: Boolean,
    keyWidth: Float,
    keyboardWidth: Float,
    modifier: Modifier = Modifier
) {
    val shouldShowBubble = swipeState.isSwiping || swipeState.isPressed
    val displayText = if (swipeState.isPressed) {
        swipeState.pressedText
    } else {
        swipeState.swipeText
    }
    
    if (!shouldShowBubble || displayText == null) {
        return
    }
    
    android.util.Log.d("SwipeBubble", "SwipeBubble called: keyBounds=$keyBounds, keyboardWidth=$keyboardWidth")
    
    val bubbleBgColor = if (isDarkTheme) Color(0xFF45474A) else Color(0xFFF0F1F2)
    val bubbleTextColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF202124)
    
    var actualBodyWidth by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val bodyHeightPx = with(density) { BubbleBodyHeight.toPx() }
    val pointerHeightPx = with(density) { BubblePointerHeight.toPx() }
    val cornerRadiusPx = with(density) { BubbleCornerRadius.toPx() }
    val thresholdPx = with(density) { 5.dp.toPx() }
    val screenMarginPx = with(density) { BubbleScreenMargin.toPx() }
    
    val layoutInfo = remember(actualBodyWidth, keyBounds, keyboardWidth) {
        android.util.Log.d("SwipeBubble", "calculateBubbleLayout: actualBodyWidth=$actualBodyWidth, keyBounds=$keyBounds, keyboardWidth=$keyboardWidth")
        if (actualBodyWidth > 0 && keyboardWidth > 0) {
            calculateBubbleLayout(
                keyBounds = keyBounds,
                bodyWidth = actualBodyWidth,
                bodyHeight = bodyHeightPx,
                pointerHeight = pointerHeightPx,
                keyboardWidth = keyboardWidth,
                thresholdPx = thresholdPx,
                screenMarginPx = screenMarginPx
            )
        } else {
            BubbleLayoutInfo(0f, 0f, 0f, 0f, 0f)
        }
    }
    
    val context = LocalContext.current
    val keyWidthPx = keyWidth
    val totalHeight = BubbleBodyHeight + BubblePointerHeight
    
    val chaiFontFamily = remember {
        FontFamily(
            androidx.compose.ui.text.font.Typeface(android.graphics.Typeface.createFromAsset(context.assets, "ChaiPUA-0.2.7-snow.ttf"))
        )
    }
    
    Box(
        modifier = modifier
            .offset { IntOffset(layoutInfo.boxLeft.roundToInt(), layoutInfo.boxTop.roundToInt()) }
            .onGloballyPositioned { coordinates ->
                actualBodyWidth = coordinates.size.width.toFloat()
            }
            .wrapContentWidth(unbounded = true, align = Alignment.Start)
            .height(totalHeight)
            .shadow(4.dp, RoundedCornerShape(BubbleCornerRadius), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .drawBehind {
                if (actualBodyWidth > 0) {
                    val bodyLeft = layoutInfo.bodyLeftInBox
                    val pointerLeft = layoutInfo.pointerLeftInBox
                    val leftGap = bodyLeft - pointerLeft
                    val rightGap = (pointerLeft + keyWidthPx) - (bodyLeft + actualBodyWidth)
                    
                    android.util.Log.d("SwipeBubble", "bodyLeft=$bodyLeft, pointerLeft=$pointerLeft, leftGap=$leftGap, rightGap=$rightGap, radius=$cornerRadiusPx")
                    
                    drawBubbleShape(
                        bodyLeft = layoutInfo.bodyLeftInBox,
                        bodyWidth = actualBodyWidth,
                        bodyHeight = bodyHeightPx,
                        pointerLeft = layoutInfo.pointerLeftInBox,
                        pointerWidth = keyWidthPx,
                        pointerHeight = pointerHeightPx,
                        cornerRadius = cornerRadiusPx,
                        color = bubbleBgColor
                    )
                }
            }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .height(BubbleBodyHeight)
                    .defaultMinSize(minWidth = 48.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText,
                    color = bubbleTextColor,
                    fontSize = 14.sp,
                    fontFamily = chaiFontFamily,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(BubblePointerHeight))
        }
    }
}