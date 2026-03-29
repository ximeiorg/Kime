package com.kingzcheung.kime.ui.theme

import androidx.compose.ui.graphics.Color

data class KeyboardColorScheme(
    val id: String,
    val name: String,
    val specialKeyLight: Color,
    val specialKeyDark: Color,
    val accentLight: Color,
    val accentDark: Color
)

object KeyboardThemes {
    val themes = listOf(
        KeyboardColorScheme(
            id = "ocean_blue",
            name = "海洋蓝",
            specialKeyLight = Color(0xFFD3E3FD),
            specialKeyDark = Color(0xFF4A90D9),
            accentLight = Color(0xFF1A73E8),
            accentDark = Color(0xFF8AB4F8)
        ),
        KeyboardColorScheme(
            id = "lavender_purple",
            name = "薰衣草紫",
            specialKeyLight = Color(0xFFE8DEF8),
            specialKeyDark = Color(0xFF6750A4),
            accentLight = Color(0xFF7B1FA2),
            accentDark = Color(0xFFCE93D8)
        ),
        KeyboardColorScheme(
            id = "forest_green",
            name = "森林绿",
            specialKeyLight = Color(0xFFC8E6C9),
            specialKeyDark = Color(0xFF4CAF50),
            accentLight = Color(0xFF2E7D32),
            accentDark = Color(0xFF81C784)
        ),
        KeyboardColorScheme(
            id = "sunset_orange",
            name = "日落橙",
            specialKeyLight = Color(0xFFFFE0B2),
            specialKeyDark = Color(0xFFFF9800),
            accentLight = Color(0xFFE65100),
            accentDark = Color(0xFFFFB74D)
        ),
        KeyboardColorScheme(
            id = "coral_red",
            name = "珊瑚红",
            specialKeyLight = Color(0xFFFFCDD2),
            specialKeyDark = Color(0xFFE57373),
            accentLight = Color(0xFFC62828),
            accentDark = Color(0xFFEF9A9A)
        ),
        KeyboardColorScheme(
            id = "slate_gray",
            name = "石墨灰",
            specialKeyLight = Color(0xFFE0E0E0),
            specialKeyDark = Color(0xFF616161),
            accentLight = Color(0xFF424242),
            accentDark = Color(0xFF9E9E9E)
        ),
        KeyboardColorScheme(
            id = "rose_pink",
            name = "玫瑰粉",
            specialKeyLight = Color(0xFFF8BBD9),
            specialKeyDark = Color(0xFFE91E63),
            accentLight = Color(0xFFAD1457),
            accentDark = Color(0xFFF48FB1)
        ),
        KeyboardColorScheme(
            id = "teal_cyan",
            name = "青碧色",
            specialKeyLight = Color(0xFFB2DFDB),
            specialKeyDark = Color(0xFF009688),
            accentLight = Color(0xFF00796B),
            accentDark = Color(0xFF80CBC4)
        )
    )
    
    fun getThemeById(id: String): KeyboardColorScheme {
        return themes.find { it.id == id } ?: themes[0]
    }
    
    fun getSpecialKeyColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.specialKeyDark else theme.specialKeyLight
    }
    
    fun getAccentColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.accentDark else theme.accentLight
    }
}