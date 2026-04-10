# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to hide the source file name.
#-renamesourcefileattribute SourceFile
# Keep plugin classes
-keep class com.kingzcheung.kime.plugin.emoji.** { *; }

# Keep KimeExtension interface implementations
-keep class * implements com.kingzcheung.kime.plugin.api.KimeExtension { *; }

# Keep KimeExtensionFactory implementations
-keep class * implements com.kingzcheung.kime.plugin.api.KimeExtensionFactory { *; }

# Keep Coil
-keep class coil.** { *; }
