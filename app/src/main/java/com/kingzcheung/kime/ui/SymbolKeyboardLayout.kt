package com.kingzcheung.kime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

@Composable
fun SymbolKeyboardLayout(
    onKeyPress: (String) -> Unit,
    keyBackgroundColor: Color,
    keyTextColor: Color,
    specialKeyBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val symbolPages = listOf(
        listOf(
            listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
            listOf("-", "+", "=", "_", "|", "\\", "/", "~", "`", "'"),
            listOf(":", ";", "\"", "<", ">", ",", ".", "?", "[", "]")
        ),
        listOf(
            listOf("{", "}", "©", "®", "™", "°", "·", "…", "×", "÷"),
            listOf("€", "£", "¥", "¢", "§", "¶", "※", "☆", "★", "○"),
            listOf("●", "■", "□", "▲", "△", "▼", "▽", "◆", "◇", "○")
        ),
        listOf(
            listOf("①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"),
            listOf("─", "│", "┌", "┐", "└", "┘", "├", "┤", "┬", "┴"),
            listOf("【", "】", "《", "》", "「", "」", "『", "』", "〔", "〕")
        )
    )
    
    var currentPageIndex by remember { mutableStateOf(0) }
    val currentPage = symbolPages.getOrElse(currentPageIndex) { symbolPages[0] }
    val hasMorePages = symbolPages.size > currentPageIndex + 1
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        currentPage.forEach { symbolRow ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                symbolRow.forEach { symbol ->
                    KeyButton(
                        text = symbol,
                        onClick = { onKeyPress(symbol) },
                        backgroundColor = keyBackgroundColor,
                        textColor = keyTextColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
IconKeyButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
            onClick = { onKeyPress("abc") },
            backgroundColor = specialKeyBackgroundColor,
            iconColor = keyTextColor,
            modifier = Modifier.weight(1.2f)
        )
            
            KeyButton(
                text = "123",
                onClick = { onKeyPress("123") },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1.2f)
            )
            
            KeyButton(
                text = "◀",
                onClick = {
                    if (currentPageIndex > 0) {
                        currentPageIndex--
                    } else {
                        currentPageIndex = symbolPages.size - 1
                    }
                },
                backgroundColor = specialKeyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1f)
            )
            
            KeyButton(
                text = "${currentPageIndex + 1}/${symbolPages.size}",
                onClick = { },
                backgroundColor = keyBackgroundColor,
                textColor = keyTextColor,
                modifier = Modifier.weight(1.5f)
            )
            
            KeyButton(
                text = "▶",
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
            
SwipeableIconKeyButton(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
            onClick = { onKeyPress("delete") },
            backgroundColor = specialKeyBackgroundColor,
            iconColor = keyTextColor,
            modifier = Modifier.weight(1.2f),
            swipeText = "清空",
            onSwipe = { onKeyPress("clear_composition") },
            onLongClick = { onKeyPress("delete") }
        )
        }
    }
}