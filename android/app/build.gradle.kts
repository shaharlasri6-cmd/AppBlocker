plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shahar.appblocker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shahar.appblocker"
        minSdk = 28
        targetSdk = 35
        versionCode = 10
        versionName = "1.3.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false
    }

    val ciKeystore = System.getenv("APPBLOCKER_KEYSTORE_PATH")
    signingConfigs {
        if (!ciKeystore.isNullOrBlank()) {
            create("github") {
                storeFile = file(ciKeystore)
                storePassword = System.getenv("APPBLOCKER_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("APPBLOCKER_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("APPBLOCKER_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!ciKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("github")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
}
