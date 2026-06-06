import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI

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
        val buildNumber = (project.findProperty("buildNumber") as String?)
            ?.toIntOrNull() ?: 0
        val baseVersionName = "0.9.1"

        versionCode = 10000 + buildNumber
        versionName = if (buildNumber > 0) "$baseVersionName.$buildNumber"
            else baseVersionName

        ndk {
            abiFilters += "arm64-v8a"
        }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val libV2rayVersion = "v26.6.1"
val libV2rayFile = file("libs/libv2ray.aar")
val libV2rayDownloadUrls = listOf(
    "https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar",
    "https://github.com/2dust/AndroidLibXrayLite/releases/download/$libV2rayVersion/libv2ray.aar",
    "https://sourceforge.net/projects/androidlibxraylite.mirror/files/$libV2rayVersion/libv2ray.aar/download",
)

repositories {
    google()
    mavenCentral()
    flatDir {
        dirs("libs")
    }
}

tasks.register("downloadLibv2ray") {
    outputs.file(libV2rayFile)

    doLast {
        if (libV2rayFile.exists() && libV2rayFile.length() > 0L) {
            println("libv2ray.aar already exists at ${libV2rayFile.relativeTo(projectDir)}.")
            return@doLast
        }

        libV2rayFile.parentFile.mkdirs()
        val temporaryFile = File(libV2rayFile.parentFile, "${libV2rayFile.name}.download")
        var lastError: Throwable? = null

        for (downloadUrl in libV2rayDownloadUrls) {
            println("Downloading libv2ray.aar $libV2rayVersion from $downloadUrl ...")
            runCatching {
                temporaryFile.delete()
                val connection = URI.create(downloadUrl).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "ViRouteFS Gradle libv2ray downloader")
                }
                BufferedInputStream(connection.getInputStream()).use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        input.copyTo(output)
                    }
                }
                check(temporaryFile.length() > 0L) { "Downloaded libv2ray.aar is empty." }
                check(temporaryFile.renameTo(libV2rayFile)) { "Could not move downloaded libv2ray.aar into app/libs." }
                println("Downloaded libv2ray.aar to ${libV2rayFile.relativeTo(projectDir)}.")
                return@doLast
            }.onFailure { error ->
                lastError = error
                println("libv2ray.aar download failed from $downloadUrl: ${error.message ?: error::class.java.simpleName}")
            }
        }

        temporaryFile.delete()
        throw GradleException(
            "Unable to download libv2ray.aar $libV2rayVersion. " +
                "Create app/libs manually and place libv2ray.aar there, then run Gradle again.",
            lastError,
        )
    }
}

tasks.register("downloadLibXray") {
    dependsOn("downloadLibv2ray")
}

tasks.named("preBuild") {
    dependsOn("downloadLibv2ray")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(files("libs/libv2ray.aar"))
    implementation(fileTree(mapOf(
        "dir" to "libs",
        "include" to listOf("*.jar")
    )))
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
}
