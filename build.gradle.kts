// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://webrtc.org/maven") }
    }
    dependencies {
        classpath("com.google.gms:google-services:4.4.4")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.android.library") version "9.2.1" apply false
    alias(libs.plugins.legacy.kapt) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    id("com.google.firebase.crashlytics") version "3.0.7" apply false
    alias(libs.plugins.hilt.android) apply false
}
