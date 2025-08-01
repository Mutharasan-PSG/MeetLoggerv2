plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id ("kotlin-kapt")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.meetloggerv2"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.meetloggerv2"
        minSdk = 26
        targetSdk = 36
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

    packagingOptions {
        exclude("META-INF/DEPENDENCIES")
    }

}

dependencies{

   implementation ("com.google.mlkit:translate:17.0.3")

implementation ("com.itextpdf:itext7-core:9.2.0")


implementation ("org.apache.poi:poi-ooxml:5.4.1") // Add this line

implementation ("androidx.cardview:cardview:1.0.0")

implementation("com.squareup.okhttp3:okhttp:5.1.0")

implementation ("co.daily:client:0.32.0")

implementation ("com.squareup.picasso:picasso:2.71828")

implementation ("com.google.code.gson:gson:2.13.1")

// Import the BoM for the Firebase platform
implementation (platform("com.google.firebase:firebase-bom:34.0.0"))

implementation("com.google.android.gms:play-services-auth:21.4.0")
implementation ("com.github.bumptech.glide:glide:4.16.0")
//  implementation(libs.androidx.legacy.support.v4)
// implementation(libs.androidx.recyclerview)
// implementation(libs.androidx.navigation.fragment.ktx)
implementation ("androidx.navigation:navigation-fragment-ktx:2.9.3")
implementation ("androidx.navigation:navigation-ui-ktx:2.9.3")
implementation(libs.firebase.auth.ktx)
implementation(libs.androidx.legacy.support.v4)
implementation(libs.androidx.recyclerview)
implementation(libs.firebase.crashlytics.buildtools)
implementation(libs.firebase.messaging.ktx)
// implementation(libs.volley)

annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")
implementation ("com.google.firebase:firebase-storage:22.0.0")

implementation ("com.google.firebase:firebase-firestore:26.0.0")
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