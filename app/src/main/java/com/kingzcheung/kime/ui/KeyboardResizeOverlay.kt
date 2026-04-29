package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun KeyboardResizeOverlay(
    initialHeightDp: Int,
    initialBottomPaddingDp: Int,
    defaultHeightDp: Int,
    defaultBottomPaddingDp: Int,
    maxContainerHeightDp: Int,
    onHeightChange: (Int) -> Unit,
    onBottomPaddingChange: (Int) -> Unit,
    onStretchChange: (Float) -> Unit,
    onReset: (Int, Int) -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val minKeyboardHeightDp = defaultHeightDp
    val maxKeyboardHeightDp = screenHeightDp / 2
    val minBottomPaddingDp = 0
    val maxBottomPaddingDp = 100

    val safeDefaultHeightDp = defaultHeightDp.coerceIn(minKeyboardHeightDp, maxKeyboardHeightDp)
    val safeDefaultBottomPaddingDp = defaultBottomPaddingDp.coerceIn(minBottomPaddingDp, maxBottomPaddingDp)
    val safeInitialHeightDp = initialHeightDp.coerceIn(minKeyboardHeightDp, maxKeyboardHeightDp)
    val safeInitialBottomPaddingDp = initialBottomPaddingDp.coerceIn(minBottomPaddingDp, maxBottomPaddingDp)

    var currentHeightDp by remember { mutableFloatStateOf(safeInitialHeightDp.toFloat()) }
    var currentBottomPaddingDp by remember { mutableFloatStateOf(safeInitialBottomPaddingDp.toFloat()) }
    var baseHeightDp by remember { mutableFloatStateOf(safeInitialHeightDp.toFloat()) }

    Box(
        modifier = modifier
            .height(maxContainerHeightDp.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height((currentHeightDp + currentBottomPaddingDp).roundToInt().dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val paddingChangeDp = with(density) { -dragAmount.y.toDp().value }
                            currentBottomPaddingDp = (currentBottomPaddingDp + paddingChangeDp)
                                .coerceIn(minBottomPaddingDp.toFloat(), maxBottomPaddingDp.toFloat())
                            onBottomPaddingChange(currentBottomPaddingDp.roundToInt())
                        },
                        onDragEnd = { }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val heightChangeDp = with(density) { -dragAmount.y.toDp().value }
                                currentHeightDp = (currentHeightDp + heightChangeDp)
                                    .coerceIn(minKeyboardHeightDp.toFloat(), maxKeyboardHeightDp.toFloat())
                                onHeightChange(currentHeightDp.roundToInt())
                                if (baseHeightDp > 0) {
                                    val stretchFactor = currentHeightDp / baseHeightDp
                                    onStretchChange(stretchFactor)
                                }
                            },
                            onDragEnd = { }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                IconButton(
                    onClick = {
                        currentHeightDp = safeDefaultHeightDp.toFloat()
                        currentBottomPaddingDp = safeDefaultBottomPaddingDp.toFloat()
                        baseHeightDp = safeDefaultHeightDp.toFloat()
                        onReset(safeDefaultHeightDp, safeDefaultBottomPaddingDp)
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "重置",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "取消",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { onConfirm(currentHeightDp.roundToInt(), currentBottomPaddingDp.roundToInt()) },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "确定",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "高度: ${currentHeightDp.roundToInt()}dp | 底部: ${currentBottomPaddingDp.roundToInt()}dp",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}