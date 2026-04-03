package com.kingzcheung.kime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// ========== 键盘主题 ==========

private val KeyboardDarkColorScheme = darkColorScheme(
    primary = AccentColorDark,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = KeyboardBackgroundDark,
    surface = KeyboardBackgroundDark,
    onPrimary = KeyTextColorDark,
    onSecondary = KeyTextColorDark,
    onTertiary = KeyTextColorDark,
    onBackground = KeyTextColorDark,
    onSurface = KeyTextColorDark
)

private val KeyboardLightColorScheme = lightColorScheme(
    primary = AccentColor,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = KeyboardBackground,
    surface = KeyboardBackground,
    onPrimary = KeyTextColor,
    onSecondary = KeyTextColor,
    onTertiary = KeyTextColor,
    onBackground = KeyTextColor,
    onSurface = KeyTextColor
)

@Composable
fun KimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) KeyboardDarkColorScheme else KeyboardLightColorScheme
        }
        darkTheme -> KeyboardDarkColorScheme
        else -> KeyboardLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// ========== 设置页面主题 ==========

private val SettingsDarkColorScheme = darkColorScheme(
    primary = SettingsPrimaryDark,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = SettingsBackgroundDark,
    surface = SettingsSurfaceDark,
    onPrimary = SettingsOnBackgroundDark,
    onSecondary = SettingsOnBackgroundDark,
    onTertiary = SettingsOnBackgroundDark,
    onBackground = SettingsOnBackgroundDark,
    onSurface = SettingsOnBackgroundDark
)

private val SettingsLightColorScheme = lightColorScheme(
    primary = SettingsPrimary,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = SettingsBackground,
    surface = SettingsSurface,
    onPrimary = SettingsOnBackground,
    onSecondary = SettingsOnBackground,
    onTertiary = SettingsOnBackground,
    onBackground = SettingsOnBackground,
    onSurface = SettingsOnBackground
)

@Composable
fun SettingsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SettingsDarkColorScheme else SettingsLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}