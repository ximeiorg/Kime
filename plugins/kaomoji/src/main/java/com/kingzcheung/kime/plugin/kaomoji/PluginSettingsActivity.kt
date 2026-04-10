package com.kingzcheung.kime.plugin.kaomoji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.kingzcheung.kime.plugin.kaomoji.ui.KaomojiSettingsScreen

class PluginSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val darkTheme = isSystemInDarkTheme()
            
            MaterialTheme(
                colorScheme = if (darkTheme) {
                    darkColorScheme(
                        primary = Color(0xFFCE93D8),
                        secondary = Color(0xFFCE93D8),
                        tertiary = Color(0xFFCE93D8)
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFFAB47BC),
                        secondary = Color(0xFFAB47BC),
                        tertiary = Color(0xFFAB47BC)
                    )
                }
            ) {
                KaomojiSettingsScreen(
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}