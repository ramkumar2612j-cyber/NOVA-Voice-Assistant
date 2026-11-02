// Automatic Vosk model setup: nobody has to download or unzip anything by hand.
// Every Gradle build (Android Studio sync/run, ./gradlew, GitHub Actions) first makes sure
// app/src/main/assets/model-en holds the official small US-English Vosk model. If it is already there and valid,
// the task does nothing (offline builds keep working). Pure JVM: no bash, curl or unzip needed, so it also works on Windows.
// Optional override (mirror / offline copy):  NOVA_VOSK_MODEL_URL=https://... or file:///path/model.zip
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

val voskModelUrl: String = System.getenv("NOVA_VOSK_MODEL_URL")?.takeIf { it.isNotBlank() }
    ?: "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
val voskModelDir: File = file("src/main/assets/model-en")
val voskRequired = listOf("am/final.mdl", "graph/HCLr.fst", "graph/Gr.fst")

fun voskValid(d: File): Boolean = voskRequired.all { File(d, it).let { f -> f.isFile && f.length() > 0L } }

val prepareVoskModel = tasks.register("prepareVoskModel") {
    group = "nova"
    description = "Downloads and validates the offline Vosk model automatically (skipped when already present)."
    // Up-to-date when the model is already valid, so repeated builds cost nothing.
    outputs.upToDateWhen { voskValid(voskModelDir) }
    doLast {
        if (voskValid(voskModelDir)) return@doLast
        val work = Files.createTempDirectory("nova-vosk").toFile()
        try {
            val zipFile = File(work, "model.zip")
            var lastError: Exception? = null
            var downloaded = false
            for (attempt in 1..3) {
                try {
                    logger.lifecycle("NOVA: downloading Vosk model (try $attempt/3) from $voskModelUrl")
                    val conn = URL(voskModelUrl).openConnection()
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 60_000
                    conn.getInputStream().use { input -> Files.copy(input, zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                    downloaded = zipFile.length() > 0L
                    if (downloaded) break
                } catch (e: Exception) {
                    lastError = e
                    Thread.sleep(2_000L * attempt)
                }
            }
            if (!downloaded) {
                throw GradleException("NOVA: could not download the Vosk model automatically (" + (lastError?.message ?: "empty file") +
                    "). Check the internet connection and run the build again, or set NOVA_VOSK_MODEL_URL to a mirror.")
            }
            // Safe unzip (blocks '../' path escape).
            val extracted = File(work, "extracted").apply { mkdirs() }
            val extractedCanon = extracted.canonicalPath + File.separator
            ZipInputStream(zipFile.inputStream().buffered()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val out = File(extracted, e.name)
                    if (!out.canonicalPath.startsWith(extractedCanon)) throw GradleException("NOVA: unsafe path in Vosk zip: ${e.name}")
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile.mkdirs()
                        Files.copy(zin, out.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
            // The archive holds one top-level folder (vosk-model-...); accept the root too.
            val source = extracted.listFiles()?.filter { it.isDirectory }?.firstOrNull { voskValid(it) }
                ?: extracted.takeIf { voskValid(it) }
                ?: throw GradleException("NOVA: downloaded Vosk archive is missing required files " + voskRequired + "; refusing to package it.")
            voskModelDir.deleteRecursively()
            voskModelDir.parentFile.mkdirs()
            source.copyRecursively(voskModelDir, overwrite = true)
            if (!voskValid(voskModelDir)) {
                voskModelDir.deleteRecursively()
                throw GradleException("NOVA: staged Vosk model failed validation.")
            }
            logger.lifecycle("NOVA: Vosk model ready at app/src/main/assets/model-en")
        } finally {
            work.deleteRecursively()
        }
    }
}

// Run before anything else in the Android build, so assets are complete for every variant and for unit tests.
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(prepareVoskModel) }
