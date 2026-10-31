pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        // v7.154 JARVIS UPGRADE: sherpa-onnx (offline neural TTS) is published via JitPack, not Maven Central.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "NOVA-Voice-Assistant"
include(":app")
