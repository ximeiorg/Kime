# Add project specific ProGuard rules here.

# Keep plugin classes
-keep class com.kingzcheung.kime.plugin.api.** { *; }
-keep class com.example.kime.plugin.** { *; }

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }