# Emoji Sticker Plugin ProGuard rules

# disable obfuscation
-dontobfuscate

# CRITICAL: Keep plugin declaration activity for Intent Filter discovery
-keep class com.kingzcheung.kime.plugin.emoji.PluginDeclaration { *; }

# CRITICAL: Keep plugin factory class referenced in AndroidManifest meta-data
-keep class com.kingzcheung.kime.plugin.emoji.EmojiPluginFactory { *; }

# CRITICAL: Keep plugin implementation class
-keep class com.kingzcheung.kime.plugin.emoji.EmojiStickerPlugin { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable