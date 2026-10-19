package com.nova.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * v7.51 PART 12 - FRAME EXTRACTOR (Android side). All numbers come from [FrameRules] (pure); this file only decodes.
 *
 *  - Runs ONLY on one separate background thread (single thread executor). Never on the main thread.
 *  - MediaMetadataRetriever.getFrameAtTime(us, OPTION_CLOSEST_SYNC) for a video; a photo is just resized + JPEG.
 *  - Every Bitmap is recycled (also on error); OutOfMemoryError and any other Throwable are caught and turned into an honest sentence.
 *  - Cancel: [cancel] (owner ROKO, STOP via NovaService) ends the run at once; the old thread notices through the generation number and stops.
 *  - Watchdog: 60 seconds ([FrameRules.WATCHDOG_MS]); then the state becomes error and a late result of the old thread is thrown away.
 *  - Frames are JPEG (max side 768, quality 80) in the private cache (cacheDir/media_frames/<id>/f<n>.jpg), removed with the media, stale after 24 h.
 *    No path leaves Kotlin: the page asks for small thumbnails by number.
 *  - Progress: [onStage] gets real [AnalysisStage] events (stage FRAMES, done / total). Part 13 / 14 use the same object.
 */
object FrameExtractor {
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-frame-extract").apply { isDaemon = true } }
    private val gen = AtomicInteger(0)
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null
    private val times = CopyOnWriteArrayList<Long>()

    /** idle | running | done | error | cancelled */
    @Volatile var state = "idle"; private set
    @Volatile var done = 0; private set
    @Volatile var total = 0; private set
    @Volatile var message = ""; private set
    @Volatile var error = ""; private set
    @Volatile var mediaId = ""; private set
    /** v7.53 13B-1: length of the last extracted video in ms (0 for a photo / unknown). Used for the honest metadata report. */
    @Volatile var lastDurationMs = 0L; private set
    /** Called on the extractor thread (or main thread for cancel / watchdog) with every real stage event. */
    @Volatile var onStage: ((AnalysisStage) -> Unit)? = null

    private fun framesRoot(c: Context): File = File(c.cacheDir, "media_frames")
    private fun framesDir(c: Context, id: String): File = File(framesRoot(c), id)

    private fun freeHeap(): Long { val rt = Runtime.getRuntime(); return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory()) }

    private fun emit(g: Int, st: String, d: Int, t: Int, msg: String) {
        if (gen.get() != g) return
        done = d; total = t; message = msg
        try { onStage?.invoke(AnalysisStage(st, d, t, msg)) } catch (e: Throwable) { }
    }

    /** Ends run [g] with [st] (done / error / cancelled). A newer or cancelled generation is ignored. */
    private fun finish(g: Int, st: String, msg: String) {
        synchronized(lock) {
            if (gen.get() != g) return
            state = st
            if (st == "error") { error = msg; message = msg } else { error = ""; message = msg }
            watchdog?.let { main.removeCallbacks(it) }; watchdog = null
        }
        try { onStage?.invoke(AnalysisStage(if (st == "error") AnalysisStage.FAILED else AnalysisStage.DONE, done, total, msg)) } catch (e: Throwable) { }
    }

    /** Starts the extraction of media [id]. Returns "ok" or a short honest reason (nothing started). [requested] null = default 6 frames. */
    fun start(c: Context, id: String?, requested: Int?): String {
        if (!MediaIntake.isValidId(id)) return FrameRules.errorText(FrameRules.ERR_NO_FILE)
        val e = MediaIntakeStore.list(c).firstOrNull { it.id == id } ?: return FrameRules.errorText(FrameRules.ERR_NO_FILE)
        if (!FrameRules.supports(e.kind)) return FrameRules.errorText(FrameRules.ERR_KIND)
        val f = MediaIntakeStore.fileOf(c, id) ?: return FrameRules.errorText(FrameRules.ERR_NO_FILE)
        val mid = id!!
        val g: Int
        synchronized(lock) {
            if (state == "running") return FrameRules.errorText(FrameRules.ERR_BUSY)
            g = gen.incrementAndGet()
            state = "running"; done = 0; total = 0; message = ""; error = ""; mediaId = mid; times.clear()
            val wd = Runnable {
                var hit = false
                synchronized(lock) { if (gen.get() == g && state == "running") { gen.incrementAndGet(); state = "error"; error = FrameRules.errorText(FrameRules.ERR_TIMEOUT); message = error; hit = true } }
                if (hit) try { onStage?.invoke(AnalysisStage(AnalysisStage.FAILED, done, total, error)) } catch (x: Throwable) { }
            }
            watchdog = wd
            main.postDelayed(wd, FrameRules.WATCHDOG_MS)
        }
        val kind = e.kind
        pool.execute {
            try {
                cleanStale(c)
                if (kind == MediaIntake.Kind.IMAGE) runImage(c, g, mid, f, requested) else runVideo(c, g, mid, f, requested)
            } catch (x: OutOfMemoryError) {
                finish(g, "error", FrameRules.errorText(FrameRules.ERR_MEMORY))
            } catch (x: Throwable) {
                finish(g, "error", FrameRules.errorText((x.message ?: x.javaClass.simpleName)))
            }
        }
        return "ok"
    }

    /** Owner ROKO or STOP: ends a running extraction at once. Safe to call any time, on any thread. */
    fun cancel() {
        var was = false
        synchronized(lock) {
            if (state == "running") {
                gen.incrementAndGet(); state = "cancelled"; message = "Roka gaya."; error = ""; was = true
                watchdog?.let { main.removeCallbacks(it) }; watchdog = null
            }
        }
        if (was) try { onStage?.invoke(AnalysisStage(AnalysisStage.FAILED, done, total, "Roka gaya.")) } catch (x: Throwable) { }
    }

    private fun alive(g: Int): Boolean = gen.get() == g

    private fun scaleDown(b: Bitmap): Bitmap {
        val t = FrameRules.targetSize(b.width, b.height)
        return if (t.first == b.width && t.second == b.height) b else Bitmap.createScaledBitmap(b, t.first, t.second, true)
    }

    private fun signature(b: Bitmap): IntArray {
        val s = Bitmap.createScaledBitmap(b, FrameRules.SIG_SIDE, FrameRules.SIG_SIDE, true)
        try {
            val px = IntArray(FrameRules.SIG_SIDE * FrameRules.SIG_SIDE)
            s.getPixels(px, 0, FrameRules.SIG_SIDE, 0, 0, FrameRules.SIG_SIDE, FrameRules.SIG_SIDE)
            return IntArray(px.size) { FrameRules.gray(px[it]) }
        } finally { if (s !== b) s.recycle() }
    }

    private fun save(b: Bitmap, f: File) { FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, FrameRules.JPEG_QUALITY, it) } }

    private fun runVideo(c: Context, g: Int, id: String, file: File, requested: Int?) {
        val dir = framesDir(c, id); dir.deleteRecursively(); dir.mkdirs()
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            lastDurationMs = dur
            val want = FrameRules.frameCount(requested, dur, freeHeap())
            if (want == 0) { finish(g, "error", FrameRules.errorText(FrameRules.ERR_MEMORY)); return }
            val pts = FrameRules.timePointsMs(dur, want)
            emit(g, AnalysisStage.FRAMES, 0, pts.size, FrameRules.stageText(0, pts.size))
            val sigs = ArrayList<IntArray>()
            var kept = 0; var dropped = 0
            for ((i, t) in pts.withIndex()) {
                if (!alive(g)) return
                var bmp: Bitmap? = null
                var sc: Bitmap? = null
                try {
                    bmp = r.getFrameAtTime(t * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bmp != null) {
                        sc = scaleDown(bmp)
                        val sig = signature(sc)
                        if (FrameRules.isDuplicateOfLast(sigs, sig)) dropped++
                        else if (alive(g)) { save(sc, File(dir, "f" + (kept + 1) + ".jpg")); sigs.add(sig); times.add(t); kept++ }
                    }
                } finally {
                    try { if (sc != null && sc !== bmp) sc.recycle() } catch (x: Throwable) { }
                    try { bmp?.recycle() } catch (x: Throwable) { }
                }
                emit(g, AnalysisStage.FRAMES, i + 1, pts.size, FrameRules.stageText(i + 1, pts.size))
            }
            if (!alive(g)) return
            if (kept == 0) finish(g, "error", FrameRules.errorText(FrameRules.ERR_NONE))
            else finish(g, "done", FrameRules.summary(kept, pts.size, dropped))
        } finally { try { r.release() } catch (x: Throwable) { } }
    }

    private fun runImage(c: Context, g: Int, id: String, file: File, requested: Int?) {
        val dir = framesDir(c, id); dir.deleteRecursively(); dir.mkdirs()
        lastDurationMs = 0L
        if (FrameRules.frameCount(1, 0, freeHeap()) == 0) { finish(g, "error", FrameRules.errorText(FrameRules.ERR_MEMORY)); return }
        emit(g, AnalysisStage.FRAMES, 0, 1, FrameRules.stageText(0, 1))
        val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) { finish(g, "error", FrameRules.errorText(FrameRules.ERR_NONE)); return }
        var ss = 1
        while (o.outWidth / (ss * 2) >= FrameRules.MAX_SIDE && o.outHeight / (ss * 2) >= FrameRules.MAX_SIDE) ss *= 2
        val o2 = BitmapFactory.Options(); o2.inSampleSize = ss
        var bmp: Bitmap? = null
        var sc: Bitmap? = null
        try {
            bmp = BitmapFactory.decodeFile(file.absolutePath, o2)
            if (bmp == null) { finish(g, "error", FrameRules.errorText(FrameRules.ERR_NONE)); return }
            sc = scaleDown(bmp)
            if (!alive(g)) return
            save(sc, File(dir, "f1.jpg")); times.add(0L)
        } finally {
            try { if (sc != null && sc !== bmp) sc.recycle() } catch (x: Throwable) { }
            try { bmp?.recycle() } catch (x: Throwable) { }
        }
        emit(g, AnalysisStage.FRAMES, 1, 1, FrameRules.stageText(1, 1))
        finish(g, "done", "1 photo taiyaar (chhota karke JPEG).")
    }

    /** JSON for the UI: no path. frames = how many were kept (only while done), secs = their time points in seconds. */
    fun statusJson(): String {
        val a = JSONArray()
        val st = state
        if (st == "done") for (t in times) a.put(t / 1000.0)
        return JSONObject().put("state", st).put("done", done).put("total", total).put("stage", AnalysisStage.FRAMES)
            .put("id", mediaId).put("message", message).put("error", error).put("frames", if (st == "done") a.length() else 0).put("secs", a).toString()
    }

    /** v7.53 13B-1: private files of the kept frames of media [id] (f1.jpg, f2.jpg ...) in order, ONLY when the last run for that id is done. Never leaves Kotlin. */
    fun keptFiles(c: Context, id: String?): List<File> {
        if (state != "done" || id == null || id != mediaId || !MediaIntake.isValidId(id)) return emptyList()
        val out = ArrayList<File>()
        for (n in 1..times.size) { val f = File(framesDir(c, id), "f$n.jpg"); if (f.exists()) out.add(f) }
        return out
    }

    /** v7.53 13B-1: time point (whole seconds) of every kept frame of the last run (a photo has 0). Empty unless done. */
    fun keptSecs(): List<Int> = if (state == "done") times.map { (it / 1000L).toInt() } else emptyList()

    /** Small JPEG data URL of kept frame number [n] (1-based) of the last finished run. "" if there is no such frame. Bitmaps recycled. */
    fun thumbDataUrl(c: Context, n: Int, maxSide: Int): String {
        if (state != "done" || n < 1 || n > times.size || !MediaIntake.isValidId(mediaId)) return ""
        val f = File(framesDir(c, mediaId), "f$n.jpg")
        if (!f.exists()) return ""
        var bmp: Bitmap? = null
        try {
            val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
            BitmapFactory.decodeFile(f.absolutePath, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return ""
            var ss = 1
            while (o.outWidth / (ss * 2) >= maxSide && o.outHeight / (ss * 2) >= maxSide) ss *= 2
            val o2 = BitmapFactory.Options(); o2.inSampleSize = ss; o2.inPreferredConfig = Bitmap.Config.RGB_565
            bmp = BitmapFactory.decodeFile(f.absolutePath, o2)
            val b = bmp ?: return ""
            val bo = java.io.ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, 72, bo)
            val txt = "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
            return if (txt.length <= 400_000) txt else ""
        } catch (x: OutOfMemoryError) { return "" } catch (x: Throwable) { return "" }
        finally { try { bmp?.recycle() } catch (x: Throwable) { } }
    }

    /** Removes frame folders of media that is gone (owner HATAO / SAAF KARO / 24 h). Never touches a running job's folder. Safe on any thread. */
    fun cleanStale(c: Context) {
        try {
            val root = framesRoot(c)
            val dirs = root.listFiles() ?: return
            val ids = MediaIntakeStore.list(c).map { it.id }.toSet()
            val now = System.currentTimeMillis()
            for (d in dirs) {
                if (state == "running" && d.name == mediaId) continue
                if (d.name !in ids || now - d.lastModified() > MediaIntake.TTL_MS) d.deleteRecursively()
            }
            synchronized(lock) { if (state != "running" && mediaId.isNotEmpty() && mediaId !in ids) { state = "idle"; message = ""; error = ""; done = 0; total = 0; times.clear() } }
        } catch (x: Throwable) { }
    }
}
