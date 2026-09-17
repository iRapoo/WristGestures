// Top-level build file. Module configuration lives in app/build.gradle.kts.

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin by itself ("built-in Kotlin"); this line only pins the
        // Kotlin compiler version instead of the one bundled with AGP.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
}
