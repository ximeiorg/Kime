package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.kime.R

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) accentColor else textColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * 候选栏组件
 * 显示输入编码和候选词列表
 */
@Composable
fun CandidateBar(
    candidates: List<String>,
    candidateComments: List<String> = emptyList(),
    inputText: String,
    isComposing: Boolean,
    onCandidateSelect: (Int) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    dividerColor: Color,
    accentColor: Color = Color(0xFF1A73E8),
    isDarkTheme: Boolean = false,
    showCandidatePage: Boolean = false,
    onToggleDarkMode: (() -> Unit)? = null,
    onLogoClick: (() -> Unit)? = null,
    showMenu: Boolean = false,
    onDismissMenu: (() -> Unit)? = null,
    onHideKeyboard: (() -> Unit)? = null,
    onShowMoreCandidates: (() -> Unit)? = null,
    showClipboardTabs: Boolean = false,
    clipboardTab: Int = 0,
    onClipboardTabChange: ((Int) -> Unit)? = null,
    onInputTextClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayCandidates = candidates.take(5)
    val hasMoreCandidates = candidates.size >= 5
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(backgroundColor)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showClipboardTabs) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDarkTheme) Color(0xFF374151) else Color(0xFFF3F4F6))
                    .clickable { onDismissMenu?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "关闭面板",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Box(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (isDarkTheme) Color(0xFF374151) else Color(0xFFF3F4F6))
                    .padding(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (clipboardTab == 0) accentColor else Color.Transparent)
                            .clickable { onClipboardTabChange?.invoke(0) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "剪贴板",
                            color = if (clipboardTab == 0) Color.White else textColor,
                            fontSize = 11.sp,
                            fontWeight = if (clipboardTab == 0) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (clipboardTab == 1) accentColor else Color.Transparent)
                            .clickable { onClipboardTabChange?.invoke(1) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "快捷发送",
                            color = if (clipboardTab == 1) Color.White else textColor,
                            fontSize = 11.sp,
                            fontWeight = if (clipboardTab == 1) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
        } else {
            if (!isComposing && inputText.isEmpty()) {
                if (showMenu && onDismissMenu != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDarkTheme) Color(0xFF374151) else Color(0xFFF3F4F6))
                            .clickable { onDismissMenu() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "关闭菜单",
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDarkTheme) Color(0xFF374151) else Color(0xFFF3F4F6))
                            .clickable { onLogoClick?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = if (isDarkTheme) R.drawable.logo_dark else R.drawable.logo),
                            contentDescription = "Kime Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
            }
            
            if (isComposing && inputText.isNotEmpty()) {
                Text(
                    text = inputText,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(enabled = onInputTextClick != null) { 
                            onInputTextClick?.invoke() 
                        }
                        .padding(end = 8.dp)
                )
                
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(dividerColor)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                displayCandidates.forEachIndexed { index, candidate ->
                    CandidateItem(
                        text = candidate,
                        index = index,
                        onClick = { onCandidateSelect(index) },
                        textColor = textColor,
                        comment = candidateComments.getOrElse(index) { "" }
                    )
                }
            }
            
Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(dividerColor)
            )
            
            if (showCandidatePage) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isDarkTheme) Color(0xFF374151) else Color(0xFFF3F4F6))
                        .clickable { onDismissMenu?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "返回键盘",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                if (hasMoreCandidates && onShowMoreCandidates != null) {
                    Text(
                        text = "更多",
                        color = accentColor,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onShowMoreCandidates() }
                            .padding(horizontal = 8.dp)
                    )
                }
                
                if (onHideKeyboard != null) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "收起键盘",
                        tint = textColor,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onHideKeyboard() }
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * 单个候选词项
 * 显示候选词和编码
 */
@Composable
fun CandidateItem(
    text: String,
    index: Int,
    onClick: () -> Unit,
    textColor: Color,
    comment: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 显示候选词
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
        // 显示编码注释
        if (comment.isNotEmpty()) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = comment,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}