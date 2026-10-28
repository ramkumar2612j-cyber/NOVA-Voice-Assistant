package com.nova.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v7.129: VoiSpark (Ultron voice) network + playback. Add-only: NovaService asks [VoiSparkSpeaker.tryStart]; any failure returns
 * to the existing Ultron-effect voice and then the normal Android voice (nothing is removed).
 * The API key lives ONLY in SecureStore slot "voispark" (encrypted). It is sent only to https://api.voispark.com in the Authorization
 * header; it is never put in a URL, a log line, ErrLog, a report or the page. The spoken text is never logged.
 */
object VoiSparkClient {
    const val SLOT = "voispark"
    private const val MAX_BODY = 400_000
    private const val MAX_AUDIO = 8_000_000
    private const val CONNECT_MS = 5000
    private const val READ_MS = 8000

    class Net(val http: Int, val body: String)

    private fun cleanId(id: String) = id.trim()

    /** One HTTPS call to api.voispark.com. Never throws: 598 = no internet, 599 = other network error / timeout. */
    fun call(key: String, method: String, path: String, json: String?): Net {
        var conn: HttpURLConnection? = null
        try {
            val u = URL(VoiSparkApi.BASE + path)
            if (u.protocol != "https" || u.host != VoiSparkApi.HOST) return Net(0, "")
            val c = u.openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = method
            c.connectTimeout = CONNECT_MS
            c.readTimeout = READ_MS
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Authorization", "Bearer " + key)
            if (json != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val s = if (code in 200..299) c.inputStream else c.errorStream
            return Net(code, if (s == null) "" else readCapped(s, MAX_BODY).toString(Charsets.UTF_8.name()))
        } catch (e: java.net.UnknownHostException) { return Net(598, "") }
        catch (e: java.net.ConnectException) { return Net(598, "") }
        catch (e: Exception) { return Net(599, "") }
        finally { try { conn?.disconnect() } catch (e: Exception) { } }
    }

    private fun readCapped(s: java.io.InputStream, cap: Int): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        s.use { i -> while (out.size() < cap) { val n = i.read(buf); if (n < 0) break; out.write(buf, 0, n) } }
        return out
    }

    /** Audio link from the answer: https only, NO key sent, no redirects, size capped. null = failed. */
    private fun download(url: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            val u = URL(url)
            if (u.protocol != "https") return null
            val c = u.openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = CONNECT_MS
            c.readTimeout = READ_MS
            c.instanceFollowRedirects = false
            if (c.responseCode !in 200..299) null else readCapped(c.inputStream, MAX_AUDIO).toByteArray().takeIf { it.size > 200 }
        } catch (e: Exception) { null } finally { try { conn?.disconnect() } catch (e: Exception) { } }
    }

    /** Result of one speech request. bytes = a playable audio file (format decided by the player). why = short owner-readable reason when bytes == null. */
    class Speech(val bytes: ByteArray?, val why: String)

    /** generate (sync=false) -> poll the task until an audio link/base64 shows up or [budgetMs] is used. */
    fun speech(key: String, voiceId: String, text: String, budgetMs: Long, cancelled: () -> Boolean = { false }): Speech {
        val t0 = System.currentTimeMillis()
        val g = call(key, "POST", VoiSparkApi.generatePath(), VoiSparkApi.generateBody(cleanId(voiceId), text))
        val env = VoiSparkApi.parseEnvelope(g.http, g.body)
        if (!env.ok) return Speech(null, VoiSparkApi.reason(g.http, env))
        VoiSparkApi.findAudio(env.data)?.let { a -> return fromAudio(a) }   // an answer that already carries the audio
        val id = VoiSparkApi.taskId(env) ?: return Speech(null, "task_id nahi mila")
        var wait = 350L
        while (System.currentTimeMillis() - t0 < budgetMs) {
            try { Thread.sleep(wait) } catch (e: InterruptedException) { return Speech(null, "roka gaya") }
            if (cancelled()) return Speech(null, "roka gaya")
            val r = call(key, "GET", VoiSparkApi.taskPath(id), null)
            val e2 = VoiSparkApi.parseEnvelope(r.http, r.body)
            if (!e2.ok) return Speech(null, VoiSparkApi.reason(r.http, e2))
            VoiSparkApi.findAudio(e2.data)?.let { a -> return fromAudio(a) }
            if (VoiSparkApi.looksFailed(e2.data)) return Speech(null, "task fail")
            wait = minOf(900L, wait + 150L)
        }
        return Speech(null, "time out (audio nahi aaya)")
    }

    private fun fromAudio(a: VoiSparkApi.Audio): Speech {
        val b = when {
            a.url != null -> download(a.url)
            a.base64 != null -> try { Base64.decode(a.base64, Base64.DEFAULT) } catch (e: Exception) { null }
            else -> null
        }
        return if (b == null || b.size < 200) Speech(null, "audio download nahi hua") else Speech(b, "ok")
    }

    // ------------------------------------------------------------------ owner-run check (Settings > VOISPARK CHECK)

    /** Real calls with the owner's key. Returns a copy-pasteable report: only PASS / FAIL / NOT TESTED lines, no key, no raw audio, no links. */
    fun check(ctx: Context, voiceId: String): String {
        val sb = StringBuilder()
        fun line(tag: String, name: String, why: String) { sb.append(tag).append("  ").append(name).append(if (why.isEmpty()) "" else " - $why").append('\n') }
        val key = SecureStore.getSlot(ctx, SLOT)
        val vid = cleanId(voiceId)
        sb.append("VOISPARK CHECK (v7.129)\n")
        if (key.isEmpty()) { line("FAIL", "API key", "key save nahi hai (Settings > VOISPARK KEY)"); return sb.toString() }
        line("PASS", "API key saved (encrypted)", "")
        if (!VoiSparkApi.validVoiceId(vid)) { line("FAIL", "Voice ID format", "UUID jaisa nahi"); return sb.toString() }

        val b = call(key, "GET", VoiSparkApi.benefitPath(), null)
        val be = VoiSparkApi.parseEnvelope(b.http, b.body)
        if (be.ok) {
            val d = be.data as? JSONObject
            line("PASS", "Key + credits (GET /api/benefit)", "plan=" + (d?.optString("plan_type", "?") ?: "?") + ", credits=" + (d?.opt("credits_remaining") ?: "?"))
        } else { line("FAIL", "Key + credits (GET /api/benefit)", VoiSparkApi.reason(b.http, be)); return sb.toString() }

        val m = call(key, "GET", VoiSparkApi.modelsPath(vid), null)
        val me = VoiSparkApi.parseEnvelope(m.http, m.body)
        val mHas = me.ok && m.body.contains(VoiSparkApi.MODEL_ID)
        if (me.ok) line(if (mHas) "PASS" else "FAIL", "Voice ID is usable for TTS", if (mHas) "model " + VoiSparkApi.MODEL_ID + " is voice ke liye hai" else "is voice ke models me " + VoiSparkApi.MODEL_ID + " nahi mila")
        else line("FAIL", "Voice ID is usable for TTS", VoiSparkApi.reason(m.http, me))

        val v = call(key, "GET", VoiSparkApi.voicesPath(), null)
        val ve = VoiSparkApi.parseEnvelope(v.http, v.body)
        if (ve.ok) {
            val hit = VoiSparkApi.findVoiceInList(ve.data, vid)
            when {
                hit.found && hit.isUltron -> line("PASS", "Voice ID belongs to Ultron", "naam: " + hit.names.joinToString(" / ").take(120))
                hit.found -> line("FAIL", "Voice ID belongs to Ultron", "ID mili par naam me 'Ultron' nahi: " + hit.names.joinToString(" / ").take(120))
                else -> line("NOT TESTED", "Voice ID belongs to Ultron", "ID pehli list me nahi dikhi (list ka page/filter docs me nahi tha); shape=" + VoiSparkApi.shape(ve.data).take(200))
            }
        } else line("FAIL", "Voice list (GET /api/v2/voices)", VoiSparkApi.reason(v.http, ve))

        // one tiny real speech: 6 characters = 24 credits
        val t0 = System.currentTimeMillis()
        val g = call(key, "POST", VoiSparkApi.generatePath(), VoiSparkApi.generateBody(vid, "नमस्ते"))
        val ge = VoiSparkApi.parseEnvelope(g.http, g.body)
        if (!ge.ok) { line("FAIL", "Generate speech", VoiSparkApi.reason(g.http, ge)); line("NOT TESTED", "Hindi / playback / latency", "generate nahi chala"); return sb.toString() }
        line("PASS", "Generate speech accepted (sync=false)", "answer shape=" + VoiSparkApi.shape(ge.data).take(200))
        val id = VoiSparkApi.taskId(ge)
        if (id == null) { line("FAIL", "task_id in answer", "nahi mila"); return sb.toString() }
        var lastShape = ""
        var audio: VoiSparkApi.Audio? = null
        val end = t0 + 20000L
        while (System.currentTimeMillis() < end && audio == null) {
            try { Thread.sleep(500) } catch (e: InterruptedException) { break }
            val r = call(key, "GET", VoiSparkApi.taskPath(id), null)
            val re = VoiSparkApi.parseEnvelope(r.http, r.body)
            if (!re.ok) { line("FAIL", "Poll task", VoiSparkApi.reason(r.http, re)); return sb.toString() }
            lastShape = VoiSparkApi.shape(re.data)
            audio = VoiSparkApi.findAudio(re.data)
            if (VoiSparkApi.looksFailed(re.data)) break
        }
        val ms = System.currentTimeMillis() - t0
        if (audio == null) { line("FAIL", "Audio found in finished task", "20s me audio nahi mila; task shape=" + lastShape.take(240)); return sb.toString() }
        val sp = fromAudio(audio)
        if (sp.bytes == null) { line("FAIL", "Audio download", sp.why); return sb.toString() }
        line("PASS", "Audio received", sp.bytes.size.toString() + " bytes, " + ms + " ms (generate se audio tak, 6 akshar)")
        line("NOT TESTED", "Hindi pronunciation", "sirf kaan se jaanch hoti hai: NOVA ko Hindi me bulwa ke suno")
        return sb.toString()
    }
}

