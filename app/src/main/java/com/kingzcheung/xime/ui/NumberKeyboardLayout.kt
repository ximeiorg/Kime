package com.kingzcheung.xime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * 九宫格数字键盘布局
 * 第1行：符号 | 1 | 2 | 3 | 退格
 * 第2行：符号 | 4 | 5 | 6 | 符号切换
 * 第3行：符号 | 7 | 8 | 9 | 表情
 * 第4行：ABC | / | 0 | . | 确定
 */
@Composable
fun NumberKeyboardLayout(
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    keyboardBackgroundColor: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    onKeyPressDown: ((String) -> Unit)? = null
) {
    val symbols = listOf("@", "+", "-")
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val commonSymbols = listOf(
        "~", "!", "#", "$", "%", "^",
        "&", "*", "(", ")", "_", "=",
        "[", "]", "{", "}", "\\", "|",
        ";", ":", "'", "\"", "<", ">"
    )

    Box(modifier = modifier.background(keyboardBackgroundColor)) {
        if (isLandscape) {
            // 横屏：左侧常用符号区 + 右侧数字键盘
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp, horizontal = 50.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 左侧：常用符号区（6列 × 5行）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    commonSymbols.chunked(6).forEach { rowSymbols ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            rowSymbols.forEach { sym ->
                                KeyButton(
                                    text = sym,
                                    onClick = { onKeyPress(sym) },
                                    backgroundColor = keyBackgroundColor,
                                    textColor = keyTextColor,
                                    modifier = Modifier.weight(1f),
                                    onPress = { onKeyPressDown?.invoke(sym) }
                                )
                            }
                            repeat(6 - rowSymbols.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // 右侧：数字键盘（与原竖屏布局完全一致）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 第一行：符号 | 1 | 2 | 3 | 退格
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KeyButton(text = "+", onClick = { onKeyPress("+") }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke("+") })
                        listOf("1","2","3").forEach { k -> KeyButton(text = k, onClick = { onKeyPress(k) }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke(k) }) }
                        SwipeableIconKeyButton(icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace), onClick = { onKeyPress("delete") }, backgroundColor = specialKeyBackgroundColor, iconColor = keyTextColor, modifier = Modifier.weight(1f), onSwipe = { onKeyPress("clear_composition") }, onLongClick = { onKeyPress("delete") }, onPress = { onKeyPressDown?.invoke("delete") })
                    }
                    // 第二行：符号 | 4 | 5 | 6 | 空格
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KeyButton(text = "-", onClick = { onKeyPress("-") }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke("-") })
                        listOf("4","5","6").forEach { k -> KeyButton(text = k, onClick = { onKeyPress(k) }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke(k) }) }
                        KeyButton(text = "符号", onClick = { onKeyPress("symbol") }, backgroundColor = specialKeyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke("symbol") })
                    }
                    // 第三行：符号 | 7 | 8 | 9 | 表情
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KeyButton(text = "*", onClick = { onKeyPress("*") }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke("*") })
                        listOf("7","8","9").forEach { k -> KeyButton(text = k, onClick = { onKeyPress(k) }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke(k) }) }
                        IconKeyButton(icon = rememberVectorPainter(Icons.Default.EmojiEmotions), onClick = { onKeyPress("emoji") }, backgroundColor = specialKeyBackgroundColor, iconColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke("emoji") })
                    }
                    // 第四行：返回 | 符号切换 | 0 | . | 确定
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconKeyButton(icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack), onClick = { onKeyPress("abc") }, backgroundColor = specialKeyBackgroundColor, iconColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke("abc") })
                        KeyButton(text = "/", onClick = { onKeyPress("/") }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke("/") })
                        KeyButton(text = "0", onClick = { onKeyPress("0") }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1.5f), onPress = { onKeyPressDown?.invoke("0") })
                        KeyButton(text = ".", onClick = { onKeyPress(".") }, backgroundColor = keyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1f), onPress = { onKeyPressDown?.invoke(".") })
                        KeyButton(text = "确定", onClick = { onKeyPress("enter") }, backgroundColor = specialKeyBackgroundColor, textColor = keyTextColor, modifier = Modifier.weight(1.2f), onPress = { onKeyPressDown?.invoke("enter") })
                    }
                }
            }
        } else {
            // 竖屏：原有布局
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(keyboardBackgroundColor)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NumberRows(
                    onKeyPress = onKeyPress,
                    keyBackgroundColor = keyBackgroundColor,
                    keyTextColor = keyTextColor,
                    specialKeyBackgroundColor = specialKeyBackgroundColor,
                    onKeyPressDown = onKeyPressDown
                )
            }
        }
    }
}

@Composable
private fun NumberRows(
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    onKeyPressDown: ((String) -> Unit)? = null
) {
    val symbols = listOf("@", "+", "-")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第一行：符号 | 1 | 2 | 3 | 退格
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyButton(
                text = symbols[0],
                onClick = { onKeyPress(symbols[0]) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(symbols[0]) }
            )
            listOf("1", "2", "3").forEach { key ->
                KeyButton(
                    text = key,
                    onClick = { onKeyPress(key) },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(1f),
                    onPress = { onKeyPressDown?.invoke(key) }
                )
            }
            SwipeableIconKeyButton(
                icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                onClick = { onKeyPress("delete") },
                backgroundColor = specialKeyBackgroundColor,
                iconColor = keyTextColor,
                modifier = Modifier.weight(1f),
                swipeText = "清空",
                onSwipe = { onKeyPress("clear_composition") },
                onLongClick = { onKeyPress("delete") },
                onPress = { onKeyPressDown?.invoke("delete") }
            )
        }

        // 第二行：符号 | 4 | 5 | 6 | 空格
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyButton(
                text = symbols[1],
                onClick = { onKeyPress(symbols[1]) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(symbols[1]) }
            )
            listOf("4", "5", "6").forEach { key ->
                KeyButton(
                    text = key,
                    onClick = { onKeyPress(key) },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(1f),
                    onPress = { onKeyPressDown?.invoke(key) }
                )
            }
            KeyButton(
                text = "符号",
                onClick = { onKeyPress("symbol") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke("symbol") }
            )
        }

        // 第三行：符号 | 7 | 8 | 9 | 表情
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            KeyButton(
                text = symbols[2],
                onClick = { onKeyPress(symbols[2]) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(symbols[2]) }
            )
            listOf("7", "8", "9").forEach { key ->
                KeyButton(
                    text = key,
                    onClick = { onKeyPress(key) },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(1f),
                    onPress = { onKeyPressDown?.invoke(key) }
                )
            }
            IconKeyButton(
                icon = rememberVectorPainter(Icons.Default.EmojiEmotions),
                onClick = { onKeyPress("emoji") },
                backgroundColor = specialKeyBackgroundColor,
                iconColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke("emoji") }
            )
        }

        // 第四行：返回 | 符号切换 | 0 | . | 确定
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconKeyButton(
                icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                onClick = { onKeyPress("abc") },
                backgroundColor = specialKeyBackgroundColor,
                iconColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke("abc") }
            )
            KeyButton(
                text = "/",
                onClick = { onKeyPress("/") },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke("/") }
            )
            KeyButton(
                text = "0",
                onClick = { onKeyPress("0") },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1.5f),
                onPress = { onKeyPressDown?.invoke("0") }
            )
            KeyButton(
                text = ".",
                onClick = { onKeyPress(".") },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(".") }
            )
            KeyButton(
                text = "确定",
                onClick = { onKeyPress("enter") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1.2f),
                onPress = { onKeyPressDown?.invoke("enter") }
            )
        }
    }
}