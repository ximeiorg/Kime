# Prediction ONNX Plugin ProGuard rules

# Disable obfuscation - CRITICAL for plugin compatibility
-dontobfuscate

# Keep plugin declaration activity (used for intent filter discovery)
-keep class com.kingzcheung.kime.plugin.prediction.PluginDeclaration { *; }

# Keep plugin factory class
-keep class com.kingzcheung.kime.plugin.prediction.PredictionPluginFactory { *; }

# Keep plugin implementation
-keep class com.kingzcheung.kime.plugin.prediction.OnnxPredictionPlugin { *; }

# Keep ONNX Runtime native methods
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** {
    native *** *(...);
}

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable