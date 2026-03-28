package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kingzcheung.kime.ui.theme.CandidateBarBackground
import com.kingzcheung.kime.ui.theme.CandidateBarBackgroundDark
import com.kingzcheung.kime.ui.theme.CandidateTextColor
import com.kingzcheung.kime.ui.theme.CandidateTextColorDark
import com.kingzcheung.kime.ui.theme.DividerColor
import com.kingzcheung.kime.ui.theme.DividerColorDark
import com.kingzcheung.kime.ui.theme.KeyBackground
import com.kingzcheung.kime.ui.theme.KeyBackgroundDark
import com.kingzcheung.kime.ui.theme.KeyTextColor
import com.kingzcheung.kime.ui.theme.KeyTextColorDark
import com.kingzcheung.kime.ui.theme.SpecialKeyBackground
import com.kingzcheung.kime.ui.theme.SpecialKeyBackgroundDark

/**
 * 键盘主视图
 * 包含候选栏和键盘布局
 * 
 * @param candidates 候选词列表
 * @param inputText 当前输入的编码（如五笔编码）
 * @param isComposing 是否正在组合输入
 * @param onKeyPress 按键回调
 * @param onCandidateSelect 候选词选择回调（传递索引）
 */
@Composable
fun KeyboardView(
    candidates: Array<String> = emptyArray(),
    inputText: String = "",
    isComposing: Boolean = false,
    isAsciiMode: Boolean = false,
    onKeyPress: (String, Boolean) -> Unit,
    onCandidateSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isShifted by remember { mutableStateOf(false) }
    var isDarkTheme by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.FULL) }
    
    val keyBgColor = if (isDarkTheme) KeyBackgroundDark else KeyBackground
    val keyTextColor = if (isDarkTheme) KeyTextColorDark else KeyTextColor
    val specialKeyBgColor = if (isDarkTheme) SpecialKeyBackgroundDark else SpecialKeyBackground
    val candidateBarBg = if (isDarkTheme) CandidateBarBackgroundDark else CandidateBarBackground
    val candidateTextColor = if (isDarkTheme) CandidateTextColorDark else CandidateTextColor
    val dividerColor = if (isDarkTheme) DividerColorDark else DividerColor

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 候选栏（包含输入编码显示）
        CandidateBar(
            candidates = candidates.toList(),
            inputText = inputText,
            isComposing = isComposing,
            onCandidateSelect = onCandidateSelect,
            backgroundColor = candidateBarBg,
            textColor = candidateTextColor,
            dividerColor = dividerColor
        )
        
        // 根据模式显示不同键盘
        when (keyboardMode) {
            KeyboardMode.FULL -> {
                // 全键盘布局
                KeyboardLayout(
                    onKeyPress = { key ->
                        when (key) {
                            "shift" -> isShifted = !isShifted
                            "mode_change" -> keyboardMode = KeyboardMode.NUMBER
                            else -> onKeyPress(key, isShifted)
                        }
                    },
                    isShifted = isShifted,
                    isAsciiMode = isAsciiMode,
                    keyBackgroundColor = keyBgColor,
                    keyTextColor = keyTextColor,
                    specialKeyBackgroundColor = specialKeyBgColor
                )
            }
            KeyboardMode.NUMBER -> {
                // 九宫格数字键盘布局
                NumberKeyboardLayout(
                    onKeyPress = { key ->
                        when (key) {
                            "abc" -> keyboardMode = KeyboardMode.FULL
                            "symbol" -> keyboardMode = KeyboardMode.SYMBOL
                            else -> onKeyPress(key, false)
                        }
                    },
                    keyBackgroundColor = keyBgColor,
                    keyTextColor = keyTextColor,
                    specialKeyBackgroundColor = specialKeyBgColor
                )
            }
            KeyboardMode.SYMBOL -> {
                // 符号键盘布局
                SymbolKeyboardLayout(
                    onKeyPress = { key ->
                        when (key) {
                            "abc" -> keyboardMode = KeyboardMode.FULL
                            "123" -> keyboardMode = KeyboardMode.NUMBER
                            else -> onKeyPress(key, false)
                        }
                    },
                    keyBackgroundColor = keyBgColor,
                    keyTextColor = keyTextColor,
                    specialKeyBackgroundColor = specialKeyBgColor
                )
            }
        }
    }
}