package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MenuItem(
    val icon: Painter,
    val label: String,
    val action: () -> Unit
)

@Composable
fun MenuBar(
    isVisible: Boolean,
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onClipboard: () -> Unit,
    onQuickSend: () -> Unit,
    onManageDict: () -> Unit,
    onEmoji: () -> Unit,
    onReloadConfig: () -> Unit,
    onSettings: () -> Unit,
    onMixedInput: () -> Unit,
    onToggleDarkMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    val bgColor = if (isDarkTheme) Color(0xFF35363A) else Color(0xFFF0F1F2)
    val textColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF202124)
    val itemBgColor = if (isDarkTheme) Color(0xFF45474A) else Color.White
    
    val menuItems = listOf(
        MenuItem(rememberVectorPainter(Icons.Default.ContentPaste), "剪贴板", onClipboard),
        MenuItem(rememberVectorPainter(Icons.Default.Bolt), "快捷发送", onQuickSend),
        MenuItem(rememberVectorPainter(Icons.Default.MenuBook), "管理词库", onManageDict),
        MenuItem(rememberVectorPainter(Icons.Default.EmojiEmotions), "表情", onEmoji),
        MenuItem(rememberVectorPainter(if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode), if (isDarkTheme) "浅色模式" else "深色模式", onToggleDarkMode),
        MenuItem(rememberVectorPainter(Icons.Default.Refresh), "重载配置", onReloadConfig),
        MenuItem(rememberVectorPainter(Icons.Default.Settings), "设置", onSettings),
        MenuItem(rememberVectorPainter(Icons.Default.Keyboard), "混输", onMixedInput)
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            menuItems.take(4).forEach { item ->
                MenuItemButton(
                    item = item,
                    bgColor = itemBgColor,
                    textColor = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            menuItems.drop(4).forEach { item ->
                MenuItemButton(
                    item = item,
                    bgColor = itemBgColor,
                    textColor = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MenuItemButton(
    item: MenuItem,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { item.action() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = item.icon,
            contentDescription = item.label,
            tint = textColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}