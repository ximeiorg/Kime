package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.kime.clipboard.ClipboardItem

@Composable
fun ClipboardView(
    clipboardItems: List<ClipboardItem>,
    quickSendItems: List<ClipboardItem>,
    selectedTab: Int,
    isDarkTheme: Boolean,
    onSelectItem: (String) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onAddToQuickSend: (Long) -> Unit,
    onRemoveFromQuickSend: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkTheme) Color(0xFF35363A) else Color(0xFFF0F1F2)
    val itemBgColor = if (isDarkTheme) Color(0xFF45474A) else Color.White
    val textColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF202124)
    val subTextColor = if (isDarkTheme) Color(0xFF9AA0A6) else Color(0xFF5F6368)
    val accentColor = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1A73E8)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
    ) {
        if (selectedTab == 0) {
            ClipboardTabContent(
                items = clipboardItems,
                itemBgColor = itemBgColor,
                textColor = textColor,
                subTextColor = subTextColor,
                accentColor = accentColor,
                onSelect = onSelectItem,
                onRemove = onRemoveItem,
                onTogglePin = onTogglePin,
                onAddToQuickSend = onAddToQuickSend
            )
        } else {
            QuickSendTabContent(
                items = quickSendItems,
                itemBgColor = itemBgColor,
                textColor = textColor,
                subTextColor = subTextColor,
                accentColor = accentColor,
                onSelect = onSelectItem,
                onRemove = onRemoveFromQuickSend
            )
        }
    }
}

@Composable
fun ClipboardTabContent(
    items: List<ClipboardItem>,
    itemBgColor: Color,
    textColor: Color,
    subTextColor: Color,
    accentColor: Color,
    onSelect: (String) -> Unit,
    onRemove: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    onAddToQuickSend: (Long) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "剪贴板为空",
                color = subTextColor,
                fontSize = 13.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(items, key = { it.id }) { item ->
                CompactClipboardItem(
                    item = item,
                    bgColor = itemBgColor,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    accentColor = accentColor,
                    onSelect = { onSelect(item.text) },
                    onRemove = { onRemove(item.id) },
                    onTogglePin = { onTogglePin(item.id) },
                    onAddToQuickSend = { onAddToQuickSend(item.id) }
                )
            }
        }
    }
}

@Composable
fun QuickSendTabContent(
    items: List<ClipboardItem>,
    itemBgColor: Color,
    textColor: Color,
    subTextColor: Color,
    accentColor: Color,
    onSelect: (String) -> Unit,
    onRemove: (Long) -> Unit
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "快捷发送为空",
                color = subTextColor,
                fontSize = 13.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            items(items, key = { it.id }) { item ->
                CompactQuickSendItem(
                    item = item,
                    bgColor = itemBgColor,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    accentColor = accentColor,
                    onSelect = { onSelect(item.text) },
                    onRemove = { onRemove(item.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactClipboardItem(
    item: ClipboardItem,
    bgColor: Color,
    textColor: Color,
    subTextColor: Color,
    accentColor: Color,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    onTogglePin: () -> Unit,
    onAddToQuickSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.text,
            color = textColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            IconButton(
                onClick = onAddToQuickSend,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.StarBorder,
                    contentDescription = "添加到快捷发送",
                    tint = subTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (item.isPinned) "取消置顶" else "置顶",
                    tint = if (item.isPinned) accentColor else subTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = subTextColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactQuickSendItem(
    item: ClipboardItem,
    bgColor: Color,
    textColor: Color,
    subTextColor: Color,
    accentColor: Color,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .clickable { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "快捷发送",
            tint = accentColor,
            modifier = Modifier
                .size(16.dp)
                .padding(horizontal = 4.dp)
        )
        
        Text(
            text = item.text,
            color = textColor,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        )
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = subTextColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}