/** Plays VoiSpark audio for NovaService. All failures are reported to the caller, which falls back to the existing voices. */
object VoiSparkSpeaker {
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

    fun pause(now: Long = System.currentTimeMillis()) { pausedUntil = now + VoiSparkApi.PAUSE_MS }
    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = now < pausedUntil
    fun resume() { pausedUntil = 0L }

    /** Cheap checks before NovaService asks for a VoiSpark voice (no network). */
    fun eligible(ctx: Context, cfg: Cfg, text: String, now: Long = System.currentTimeMillis()): Boolean {
        if (!cfg.voiSparkOn) return false
        if (isPaused(now)) return false
        if (text.isBlank() || VoiSparkApi.tooLong(text, cfg.voiSparkMaxChars)) return false
        if (!VoiSparkApi.validVoiceId(cfg.voiSparkVoiceId)) return false
        return SecureStore.hasSlot(ctx, VoiSparkClient.SLOT)
    }

    /**
     * Blocking (worker thread): request audio, then play it. FAILED = nothing was played, caller uses the existing voice.
     * [onPlayStart] runs just before the sound starts (HUD mouth).
     */
    fun speak(ctx: Context, cfg: Cfg, text: String, onPlayStart: () -> Unit): Int {
        val my = gen
        val cancelled = { gen != my }
        val key = SecureStore.getSlot(ctx, VoiSparkClient.SLOT)
        if (key.isEmpty()) return FAILED
        val sp = try { VoiSparkClient.speech(key, cfg.voiSparkVoiceId, text, 10000L, cancelled) } catch (e: Throwable) { VoiSparkClient.Speech(null, "error") }
        if (cancelled()) return STOPPED
        if (sp.bytes == null) {
            lastWhy = sp.why
            ErrLog.addOnce("VoiSpark", sp.why)
            pause()
            return FAILED
        }
        val f = File(ctx.cacheDir, "voispark_" + System.nanoTime() + ".bin")
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
            return if (cancelled()) STOPPED else if (ok) DONE else { lastWhy = "playback fail"; ErrLog.addOnce("VoiSpark", "playback fail"); pause(); FAILED }
        } catch (e: Throwable) {
            lastWhy = "playback fail"
            ErrLog.addOnce("VoiSpark", "playback " + e.javaClass.simpleName)
            pause()
            return if (cancelled()) STOPPED else FAILED
        } finally {
            player = null
            try { mp?.release() } catch (e: Throwable) { }
            try { f.delete() } catch (e: Throwable) { }
        }
    }
}
