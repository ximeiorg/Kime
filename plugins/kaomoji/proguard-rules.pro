# Kaomoji Plugin ProGuard rules

# disable obfuscation
-dontobfuscate

# CRITICAL: Keep plugin declaration activity for Intent Filter discovery
# R8 shrinking will remove empty activities that appear "unused"
-keep class com.kingzcheung.kime.plugin.kaomoji.PluginDeclaration { *; }

# CRITICAL: Keep plugin factory class referenced in AndroidManifest meta-data
-keep class com.kingzcheung.kime.plugin.kaomoji.KaomojiPluginFactory { *; }

# CRITICAL: Keep plugin implementation class
-keep class com.kingzcheung.kime.plugin.kaomoji.KaomojiPlugin { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable