plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id ("kotlin-kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.meetloggerv2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.meetloggerv2"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

}

dependencies {
    //  implementation ("com.google.cloud:google-cloud-speech:4.50.0")


// https://mvnrepository.com/artifact/io.github.webrtc-sdk/android
    implementation("io.github.webrtc-sdk:android:125.6422.06.1")
    //  implementation ("io.agora.rtc:full-sdk:4.5.0") // Agora SDK

    // implementation("org.jitsi.react:jitsi-meet-sdk:10.2.1")
    //  implementation("org.jitsi.meet.sdk:JitsiMeetView:2.9.0")  // Or another version, if needed

    //  implementation("org.jitsi.react:jitsi-meet-sdk:10.3.0") {
    //   isTransitive = true  // This is the correct syntax
    //}
// Use latest version

   //implementation(libs.firebase.auth.ktx)

    implementation ("co.daily:client:0.27.0")

    implementation ("com.squareup.picasso:picasso:2.71828")

    implementation ("com.google.code.gson:gson:2.10.1")

    // Import the BoM for the Firebase platform
    implementation (platform("com.google.firebase:firebase-bom:33.7.0"))

    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation ("com.github.bumptech.glide:glide:4.15.1")
  //  implementation(libs.androidx.legacy.support.v4)
   // implementation(libs.androidx.recyclerview)
   // implementation(libs.androidx.navigation.fragment.ktx)
    implementation ("androidx.navigation:navigation-fragment-ktx:2.8.5")
    implementation ("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation(libs.firebase.auth.ktx)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.androidx.recyclerview)
    // implementation(libs.volley)

    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.1")
    implementation ("com.google.firebase:firebase-storage:21.0.1")

    implementation ("com.google.firebase:firebase-firestore:25.1.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
   implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}