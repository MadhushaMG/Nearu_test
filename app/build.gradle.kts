plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.codara.nearutest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.codara.nearutest"
        minSdk = 24
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Mapbox v11
    implementation("com.mapbox.maps:android:11.9.0")

    // Google FusedLocation (modern API)
    implementation("com.google.android.gms:play-services-location:21.3.0")
}