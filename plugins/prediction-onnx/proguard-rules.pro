# Prediction ONNX Plugin ProGuard rules

# disable obfuscation
-dontobfuscate

# CRITICAL: Keep plugin declaration activity for Intent Filter discovery
-keep class com.kingzcheung.kime.plugin.prediction.PluginDeclaration { *; }

# CRITICAL: Keep plugin factory class referenced in AndroidManifest meta-data
-keep class com.kingzcheung.kime.plugin.prediction.PredictionPluginFactory { *; }

# CRITICAL: Keep plugin implementation class
-keep class com.kingzcheung.kime.plugin.prediction.OnnxPredictionPlugin { *; }

# Keep ONNX Runtime native methods
-keep class ai.onnxruntime.** { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable