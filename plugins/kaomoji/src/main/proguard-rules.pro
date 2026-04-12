# disable obfuscation
-dontobfuscate
# disable optimizations
-dontoptimize

# Keep Kotlin standard library
-keep class kotlin.** { *; }
-keep class kotlin.jvm.** { *; }
-keep class kotlin.collections.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlin.reflect.** { *; }

# CRITICAL: Keep plugin classes for discovery
-keep class com.kingzcheung.kime.plugin.kaomoji.PluginDeclaration { *; }
-keep class com.kingzcheung.kime.plugin.kaomoji.KaomojiPluginFactory { *; }
-keep class com.kingzcheung.kime.plugin.kaomoji.KaomojiPlugin { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable