package com.nova.assistant

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlurWithFrameOverlaid
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * v7.173 VIDEO STUDIO (Android side). The owner picks videos, VideoStudioRules decides the edit, this file does the work:
 *  1) measure: how much each clip moves (small frames, MediaMetadataRetriever),
 *  2) render: ONE Media3 Transformer export per piece (trim + zoom animation + light contrast + 9:16 or 16:9 canvas),
 *  3) join: platform MediaExtractor + MediaMuxer glue the pieces; slow-mo pieces are video re-timed x2 with silent audio,
 *  4) save to Movies/JARVIS and tell the owner by voice + screen.
 * Only APIs already used elsewhere in this project (Transformer.start(EditedMediaItem), Effects, Presentation, blur overlay)
 * plus MatrixTransformation / Contrast / ClippingConfiguration. Transformer is always built, started and cancelled on the MAIN looper
 * (Media3 requires its application thread). Written WITHOUT a compiler and NOT phone-tested: expect a first build fix.
 */
object VideoStudio {
    class Picked(val id: String, val uri: Uri, val name: String, val durMs: Long, val aspect: Float)

    @Volatile var ui: ((String) -> Unit)? = null

    private val picked = ArrayList<Picked>()
    @Volatile private var running = false
    @Volatile private var cancelFlag = false
    @Volatile private var progress = 0
    @Volatile private var msg = ""
    @Volatile private var resultUri: String = ""
    @Volatile private var resultSec = 0L
    @Volatile private var resultNote = ""
    @Volatile private var lastErr = ""

    private const val W_V = 720; private const val H_V = 1280
    private const val SEG_TIMEOUT_MS = 8 * 60_000L
    private const val MEASURE_MAX_MS = 90_000L   // lighter on the phone: only the first 90 s of a clip is measured

    // ---------------------------------------------------------------- picking

