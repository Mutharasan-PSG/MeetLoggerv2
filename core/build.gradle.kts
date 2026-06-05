plugins {
    id("com.android.library")
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.meetloggerv2.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Navigation and Fragment KTX
    implementation(libs.androidx.fragment.ktx)

    // Firebase (for core AuthSession/ListenerRegistration)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-storage:22.0.1")

    // Hilt DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    
    // specialized APIs needed by core (Retrofit client is in data, but API interfaces can go there)
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation("com.google.code.gson:gson:2.14.0")

    // Document Processing
    implementation("com.itextpdf:itext7-core:9.6.0")
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
