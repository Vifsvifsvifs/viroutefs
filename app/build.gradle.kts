plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val alphaKeystorePath = providers.environmentVariable("VIROUTEFS_ALPHA_KEYSTORE_PATH").orNull
val alphaKeystorePassword = providers.environmentVariable("VIROUTEFS_ALPHA_KEYSTORE_PASSWORD").orNull
val alphaKeyAlias = providers.environmentVariable("VIROUTEFS_ALPHA_KEY_ALIAS").orNull
val alphaKeyPassword = providers.environmentVariable("VIROUTEFS_ALPHA_KEY_PASSWORD").orNull
val hasAlphaSigning = listOf(
    alphaKeystorePath,
    alphaKeystorePassword,
    alphaKeyAlias,
    alphaKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.vifs.viroutefs"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vifs.viroutefs"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "0.6.11-alpha"
    }

    signingConfigs {
        if (hasAlphaSigning) {
            create("alpha") {
                storeFile = file(alphaKeystorePath!!)
                storePassword = alphaKeystorePassword
                keyAlias = alphaKeyAlias
                keyPassword = alphaKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasAlphaSigning) {
                signingConfig = signingConfigs.getByName("alpha")
            }
        }
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }

}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
