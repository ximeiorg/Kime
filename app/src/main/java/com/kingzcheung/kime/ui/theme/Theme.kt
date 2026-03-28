package com.kingzcheung.kime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
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

private val LightColorScheme = lightColorScheme(
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
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}