plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.senle.widgets"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.senle.widgets"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "2.6"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "senle123456"
            keyAlias = "senlewidgets"
            keyPassword = "senle123456"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
}
