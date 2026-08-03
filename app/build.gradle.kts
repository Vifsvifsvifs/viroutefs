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
    ?.takeIf(String::isNotBlank)
    ?: "https://messenger.online.sberbank.ru/sl/PV0SJRfgsEARtx5Ka"
val baseVersionName = "0.14.0-beta.7"
val appVersionName = if (buildNumber > 0) "$baseVersionName.$buildNumber" else baseVersionName
val appVersionCode = 14007 + buildNumber

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
        isCoreLibraryDesugaringEnabled = true
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
            // ByeDPI, Xray and optional root-only engines are app-private
            // executables in nativeLibraryDir.
            useLegacyPackaging = true
            // Xray-core is a PIE command stored under jniLibs so Android extracts
            // it as an executable. Keep the verified byte-for-byte artifact.
            keepDebugSymbols += setOf(
                "**/libxray.so",
                "**/libzapret2.so",
                "**/libtcpdump.so",
                "**/libwg.so",
                "**/libwg-quick.so",
                "**/libwg-go.so",
            )
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
val zapret2Version = "v1.0.4-2c21faa80e1acb71ddceb8b49176f266b7d33f05-android-arm64-16k"
val zapret2Sha256 = "2e1a0e950e0bc7189b5662e54fdd66d749d51215b167a647f15659554e7b4090"
val zapret2File = file("src/main/jniLibs/arm64-v8a/libzapret2.so")
val tcpdumpVersion = "tcpdump-4.99.6-libpcap-1.10.6-android-arm64-16k"
val tcpdumpSha256 = "adb46aa539d42efb6d07c1afc42edc39954fd59a46c09561411bb98bb176c4da"
val tcpdumpFile = file("src/main/jniLibs/arm64-v8a/libtcpdump.so")
val wireGuardTunnelVersion = "1.0.20260102"
val wireGuardTunnelAarSha256 = "2b9c16db026496123e4db695d26d03d1958a201096c7c4c89b21077dc70f3119"
val wireGuardTunnelVerification = configurations.detachedConfiguration(
    dependencies.create("com.wireguard.android:tunnel:$wireGuardTunnelVersion"),
).apply {
    isTransitive = false
}

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

tasks.register("verifyZapret2") {
    inputs.file(zapret2File)
    doLast {
        check(zapret2File.exists() && zapret2File.length() > 0L) {
            "Missing ${zapret2File.relativeTo(projectDir)} ($zapret2Version)."
        }
        check(zapret2File.sha256() == zapret2Sha256) {
            "${zapret2File.relativeTo(projectDir)} does not match pinned zapret2 $zapret2Version SHA-256."
        }
        println("Verified ${zapret2File.relativeTo(projectDir)} as zapret2 $zapret2Version.")
    }
}

tasks.register("verifyTcpdump") {
    inputs.file(tcpdumpFile)
    doLast {
        check(tcpdumpFile.exists() && tcpdumpFile.length() > 0L) {
            "Missing ${tcpdumpFile.relativeTo(projectDir)} ($tcpdumpVersion)."
        }
        check(tcpdumpFile.sha256() == tcpdumpSha256) {
            "${tcpdumpFile.relativeTo(projectDir)} does not match pinned $tcpdumpVersion SHA-256."
        }
        println("Verified ${tcpdumpFile.relativeTo(projectDir)} as $tcpdumpVersion.")
    }
}

tasks.register("verifyWireGuardTunnel") {
    doLast {
        val artifact = wireGuardTunnelVerification.singleFile
        check(artifact.exists() && artifact.length() > 0L) {
            "Missing official WireGuard tunnel AAR $wireGuardTunnelVersion."
        }
        check(artifact.sha256() == wireGuardTunnelAarSha256) {
            "WireGuard tunnel AAR does not match pinned $wireGuardTunnelVersion SHA-256."
        }
        println("Verified official WireGuard tunnel AAR $wireGuardTunnelVersion.")
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
    dependsOn("verifyLibbox", "verifyLibXray", "verifyByeDpi", "verifyZapret2", "verifyTcpdump", "verifyWireGuardTunnel")
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
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
    implementation("com.wireguard.android:tunnel:$wireGuardTunnelVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
}
