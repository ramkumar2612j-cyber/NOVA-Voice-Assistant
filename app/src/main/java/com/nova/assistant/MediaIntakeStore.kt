package com.nova.assistant

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Base64
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v7.51 PART 11-B - MEDIA INTAKE STORE (Android side). All decisions come from [MediaIntake] (pure rules); this file only moves bytes.
 *
 *  - Share (ACTION_SEND / SEND_MULTIPLE) or the system picker hands over content:// uris. No gallery permission, no network.
 *  - Every copy runs on ONE background thread, as a STREAM (64 KB buffer): a 300 MB video is never held in memory.
 *  - The copy stops when the owner cancels ([cancel], STOP) or when the bytes passed the size limit ([MediaIntake.streamOk]),
 *    whatever the provider claimed. A half-written file is deleted.
 *  - Files live in the app's PRIVATE cache (cacheDir/media_intake). Index = index.json there. [cleanExpired] removes anything older than
 *    24 h (called on every app start / list); [clear] removes everything now. Chat history keeps only name + size ([MediaIntake.chatLabel]).
 *  - A shared LINK is stored as a link entry only (no download). Honest wording: [MediaIntake.linkNote].
 * 11-C adds the bridge + MEDIA MILA card on top of [list] / [remove] / [clear] / [status].
 */
object MediaIntakeStore {
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-media-intake").apply { isDaemon = true } }
    private val cancelFlag = AtomicBoolean(false)
    private val lock = Any()

    /** What the UI may ask while / after a share: busy flag, progress of the file being copied, last result sentence. */
    @Volatile var busy = false; private set
    @Volatile var progressName = ""; private set
    @Volatile var progressPct = 0; private set
    @Volatile var lastMessage = ""; private set
    @Volatile var lastAdded = 0; private set
    @Volatile var lastRejected = 0; private set
    /** Set by MainActivity: called on the store thread when a share finished (11-C turns it into a card refresh). */
    @Volatile var onChanged: (() -> Unit)? = null

    private fun dir(c: Context): File = File(c.cacheDir, "media_intake").also { it.mkdirs() }
    private fun indexFile(c: Context): File = File(dir(c), "index.json")

    // ---------------------------------------------------------------- index

