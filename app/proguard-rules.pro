# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
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

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep plugin API classes
-keep interface com.kingzcheung.kime.plugin.api.** { *; }
-keep class com.kingzcheung.kime.plugin.api.** { *; }

# Keep Rime native classes
-keep class com.kingzcheung.kime.rime.** { *; }
-keep class com.kingzcheung.kime.**Jni** { *; }

# Keep data classes that might be used for serialization
-keep class com.kingzcheung.kime.data.** { *; }

# Keep Compose-related classes
-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.ui.tooling.preview.Preview class * { *; }

# Keep view models
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep classes used for JSON serialization if present
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep @interface com.squareup.moshi.*

# Keep kotlinx serialization annotations
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class kotlin.reflect.jvm.internal.impl.serialization.deserialization.* { *; }