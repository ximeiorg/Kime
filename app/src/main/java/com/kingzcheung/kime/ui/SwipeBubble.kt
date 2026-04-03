package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlin.math.roundToInt

private val BubbleSize = 50.dp
private val BubbleWidthDown = 180.dp
private val BubbleHeightDown = 40.dp
private val BubbleOffsetY = -48.dp

fun calculateBubblePosition(
    keyCenterX: Float,
    keyTop: Float,
    bubbleWidthPx: Float,
    bubbleHeightPx: Float,
    bubbleOffsetYPx: Float,
    keyboardWidth: Float,
    keyboardLeft: Float
): IntOffset {
    val bubbleLeft = keyCenterX - bubbleWidthPx / 2
    val bubbleTop = keyTop + bubbleOffsetYPx
    
    val clampedLeft = bubbleLeft.coerceIn(keyboardLeft, keyboardLeft + keyboardWidth - bubbleWidthPx)
    
    return IntOffset(clampedLeft.roundToInt(), bubbleTop.roundToInt())
}

@Composable
fun SwipeBubble(
    swipeState: SwipeState,
    bubblePosition: IntOffset,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    if (!swipeState.isSwiping || swipeState.swipeText == null) {
        return
    }
    
    val currentSwipeText = swipeState.swipeText
    val bubbleBgColor = if (isDarkTheme) Color(0xFF45474A) else Color(0xFFF0F1F2)
    val bubbleTextColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF202124)
    
    if (swipeState.isSwipeDown) {
        SwipeDownBubble(
            charInfos = swipeState.charInfos,
            text = currentSwipeText,
            bubblePosition = bubblePosition,
            bubbleBgColor = bubbleBgColor,
            bubbleTextColor = bubbleTextColor,
            modifier = modifier
        )
    } else {
        SwipeUpBubble(
            text = currentSwipeText,
            bubblePosition = bubblePosition,
            bubbleBgColor = bubbleBgColor,
            bubbleTextColor = bubbleTextColor,
            modifier = modifier
        )
    }
}

@Composable
private fun SwipeDownBubble(
    charInfos: List<CharInfo>,
    text: String,
    bubblePosition: IntOffset,
    bubbleBgColor: Color,
    bubbleTextColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasSvgChars = charInfos.any { it.hasSvg }
    
    Box(
        modifier = modifier
            .offset { bubblePosition }
            .shadow(6.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
            .background(bubbleBgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (hasSvgChars) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                charInfos.forEach { charInfo ->
                    if (charInfo.hasSvg) {
                        val svgPath = SubcharHelper.getSvgPath(charInfo.char)
                        if (svgPath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data("file:///android_asset/$svgPath")
                                    .decoderFactory(SvgDecoder.Factory())
                                    .build(),
                                contentDescription = charInfo.char,
                                modifier = Modifier.size(18.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(bubbleTextColor)
                            )
                        }
                    } else {
                        Text(
                            text = charInfo.char,
                            color = bubbleTextColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            Text(
                text = text,
                color = bubbleTextColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 3,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun SwipeUpBubble(
    text: String,
    bubblePosition: IntOffset,
    bubbleBgColor: Color,
    bubbleTextColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset { bubblePosition }
            .shadow(6.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x55000000), spotColor = Color(0x55000000))
            .background(bubbleBgColor, RoundedCornerShape(8.dp))
            .size(BubbleSize),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = bubbleTextColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun rememberBubbleSizes(): BubbleSizes {
    val density = androidx.compose.ui.platform.LocalDensity.current
    return remember(density) {
        BubbleSizes(
            bubbleSizePx = with(density) { BubbleSize.toPx() },
            bubbleWidthDownPx = with(density) { BubbleWidthDown.toPx() },
            bubbleHeightDownPx = with(density) { BubbleHeightDown.toPx() },
            bubbleOffsetYPx = with(density) { BubbleOffsetY.toPx() }
        )
    }
}

data class BubbleSizes(
    val bubbleSizePx: Float,
    val bubbleWidthDownPx: Float,
    val bubbleHeightDownPx: Float,
    val bubbleOffsetYPx: Float
)