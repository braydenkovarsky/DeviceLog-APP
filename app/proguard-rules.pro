# -------------------------------------------------------------------------
# STANDARD PROJECT TEMPLATE RULES
# -------------------------------------------------------------------------

# Protect JavaScript interfaces in WebViews
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve line numbers for readable Play Store crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -------------------------------------------------------------------------
# ANDROID 15 (API 35) & SYSTEM COMPLIANCE
# -------------------------------------------------------------------------

# Protect foreground service annotations for Android 15
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Annotation*

# Ensure the OS can find your Telemetry Service by name
-keep class com.example.devicelog.TelemetryService { *; }

# -------------------------------------------------------------------------
# HARDWARE TELEMETRY & DATA MODELS
# -------------------------------------------------------------------------

# Prevent renaming of internal data structures (Trickle Charging logic)
-keepclassmembers class com.example.devicelog.** { *; }

# -------------------------------------------------------------------------
# COROUTINES & THREAD SAFETY (ANR PREVENTION)
# -------------------------------------------------------------------------

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# -------------------------------------------------------------------------
# GLIDE & UI COMPONENT PROTECTION
# -------------------------------------------------------------------------

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule

-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# -------------------------------------------------------------------------
# GOOGLE MATERIAL COMPONENTS
# -------------------------------------------------------------------------

-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# -------------------------------------------------------------------------
# GOOGLE MOBILE ADS (ADMOB) - 2026 SDK COMPLIANCE
# -------------------------------------------------------------------------

# Prevent R8 from stripping AdMob SDK classes
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.mediation.** { *; }

# Protect mediation interfaces for ad delivery
-keep interface com.google.android.gms.ads.mediation.** { *; }
-keep interface com.google.ads.mediation.** { *; }

# Fix for SDK 24.9.0 LocaleManager and general Ads warnings
-dontwarn com.google.android.gms.ads.**
-dontwarn com.google.ads.mediation.**
-dontwarn android.app.LocaleManager

# Required for AdMob hidden WebView rendering
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
}