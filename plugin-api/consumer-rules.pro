# Consumer rules for plugin-api library

# Keep all public API classes - CRITICAL for plugin compatibility
-keep class com.kingzcheung.kime.plugin.api.** { *; }
-keep interface com.kingzcheung.kime.plugin.api.** { *; }
-keep enum com.kingzcheung.kime.plugin.api.** { *; }