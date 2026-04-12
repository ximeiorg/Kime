# App ProGuard rules

# disable obfuscation
-dontobfuscate

# Keep Kotlin standard library - CRITICAL for plugin compatibility
-keep class kotlin.** { *; }
-keep class kotlin.collections.** { *; }

# Keep plugin API classes
-keep class com.kingzcheung.kime.plugin.api.** { *; }
-keep interface com.kingzcheung.kime.plugin.api.** { *; }

# Keep Rime native classes
-keep class com.kingzcheung.kime.rime.** { *; }
-keep class com.kingzcheung.kime.**Jni** { *; }

# remove kotlin null checks
-processkotlinnullchecks remove

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable