package com.nova.assistant

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v7.154 JARVIS UPGRADE — the offline neural deep voice.
 *
 * What this is: the open-source Piper voice "en_GB-alan-medium" (a deep male British voice) running fully OFFLINE
 * on the phone through sherpa-onnx (MIT licence, Apache-2.0 models). No API key, no server, no movie recordings,
 * no cloned actor voice - it is a legally usable open model.
 *
 * Rules (add-only):
 *  - the model (20 MB tar.bz2) is downloaded ONLY when the owner asks for it, resumable + SHA-256 verified,
 *    extracted safely by [TarBz] (path-traversal safe, size limited);
 *  - the engine stays loaded while the feature is ON; it is released when the feature is turned OFF;
 *  - playback is STREAMED (generation callback -> AudioTrack), so speech starts before the whole sentence is
 *    generated and STOP interrupts generation immediately (callback returns 0);
 *  - ANY failure returns FAILED and the caller falls back to the existing v7.153 voice chain
 *    (Jarvis server -> JarvisLocal -> Ultron -> normal TTS). Nothing from the old behaviour is removed.
 *
 * Honest evidence: written + contract-checked; NOT compiled here (no Android SDK in this sandbox),
 * NOT tested on a phone yet - see OWNER_PHONE_TEST_LIST_v7_154.md.
 */
object JarvisDeepVoice {
    // Verified 2026-10-30: this exact file was downloaded and its SHA-256 computed from the sherpa-onnx release.
    const val URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium-int8.tar.bz2"
    const val SHA256 = "f0b2526c9600657141ccbbe412f6159dceedab5bab4ba4e8857d9443acb50de4"
    const val SIZE = 21103831L
    const val MODEL_ID = "jarvis-deep-alan"
    private const val DIR_NAME = "vits-piper-en_GB-alan-medium-int8"
    const val MODEL_FILE = "en_GB-alan-medium.onnx"
    private const val TOKENS_FILE = "tokens.txt"
    private const val ESPEAK_DIR = "espeak-ng-data"

    const val FAILED = 0      // nothing was played: the caller falls back to the existing voice chain
    const val DONE = 1
    const val STOPPED = 2

    private val installing = AtomicBoolean(false)

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var stopFlag = false
    @Volatile private var track: AudioTrack? = null

    fun root(ctx: Context): File = File(ctx.applicationContext.filesDir, "jarvis-deep")
    fun modelDir(ctx: Context): File = File(root(ctx), DIR_NAME)

    /** true when the model files are installed and look complete (cheap checks, no engine load). */
    fun ready(ctx: Context): Boolean {
        val d = modelDir(ctx)
        return File(d, MODEL_FILE).length() > 15_000_000L &&
            File(d, TOKENS_FILE).length() in 100..200_000L &&
            File(d, ESPEAK_DIR).isDirectory
    }

    /** One short line for Settings / SELF TEST. Never loads the engine. */
    fun infoLine(ctx: Context): String =
        if (ready(ctx)) "deep voice model: installed (" + (File(modelDir(ctx), MODEL_FILE).length() / 1048576L) + " MB)"
        else "deep voice model: not downloaded (20 MB, bolo: nova deep voice download karo)"

    /**
     * Download + extract the model (worker thread only). Progress 0..100 via [onProgress].
     * Returns null on success or a short failure reason. A failed/partial download is NEVER used:
     * checksum mismatch deletes the file, a bad archive deletes the directory.
     */
    fun install(ctx: Context, onProgress: (Int) -> Unit, cancelled: () -> Boolean): String? {
        val app = ctx.applicationContext
        if (ready(app)) return null
        if (!installing.compareAndSet(false, true)) return "already downloading"
        try {
            val part = File(root(app), "voice.tar.bz2.part")
            root(app).mkdirs()
            when (val r = ModelDownload.download(URL, part, SIZE, SHA256, { u -> u.startsWith("https://") }, cancelled, onProgress)) {
                is ModelDownload.Result.Fail -> {
                    val resumableNote = if (r.resumable) " (resume: dobara bolo)" else ""
                    return r.reason + resumableNote
                }
                is ModelDownload.Result.Ok -> {
                    val ex = TarBz.extract(part, root(app))
                    if (ex is TarBz.Outcome.Fail) {
                        root(app).deleteRecursively()
                        return "extract failed: " + ex.reason
                    }
                    if (!ready(app)) {
                        root(app).deleteRecursively()
                        return "archive did not contain the voice files"
                    }
                    part.delete()
                    return null
                }
            }
        } finally {
            installing.set(false)
        }
    }

    /** Free the engine (when the feature is turned OFF or the service dies). Never throws. */
    fun release() {
        try { engine?.release() } catch (e: Throwable) { ErrLog.addOnce("DeepVoice", "release " + e.javaClass.simpleName) }
        engine = null
    }

    fun stop() {
        stopFlag = true
        val t = track
        if (t != null) try { t.pause(); t.flush() } catch (e: Throwable) { ErrLog.add("DeepVoice", e.toString()) }
    }

    /**
     * Speak [text] with the deep voice, streaming, interruptible. BLOCKING - worker thread only.
     * [rate] = the owner's speech-rate setting (0.6..1.6) mapped to the model speed.
     * Returns DONE / STOPPED / FAILED (FAILED always falls back to the old voice chain).
     */
    @SuppressLint("MissingPermission")
    fun speak(ctx: Context, text: String, rate: Float, onPlayStart: () -> Unit): Int {
        val clean = JarvisDeepRules.clean(text)
        if (clean.isBlank()) return FAILED
        val app = ctx.applicationContext
        if (!ready(app)) return FAILED
        stopFlag = false
        val eng = obtain(app) ?: return FAILED
        val speed = rate.coerceIn(0.7f, 1.25f)
        var t: AudioTrack? = null
        var playedAny = false
        try {
            val sampleRate = eng.sampleRate
            if (sampleRate < 8000) return FAILED
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val fmt = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            if (minBuf <= 0) return FAILED
            t = AudioTrack(attr, fmt, maxOf(minBuf, 16384), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return FAILED }
            track = t
            t.play()
            var res = DONE
            var written = 0
            eng.generateWithCallback(text = clean, sid = 0, speed = speed) { samples, _ ->
                if (stopFlag) return@generateWithCallback 0
                if (!playedAny) { playedAny = true; onPlayStart() }
                var pos = 0
                while (pos < samples.size && !stopFlag) {
                    // light chunk-safe warmth: a fixed soft drive, no state between chunks
                    val out = FloatArray(samples.size - pos)
                    for (i in out.indices) out[i] = warm(samples[pos + i])
                    val w = t.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
                    if (w <= 0) break
                    pos += w
                    written += w
                }
                if (stopFlag) 0 else 1
            }
            if (stopFlag) res = STOPPED
            // wait until the last written frame really played (or stopped / out of time)
            val deadline = System.currentTimeMillis() + 60_000L
            while (!stopFlag && t.playbackHeadPosition < written && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(20) } catch (e: InterruptedException) { break }
            }
            return res
        } catch (e: Throwable) {
            ErrLog.addOnce("DeepVoice", "speak " + e.javaClass.simpleName)
            return if (playedAny) (if (stopFlag) STOPPED else DONE) else FAILED
        } finally {
            track = null
            try { t?.stop() } catch (e: Throwable) { }
            try { t?.release() } catch (e: Throwable) { }
        }
    }

    /** Fixed soft saturation: adds warmth without any cross-chunk state (streaming-safe). */
    private fun warm(s: Float): Float = kotlin.math.tanh(1.15f * s) * 0.95f

    /** Loads the engine once and keeps it; any load failure disables this voice for the session (FAILED falls back). */
    private fun obtain(ctx: Context): OfflineTts? {
        engine?.let { return it }
        return try {
            val dir = modelDir(ctx)
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(dir, MODEL_FILE).absolutePath,
                        lexicon = "",
                        tokens = File(dir, TOKENS_FILE).absolutePath,
                        dataDir = File(dir, ESPEAK_DIR).absolutePath,
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = 1,
            )
            val e = OfflineTts(assetManager = null, config = config)
            engine = e
            e
        } catch (e: Throwable) {
            ErrLog.addOnce("DeepVoice", "load " + e.javaClass.simpleName + ": " + (e.message ?: ""))
            null
        }
    }
}
