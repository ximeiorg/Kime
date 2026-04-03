package com.kingzcheung.kime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
    val radius = cornerRadius.coerceAtMost(bodyWidth / 2f).coerceAtMost(bodyHeight / 2f)
    val pointerRadius = cornerRadius.coerceAtMost(pointerWidth / 2f)
    
    val bodyRight = bodyLeft + bodyWidth
    val pointerRight = pointerLeft + pointerWidth
    val bodyBottom = bodyHeight
    val pointerTop = bodyBottom
    val pointerBottom = bodyBottom + pointerHeight
    
    // 从主体左上角开始（圆角后）
    path.moveTo(bodyLeft + radius, 0f)
    
    // 主体上边 + 左上圆角
    path.lineTo(bodyRight - radius, 0f)
    path.quadraticBezierTo(bodyRight, 0f, bodyRight, radius)
    
    // 主体右边
    path.lineTo(bodyRight, bodyBottom - radius)
    
    // 主体右下角到pointer衔接处的贝塞尔曲线
    // 如果主体右边超出pointer右边，需要衔接
    if (bodyRight > pointerRight) {
        // 主体右下圆角
        path.quadraticBezierTo(bodyRight, bodyBottom, bodyRight - radius, bodyBottom)
        // 水平连接到pointer右边上方
        path.lineTo(pointerRight + pointerRadius, bodyBottom)
        // pointer右上圆角
        path.quadraticBezierTo(pointerRight, bodyBottom, pointerRight, bodyBottom + pointerRadius)
    } else {
        // 直接用贝塞尔曲线从主体右下角连接到pointer右上角
        path.quadraticBezierTo(
            bodyRight, bodyBottom,
            pointerRight, pointerTop + pointerRadius
        )
    }
    
    // pointer右边
    path.lineTo(pointerRight, pointerBottom - pointerRadius)
    
    // pointer右下圆角
    path.quadraticBezierTo(pointerRight, pointerBottom, pointerRight - pointerRadius, pointerBottom)
    
    // pointer下边
    path.lineTo(pointerLeft + pointerRadius, pointerBottom)
    
    // pointer左下圆角
    path.quadraticBezierTo(pointerLeft, pointerBottom, pointerLeft, pointerBottom - pointerRadius)
    
    // pointer左边
    path.lineTo(pointerLeft, bodyBottom + pointerRadius)
    
    // 主体左边到pointer衔接处的贝塞尔曲线
    if (bodyLeft < pointerLeft) {
        // pointer左上圆角
        path.quadraticBezierTo(pointerLeft, bodyBottom, pointerLeft - pointerRadius, bodyBottom)
        // 水平连接到主体左边
        path.lineTo(bodyLeft + radius, bodyBottom)
        // 主体左下圆角
        path.quadraticBezierTo(bodyLeft, bodyBottom, bodyLeft, bodyBottom - radius)
    } else {
        // 直接用贝塞尔曲线从pointer左上角连接到主体左下角
        path.quadraticBezierTo(
            pointerLeft, bodyBottom,
            bodyLeft, bodyBottom - radius
        )
    }
    
    // 主体左边
    path.lineTo(bodyLeft, radius)
    
    // 主体左上圆角
    path.quadraticBezierTo(bodyLeft, 0f, bodyLeft + radius, 0f)
    
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
    keyboardWidth: Float
): BubbleLayoutInfo {
    val pointerWidth = keyBounds.width
    val pointerLeft = keyBounds.left
    val pointerCenterX = pointerLeft + pointerWidth / 2
    
    val idealBodyLeft = pointerCenterX - bodyWidth / 2
    val clampedBodyLeft = idealBodyLeft.coerceIn(0f, keyboardWidth - bodyWidth)
    
    val bodyLeft = clampedBodyLeft
    val bodyRight = bodyLeft + bodyWidth
    val pointerRight = pointerLeft + pointerWidth
    
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
    if (!swipeState.isSwiping || swipeState.swipeText == null) {
        return
    }
    
    val currentSwipeText = swipeState.swipeText
    val bubbleBgColor = if (isDarkTheme) Color(0xFF45474A) else Color(0xFFF0F1F2)
    val bubbleTextColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF202124)
    
    var actualBodyWidth by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val bodyHeightPx = with(density) { BubbleBodyHeight.toPx() }
    val pointerHeightPx = with(density) { BubblePointerHeight.toPx() }
    val cornerRadiusPx = with(density) { BubbleCornerRadius.toPx() }
    
    val layoutInfo = remember(actualBodyWidth, keyBounds, keyboardWidth) {
        if (actualBodyWidth > 0 && keyboardWidth > 0) {
            calculateBubbleLayout(
                keyBounds = keyBounds,
                bodyWidth = actualBodyWidth,
                bodyHeight = bodyHeightPx,
                pointerHeight = pointerHeightPx,
                keyboardWidth = keyboardWidth
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentSwipeText,
                    color = bubbleTextColor,
                    fontSize = 13.sp,
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