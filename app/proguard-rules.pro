# App ProGuard rules

# disable obfuscation
-dontobfuscate
# disable optimizations - CRITICAL for plugin compatibility
-dontoptimize

# Keep Kotlin standard library - CRITICAL for plugin compatibility
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlin.jvm.** { *; }
-keep class kotlin.jvm.functions.** { *; }
-keep class kotlin.collections.** { *; }
-keep class kotlin.collections.deserializations.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep class kotlin.coroutines.intrinsics.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.text.** { *; }
-keep class kotlin.internal.** { *; }
-keep class kotlin.experimental.** { *; }

# Keep Kotlin inline functions
-keep class kotlin.coroutines.Intrinsics { *; }
-keep class kotlin.coroutines.RestrictedSuspension { *; }
-keep class kotlin.coroutines.SafeContinuation { *; }

# Keep kotlinx.coroutines
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# Keep plugin API classes
-keep class com.kingzcheung.kime.plugin.api.** { *; }
-keep interface com.kingzcheung.kime.plugin.api.** { *; }

# Keep Rime native classes
-keep class com.kingzcheung.kime.rime.** { *; }
-keep class com.kingzcheung.kime.**Jni** { *; }

# remove kotlin null checks
-processkotlinnullchecks remove

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable