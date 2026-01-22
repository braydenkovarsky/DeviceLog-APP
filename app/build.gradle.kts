plugins {
    // Standard plugins required for Android-specific build tasks and Kotlin-to-JVM compilation.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.devicelog"

    // Target API 35 is mandatory for Play Store releases in 2026.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.devicelog"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 Obfuscation: Protects your telemetry logic and charging-state algorithms.
            isMinifyEnabled = true

            // Resource Shrinking: Reduces APK size to improve download rates.
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // --- ADMOB INTEGRATION ---
    // This allows your App ID and Unit ID to communicate with Google's ad servers.
    implementation("com.google.android.gms:play-services-ads:24.9.0")

    // --- EXISTING TELEMETRY & UI LIBS ---
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("com.google.android.material:material:1.11.0")

    // --- ANDROIDX CORE ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}