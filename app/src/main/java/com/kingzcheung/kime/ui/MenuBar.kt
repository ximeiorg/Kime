package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class MenuItem(
    val icon: String,
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
    onHandwriting: () -> Unit,
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
        MenuItem("📋", "剪贴板", onClipboard),
        MenuItem("⚡", "快捷发送", onQuickSend),
        MenuItem("✍", "手写找字", onHandwriting),
        MenuItem("😊", "表情", onEmoji),
        MenuItem("🌓", if (isDarkTheme) "浅色模式" else "深色模式", onToggleDarkMode),
        MenuItem("🔄", "重载配置", onReloadConfig),
        MenuItem("⚙", "设置", onSettings),
        MenuItem("混", "混输", onMixedInput)
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable { item.action() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = item.icon,
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.label,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}