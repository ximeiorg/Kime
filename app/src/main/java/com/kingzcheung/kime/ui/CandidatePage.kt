package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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

@Composable
fun CandidatePage(
    candidates: List<String>,
    candidateComments: List<String> = emptyList(),
    inputText: String,
    onCandidateSelect: (Int) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        if (inputText.isNotEmpty()) {
            Text(
                text = "编码: $inputText",
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(candidates) { index, candidate ->
                CandidatePageItem(
                    text = candidate,
                    comment = candidateComments.getOrElse(index) { "" },
                    onClick = { onCandidateSelect(index) },
                    textColor = textColor
                )
            }
        }
    }
}

@Composable
fun CandidatePageItem(
    text: String,
    comment: String = "",
    onClick: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val displayComment = comment.replace("~", "")
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(textColor.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 8.dp)
        )
        if (displayComment.isNotEmpty()) {
            Text(
                text = displayComment,
                color = textColor.copy(alpha = 0.5f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = (-4).dp)
            )
        }
    }
}