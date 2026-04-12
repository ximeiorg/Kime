# Kaomoji Plugin ProGuard rules

# Disable obfuscation - CRITICAL for plugin compatibility
-dontobfuscate

# Keep plugin declaration activity (used for intent filter discovery)
-keep class com.kingzcheung.kime.plugin.kaomoji.PluginDeclaration { *; }

# Keep plugin factory class
-keep class com.kingzcheung.kime.plugin.kaomoji.KaomojiPluginFactory { *; }

# Keep plugin implementation
-keep class com.kingzcheung.kime.plugin.kaomoji.KaomojiPlugin { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable