plugins {
    // Declared with `apply false` in the root build file: AGP is already on the
    // classpath from :app, so a version here would be rejected as unverifiable.
    id("com.android.test")
    id("androidx.baselineprofile")
}

// No Kotlin plugin here on purpose: AGP 9 compiles Kotlin built-in and rejects
// the KGP plugin, which is why the root build file dropped it too.

android {
    namespace = "com.ivor.ivormusic.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // The generator drives the real app through UI Automator, so it needs
        // a device rather than a JVM test.
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

// Generation needs a rooted or userdebug device. The Pixel_10 AVD is a Google
// Play image and cannot be rooted; Triat_Bench (android-36, google_apis) can,
// and so can the Gradle managed device below (an AOSP image), which is what
// the baseline-profile CI workflow uses. Locally the connected device is the
// default; -Ptriat.baselineProfile.managed switches to the managed device.
// See docs/baseline-profile.md.
val useManagedDevice = providers.gradleProperty("triat.baselineProfile.managed").isPresent

android {
    testOptions.managedDevices.allDevices {
        create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices.clear()
    if (useManagedDevice) managedDevices += "pixel6Api34"
    useConnectedDevices = !useManagedDevice
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
