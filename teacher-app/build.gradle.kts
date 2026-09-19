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
        minSdk = 26
        targetSdk = 34
        versionCode = 26
        versionName = "1.13.12"
    }

    // v1.13.8 — ONE permanent signing key, checked into the repo.
    // Before this, every CI run signed with a throwaway debug key, so Android
    // rejected updates on phones that already had the app (signature mismatch),
    // and the 1.13.7 build accidentally shipped versionCode 14 (LOWER than the
    // existing 20), which Android also refuses as a downgrade. Both together
    // made the app look like "it won't install / won't open" on teachers' phones.
    // SECURITY 2026-09-19: the previous upload keystore was committed to
    // source control with hard-coded passwords. Treat that key as COMPROMISED.
    // Signing is now environment-driven: CI injects the NEW (rotated) key via
    // SRS_UPLOAD_STORE_BASE64, SRS_UPLOAD_STORE_PASSWORD, SRS_UPLOAD_KEY_ALIAS,
    // SRS_UPLOAD_KEY_PASSWORD. When absent, release builds fail fast instead of
    // silently falling back to a debug key. See docs/KEY-ROTATION.md.
    signingConfigs {
        create("upload") {
            val storeB64 = providers.environmentVariable("SRS_UPLOAD_STORE_BASE64").orNull
            if (storeB64 != null) {
                val ks = File(rootProject.buildDir, "upload.keystore")
                if (!ks.exists() || System.getenv("SRS_UPLOAD_STORE_REFRESH") != null) {
                    ks.parentFile.mkdirs()
                    ks.writeBytes(java.util.Base64.getDecoder().decode(storeB64))
                }
                storeFile = ks
                storePassword = providers.environmentVariable("SRS_UPLOAD_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SRS_UPLOAD_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SRS_UPLOAD_KEY_PASSWORD").get()
            }
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
}
