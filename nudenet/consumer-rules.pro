# ONNX Runtime dùng JNI + reflection nội bộ; giữ lại khi app bật R8/minify.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
