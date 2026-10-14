package com.nova.assistant

import android.content.Context
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Free offline Hindi listening pack (Vosk small Hindi model, about 42 MB, public download from alphacephei.com).
 * Used ONLY as a fallback listener: after the normal Vosk text and the optional Groq / Gemini listeners found nothing,
 * the same audio is read once more by this model. Audio never leaves the phone. Downloaded by itself in the background
 * (AutoSetup) when "auto models" is ON. Safe unzip rules (one https host, size caps, no path escape).
 * Evidence level: written + statically checked. Not compiled here, not phone tested.
 */
object HindiPack {
    private const val URL_ZIP = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"
    private const val HOST = "alphacephei.com"
    private const val MAX_ZIP_BYTES = 120L * 1024 * 1024
    private const val MAX_UNZIPPED_BYTES = 300L * 1024 * 1024
    private const val PREF = "nova_ext"
    private const val LAST = "hindi_pack_last"
    private const val GAP_MS = 30L * 60 * 1000

    @Volatile private var model: Model? = null
    @Volatile var lastWhy: String = ""
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun dir(ctx: Context) = File(ctx.filesDir, "model-hi")

    fun ready(ctx: Context): Boolean {
        val d = dir(ctx)
        return File(d, ".done").isFile && File(d, "am/final.mdl").isFile && File(d, "graph").isDirectory
    }

    /** Loaded model or null (not downloaded / failed to load). Worker thread only. */
    @Synchronized fun model(ctx: Context): Model? {
        model?.let { return it }
        if (!ready(ctx)) return null
        return try {
            val m = Model(dir(ctx).absolutePath)
            model = m
            m
        } catch (t: Throwable) {
            lastWhy = t.javaClass.simpleName
            try { dir(ctx).deleteRecursively() } catch (e: Throwable) { ErrLog.add("HindiPack", e.toString()) }   // corrupt copy: downloaded again next time
            null
        }
    }

    /** Reads 16 kHz mono 16-bit PCM and returns the Devanagari text ("" = nothing / not available). Worker thread only. */
    fun hear(ctx: Context, pcm: ByteArray, rate: Int): String {
        val m = model(ctx) ?: return ""
        var rec: Recognizer? = null
        return try {
            val r = Recognizer(m, rate.toFloat())
            rec = r
            val sb = StringBuilder()
            var off = 0
            while (off < pcm.size) {
                val n = minOf(3200, pcm.size - off)
                if (r.acceptWaveForm(pcm.copyOfRange(off, off + n), n)) sb.append(' ').append(Logic.extractText(r.result))
                off += n
            }
            sb.append(' ').append(Logic.extractText(r.finalResult))
            sb.toString().trim()
        } catch (t: Throwable) {
            lastWhy = t.javaClass.simpleName
            ""
        } finally {
            try { rec?.close() } catch (t: Throwable) { }
        }
    }

    /** Background download, at most one at a time, at most one try per 30 minutes. null = ready, otherwise a short reason. Worker thread only. */
    fun ensure(ctx: Context): String? {
        val app = ctx.applicationContext
        if (ready(app)) return null
        val p = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong(LAST, 0L) < GAP_MS) return "wait"
        if (!busy.compareAndSet(false, true)) return "busy"
        try {
            p.edit().putLong(LAST, now).apply()
            val err = downloadOnce(app)
            lastWhy = err ?: ""
            return err
        } finally {
            busy.set(false)
        }
    }

    private fun downloadOnce(ctx: Context): String? {
        val tmpZip = File(ctx.cacheDir, "hi.zip.part")
        val tmpDir = File(ctx.cacheDir, "hi_unpack")
        try {
            tmpZip.delete()
            tmpDir.deleteRecursively()
            var cur = URL_ZIP
            var c: HttpURLConnection? = null
            for (hop in 0..4) {
                val u = URL(cur)
                if (u.protocol != "https" || !(u.host == HOST || u.host.endsWith(".$HOST"))) return "blocked host"
                val x = u.openConnection() as HttpURLConnection
                x.instanceFollowRedirects = false
                x.connectTimeout = 20000
                x.readTimeout = 60000
                x.setRequestProperty("User-Agent", "NOVAAndroidAssistant (hindi pack)")
                val code = x.responseCode
                if (code in 300..399) {
                    val loc = x.getHeaderField("Location")
                    x.disconnect()
                    if (loc == null) return "redirect"
                    cur = URL(u, loc).toString()
                    continue
                }
                if (code != 200) { x.disconnect(); return "http $code" }
                c = x
                break
            }
            val conn = c ?: return "too many redirects"
            var total = 0L
            conn.inputStream.use { ins ->
                tmpZip.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ZIP_BYTES) throw IOException("too big")
                        out.write(buf, 0, n)
                    }
                }
            }
            conn.disconnect()
            tmpDir.mkdirs()
            val root = tmpDir.canonicalPath + File.separator
            var unzipped = 0L
            ZipInputStream(tmpZip.inputStream()).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val dest = File(tmpDir, e.name)
                    if (!dest.canonicalPath.startsWith(root)) throw IOException("bad zip path")
                    if (e.isDirectory) { dest.mkdirs(); continue }
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { out ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val n = zin.read(buf)
                            if (n < 0) break
                            unzipped += n
                            if (unzipped > MAX_UNZIPPED_BYTES) throw IOException("too big")
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            val kids = tmpDir.listFiles() ?: emptyArray()
            val src = if (kids.size == 1 && kids[0].isDirectory) kids[0] else tmpDir
            if (!File(src, "am/final.mdl").isFile || !File(src, "graph").isDirectory) return "model files missing"
            val dest = dir(ctx)
            dest.deleteRecursively()
            if (!src.renameTo(dest)) return "could not save"
            File(dest, ".done").writeText("1")
            return if (ready(ctx)) null else "model files missing"
        } catch (t: Throwable) {
            return t.javaClass.simpleName
        } finally {
            try { tmpZip.delete() } catch (t: Throwable) { }
            try { tmpDir.deleteRecursively() } catch (t: Throwable) { ErrLog.add("HindiPack", t.toString()) }
        }
    }
}
