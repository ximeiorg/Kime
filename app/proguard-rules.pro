# App ProGuard rules

# Disable obfuscation - CRITICAL for plugin compatibility
# Allows optimization and shrinking but keeps original class/method names
-dontobfuscate

# Keep plugin system classes - CRITICAL for plugin loading
-keep class com.kingzcheung.kime.plugin.** { *; }
-keep interface com.kingzcheung.kime.plugin.** { *; }

# Keep plugin API classes
-keep class com.kingzcheung.kime.plugin.api.** { *; }
-keep interface com.kingzcheung.kime.plugin.api.** { *; }

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

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep Android DexClassLoader internals used by plugin loader (reflection)
-keep class dalvik.system.BaseDexClassLoader { *; }
-keep class dalvik.system.DexClassLoader { *; }
-keep class dalvik.system.PathClassLoader { *; }
-keepclassmembers class dalvik.system.BaseDexClassLoader {
    *** pathList;
}
-keepclassmembers class * {
    *** pathList;
    *** nativeLibraryDirectories;
}