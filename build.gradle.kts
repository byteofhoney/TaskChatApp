// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
// Compose compiler plugin (required for Kotlin 2.x + Compose)
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
// Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.3" apply false
}