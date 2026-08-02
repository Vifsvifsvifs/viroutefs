import java.io.File
import java.security.MessageDigest

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
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0
val donationUrl = providers.environmentVariable("VIROUTEFS_DONATION_URL")
    .orElse(providers.gradleProperty("viroutefsDonationUrl"))
    .orNull
    ?.trim()
    .orEmpty()
val baseVersionName = "0.14.0-beta.2"
val appVersionName = if (buildNumber > 0) "$baseVersionName.$buildNumber" else baseVersionName
val appVersionCode = 14002 + buildNumber

android {
    namespace = "dev.vifs.viroutefs"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.vifs.viroutefs"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField(
            "String",
            "DONATION_URL",
            "\"${donationUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )

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
            if (hasAlphaSigning) {
                signingConfig = signingConfigs.getByName("alpha")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // ByeDPI and Xray are app-private executables in nativeLibraryDir.
            useLegacyPackaging = true
            // Xray-core is a PIE command stored under jniLibs so Android extracts
            // it as an executable. Keep the verified byte-for-byte artifact.
            keepDebugSymbols += "**/libxray.so"
        }
    }
}

val libBoxVersion = "v1.14.0-alpha.50-viroutefs-arm64-openvpn-openconnect-no-naive-16k"
val libBoxSha256 = "f3729b42c247c257adc2c7d03b1134ed7139b6f77da174f69f036d4fa4c7b685"
val libBoxFile = file("libs/libbox.aar")
val xrayVersion = "xray-core-94ffd50060f1-arm64"
val xraySha256 = "9bb0b815086395164066b5fa27b1797bf9a0fcc493d1491f02166560604dcaff"
val xrayFile = file("src/main/jniLibs/arm64-v8a/libxray.so")
val byeDpiVersion = "ba532298de7b28cfe854aea83d061369d13ca290-arm64-16k"
val byeDpiSha256 = "abae93da6e426da5bbe5611f53a550eccb021d7be88b2c13865461024c4862d1"
val byeDpiFile = file("src/main/jniLibs/arm64-v8a/libbyedpi.so")

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

repositories {
    google()
    mavenCentral()
    flatDir {
        dirs("libs")
    }
}

tasks.register("verifyLibbox") {
    inputs.file(libBoxFile)
    doLast {
        check(libBoxFile.exists() && libBoxFile.length() > 0L) {
            "Missing ${libBoxFile.relativeTo(projectDir)} ($libBoxVersion)."
        }
        check(libBoxFile.sha256() == libBoxSha256) {
            "${libBoxFile.relativeTo(projectDir)} does not match the pinned $libBoxVersion SHA-256."
        }
        println("Verified ${libBoxFile.relativeTo(projectDir)} as $libBoxVersion.")
    }
}

tasks.register("verifyByeDpi") {
    inputs.file(byeDpiFile)
    doLast {
        check(byeDpiFile.exists() && byeDpiFile.length() > 0L) {
            "Missing ${byeDpiFile.relativeTo(projectDir)} ($byeDpiVersion)."
        }
        check(byeDpiFile.sha256() == byeDpiSha256) {
            "${byeDpiFile.relativeTo(projectDir)} does not match the pinned ByeDPI $byeDpiVersion SHA-256."
        }
        println("Verified ${byeDpiFile.relativeTo(projectDir)} as ByeDPI $byeDpiVersion.")
    }
}

tasks.register("verifyLibXray") {
    inputs.file(xrayFile)
    doLast {
        check(xrayFile.exists() && xrayFile.length() > 0L) {
            "Missing ${xrayFile.relativeTo(projectDir)} ($xrayVersion)."
        }
        check(xrayFile.sha256() == xraySha256) {
            "${xrayFile.relativeTo(projectDir)} does not match the pinned $xrayVersion SHA-256."
        }
        println("Verified ${xrayFile.relativeTo(projectDir)} as $xrayVersion.")
    }
}

tasks.named("preBuild") {
    dependsOn("verifyLibbox", "verifyLibXray", "verifyByeDpi")
}

tasks.register("printVersionName") {
    doLast { println(appVersionName) }
}

tasks.register("printVersionCode") {
    doLast { println(appVersionCode) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    val cameraXVersion = "1.6.1"

    implementation(files("libs/libbox.aar"))
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("com.google.zxing:core:3.5.4")
    implementation("org.snakeyaml:snakeyaml-engine:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
}
