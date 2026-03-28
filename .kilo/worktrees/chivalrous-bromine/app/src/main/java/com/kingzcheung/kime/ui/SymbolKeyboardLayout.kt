package com.kingzcheung.kime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 符号键盘布局
 * 显示常用符号，支持翻页
 */
@Composable
fun SymbolKeyboardLayout(
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    // 符号列表，分页显示
    val symbolPages = listOf(
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
        listOf("-", "+", "=", "_", "|", "\\", "/", "~", "`", "'"),
        listOf(":", ";", "\"", "<", ">", ",", ".", "?", "[", "]"),
        listOf("{", "}", "©", "®", "™", "°", "·", "…", "×", "÷")
    )
    var currentPageIndex by remember { mutableStateOf(0) }
    val currentSymbols = symbolPages.getOrElse(currentPageIndex) { symbolPages[0] }
    val hasMorePages = symbolPages.size > currentPageIndex + 1
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 第一行：10个符号
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            currentSymbols.take(10).forEach { symbol ->
                KeyButton(
                    text = symbol,
                    onClick = { onKeyPress(symbol) },
                    backgroundColor = keyBackgroundColor,
                    textColor = keyTextColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // 第二行：ABC | 123 | 翻页 | 空格 | 确定
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 返回字母键盘
            KeyButton(
                text = "ABC",
                onClick = { onKeyPress("abc") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f)
            )
            
            // 返回数字键盘
            KeyButton(
                text = "123",
                onClick = { onKeyPress("123") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f)
            )
            
            // 翻页按钮
            KeyButton(
                text = if (hasMorePages) "下一页" else "第一页",
                onClick = {
                    if (hasMorePages) {
                        currentPageIndex++
                    } else {
                        currentPageIndex = 0
                    }
                },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f)
            )
            
            // 空格
            KeyButton(
                text = "空格",
                onClick = { onKeyPress("space") },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f)
            )
            
            // 确定
            KeyButton(
                text = "确定",
                onClick = { onKeyPress("enter") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}