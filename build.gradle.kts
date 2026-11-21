// Root build file. Versions pinned to the Velnox verified matrix:
// AGP 8.7.2 / Kotlin 2.0.21 / Compose BOM 2024.10.01.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
