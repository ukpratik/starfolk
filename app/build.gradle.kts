// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    id("starfolk.android.application")
    id("starfolk.android.compose")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutlibraries.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.ukpratik.starfolk"

    defaultConfig {
        applicationId = "io.github.ukpratik.starfolk" // permanent after the first upload (ADR-010)
        // versionCode = MAJOR × 10000 + MINOR × 100 + PATCH (ADR-011).
        versionCode = 9901 // 1.0.0-rc1: release candidates sit just below 1.0.0 (10000)
        versionName = "1.0.0-rc1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            // LLD §7: the Aptabase app key comes from ~/.gradle/gradle.properties; empty = analytics stays off.
            buildConfigField("String", "APTABASE_KEY", "\"${providers.gradleProperty("starfolk.aptabase.key").getOrElse("")}\"")
            buildConfigField("String", "STORE_URL", "\"https://play.google.com/store/apps/details?id=io.github.ukpratik.starfolk\"")
        }
        create("fdroid") {
            dimension = "store"
            // Local test builds only: F-Droid's build server has no upload key, so it builds unsigned and signs with its own.
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            buildConfigField("boolean", "ANALYTICS_AVAILABLE", "false")
            buildConfigField("String", "APTABASE_KEY", "\"\"")
            buildConfigField("String", "STORE_URL", "\"https://f-droid.org/packages/io.github.ukpratik.starfolk/\"")
        }
    }

    // ADR-011: reproducible builds for F-Droid.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

// LLD §2: Room schemas for both databases are exported and committed. Pre-release they stay at version 1;
// from the first release every change ships a Migration and a MigrationTestHelper test.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
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
            // ADR-013: Install Referrer (play flavour only, US-018) is under the Android SDK License.
            "ASDKL" to listOf("com.google.android.gms", "com.android.installreferrer"),
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    // ADR-011: proprietary Play code only in the play flavour; fdroid binds no-ops.
    "playImplementation"(libs.play.review.ktx)
    "playImplementation"(libs.play.installreferrer)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // NFR-006: the no-network instrumented test (nightly / before release, needs an emulator or device).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
