# === KEEP JSBridge for WebView ===
# JSBridge - full retention including annotations
-keep class work.isdzulqor.oalla.ChatFragment$JSBridge { *; }
-keepclassmembers class work.isdzulqor.oalla.ChatFragment$JSBridge {
    @android.webkit.JavascriptInterface <methods>;
    public *;
}

# === KEEP MainActivity methods related to JNI ===
-keep class work.isdzulqor.oalla.MainActivity {
    public native *;
    public void logFromNative(java.lang.String);
}

# Keep Gson annotations and constructors
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    public <init>();
}

# Preserve all type info used by Gson
-keepattributes *Annotation*, Signature


# Keep all model-related classes fully
-keep class work.isdzulqor.oalla.ModelData { *; }
-keep class work.isdzulqor.oalla.ModelDataInfo { *; }
-keep class work.isdzulqor.oalla.ModelVariant { *; }
-keep class work.isdzulqor.oalla.ModelSuggestion { *; }

