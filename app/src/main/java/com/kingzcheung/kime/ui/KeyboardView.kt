package com.kingzcheung.kime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

@Composable
fun KeyboardView(
    candidates: Array<String> = emptyArray(),
    inputText: String = "",
    isComposing: Boolean = false,
    isAsciiMode: Boolean = false,
    isDarkTheme: Boolean = false,
    onKeyPress: (String, Boolean) -> Unit,
    onCandidateSelect: (Int) -> Unit,
    onToggleDarkMode: (() -> Unit)? = null,
    onClipboard: (() -> Unit)? = null,
    onQuickSend: (() -> Unit)? = null,
    onHandwriting: (() -> Unit)? = null,
    onEmoji: (() -> Unit)? = null,
    onReloadConfig: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onMixedInput: (() -> Unit)? = null,
    onHideKeyboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isShifted by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.FULL) }
    var showMenu by remember { mutableStateOf(false) }
    var showCandidatePage by remember { mutableStateOf(false) }
    
    val keyBgColor = if (isDarkTheme) KeyBackgroundDark else KeyBackground
    val keyTextColor = if (isDarkTheme) KeyTextColorDark else KeyTextColor
    val specialKeyBgColor = if (isDarkTheme) SpecialKeyBackgroundDark else SpecialKeyBackground
    val candidateBarBg = if (isDarkTheme) CandidateBarBackgroundDark else CandidateBarBackground
    val candidateTextColor = if (isDarkTheme) CandidateTextColorDark else CandidateTextColor
    val dividerColor = if (isDarkTheme) DividerColorDark else DividerColor

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            CandidateBar(
                candidates = candidates.toList(),
                inputText = inputText,
                isComposing = isComposing,
                onCandidateSelect = onCandidateSelect,
                backgroundColor = candidateBarBg,
                textColor = candidateTextColor,
                dividerColor = dividerColor,
                onToggleDarkMode = onToggleDarkMode,
                onLogoClick = { showMenu = true },
                showMenu = showMenu,
                onDismissMenu = { showMenu = false },
                onHideKeyboard = onHideKeyboard,
                onShowMoreCandidates = { showCandidatePage = true }
            )
            
            // 显示菜单、候选词页面或键盘
            when {
                showMenu -> {
                    MenuBar(
                        isVisible = true,
                        isDarkTheme = isDarkTheme,
                        onDismiss = { showMenu = false },
                        onClipboard = { onClipboard?.invoke(); showMenu = false },
                        onQuickSend = { onQuickSend?.invoke(); showMenu = false },
                        onHandwriting = { onHandwriting?.invoke(); showMenu = false },
                        onEmoji = { onEmoji?.invoke(); showMenu = false },
                        onReloadConfig = { onReloadConfig?.invoke(); showMenu = false },
                        onSettings = { onSettings?.invoke(); showMenu = false },
                        onMixedInput = { onMixedInput?.invoke(); showMenu = false },
                        onToggleDarkMode = { onToggleDarkMode?.invoke() },
                        modifier = Modifier.weight(1f)
                    )
                }
                showCandidatePage -> {
                    CandidatePage(
                        candidates = candidates.toList(),
                        inputText = inputText,
                        onCandidateSelect = { index ->
                            onCandidateSelect(index)
                            showCandidatePage = false
                        },
                        backgroundColor = candidateBarBg,
                        textColor = candidateTextColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    when (keyboardMode) {
                        KeyboardMode.FULL -> {
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
                                isDarkTheme = isDarkTheme,
                                keyBackgroundColor = keyBgColor,
                                keyTextColor = keyTextColor,
                                specialKeyBackgroundColor = specialKeyBgColor
                            )
                        }
                        KeyboardMode.NUMBER -> {
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
        }
    }
}