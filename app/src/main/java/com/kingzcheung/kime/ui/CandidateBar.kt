package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.kime.R

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
    associationCandidates: List<String> = emptyList(),
    onAssociationSelect: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayCandidates = candidates.take(5)
    val hasMoreCandidates = candidates.size >= 5
    val displayAssociation = associationCandidates.take(5)
    val hasMoreAssociation = associationCandidates.size >= 5
    val hasAnyMore = hasMoreCandidates || hasMoreAssociation
    
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
                val inputTextInteractionSource = remember { MutableInteractionSource() }
                val isInputTextPressed by inputTextInteractionSource.collectIsPressedAsState()
                
                Text(
                    text = inputText,
                    color = textColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isInputTextPressed && onInputTextClick != null) 
                                (if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f))
                            else 
                                Color.Transparent
                        )
                        .clickable(
                            enabled = onInputTextClick != null,
                            interactionSource = inputTextInteractionSource,
                            indication = null
                        ) { 
                            onInputTextClick?.invoke() 
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(dividerColor)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(displayCandidates) { index, candidate ->
                    CandidateItem(
                        text = candidate,
                        index = index,
                        onClick = { onCandidateSelect(index) },
                        textColor = textColor,
                        comment = candidateComments.getOrElse(index) { "" }
                    )
                }
                
                if (displayAssociation.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(dividerColor.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp)
                        )
                    }
                    
                    itemsIndexed(displayAssociation) { index, candidate ->
                        CandidateItem(
                            text = candidate,
                            index = -1,
                            onClick = { onAssociationSelect?.invoke(index) },
                            textColor = textColor,
                            comment = ""
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
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
                if (hasAnyMore && onShowMoreCandidates != null) {
                    val moreInteractionSource = remember { MutableInteractionSource() }
                    val isMorePressed by moreInteractionSource.collectIsPressedAsState()
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isMorePressed) (if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = moreInteractionSource,
                                indication = null,
                                onClick = { onShowMoreCandidates() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "更多",
                            color = if (isMorePressed) textColor.copy(alpha = 0.6f) else textColor,
                            fontSize = 11.sp
                        )
                    }
                }
                
                if (onHideKeyboard != null) {
                    val hideKeyboardInteractionSource = remember { MutableInteractionSource() }
                    val isHideKeyboardPressed by hideKeyboardInteractionSource.collectIsPressedAsState()
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isHideKeyboardPressed) (if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = hideKeyboardInteractionSource,
                                indication = null,
                                onClick = { onHideKeyboard() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "收起键盘",
                            tint = if (isHideKeyboardPressed) textColor.copy(alpha = 0.6f) else textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
            .padding(horizontal = 4.dp, vertical = 2.dp),
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