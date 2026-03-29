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
import com.kingzcheung.kime.clipboard.ClipboardItem
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
import com.kingzcheung.kime.ui.theme.KeyboardThemes

@Composable
fun KeyboardView(
    candidates: Array<String> = emptyArray(),
    candidateComments: Array<String> = emptyArray(),
    inputText: String = "",
    isComposing: Boolean = false,
    isAsciiMode: Boolean = false,
    schemaName: String = "",
    enterKeyText: String = "发送",
    isDarkTheme: Boolean = false,
    themeId: String = "ocean_blue",
    showBottomButtons: Boolean = false,
    clipboardItems: List<ClipboardItem> = emptyList(),
    quickSendItems: List<ClipboardItem> = emptyList(),
    onKeyPress: (String, Boolean) -> Unit,
    onCandidateSelect: (Int) -> Unit,
    onToggleDarkMode: (() -> Unit)? = null,
    onClipboard: (() -> Unit)? = null,
    onClipboardSelect: ((String) -> Unit)? = null,
    onClipboardRemove: ((Long) -> Unit)? = null,
    onClipboardTogglePin: ((Long) -> Unit)? = null,
    onClipboardClearAll: (() -> Unit)? = null,
    onAddToQuickSend: ((Long) -> Unit)? = null,
    onRemoveFromQuickSend: ((Long) -> Unit)? = null,
    onQuickSend: (() -> Unit)? = null,
    onHandwriting: (() -> Unit)? = null,
    onEmoji: (() -> Unit)? = null,
    onReloadConfig: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onMixedInput: (() -> Unit)? = null,
    onHideKeyboard: (() -> Unit)? = null,
    onSwitchKeyboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isShifted by remember { mutableStateOf(false) }
    var keyboardMode by remember { mutableStateOf(KeyboardMode.FULL) }
    var showMenu by remember { mutableStateOf(false) }
    var showCandidatePage by remember { mutableStateOf(false) }
    var showClipboard by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var clipboardTab by remember { mutableStateOf(0) }
    
    val keyBgColor = if (isDarkTheme) KeyBackgroundDark else KeyBackground
    val keyTextColor = if (isDarkTheme) KeyTextColorDark else KeyTextColor
    val specialKeyBgColor = KeyboardThemes.getSpecialKeyColor(themeId, isDarkTheme)
    val accentColor = KeyboardThemes.getAccentColor(themeId, isDarkTheme)
    val candidateBarBg = if (isDarkTheme) CandidateBarBackgroundDark else CandidateBarBackground
    val candidateTextColor = accentColor
    val dividerColor = if (isDarkTheme) DividerColorDark else DividerColor

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
CandidateBar(
                candidates = candidates.toList(),
                candidateComments = candidateComments.toList(),
                inputText = inputText,
                isComposing = isComposing,
                onCandidateSelect = onCandidateSelect,
                backgroundColor = candidateBarBg,
                textColor = candidateTextColor,
                dividerColor = dividerColor,
                accentColor = accentColor,
                isDarkTheme = isDarkTheme,
                showCandidatePage = showCandidatePage,
                onToggleDarkMode = onToggleDarkMode,
                onLogoClick = { showMenu = true },
                showMenu = showMenu,
                onDismissMenu = {
                    if (showClipboard) {
                        showClipboard = false
                    } else if (showCandidatePage) {
                        showCandidatePage = false
                    } else {
                        showMenu = false
                    }
                },
                onHideKeyboard = onHideKeyboard,
                onShowMoreCandidates = { showCandidatePage = true },
                showClipboardTabs = showClipboard,
                clipboardTab = clipboardTab,
                onClipboardTabChange = { clipboardTab = it },
                onInputTextClick = {
                    if (inputText.isNotEmpty()) {
                        onClipboardSelect?.invoke(inputText)
                    }
                }
            )
            
            // 显示菜单、剪切板、候选词页面或键盘
            when {
                showMenu -> {
                    MenuBar(
                        isVisible = true,
                        isDarkTheme = isDarkTheme,
                        onDismiss = { showMenu = false },
                        onClipboard = { 
                            showClipboard = true
                            clipboardTab = 0
                            showMenu = false
                            onClipboard?.invoke() 
                        },
                        onQuickSend = { 
                            showClipboard = true
                            clipboardTab = 1
                            showMenu = false
                            onQuickSend?.invoke() 
                        },
                        onHandwriting = { onHandwriting?.invoke(); showMenu = false },
                        onEmoji = { 
                            showEmoji = true
                            showMenu = false 
                        },
                        onReloadConfig = { onReloadConfig?.invoke(); showMenu = false },
                        onSettings = { onSettings?.invoke(); showMenu = false },
                        onMixedInput = { onMixedInput?.invoke(); showMenu = false },
                        onToggleDarkMode = { onToggleDarkMode?.invoke() },
                        modifier = Modifier.weight(1f)
                    )
                }
                showClipboard -> {
                    ClipboardView(
                        clipboardItems = clipboardItems,
                        quickSendItems = quickSendItems,
                        selectedTab = clipboardTab,
                        isDarkTheme = isDarkTheme,
                        onSelectItem = { text ->
                            onClipboardSelect?.invoke(text)
                            showClipboard = false
                        },
                        onRemoveItem = { id -> onClipboardRemove?.invoke(id) },
                        onTogglePin = { id -> onClipboardTogglePin?.invoke(id) },
                        onAddToQuickSend = { id -> onAddToQuickSend?.invoke(id) },
                        onRemoveFromQuickSend = { id -> onRemoveFromQuickSend?.invoke(id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                showEmoji -> {
                    EmojiKeyboardLayout(
                        onEmojiSelect = { emoji ->
                            if (emoji == "delete") {
                                onKeyPress("delete", false)
                            } else {
                                onClipboardSelect?.invoke(emoji)
                            }
                        },
                        onBack = { showEmoji = false },
                        backgroundColor = candidateBarBg,
                        textColor = keyTextColor,
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
                                schemaName = schemaName,
                                enterKeyText = enterKeyText,
                                isDarkTheme = isDarkTheme,
                                keyBackgroundColor = keyBgColor,
                                keyTextColor = keyTextColor,
                                specialKeyBackgroundColor = specialKeyBgColor,
                                showBottomButtons = showBottomButtons,
                                onHideKeyboard = onHideKeyboard,
                                onSwitchKeyboard = onSwitchKeyboard
                            )
                        }
                        KeyboardMode.NUMBER -> {
                            NumberKeyboardLayout(
                                onKeyPress = { key ->
                                    when (key) {
                                        "abc" -> keyboardMode = KeyboardMode.FULL
                                        "symbol" -> keyboardMode = KeyboardMode.SYMBOL
                                        "emoji" -> showEmoji = true
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