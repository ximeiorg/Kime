# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep plugin classes
-keep class com.kingzcheung.kime.plugin.kaomoji.** { *; }

# Keep KimeExtension interface implementations
-keep class * implements com.kingzcheung.kime.plugin.api.KimeExtension { *; }

# Keep KimeExtensionFactory implementations
-keep class * implements com.kingzcheung.kime.plugin.api.KimeExtensionFactory { *; }