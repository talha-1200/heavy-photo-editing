plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// AdMob IDs. Debug builds ALWAYS use Google's test IDs (clicking real ads while testing can get the account banned).
// Release builds use your real IDs.
val admobTestApp = "ca-app-pub-3940256099942544~3347511713"
val admobTestBanner = "ca-app-pub-3940256099942544/9214589741"
val admobTestInterstitial = "ca-app-pub-3940256099942544/1033173712"
val admobLiveApp = "ca-app-pub-9672803485950054~7575219099"
val admobLiveBanner = "ca-app-pub-9672803485950054/6697378006"
val admobLiveInterstitial = "ca-app-pub-9672803485950054/9909116276"

android {
    namespace = "com.heavy.photoediting"
    compileSdk = 35   // required by the AdMob GMA Next-Gen SDK

    defaultConfig {
        applicationId = "com.heavy.photoediting"
        minSdk = 29          // 29+ => saving to MediaStore needs NO storage permission
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_APP_ID", "\"$admobTestApp\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobTestBanner\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobTestInterstitial\"")
            manifestPlaceholders["admobAppId"] = admobTestApp
        }
        release {
            buildConfigField("String", "ADMOB_APP_ID", "\"$admobLiveApp\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobLiveBanner\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobLiveInterstitial\"")
            manifestPlaceholders["admobAppId"] = admobLiveApp
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // CameraX (Apache-2.0)
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // On-device face contours for beauty tools (bundled model, works offline)
    implementation("com.google.mlkit:face-detection:16.1.6")
    // AdMob (GMA Next-Gen SDK). Check https://developers.google.com/admob/android/next-gen/rel-notes for a newer version.
    implementation("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6") // person cut-out, on-device
}
