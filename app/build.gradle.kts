plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciVersionName = (findProperty("versionName") as String?)?.takeIf { it.isNotBlank() }
val ciVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull()
fun nonBlankEnv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val keystorePath = nonBlankEnv("TIMESENSE_KEYSTORE").orEmpty()
val releaseKeystore = keystorePath.takeIf { it.isNotBlank() }?.let { file(it) }?.takeIf { it.isFile }

android {
    namespace = "com.cea.timesense"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cea.timesense"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode ?: 6
        versionName = ciVersionName ?: "1.0.5"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = nonBlankEnv("TIMESENSE_STORE_PASSWORD") ?: ""
                keyAlias = nonBlankEnv("TIMESENSE_KEY_ALIAS") ?: "upload"
                keyPassword = nonBlankEnv("TIMESENSE_KEY_PASSWORD")
                    ?: nonBlankEnv("TIMESENSE_STORE_PASSWORD")
                    ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
