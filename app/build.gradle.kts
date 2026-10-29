plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Offline Vosk model is fetched automatically before every build (no manual download step).
apply(from = rootProject.file("gradle/vosk-model.gradle.kts"))
// Fixed signing key (step B). CI passes the keystore through environment variables; without them the normal
// throwaway debug key is used (local builds, pull requests from forks). An in-app APK update only works when
// the installed app and the new APK are signed with the SAME key.
val novaKsPath: String? = System.getenv("NOVA_KEYSTORE_PATH")
val novaKsPass: String? = System.getenv("NOVA_KEYSTORE_PASSWORD")
val novaKsAlias: String? = System.getenv("NOVA_KEY_ALIAS")
val novaKeyPass: String? = System.getenv("NOVA_KEY_PASSWORD") ?: novaKsPass
val haveNovaKeystore: Boolean =
    !novaKsPath.isNullOrBlank() && file(novaKsPath).exists() &&
        !novaKsPass.isNullOrBlank() && !novaKsAlias.isNullOrBlank()

android {
    namespace = "com.nova.assistant"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nova.assistant"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("NOVA_VERSION_CODE") ?: "37").toIntOrNull() ?: 37
        versionName = "7.138"
        // The offline engine (llama.cpp) ships arm64-v8a + x86_64; real phones are arm64. Keeps the APK smaller.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    signingConfigs {
        if (haveNovaKeystore) {
            create("nova") {
                storeFile = file(novaKsPath ?: "")
                storePassword = novaKsPass
                keyAlias = novaKsAlias
                keyPassword = novaKeyPass
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (haveNovaKeystore) signingConfig = signingConfigs.getByName("nova")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
// Some transitive libraries pull androidx.core 1.15.0, which needs compileSdk 35. This project is on compileSdk 34 (AGP 8.5.2),
// so keep every androidx.core artifact on 1.13.1.
configurations.all {
    resolutionStrategy {
        force("androidx.core:core:1.13.1", "androidx.core:core-ktx:1.13.1")
    }
}
dependencies {
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.1.0")
    implementation("ai.picovoice:porcupine-android:3.0.2")   // v6.56: optional better wake word (needs the owner's AccessKey + nova.ppn); Vosk stays as fallback
    implementation("androidx.fragment:fragment:1.6.2")
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.work:work-runtime:2.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // v6.86: the real org.json for unit tests (the android.jar one only has stubs that throw)
}
