package com.nova.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v7.136: JARVIS voice network + playback (replaces the old online voice). Add-only fallback chain kept: Jarvis -> Ultron-effect voice -> normal Android voice.
 * The token (optional) lives ONLY in SecureStore slot "jarvis"; it is sent only to the host of the saved server address, in the Authorization header,
 * never in a URL, a log line, ErrLog or a report. The spoken text is never logged.
 */
object JarvisVoiceClient {
    const val SLOT = "jarvis"
    private const val CONNECT_MS = 5000
    private const val READ_MS = 20000

    class Net(val http: Int, val body: ByteArray)

    private fun readCapped(s: java.io.InputStream, cap: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        s.use { i -> while (out.size() < cap) { val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n) } }
        return out.toByteArray()
    }

    /** One HTTP call to the saved server. Never throws: 0 = bad address, 598 = no internet / refused, 599 = other error / timeout. */
    fun call(base: String, token: String, method: String, url: String, json: String?, maxBody: Int = JarvisVoiceApi.MAX_AUDIO): Net {
        var conn: HttpURLConnection? = null
        try {
            val clean = JarvisVoiceApi.cleanBase(base)
            if (clean.isEmpty()) return Net(0, ByteArray(0))
            val u = URL(url)
            if (JarvisVoiceApi.host(clean) != u.host.lowercase() || !url.startsWith(clean)) return Net(0, ByteArray(0))   // the token only goes to the saved server
            val c = u.openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = method
            c.connectTimeout = CONNECT_MS
            c.readTimeout = READ_MS
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "audio/wav, application/json")
            if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer " + token)
            if (json != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val s = if (code in 200..299) c.inputStream else c.errorStream
            return Net(code, if (s == null) ByteArray(0) else readCapped(s, maxBody))
        } catch (e: java.net.UnknownHostException) { return Net(598, ByteArray(0)) }
        catch (e: java.net.ConnectException) { return Net(598, ByteArray(0)) }
        catch (e: Exception) { return Net(599, ByteArray(0)) }
        finally { try { conn?.disconnect() } catch (e: Exception) { } }
    }

    /** Result of one speech request. bytes = a wav file. why = short owner-readable reason when bytes == null. */
    class Speech(val bytes: ByteArray?, val why: String)

    fun speech(base: String, token: String, text: String): Speech {
        val r = call(base, token, "POST", JarvisVoiceApi.ttsUrl(JarvisVoiceApi.cleanBase(base)), JarvisVoiceApi.body(text))
        if (r.http !in 200..299) return Speech(null, JarvisVoiceApi.reason(r.http))
        if (!JarvisVoiceApi.isWav(r.body)) return Speech(null, JarvisVoiceApi.reason(599))
        return Speech(r.body, "ok")
    }

    /** Owner-run check (Settings > JARVIS CHECK). Report has only PASS / FAIL / NOT TESTED lines: no token, no address, no audio. */
    fun check(ctx: Context, base: String): String {
        val sb = StringBuilder()
        fun line(tag: String, name: String, why: String) { sb.append(tag).append("  ").append(name).append(if (why.isEmpty()) "" else " - $why").append('\n') }
        sb.append("JARVIS CHECK (v7.136)\n")
        val clean = JarvisVoiceApi.cleanBase(base)
        if (clean.isEmpty()) { line("FAIL", "Server address", "https://... address save nahi hai ya galat hai"); return sb.toString() }
        line("PASS", "Server address format", "host = " + JarvisVoiceApi.host(clean))
        val token = SecureStore.getSlot(ctx, SLOT)
        line(if (token.isEmpty()) "NOT TESTED" else "PASS", "Token saved (encrypted)", if (token.isEmpty()) "token nahi hai: sirf tab theek hai jab server bina token ka ho" else "")
        val h = call(clean, token, "GET", JarvisVoiceApi.healthUrl(clean), null, 20000)
        val ok = h.http in 200..299 && try { JSONObject(String(h.body, Charsets.UTF_8)).optBoolean("ok", false) } catch (e: Exception) { false }
        if (!ok) { line("FAIL", "Server health (GET /health)", JarvisVoiceApi.reason(h.http)); line("NOT TESTED", "Speech", "health fail"); return sb.toString() }
        line("PASS", "Server health (GET /health)", "")
        val t0 = System.currentTimeMillis()
        val sp = speech(clean, token, "The workshop is ready.")
        val ms = System.currentTimeMillis() - t0
        if (sp.bytes == null) { line("FAIL", "Speech (POST /tts)", sp.why); return sb.toString() }
        line("PASS", "Speech received (wav)", sp.bytes.size.toString() + " bytes, " + ms + " ms")
        line(if (ms <= 6000) "PASS" else "FAIL", "Speed", if (ms <= 6000) "6 second se kam" else "bahut dheema: GPU wala server chahiye")
        line("NOT TESTED", "Awaaz kaisi hai", "sirf kaan se jaanch hoti hai: English me NOVA se kuch bulwa ke suno")
        return sb.toString()
    }
}

/** Plays Jarvis audio for NovaService. All failures are reported to the caller, which falls back to the existing voices. */
object JarvisSpeaker {
    /** Every stop() moves the generation on; a running speak() that started in an older generation is cancelled (no race with a newer reply). */
    @Volatile private var gen = 0
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var latch: CountDownLatch? = null
    @Volatile private var pausedUntil = 0L
    @Volatile var lastWhy = ""

    const val FAILED = 0
    const val DONE = 1
    const val STOPPED = 2

    fun stop() {
        gen++
        val p = player
        try { p?.stop() } catch (e: Throwable) { }
        latch?.countDown()
    }

    fun pause(now: Long = System.currentTimeMillis()) { pausedUntil = now + JarvisVoiceApi.PAUSE_MS }
    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = now < pausedUntil
    fun resume() { pausedUntil = 0L }

    /** The generation as it is NOW. The caller reads it on ITS thread before starting the worker, so a STOP in the gap is not lost. */
    fun generation(): Int = gen

    /** Pure decision (JUnit): should this reply be spoken by Jarvis? Needs a valid server address; English text only. */
    fun wants(on: Boolean, url: String, anyLang: Boolean, uiLangEn: Boolean, text: String, maxChars: Int, paused: Boolean): Boolean {
        if (!on || paused) return false
        if (JarvisVoiceApi.cleanBase(url).isEmpty()) return false
        if (text.isBlank() || JarvisVoiceApi.tooLong(text, maxChars)) return false
        if (!uiLangEn && !anyLang) return false
        return JarvisVoiceApi.englishOk(text)
    }

    /** Cheap checks before NovaService asks for a Jarvis voice (no network). */
    fun eligible(ctx: Context, cfg: Cfg, text: String, now: Long = System.currentTimeMillis()): Boolean =
        wants(cfg.jarvisOn, cfg.jarvisUrl, cfg.jarvisAnyLang, cfg.lang == "en", text, cfg.jarvisMaxChars, isPaused(now))

    /**
     * Blocking (worker thread): request audio, then play it. FAILED = nothing was played, caller uses the existing voice.
     * [startGen] = generation read on the calling thread. [onPlayStart] runs just before the sound starts (HUD mouth).
     */
    fun speak(ctx: Context, cfg: Cfg, text: String, startGen: Int, onPlayStart: () -> Unit): Int {
        val my = startGen
        val cancelled = { gen != my }
        val token = SecureStore.getSlot(ctx, JarvisVoiceClient.SLOT)
        val sp = try { JarvisVoiceClient.speech(cfg.jarvisUrl, token, text) } catch (e: Throwable) { JarvisVoiceClient.Speech(null, "error") }
        if (cancelled()) return STOPPED
        if (sp.bytes == null) {
            lastWhy = sp.why
            ErrLog.addOnce("Jarvis", sp.why)
            pause()
            return FAILED
        }
        val f = File(ctx.cacheDir, "jarvis_" + System.nanoTime() + ".wav")
        var mp: MediaPlayer? = null
        try {
            f.writeBytes(sp.bytes)
            val done = CountDownLatch(1)
            latch = done
            var ok = false
            mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setDataSource(f.absolutePath)
            mp.setOnCompletionListener { ok = true; done.countDown() }
            mp.setOnErrorListener { _, _, _ -> done.countDown(); true }
            mp.prepare()
            if (cancelled()) return STOPPED
            player = mp
            onPlayStart()
            mp.start()
            val maxMs = mp.duration.toLong().coerceAtLeast(1000L) + 4000L
            done.await(maxMs, TimeUnit.MILLISECONDS)
            return if (cancelled()) STOPPED else if (ok) DONE else { lastWhy = "playback fail"; ErrLog.addOnce("Jarvis", "playback fail"); pause(); FAILED }
        } catch (e: Throwable) {
            lastWhy = "playback fail"
            ErrLog.addOnce("Jarvis", "playback " + e.javaClass.simpleName)
            pause()
            return if (cancelled()) STOPPED else FAILED
        } finally {
            player = null
            try { mp?.release() } catch (e: Throwable) { }
            try { f.delete() } catch (e: Throwable) { }
        }
    }
}
