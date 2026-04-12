# Prediction ONNX Plugin ProGuard rules

# Disable obfuscation - CRITICAL for plugin compatibility
-dontobfuscate

# Keep ONNX Runtime native methods
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** {
    native *** *(...);
}

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable