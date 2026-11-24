import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: set all four ESL_* environment variables to sign with your own key
// (same variables as apps/clock). Without them the release APK is signed with the debug key,
// which is fine for sideloading.
val releaseKeystore: String? = providers.environmentVariable("ESL_KEYSTORE").orNull
val releaseKeystorePass: String? = providers.environmentVariable("ESL_KEYSTORE_PASS").orNull
val releaseKeyAlias: String? = providers.environmentVariable("ESL_KEY_ALIAS").orNull
val releaseKeyPass: String? = providers.environmentVariable("ESL_KEY_PASS").orNull
val hasReleaseKey = listOf(releaseKeystore, releaseKeystorePass, releaseKeyAlias, releaseKeyPass)
    .all { !it.isNullOrEmpty() }

android {
    namespace = "ai.pocket.heyclaude"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.pocket.heyclaude"
        minSdk = 26
        // LineageOS 18.1 on the Echo Spot is Android 11 (API 30).
        targetSdk = 30
        versionCode = 10
        versionName = "1.0.0"
        // The Echo Spot runs 32-bit ARM; leaving out the other ABIs shrinks the APK a lot.
        ndk { abiFilters += "armeabi-v7a" }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseKeystorePass
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sideload-only app: without ESL_* variables, sign release with the debug key so
            // `assembleRelease` yields an installable APK without a keystore.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        noCompress += "onnx"
    }

    lint {
        // targetSdk 30 is intentional (device runs Android 11); don't fail on
        // Play-Store-oriented checks such as ExpiredTargetSdkVersion.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // Offline speech-to-text for music commands (model pushed separately, see VoiceCommands).
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation("junit:junit:4.13.2")
}

// ---------------------------------------------------------------------------
// openWakeWord models (CC BY-NC-SA 4.0) are not committed. They are fetched
// into src/main/assets/models/ before every build; files that already exist
// are skipped. Trained wake/stop models (hey_claude.onnx, danke_claude.onnx,
// hey_chatgpt.onnx, danke_chatgpt.onnx) can be dropped next to them, or pushed
// to the app's external files dir at runtime — they are never downloaded.
// ---------------------------------------------------------------------------
val wakeWordModelBaseUrl = "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1"
val wakeWordModels = listOf(
    "melspectrogram.onnx",
    "embedding_model.onnx",
    "hey_jarvis_v0.1.onnx",
    "alexa_v0.1.onnx",
)
val wakeWordModelDir: File = file("src/main/assets/models")

fun downloadWithCurl(url: String, target: File): Boolean {
    return try {
        val proc = ProcessBuilder(
            "curl", "-fsSL", "--retry", "3", "--connect-timeout", "20",
            "-o", target.absolutePath, url,
        ).inheritIO().start()
        proc.waitFor() == 0 && target.isFile && target.length() > 0
    } catch (e: Exception) {
        false
    }
}

fun downloadWithJava(url: String, target: File): Boolean {
    return try {
        val conn = uri(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        conn.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
        target.isFile && target.length() > 0
    } catch (e: Exception) {
        logger.warn("Java download of $url failed: ${e.message}")
        false
    }
}

val downloadWakeWordModels by tasks.registering {
    group = "spot voice"
    description = "Downloads the openWakeWord ONNX models into src/main/assets/models/."
    doLast {
        wakeWordModelDir.mkdirs()
        for (name in wakeWordModels) {
            val target = File(wakeWordModelDir, name)
            if (target.isFile && target.length() > 0) continue
            val url = "$wakeWordModelBaseUrl/$name"
            val tmp = File(wakeWordModelDir, "$name.part")
            tmp.delete()
            logger.lifecycle("Downloading $url")
            // curl first: it honours HTTPS_PROXY and the system trust store
            // (macOS ships it). Plain JVM download is the fallback.
            val ok = downloadWithCurl(url, tmp) || downloadWithJava(url, tmp)
            if (!ok || tmp.length() < 1024) {
                tmp.delete()
                throw GradleException(
                    "Could not download $url — download it manually into ${wakeWordModelDir.path}",
                )
            }
            if (!tmp.renameTo(target)) {
                throw GradleException("Could not move ${tmp.path} to ${target.path}")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn(downloadWakeWordModels)
}
