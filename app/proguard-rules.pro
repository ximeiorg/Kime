# App ProGuard rules

# Keep plugin API classes - CRITICAL for plugin compatibility
# Plugins don't use R8, so app must not obfuscate plugin-api classes
-keep interface com.kingzcheung.kime.plugin.api.** { *; }
-keep class com.kingzcheung.kime.plugin.api.** { *; }

# Keep Rime native classes
-keep class com.kingzcheung.kime.rime.** { *; }
-keep class com.kingzcheung.kime.**Jni** { *; }

# Keep data classes that might be used for serialization
-keep class com.kingzcheung.kime.data.** { *; }

# Keep Compose-related classes
-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.ui.tooling.preview.Preview class * { *; }

# Keep view models
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep kotlinx serialization annotations
-keep @kotlinx.serialization.Serializable class * { *; }