    private fun readIndex(c: Context): MutableList<MediaIntake.Entry> {
        val out = ArrayList<MediaIntake.Entry>()
        try {
            val f = indexFile(c)
            if (!f.exists()) return out
            val a = JSONArray(f.readText(Charsets.UTF_8))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val kind = try { MediaIntake.Kind.valueOf(o.optString("kind")) } catch (e: Exception) { MediaIntake.Kind.UNKNOWN }
                val id = o.optString("id")
                if (!MediaIntake.isValidId(id)) continue
                out.add(MediaIntake.Entry(id, kind, o.optString("name"), o.optLong("size", -1L), o.optLong("created", 0L), o.optString("file"), o.optString("url")))
            }
        } catch (e: Throwable) { ErrLog.add("MediaIntakeStore", "index read: " + e.toString()) }
        return out
    }

    private fun writeIndex(c: Context, list: List<MediaIntake.Entry>) {
        try {
            val a = JSONArray()
            for (e in list) a.put(JSONObject().put("id", e.id).put("kind", e.kind.name).put("name", e.name).put("size", e.size).put("created", e.createdMs).put("file", e.file).put("url", e.url))
            val tmp = File(dir(c), "index.tmp")
            tmp.writeText(a.toString(), Charsets.UTF_8)
            val dst = indexFile(c)
            if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
        } catch (e: Throwable) { ErrLog.add("MediaIntakeStore", "index write: " + e.toString()) }
    }

    private fun deleteFiles(c: Context, e: MediaIntake.Entry) {
        if (e.file.isNotEmpty() && !e.file.contains('/') && !e.file.contains("..")) try { File(dir(c), e.file).delete() } catch (x: Throwable) { }
    }

    // ---------------------------------------------------------------- public API used by MainActivity (11-B) and the bridge (11-C)

    /** Removes every item older than 24 h and every index line whose file vanished (the OS may clear the cache). Returns how many went. */
    fun cleanExpired(c: Context, nowMs: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val all = readIndex(c)
        val gone = HashSet(MediaIntake.expiredIds(all, nowMs))
        for (e in all) if (e.kind != MediaIntake.Kind.LINK && (e.file.isEmpty() || !File(dir(c), e.file).exists())) gone.add(e.id)
        if (gone.isEmpty()) return 0
        all.filter { it.id in gone }.forEach { deleteFiles(c, it) }
        writeIndex(c, all.filter { it.id !in gone })
        gone.size
    }

    fun list(c: Context): List<MediaIntake.Entry> { cleanExpired(c); return synchronized(lock) { readIndex(c) } }

    /** Private file of one item for DEKHO / analysis (11-C / Part 13). null if the id is bad, a link, or the file is gone. */
    fun fileOf(c: Context, id: String?): File? {
        if (!MediaIntake.isValidId(id)) return null
        val e = synchronized(lock) { readIndex(c).firstOrNull { it.id == id } } ?: return null
        if (e.file.isEmpty()) return null
        val f = File(dir(c), e.file)
        return if (f.exists()) f else null
    }

    fun remove(c: Context, id: String?): Boolean = synchronized(lock) {
        if (!MediaIntake.isValidId(id)) return false
        val all = readIndex(c)
        val hit = all.firstOrNull { it.id == id } ?: return false
        deleteFiles(c, hit)
        writeIndex(c, all.filter { it.id != id })
        true
    }

    /** SAAF KARO: every file + the index, now. */
    fun clear(c: Context): Int = synchronized(lock) {
        val all = readIndex(c)
        all.forEach { deleteFiles(c, it) }
        try { dir(c).listFiles()?.forEach { if (it.isFile) it.delete() } } catch (e: Throwable) { }
        writeIndex(c, emptyList())
        all.size
    }

    /** Stops a copy in progress (owner CANCEL or STOP). The half-written file is deleted by the copy loop. */
    fun cancel() { cancelFlag.set(true) }

    /** One honest line for "kya mila" (never a path). */
    fun statusText(c: Context): String { val l = list(c); return MediaIntake.statusLine(l.count { it.kind != MediaIntake.Kind.LINK }, l.count { it.kind == MediaIntake.Kind.LINK }) }

    // ---------------------------------------------------------------- intake

    /** Called by MainActivity for every incoming intent. Does nothing unless it is a SEND / SEND_MULTIPLE share. Returns true if it took the intent. */
    fun onIntent(c: Context, i: Intent?): Boolean {
        val act = i?.action ?: return false
        if (act != Intent.ACTION_SEND && act != Intent.ACTION_SEND_MULTIPLE) return false
        val app = c.applicationContext
        val uris = streamUris(i)
        val text = try { i.getStringExtra(Intent.EXTRA_TEXT) } catch (e: Throwable) { null }
        val mimeHint = i.type
        ingest(app, uris, text, mimeHint)
        return true
    }

    /** System picker result (ACTION_GET_CONTENT with EXTRA_ALLOW_MULTIPLE): same pipeline as a share. */
    fun ingest(c: Context, uris: List<Uri>, sharedText: String?, mimeHint: String?) {
        val app = c.applicationContext
        cancelFlag.set(false)
        pool.execute {
            busy = true; progressPct = 0; progressName = ""
            try { run(app, uris, sharedText, mimeHint) }
            catch (e: Throwable) { lastMessage = "Media lene me dikkat aayi: " + (e.message ?: e.javaClass.simpleName).take(80); ErrLog.add("MediaIntakeStore", e.toString()) }
            finally { busy = false; progressPct = 0; progressName = ""; try { onChanged?.invoke() } catch (e: Throwable) { ErrLog.addOnce("quiet:MediaIntakeStore", e.toString()) } }
        }
    }

    private fun streamUris(i: Intent): List<Uri> {
        val out = ArrayList<Uri>()
        try {
            if (i.action == Intent.ACTION_SEND_MULTIPLE) {
                val l: List<Uri>? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                if (l != null) out.addAll(l)
            } else {
                val u: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (u != null) out.add(u)
            }
            val cd = i.clipData
            if (out.isEmpty() && cd != null) for (k in 0 until cd.itemCount) cd.getItemAt(k).uri?.let { out.add(it) }
        } catch (e: Throwable) { ErrLog.add("MediaIntakeStore", "uris: " + e.toString()) }
        return out
    }

    private fun meta(r: ContentResolver, u: Uri): Pair<String?, Long> {
        var name: String? = null; var size = -1L
        var cur: Cursor? = null
        try {
            cur = r.query(u, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            if (cur != null && cur.moveToFirst()) {
                val ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME); val si = cur.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0 && !cur.isNull(ni)) name = cur.getString(ni)
                if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si)
            }
        } catch (e: Throwable) { ErrLog.addOnce("quiet:MediaIntakeStore", e.toString()) } finally { try { cur?.close() } catch (e: Throwable) { } }
        return Pair(name, size)
    }

    private fun run(c: Context, uris: List<Uri>, sharedText: String?, mimeHint: String?) {
        val r = c.contentResolver
        val have = synchronized(lock) { readIndex(c) }
        val cands = uris.map { u ->
            val (n, s) = meta(r, u)
            val mime = try { r.getType(u) } catch (e: Throwable) { null } ?: mimeHint
            MediaIntake.Candidate(u.toString(), n ?: u.lastPathSegment, mime, s)
        }
        val plan = MediaIntake.plan(cands, sharedText, MediaIntake.room(have).let { MediaIntake.MAX_FILES - it })
        val reasons = ArrayList<String>(); plan.rejected.forEach { reasons.add(it.name + ": " + it.reason) }
        var added = 0
        for (a in plan.accepted) {
            if (cancelFlag.get()) { reasons.add("Roka gaya."); break }
            val id = "m" + System.currentTimeMillis() + "_" + (added + 1)
            val fileName = MediaIntake.cacheFileName(id, a.name, a.kind)
            progressName = a.name; progressPct = 0
            val res = copyStream(r, Uri.parse(a.uri), File(dir(c), fileName), a)
            if (res.first) {
                synchronized(lock) {
                    val all = readIndex(c)
                    all.add(MediaIntake.Entry(id, a.kind, a.name, res.second, System.currentTimeMillis(), fileName, ""))
                    writeIndex(c, all)
                }
                added++
            } else reasons.add(a.name + ": " + res.third)
        }
        var links = 0
        for (u in plan.links) {
            synchronized(lock) {
                val all = readIndex(c)
                if (all.none { it.kind == MediaIntake.Kind.LINK && it.url.equals(u, ignoreCase = true) }) {
                    all.add(MediaIntake.Entry("l" + System.currentTimeMillis() + "_" + (links + 1), MediaIntake.Kind.LINK, "link_" + (links + 1), 0L, System.currentTimeMillis(), "", u))
                    writeIndex(c, all); links++
                }
            }
        }
        lastAdded = added + links; lastRejected = reasons.size
        lastMessage = when {
            added + links == 0 && reasons.isEmpty() -> "Share me koi photo, video ya https link nahi mila."
            reasons.isEmpty() -> MediaIntake.statusLine(added, links)
            else -> MediaIntake.statusLine(added, links) + " Nahi li: " + reasons.joinToString(" | ").take(300)
        }
    }

    /** Stream copy. Returns (ok, bytesCopied, reason). Deletes the half-written file on any failure. */
    private fun copyStream(r: ContentResolver, src: Uri, dst: File, a: MediaIntake.Accepted): Triple<Boolean, Long, String> {
        var total = 0L
        try {
            r.openInputStream(src).use { input ->
                if (input == null) return Triple(false, 0L, "File khul nahi payi.")
                FileOutputStream(dst).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastPct = -1
                    while (true) {
                        if (cancelFlag.get()) { dst.delete(); return Triple(false, total, "Roka gaya.") }
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (!MediaIntake.streamOk(a.kind, total)) { out.close(); dst.delete(); return Triple(false, total, MediaIntake.check(a.kind, total).reason) }
                        out.write(buf, 0, n)
                        if (a.size > 0) { val p = (total * 100 / a.size).toInt().coerceIn(0, 100); if (p != lastPct) { lastPct = p; progressPct = p } }
                    }
                }
            }
            if (total == 0L) { dst.delete(); return Triple(false, 0L, "File khaali hai (0 byte).") }
            progressPct = 100
            return Triple(true, total, "")
        } catch (e: OutOfMemoryError) {
            dst.delete(); return Triple(false, total, "Memory kam padi, file chhodi.")
        } catch (e: Throwable) {
            dst.delete(); return Triple(false, total, "Copy nahi ho payi: " + (e.message ?: e.javaClass.simpleName).take(60))
        }
    }

    /** Item the next chat message is about (set by CHAT ME JODO; Part 13 reads it). Id only, never a path. */
    @Volatile var attachedId = ""; private set

    fun attach(c: Context, id: String?): String {
        val e = list(c).firstOrNull { it.id == id } ?: return ""
        attachedId = e.id
        return if (e.kind == MediaIntake.Kind.LINK) e.url else MediaIntake.chatLabel(e.name, e.size)
    }

    /** JPEG data URL (<= ~400 KB text) of a photo or the first frame of a video, longest side [maxSide]. Decoded with inSampleSize, bitmaps recycled. "" on any problem. */
    fun thumbDataUrl(c: Context, id: String?, maxSide: Int): String {
        val e = list(c).firstOrNull { it.id == id } ?: return ""
        val f = fileOf(c, id) ?: return ""
        var bmp: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            if (e.kind == MediaIntake.Kind.IMAGE) {
                val o = BitmapFactory.Options(); o.inJustDecodeBounds = true
                BitmapFactory.decodeFile(f.absolutePath, o)
                if (o.outWidth <= 0 || o.outHeight <= 0) return ""
                var ss = 1
                while (o.outWidth / (ss * 2) >= maxSide && o.outHeight / (ss * 2) >= maxSide) ss *= 2
                val o2 = BitmapFactory.Options(); o2.inSampleSize = ss; o2.inPreferredConfig = Bitmap.Config.RGB_565
                bmp = BitmapFactory.decodeFile(f.absolutePath, o2)
            } else if (e.kind == MediaIntake.Kind.VIDEO) {
                val r = MediaMetadataRetriever()
                try { r.setDataSource(f.absolutePath); bmp = r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) } finally { try { r.release() } catch (x: Throwable) { } }
            }
            val b = bmp ?: return ""
            val k = maxSide.toFloat() / Math.max(b.width, b.height).toFloat()
            val sc: Bitmap = if (k < 1f) Bitmap.createScaledBitmap(b, Math.max(1, (b.width * k).toInt()), Math.max(1, (b.height * k).toInt()), true) else b
            scaled = sc
            val bo = java.io.ByteArrayOutputStream()
            sc.compress(Bitmap.CompressFormat.JPEG, 72, bo)
            val txt = "data:image/jpeg;base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
            return if (txt.length <= 400_000) txt else ""
        } catch (x: OutOfMemoryError) { return "" } catch (x: Throwable) { return "" }
        finally { try { if (scaled != null && scaled !== bmp) scaled.recycle(); bmp?.recycle() } catch (x: Throwable) { ErrLog.addOnce("quiet:MediaIntakeStore", x.toString()) } }
    }

    /** JSON for the UI (11-C bridge): no path, no uri. Only id, kind, name, size text, age, link url. */
    fun listJson(c: Context): String {
        val a = JSONArray()
        for (e in list(c)) a.put(JSONObject().put("id", e.id).put("kind", e.kind.name.lowercase()).put("name", e.name)
            .put("size", MediaIntake.humanSize(e.size)).put("link", e.url).put("note", if (e.kind == MediaIntake.Kind.LINK) MediaIntake.linkNote(e.url) else ""))
        return JSONObject().put("items", a).put("busy", busy).put("progress", progressPct).put("name", progressName).put("message", lastMessage).toString()
    }
}
