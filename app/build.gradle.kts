// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("starfolk.android.application")
    id("starfolk.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.ukpratik.starfolk"

    defaultConfig {
        applicationId = "io.github.ukpratik.starfolk" // permanent after the first upload (ADR-010)
        // versionCode = MAJOR × 10000 + MINOR × 100 + PATCH (ADR-011).
        versionCode = 100
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // ADR-011: the upload key lives in ~/.android-keys/, passwords in ~/.gradle/gradle.properties, never in the repo.
    // Without them, release builds stay unsigned (CI). F-Droid signs its own builds.
    signingConfigs {
        val storeFilePath = providers.gradleProperty("starfolk.upload.storeFile").orNull
        if (storeFilePath != null) {
            create("upload") {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("starfolk.upload.storePassword").get()
                keyAlias = providers.gradleProperty("starfolk.upload.keyAlias").get()
                keyPassword = providers.gradleProperty("starfolk.upload.keyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // ADR-011 / D-028: play = opt-in analytics, Install Referrer, In-App Review (from M-D);
    // fdroid = no network permission, no Play classes.
    flavorDimensions += "store"
    productFlavors {
        create("play") {
            dimension = "store"
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            buildConfigField("boolean", "ANALYTICS_AVAILABLE", "true")
        }
        create("fdroid") {
            dimension = "store"
            buildConfigField("boolean", "ANALYTICS_AVAILABLE", "false")
        }
    }

    // ADR-011: reproducible builds for F-Droid.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

// ADR-008: content/v1 is validated and packed into assets/content at build time by :tools:content (never in git).
val contentTool: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

abstract class PackContent : JavaExec() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    override fun exec() {
        args("pack", contentDir.get().asFile.path, outputDir.get().asFile.resolve("content").path)
        super.exec()
    }
}

val packContent = tasks.register<PackContent>("packContent") {
    classpath = contentTool
    mainClass.set("io.github.ukpratik.starfolk.tools.MainKt")
    contentDir.set(rootProject.layout.projectDirectory.dir("content/v1"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(packContent, PackContent::outputDir)
    }
}

// One Baseline Profile for both flavours (identical code paths), merged into main.
baselineProfile {
    mergeIntoMain = true
}

// ADR-013 licence gate: generated at build time, fully offline; strict mode fails on licences outside the allowlist.
aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
        filterVariants.addAll("playRelease", "fdroidRelease")
    }
    export {
        excludeFields.addAll("generated", "funding", "developers")
    }
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "OFL-1.1")
        allowedLicensesMap = mapOf(
            "PCSDKToS" to listOf("com.google.android.play"),
            "ASDKL" to listOf("com.google.android.gms"),
        )
    }
}

dependencies {
    implementation(project(":core"))
    contentTool(project(":tools:content"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
