# Consumer rules for plugin-api library
# Applied to app (host) that uses plugin-api

# Keep all public API classes - prevents obfuscation mismatch with plugins
-keep class com.kingzcheung.kime.plugin.api.** { *; }
-keep interface com.kingzcheung.kime.plugin.api.** { *; }