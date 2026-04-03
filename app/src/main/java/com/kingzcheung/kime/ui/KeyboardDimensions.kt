package com.kingzcheung.kime.ui

import androidx.compose.ui.unit.dp

/**
 * 键盘统一尺寸配置
 * 所有按键、气泡组件共享这些尺寸，确保视觉一致性
 */
object KeyboardDimensions {
    // 按键尺寸
    val KeyHeight = 44.dp
    val KeyCornerRadius = 4.dp
    
    // 气泡尺寸
    val BubbleSize = 48.dp  // 上滑气泡主体
    val BubbleWidthDown = 160.dp  // 下滑气泡宽度
    val BubbleHeightDown = 36.dp  // 下滑气泡主体高度
    val BubbleCornerRadius = 6.dp
    
    // 气泡底部pointer（覆盖按键的部分）
    // 注意：按键实际宽度由weight决定，这里使用估算值
    // 10键布局中，每个按键约占屏幕宽度的1/10减去间距
    val BubblePointerHeight = KeyHeight  // 与按键同高，确保完全覆盖
    
    // 间距
    val KeySpacing = 4.dp
    val RowSpacing = 6.dp
    val KeyboardPaddingHorizontal = 4.dp
    val KeyboardPaddingVertical = 8.dp
}