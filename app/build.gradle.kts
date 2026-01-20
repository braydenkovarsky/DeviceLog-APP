plugins {
    // Standard plugins required for Android-specific build tasks and Kotlin-to-JVM compilation.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // We added this unique namespace to prevent package collisions in the Android ecosystem.
    namespace = "com.example.devicelog"

    // We added compileSdk 35 because Google Play mandates targeting Android 15 (API 35)
    // for all new apps and updates in 2026 to ensure support for modern hardware features.
    compileSdk = 35

    defaultConfig {
        // We added a specific applicationId as this acts as the permanent store-front ID.
        applicationId = "com.example.devicelog"

        // We added minSdk 26 to support Android 8.0 and above, ensuring access to
        // critical background execution limits and notification channel requirements.
        minSdk = 26

        // We added targetSdk 35 to signal to the OS that the app is optimized for
        // Android 15's latest security, privacy, and performance behaviors.
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // We added minifyEnabled = true to trigger the R8 compiler. This obfuscates
            // the code to protect proprietary hardware logic and prevents reverse-engineering.
            isMinifyEnabled = true

            // We added isShrinkResources to eliminate unused assets, which reduces the APK size
            // and improves user acquisition rates in regions with limited bandwidth.
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // We added Java 17 compatibility because the Android 15 build toolchain and
    // Gradle 8.x+ require it for stable compilation of modern language features.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // We added the SplashScreen library to provide a unified, OS-managed launch experience.
    // This prevents the "double splash" issue on Android 12+ (API 31) and ensures consistency.
    implementation("androidx.core:core-splashscreen:1.0.1")

    // We added Coroutines to handle asynchronous hardware polling and pings.
    // This prevents Application Not Responding (ANR) flags by keeping the main thread free.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // We added Glide to manage image memory usage efficiently, which is a major
    // factor in app stability and prevents high crash rates during Play Store review.
    implementation("com.github.bumptech.glide:glide:4.15.1")

    // We added Material components to ensure the UI follows the "Material 3" guidelines
    // required for professional-grade design scores in the Play Console.
    implementation("com.google.android.material:material:1.11.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
