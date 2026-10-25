// SPDX-License-Identifier: GPL-3.0-or-later
// Baseline Profile + macrobenchmarks: cold start (NFR-001) and the sky frame-timing spike (NFR-002, D-012).
// Needs a device or emulator (API 28+); never part of scripts/ci.sh.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.ukpratik.starfolk.baselineprofile"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Both flavours run the same UI code (ADR-011); profile the Play one.
        missingDimensionStrategy("store", "play")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