    fun addPicked(ctx: Context, uris: List<Uri>): String {
        if (running) return "Edit chal rahi hai, thoda ruko."
        var skipped = 0
        val found = ArrayList<Picked>()
        // slow file reading happens OUTSIDE the lock: the page polls studioStatus every 1.5 s and must never wait for it
        for (u in uris) {
            if (synchronized(this) { picked.size + found.size >= VideoStudioRules.MAX_CLIPS }) { skipped++; continue }
            if (synchronized(this) { picked.any { it.uri == u } } || found.any { it.uri == u }) continue
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, u)
                val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (dur <= 0 || w <= 0 || h <= 0) { skipped++; continue }
                val asp = if (rot == 90 || rot == 270) h.toFloat() / w else w.toFloat() / h
                found.add(Picked("v" + System.nanoTime(), u, nameOf(ctx, u), dur, asp))
            } catch (e: Throwable) { skipped++ } finally { try { r.release() } catch (e: Throwable) { } }
        }
        synchronized(this) { for (f in found) if (picked.size < VideoStudioRules.MAX_CLIPS) picked.add(f) }
        return if (skipped > 0) "$skipped file video nahi thi ya limit (8) se zyada thi." else ""
    }

    private fun nameOf(ctx: Context, u: Uri): String = try {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "video"
    } catch (e: Throwable) { "video" }

    @Synchronized fun remove(id: String) { if (!running) picked.removeAll { it.id == id } }
    @Synchronized fun clear() { if (!running) { picked.clear(); resultUri = ""; resultNote = ""; lastErr = ""; progress = 0; msg = "" } }
    fun cancel() { cancelFlag = true }

    @Synchronized fun snapshotJson(): String {
        val a = JSONArray()
        for (p in picked) a.put(JSONObject().put("id", p.id).put("name", p.name.take(40)).put("sec", p.durMs / 1000))
        return JSONObject().put("clips", a).put("busy", running).put("progress", progress).put("msg", msg)
            .put("hasResult", resultUri.isNotEmpty()).put("resultSec", resultSec).put("note", resultNote).put("err", lastErr).toString()
    }

    // ---------------------------------------------------------------- measuring

    private fun measure(ctx: Context, p: Picked, index: Int): VideoStudioRules.Clip {
        val motion = ArrayList<Float>()
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, p.uri)
            val limit = minOf(p.durMs, MEASURE_MAX_MS)
            val step = if (p.durMs > 30_000L) 1000L else VideoStudioRules.STEP_MS   // long clips: fewer frames = less heat, no hang
            var t = 0L
            var prev: IntArray? = null
            while (t < limit && !cancelFlag) {
                val lum = lumaAt(r, t)
                motion.add(if (lum != null && prev != null) VideoStudioRules.motionBetween(prev, lum) else 0f)
                if (lum != null) prev = lum
                t += step
                try { Thread.sleep(15) } catch (e: InterruptedException) { break }   // let the phone breathe between frames
            }
            if (motion.size > 1 && motion[0] == 0f) motion[0] = motion[1]
        } catch (e: Throwable) { ErrLog.add("VideoStudio", "measure: $e") } finally { try { r.release() } catch (e: Throwable) { } }
        return VideoStudioRules.Clip(index, p.durMs, motion, if (p.durMs > 30_000L) 1000L else VideoStudioRules.STEP_MS)
    }

    private fun lumaAt(r: MediaMetadataRetriever, tMs: Long): IntArray? = try {
        val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 48, 48)
                           else r.getFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { Bitmap.createScaledBitmap(it, 48, 48, true) }
        if (bmp == null) null else {
            val s = if (bmp.width == 48 && bmp.height == 48) bmp else Bitmap.createScaledBitmap(bmp, 48, 48, true)
            val px = IntArray(48 * 48)
            s.getPixels(px, 0, 48, 0, 0, 48, 48)
            val out = IntArray(px.size) { i -> val c = px[i]; (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10 }
            if (s !== bmp) s.recycle()
            bmp.recycle()
            out
        }
    } catch (e: Throwable) { null }

    // ---------------------------------------------------------------- one piece through Media3

    /** null = ok, else a short reason. Everything about Transformer happens on the main looper. */
    private fun exportSeg(app: Context, p: Picked, s: VideoStudioRules.Seg, out: File, vertical: Boolean): String? {
        if (out.exists()) out.delete()
        val w = if (vertical) W_V else H_V
        val h = if (vertical) H_V else W_V
        val latch = CountDownLatch(1)
        val code = AtomicInteger(0)   // 0 running, 1 done, 2 error
        val why = arrayOf("")
        val holder = arrayOfNulls<Transformer>(1)
        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                val clip = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s.startMs).setEndPositionMs(s.endMs).build()
                val item = MediaItem.Builder().setUri(p.uri).setClippingConfiguration(clip).build()
                val fx = ArrayList<Effect>()
                if (s.zoom != VideoStudioRules.Zoom.NONE) {
                    val durUs = (s.srcMs * 1000L).coerceAtLeast(1L).toFloat()
                    val z = s.zoom
                    fx.add(MatrixTransformation { t ->
                        val m = Matrix()
                        val sc = VideoStudioRules.zoomAt(z, t / durUs)
                        m.setScale(sc, sc)
                        m
                    })
                }
                fx.add(Contrast(0.12f))
                fx.add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
                val target = w.toFloat() / h
                if (kotlin.math.abs(p.aspect - target) / target > 0.12f) fx.add(GaussianBlurWithFrameOverlaid(20f, 1f, 1f))
                val edited = EditedMediaItem.Builder(item).setEffects(Effects(emptyList(), fx)).setRemoveAudio(s.speed < 1f).build()
                val request = TransformationRequest.Builder().setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).build()
                val tr = Transformer.Builder(app).setTransformationRequest(request).addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { code.set(1); latch.countDown() }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        why[0] = exportException.message ?: "export fail"; code.set(2); latch.countDown()
                    }
                }).build()
                holder[0] = tr
                tr.start(edited, out.absolutePath)
            } catch (e: Throwable) { why[0] = e.javaClass.simpleName + " " + (e.message ?: ""); code.set(2); latch.countDown() }
        }
        val t0 = System.currentTimeMillis()
        while (!latch.await(500, TimeUnit.MILLISECONDS)) {
            if (cancelFlag || System.currentTimeMillis() - t0 > SEG_TIMEOUT_MS) {
                main.post { try { holder[0]?.cancel() } catch (e: Throwable) { } }
                out.delete()
                return if (cancelFlag) "rok diya" else "ek tukde ka time nikal gaya"
            }
        }
        return if (code.get() == 1 && out.exists() && out.length() > 0) null else { out.delete(); why[0].take(90).ifEmpty { "export fail" } }
    }

    // ---------------------------------------------------------------- join (platform muxer)

    private class SegFile(val file: File, val seg: VideoStudioRules.Seg)

    private fun silentFrame(rate: Int, ch: Int): ByteArray? = try {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch)
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        c.start()
        val zero = ByteArray(1024 * 2 * ch)
        val info = MediaCodec.BufferInfo()
        val frames = ArrayList<ByteArray>()
        var fed = 0
        var spins = 0
        while (frames.size < 8 && spins < 600) {
            spins++
            val ii = c.dequeueInputBuffer(10_000)
            if (ii >= 0 && fed < 40) {
                val b = c.getInputBuffer(ii)!!
                b.clear(); b.put(zero)
                c.queueInputBuffer(ii, 0, zero.size, fed * 1024L * 1_000_000L / rate, 0)
                fed++
            }
            val oi = c.dequeueOutputBuffer(info, 10_000)
            if (oi >= 0) {
                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                    val ob = c.getOutputBuffer(oi)!!
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    val a = ByteArray(info.size); ob.get(a); frames.add(a)
                }
                c.releaseOutputBuffer(oi, false)
            }
        }
        try { c.stop() } catch (e: Throwable) { }
        c.release()
        frames.getOrNull(5) ?: frames.lastOrNull()
    } catch (e: Throwable) { ErrLog.add("VideoStudio", "silence: $e"); null }

    private fun trackOf(ex: MediaExtractor, prefix: String): Int {
        for (i in 0 until ex.trackCount) if ((ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith(prefix)) return i
        return -1
    }

    /** null = ok. Pieces share one encoder setup, so their video format matches; audio of a different rate/channels is replaced by silence (reported). */
    private fun join(files: List<SegFile>, out: File, onProgress: (Int) -> Unit): String? {
        var muxer: MediaMuxer? = null
        try {
            var vFmt: MediaFormat? = null
            var aFmt: MediaFormat? = null
            for (sf in files) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(sf.file.absolutePath)
                    val vi = trackOf(ex, "video/"); val ai = trackOf(ex, "audio/")
                    if (vFmt == null && vi >= 0) vFmt = ex.getTrackFormat(vi)
                    if (aFmt == null && ai >= 0 && sf.seg.speed >= 1f) aFmt = ex.getTrackFormat(ai)
                } finally { ex.release() }
            }
            if (vFmt == null) return "video track nahi mila"
            val rate = aFmt?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 0
            val ch = aFmt?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 0
            val silent = if (aFmt != null) silentFrame(rate, ch) else null
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val vTrack = m.addTrack(vFmt)
            val aTrack = if (aFmt != null && silent != null) m.addTrack(aFmt) else -1
            m.start()
            var offsetUs = 0L
            var buf = ByteBuffer.allocateDirect(2 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            for ((idx, sf) in files.withIndex()) {
                if (cancelFlag) return "rok diya"
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(sf.file.absolutePath)
                    val vi = trackOf(ex, "video/"); val ai = trackOf(ex, "audio/")
                    if (vi < 0) return "ek tukde me video nahi"
                    val stretch = 1f / sf.seg.speed
                    ex.selectTrack(vi)
                    var firstPts = -1L; var lastRel = 0L
                    while (true) {
                        val size = ex.sampleSize.toInt()
                        if (size < 0) break
                        if (size > buf.capacity()) buf = ByteBuffer.allocateDirect(size + 4096)
                        buf.clear()
                        val n = ex.readSampleData(buf, 0)
                        if (n < 0) break
                        val pts = ex.sampleTime
                        if (firstPts < 0) firstPts = pts
                        val rel = ((pts - firstPts) * stretch).toLong()
                        info.set(0, n, offsetUs + rel, if ((ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        m.writeSampleData(vTrack, buf, info)
                        lastRel = rel
                        ex.advance()
                    }
                    ex.unselectTrack(vi)
                    val segUs = lastRel + (33_333L * stretch).toLong()
                    if (aTrack >= 0) {
                        val usable = ai >= 0 && sf.seg.speed >= 1f && run {
                            val f = ex.getTrackFormat(ai)
                            f.getInteger(MediaFormat.KEY_SAMPLE_RATE) == rate && f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == ch
                        }
                        var audioEnd = 0L
                        if (usable) {
                            ex.selectTrack(ai)
                            var a0 = -1L
                            while (true) {
                                val size = ex.sampleSize.toInt()
                                if (size < 0) break
                                if (size > buf.capacity()) buf = ByteBuffer.allocateDirect(size + 4096)
                                buf.clear()
                                val n = ex.readSampleData(buf, 0)
                                if (n < 0) break
                                val pts = ex.sampleTime
                                if (a0 < 0) a0 = pts
                                val rel = pts - a0
                                if (rel >= segUs) break
                                info.set(0, n, offsetUs + rel, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                                m.writeSampleData(aTrack, buf, info)
                                audioEnd = rel + 1024L * 1_000_000L / rate
                                ex.advance()
                            }
                            ex.unselectTrack(ai)
                        }
                        // fill whatever the audio did not cover (slow-mo pieces, different audio format, short tails) with real silent AAC frames
                        val frameUs = 1024L * 1_000_000L / rate
                        var t = audioEnd
                        val sb = silent!!
                        while (t + frameUs <= segUs) {
                            val bb = ByteBuffer.allocateDirect(sb.size); bb.put(sb); bb.flip()
                            info.set(0, sb.size, offsetUs + t, MediaCodec.BUFFER_FLAG_KEY_FRAME)
                            m.writeSampleData(aTrack, bb, info)
                            t += frameUs
                        }
                    }
                    offsetUs += segUs
                } finally { ex.release() }
                onProgress(idx + 1)
            }
            m.stop()
            return null
        } catch (e: Throwable) {
            ErrLog.add("VideoStudio", "join: $e")
            return "jodte waqt dikkat (" + e.javaClass.simpleName + ")"
        } finally {
            try { muxer?.release() } catch (e: Throwable) { }
        }
    }

    // ---------------------------------------------------------------- run

    fun start(ctx: Context, instructions: String, en: Boolean = false): String {
        val app = ctx.applicationContext
        val list: List<Picked> = synchronized(this) {
            if (running) return "Edit pehle se chal rahi hai."
            if (picked.isEmpty()) return "Pehle video chuno (VIDEO CHUNO dabao)."
            running = true; cancelFlag = false; progress = 1; msg = "Videos dekh raha hu..."; resultUri = ""; resultNote = ""; lastErr = ""
            ArrayList(picked)
        }
        Thread({
            val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wl = try { pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jarvis:studio")?.also { it.acquire(40 * 60_000L) } } catch (e: Throwable) { null }
            val dir = File(app.cacheDir, "studio")
            try {
                dir.deleteRecursively(); dir.mkdirs()
                val o = VideoStudioRules.parseOptions(instructions)
                val clips = ArrayList<VideoStudioRules.Clip>()
                for ((i, p) in list.withIndex()) {
                    msg = "Video ${i + 1}/${list.size} dekh raha hu (action dhoondh raha hu)"
                    clips.add(measure(app, p, i))
                    progress = 2 + (20 * (i + 1) / list.size)
                    if (cancelFlag) throw IllegalStateException("rok diya")
                }
                val plan = VideoStudioRules.plan(clips, o)
                if (plan.isEmpty()) throw IllegalStateException("video bahut chhoti hain")
                val summary = VideoStudioRules.planSummary(plan, plan.map { it.clip }.toSet().size, o)
                val segFiles = ArrayList<SegFile>()
                for ((k, s) in plan.withIndex()) {
                    msg = "Edit ho rahi hai: tukda ${k + 1}/${plan.size} ($summary)"
                    val f = File(dir, "seg_" + k + ".mp4")
                    val err = exportSeg(app, list[s.clip], s, f, o.vertical)
                    if (err != null) throw IllegalStateException(err)
                    segFiles.add(SegFile(f, s))
                    progress = 22 + (68 * (k + 1) / plan.size)
                }
                msg = "Tukde jod raha hu..."
                val outFile = File(dir, "JARVIS_edit_" + System.currentTimeMillis() + ".mp4")
                val jerr = join(segFiles, outFile) { }
                if (jerr != null) throw IllegalStateException(jerr)
                progress = 95; msg = "Save kar raha hu..."
                val uri = saveToMovies(app, outFile)
                synchronized(this) {
                    resultSec = VideoStudioRules.totalOutMs(plan) / 1000
                    resultUri = uri?.toString() ?: ""
                    resultNote = summary + (if (o.understood.isEmpty()) "" else " | samjha: " + o.understood.joinToString(", ")) +
                        (if (uri == null) " | Gallery me save nahi hua (phone purana), file: " + outFile.absolutePath else "")
                    progress = 100; msg = "Taiyaar!"
                }
                for (f in segFiles) f.file.delete()
                if (uri != null) outFile.delete()
                alert(VideoStudioRules.doneText(resultSec, en))
            } catch (e: Throwable) {
                val why = (e.message ?: e.javaClass.simpleName).take(100)
                ErrLog.add("VideoStudio", "run: $e")
                synchronized(this) { lastErr = why; msg = "Nahi ho payi"; progress = 0 }
                alert(VideoStudioRules.failText(why, en))
            } finally {
                try { if (wl?.isHeld == true) wl.release() } catch (e: Throwable) { }
                synchronized(this) { running = false }
                try { ui?.invoke("window.onStudioEvent&&window.onStudioEvent()") } catch (e: Throwable) { }
            }
        }, "jarvis-studio").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }.start()
        return ""
    }

    private fun alert(text: String) { try { JarvisService.instance?.onPayAlert(text) } catch (e: Throwable) { } }

    // ---------------------------------------------------------------- result

    private fun saveToMovies(ctx: Context, f: File): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/JARVIS")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return null
            val ok = ctx.contentResolver.openOutputStream(uri)?.use { os -> f.inputStream().use { it.copyTo(os) }; true } ?: false
            if (ok) uri else null
        } catch (e: Throwable) { ErrLog.add("VideoStudio", "save: $e"); null }
    }

    fun resultUri(): Uri? = resultUri.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }

    fun openScreen() { try { ui?.invoke("window.__studioOpen&&window.__studioOpen()") } catch (e: Throwable) { } }
}
