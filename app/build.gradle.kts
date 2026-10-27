plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "jp.ikegami.roastscale"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.ikegami.roastscale"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("ROAST_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ROAST_VERSION_NAME") ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("releaseSecure") {
            val keystorePath = System.getenv("ROAST_KEYSTORE")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ROAST_STORE_PASSWORD")
                keyAlias = System.getenv("ROAST_KEY_ALIAS")
                keyPassword = System.getenv("ROAST_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("ROAST_KEYSTORE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("releaseSecure")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
