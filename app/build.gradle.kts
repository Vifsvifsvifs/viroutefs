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
val baseVersionName = "0.12.0-beta.1"
val appVersionName = if (buildNumber > 0) "$baseVersionName.$buildNumber" else baseVersionName
val appVersionCode = 12001 + buildNumber

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
            // ByeDPI is launched as an app-private executable from nativeLibraryDir.
            useLegacyPackaging = true
        }
    }
}

val libBoxVersion = "v1.14.0-alpha.50-viroutefs-arm64-openvpn-openconnect-no-naive-16k"
val libBoxSha256 = "f3729b42c247c257adc2c7d03b1134ed7139b6f77da174f69f036d4fa4c7b685"
val libBoxFile = file("libs/libbox.aar")
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

tasks.named("preBuild") {
    dependsOn("verifyLibbox", "verifyByeDpi")
}

tasks.register("printVersionName") {
    doLast { println(appVersionName) }
}

tasks.register("printVersionCode") {
    doLast { println(appVersionCode) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20240303")
}
