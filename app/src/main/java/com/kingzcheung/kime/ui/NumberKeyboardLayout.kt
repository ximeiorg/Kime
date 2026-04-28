package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * 九宫格数字键盘布局
 * 第1行：符号 | 1 | 2 | 3 | 退格
 * 第2行：符号 | 4 | 5 | 6 | 空格
 * 第3行：符号 | 7 | 8 | 9 | 表情
 * 第4行：ABC | 符号切换 | 0 | . | 确定
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
    
    Box(modifier = modifier.background(keyboardBackgroundColor)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(keyboardBackgroundColor)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
        // 第一行：符号 | 1 | 2 | 3 | 退格
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 左列：符号1
            KeyButton(
                text = symbols[0],
                onClick = { onKeyPress(symbols[0]) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(symbols[0]) }
            )
            
            // 数字 1 2 3
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
            
            // 右列：退格
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
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 左列：符号2
            KeyButton(
                text = symbols[1],
                onClick = { onKeyPress(symbols[1]) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(symbols[1]) }
            )
            
            // 数字 4 5 6
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
            
            // 右列：空格
            KeyButton(
                text = "空格",
                onClick = { onKeyPress("space") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke("space") }
            )
        }
        
        // 第三行：符号 | 7 | 8 | 9 | 表情
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 左列：符号3
            KeyButton(
                text = symbols[2],
                onClick = { onKeyPress(symbols[2]) },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(symbols[2]) }
            )
            
            // 数字 7 8 9
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
            
            // 右列：表情
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
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 返回键
            IconKeyButton(
                icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                onClick = { onKeyPress("abc") },
                backgroundColor = specialKeyBackgroundColor,
                iconColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke("abc") }
            )
            
            // 符号切换按钮
            KeyButton(
                text = "符号",
                onClick = { onKeyPress("symbol") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke("symbol") }
            )
            
            // 数字 0
            KeyButton(
                text = "0",
                onClick = { onKeyPress("0") },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1.5f),
                onPress = { onKeyPressDown?.invoke("0") }
            )
            
            // 小数点
            KeyButton(
                text = ".",
                onClick = { onKeyPress(".") },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f),
                onPress = { onKeyPressDown?.invoke(".") }
            )
            
            // 确定/发送
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
}