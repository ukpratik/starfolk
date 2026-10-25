// SPDX-License-Identifier: GPL-3.0-or-later
// Pure Kotlin rules: app-day, content model and rules, quest pick, Shelter progress, safety, sky layout, milestones
// (ADR-002, LLD §1). No Android. kotlinx-serialization is the one library (the content JSON).
plugins {
    id("starfolk.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
