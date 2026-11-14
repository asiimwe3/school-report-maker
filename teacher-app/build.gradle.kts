import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.derycode.srs.teacher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.derycode.srs.teacher"
        minSdk = 24
        targetSdk = 34
        versionCode = 63
        versionName = "1.20.18"
        // The reading model ships inside the APK so scanning works offline, but
        // only for the two processor types real phones use — that keeps the
        // download near 20MB instead of 47MB.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // v1.13.8 — ONE permanent signing key, checked into the repo.
    // Before this, every CI run signed with a throwaway debug key, so Android
    // rejected updates on phones that already had the app (signature mismatch),
    // and the 1.13.7 build accidentally shipped versionCode 14 (LOWER than the
    // existing 20), which Android also refuses as a downgrade. Both together
    // made the app look like "it won't install / won't open" on teachers' phones.
    signingConfigs {
        create("upload") {
            // Signing material. CI should provide SRS_KEYSTORE_B64 (base64 of the
            // keystore) + SRS_KEYSTORE_PASSWORD so the keystore can be rotated and
            // removed from the repo. Falls back to the committed keystore so local
            // builds and existing CI keep working unchanged.
            val pathFromEnv = System.getenv("SRS_KEYSTORE_PATH")
            val b64 = System.getenv("SRS_KEYSTORE_B64")
            val keystoreFile: File = when {
                !pathFromEnv.isNullOrBlank() -> file(pathFromEnv)
                !b64.isNullOrBlank() -> {
                    val out = File(rootProject.projectDir, "keys/teacher-upload.keystore")
                    out.parentFile?.mkdirs()
                    out.writeBytes(Base64.getDecoder().decode(b64))
                    out
                }
                else -> rootProject.file("keys/teacher-upload.keystore")
            }
            storeFile = keystoreFile
            storePassword = System.getenv("SRS_KEYSTORE_PASSWORD") ?: "derycode-teacher"
            keyAlias = System.getenv("SRS_KEY_ALIAS") ?: "teacher-upload"
            keyPassword = System.getenv("SRS_KEY_PASSWORD") ?: "derycode-teacher"
            // All three schemes on purpose. v2 alone is enough for Android 8+, but
            // several OEM installers on Android 7 still want the JAR (v1) signature
            // and refuse the file as "invalid" without it. v3 covers key rotation.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("upload")
        }
        release {
            signingConfig = signingConfigs.getByName("upload")
            // v1.13.9 — real RELEASE build (every APK up to 1.13.8 was a debug
            // build: 78MB of dex, debuggable, and Android killed it at startup on
            // low-memory phones). R8 shrinking also drops material-icons-extended
            // to only the icons actually used.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // QR scanning — one code connects every teacher (offline, no Google Play services needed)
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // Reads a photo of a marks sheet on the phone itself: the model is bundled in
    // the APK, so scanning works with no internet and no Google Play services.
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
