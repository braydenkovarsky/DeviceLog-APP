# -------------------------------------------------------------------------
# STANDARD PROJECT TEMPLATE RULES
# -------------------------------------------------------------------------

# We added this to protect any JavaScript interfaces in your WebViews.
# Using '*' ensures that even if you don't have a specific class name yet,
# any method marked with @JavascriptInterface will not be deleted or renamed.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# We added this to preserve line number information.
# This is mandatory for "Play Store Approval" so you can read crash reports in the console.
-keepattributes SourceFile,LineNumberTable

# We added this to hide the original source file name in the final APK for security.
-renamesourcefileattribute SourceFile

# -------------------------------------------------------------------------
# ANDROID 15 (API 35) & SYSTEM COMPLIANCE
# -------------------------------------------------------------------------

# We added this to protect the 'specialUse' metadata.
# Android 15 requires these annotations to be visible at runtime for foreground services.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Annotation*

# We added this to ensure the OS can always find your Telemetry Service by its exact name.
-keep class com.example.devicelog.TelemetryService { *; }

# -------------------------------------------------------------------------
# HARDWARE TELEMETRY & DATA MODELS
# -------------------------------------------------------------------------

# We added this to prevent R8 from renaming your internal data structures.
# This ensures that your hardware polling logic remains functional after obfuscation.
-keepclassmembers class com.example.devicelog.** { *; }

# -------------------------------------------------------------------------
# COROUTINES & THREAD SAFETY (ANR PREVENTION)
# -------------------------------------------------------------------------

# We added these to prevent the app from crashing during background sensor pings.
# Coroutines use internal reflection that will break if these names are scrambled.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# -------------------------------------------------------------------------
# GLIDE & UI COMPONENT PROTECTION
# -------------------------------------------------------------------------

# We added these to ensure hardware icons and dashboard images load correctly.
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule

# We added this to protect your Dashboard UI.
# This prevents 'InflationExceptions' when the app tries to load your layout files.
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# -------------------------------------------------------------------------
# GOOGLE MATERIAL COMPONENTS
# -------------------------------------------------------------------------

# We added this to protect Material Design components like the Sidebar and Navigation Rail.
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**