# Emoji Sticker Plugin ProGuard rules

# Disable obfuscation - CRITICAL for plugin compatibility
-dontobfuscate

# Keep plugin declaration activity (used for intent filter discovery)
-keep class com.kingzcheung.kime.plugin.emoji.PluginDeclaration { *; }

# Keep plugin factory class
-keep class com.kingzcheung.kime.plugin.emoji.EmojiPluginFactory { *; }

# Keep plugin implementation
-keep class com.kingzcheung.kime.plugin.emoji.EmojiStickerPlugin { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable