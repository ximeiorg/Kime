# Emoji Sticker Plugin ProGuard rules

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
-keep class com.kingzcheung.kime.plugin.emoji.PluginDeclaration { *; }
-keep class com.kingzcheung.kime.plugin.emoji.EmojiPluginFactory { *; }
-keep class com.kingzcheung.kime.plugin.emoji.EmojiStickerPlugin { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable