plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val keystorePath = System.getenv("REMOTE_KEYSTORE_PATH")
val storePasswordValue = System.getenv("REMOTE_STORE_PASSWORD")
val keyAliasValue = System.getenv("REMOTE_KEY_ALIAS")
val keyPasswordValue = System.getenv("REMOTE_KEY_PASSWORD")
val hasReleaseSigning = !keystorePath.isNullOrBlank() &&
    !storePasswordValue.isNullOrBlank() && !keyAliasValue.isNullOrBlank() &&
    !keyPasswordValue.isNullOrBlank()

android {
    namespace = "com.remote.onboard"
    compileSdk = 35

    // Remote is currently distributed as a sideloaded APK, not through Google Play.
    // Keep targetSdk 32 for the Android 12 test device and suppress only the Play-target warning.
    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    defaultConfig {
        applicationId = "com.remote.onboard"
        minSdk = 26
        targetSdk = 32
        versionCode = runNumber
        versionName = "0.1.$runNumber"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Debug builds are for local development only. Their signing key may vary by runner.
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    // USB CDC, FTDI, CP210x, CH34x and Prolific serial adapters.
    implementation("com.github.mik3y:usb-serial-for-android:3.9.0")
}