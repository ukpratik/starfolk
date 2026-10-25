// SPDX-License-Identifier: GPL-3.0-or-later
// Content CLI (ADR-008, LLD §3.2): validate | pack | report | lock. Shares ContentRules with the app via :core.
plugins {
    id("starfolk.jvm.library")
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("io.github.ukpratik.starfolk.tools.MainKt")
    applicationName = "starfolk-content"
}

dependencies {
    implementation(project(":core"))
}

val contentDir = rootProject.layout.projectDirectory.dir("content/v1")

// CI gate (ADR-013): `-Pstarfolk.release=true` turns drafting warnings (gaps, reviews, helpline freshness) into errors.
val validateContent = tasks.register<JavaExec>("validateContent") {
    group = "verification"
    description = "Validates content/v1 against the content rules (LLD §3.2)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    inputs.dir(contentDir).withPropertyName("content")
    val release = providers.gradleProperty("starfolk.release").getOrElse("false").toBoolean()
    args(listOf("validate", contentDir.asFile.path) + if (release) listOf("--release") else emptyList())
}

tasks.named("check") { dependsOn(validateContent) }

tasks.named<Test>("test") {
    inputs.dir(contentDir).withPropertyName("content")
    systemProperty("starfolk.content", contentDir.asFile.path)
}

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
