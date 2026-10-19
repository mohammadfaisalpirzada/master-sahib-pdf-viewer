plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Apply Firebase's google-services plugin only when the config file is present,
// so the project still builds before Firebase is set up.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.themastersahib.pdfviewer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.themastersahib.pdfviewer"
        minSdk = 24          // Android 7.0+ (PdfRenderer available since API 21, ML Kit scanner needs Play Services)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing — reads the keystore from env vars (set by CI from GitHub
    // secrets). When absent (plain local build) the release stays unsigned.
    val ksFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ksFile != null && file(ksFile).exists()) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (ksFile != null && file(ksFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    // --- Compose (BOM keeps versions aligned) ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // --- Core ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // --- Urdu / nastaliq OCR (needs urd.traineddata in assets/tessdata) ---
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.7.0")

    // --- Printing (CNIC duplex / collage straight to printer) ---
    implementation("androidx.print:print:1.0.0")

    // --- Room (local cache; kept for offline device storage) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Firebase: Auth (login) + Firestore (per-user cloud data) ---
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // --- KILLER FEATURE 2: CamScanner-style document scanner (free, on-device) ---
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // --- Reader engine: Pdfium-backed view (zoom, night mode, page slider) ---
    // Maintained fork of barteksc; same `com.github.barteksc.pdfviewer` package, resolvable on JitPack.
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")

    // --- Text extraction for search (Apache 2.0) ---
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // --- OCR (on-device, free) ---
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
