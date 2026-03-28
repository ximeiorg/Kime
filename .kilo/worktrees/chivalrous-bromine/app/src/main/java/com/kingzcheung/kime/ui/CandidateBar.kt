package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 候选栏组件
 * 显示输入编码和候选词列表
 */
@Composable
fun CandidateBar(
    candidates: List<String>,
    inputText: String,
    isComposing: Boolean,
    onCandidateSelect: (Int) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    dividerColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(backgroundColor)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 显示当前输入编码（如五笔编码）
        if (isComposing && inputText.isNotEmpty()) {
            Text(
                text = inputText,
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            // 分隔线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(dividerColor)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        // 候选词横向滚动列表
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            candidates.forEachIndexed { index, candidate ->
                CandidateItem(
                    text = candidate,
                    index = index,
                    onClick = { onCandidateSelect(index) },
                    textColor = textColor
                )
            }
        }
        
        // 分隔线
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(dividerColor)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 更多候选词按钮
        Text(
            text = "更多",
            color = textColor,
            fontSize = 14.sp,
            modifier = Modifier.clickable { }
        )
    }
}

/**
 * 单个候选词项
 * 显示序号和候选词
 */
@Composable
fun CandidateItem(
    text: String,
    index: Int,
    onClick: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 显示序号（1-9，超过9显示...）
        if (index < 9) {
            Text(
                text = "${index + 1}.",
                color = textColor.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )
        }
        Spacer(modifier = Modifier.width(2.dp))
        // 显示候选词
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal
        )
    }
}