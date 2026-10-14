package com.nova.assistant

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Part 6C: manager for recordings created by NOVA only. MediaStore paths are deliberately scoped to NOVA's folders. */
object RecordingManager {
    private const val PREF = "nova_recording_manager"
    private const val KEY_DAYS = "auto_delete_days"
    data class Item(val id: String, val name: String, val uri: Uri, val mime: String, val size: Long, val date: Long, val duration: Long, val privateFile: File? = null)

    fun autoDeleteDays(ctx: Context): Int = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY_DAYS, 0).coerceIn(0, 365)

    fun setAutoDeleteDays(ctx: Context, days: Int): String {
        if (days !in setOf(0, 7, 30, 90, 180, 365)) return "invalid"
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY_DAYS, days).apply()
        if (days > 0) deleteOld(ctx, days)
        return "ok"
    }

    fun list(ctx: Context): List<Item> {
        val items = ArrayList<Item>()
        if (Build.VERSION.SDK_INT >= 29) {
            queryMedia(ctx, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "Music/NOVA/", "audio/mp4", items)
            queryMedia(ctx, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/NOVA/", "video/mp4", items)
        }
        // Legacy Android and unfinished private files are listed without pretending that they're public MediaStore files.
        val audioDir = RecordSession.dir(ctx)
        // Never offer the current writer's unfinished segment to Play/Share/Delete.
        if (!RecordSession.active) audioDir.listFiles()?.filter { it.isFile && it.extension.equals("m4a", true) }?.forEach { f ->
            if (items.none { it.name == f.name }) items.add(Item("file:" + f.absolutePath, f.name, Uri.fromFile(f), "audio/mp4", f.length(), f.lastModified(), durationFromAudio(f), f))
        }
        if (!VideoRecordService.isBusy()) videoDir(ctx).listFiles()?.filter { it.isFile && it.extension.equals("mp4", true) }?.forEach { f ->
            if (items.none { it.name == f.name }) items.add(Item("file:" + f.absolutePath, f.name, Uri.fromFile(f), "video/mp4", f.length(), f.lastModified(), durationFromVideo(f), f))
        }
        return items.sortedByDescending { it.date }
    }

    private fun queryMedia(ctx: Context, base: Uri, relPath: String, mime: String, out: MutableList<Item>) {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.Video.VideoColumns.DURATION)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.IS_PENDING}=0"
        try {
            ctx.contentResolver.query(base, projection, selection, arrayOf(relPath), "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { c ->
                val idI = c.getColumnIndex(MediaStore.MediaColumns._ID); val nameI = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeI = c.getColumnIndex(MediaStore.MediaColumns.SIZE); val dateI = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val modI = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED); val mimeI = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val durI = c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                while (c.moveToNext()) {
                    if (idI < 0 || nameI < 0) continue
                    val id = c.getLong(idI); val uri = Uri.withAppendedPath(base, id.toString())
                    out.add(Item(
                        id = uri.toString(),
                        name = c.safeString(nameI),
                        uri = uri,
                        mime = c.safeString(mimeI).ifBlank { mime },
                        size = c.safeLong(sizeI),
                        date = c.safeLong(dateI) * 1000L,
                        duration = if (durI >= 0) c.safeLong(durI) else 0L
                    ))
                }
            }
        } catch (ignored: Exception) { ErrLog.add("RecordingManager", ignored.toString()) }
    }

    private fun Cursor.safeString(i: Int): String = if (i >= 0) try { getString(i) ?: "" } catch (ignored: Exception) { "" } else ""
    private fun Cursor.safeLong(i: Int): Long = if (i >= 0) try { getLong(i) } catch (ignored: Exception) { 0L } else 0L

    fun listJson(ctx: Context): String {
        val a = JSONArray()
        list(ctx).forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("mime", it.mime)
            .put("size", it.size).put("date", it.date).put("duration", it.duration).put("location", if (it.privateFile == null) "NOVA folder" else "app folder")) }
        return a.toString()
    }

    private fun resolve(ctx: Context, id: String): Item? = list(ctx).firstOrNull { it.id == id }

    fun play(ctx: Context, id: String): String {
        val item = resolve(ctx, id) ?: return "not_found"
        val uri = contentUri(ctx, item) ?: return "unsupported"
        return try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(Intent.createChooser(i, "NOVA recording").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "ok"
        } catch (ignored: Exception) { "no_player" }
    }

    fun share(ctx: Context, id: String): String {
        val item = resolve(ctx, id) ?: return "not_found"
        val uri = contentUri(ctx, item) ?: return "unsupported"
        return try {
            val i = Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(Intent.createChooser(i, "Share NOVA recording").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "ok"
        } catch (ignored: Exception) { "fail" }
    }

    fun delete(ctx: Context, id: String): Boolean {
        val item = resolve(ctx, id) ?: return false
        return try { if (item.privateFile != null) item.privateFile.delete() else ctx.contentResolver.delete(item.uri, null, null) > 0 } catch (ignored: Exception) { false }
    }

    private fun contentUri(ctx: Context, item: Item): Uri? {
        if (item.privateFile == null) return item.uri
        return try { FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", item.privateFile) } catch (ignored: Exception) { null }
    }

    fun videoDir(ctx: Context): File = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir, "rec")

    /** Best-effort recovery: promote only crash-left files that a media parser can actually open. Invalid parts are retained for review. */
    fun recover(ctx: Context): String {
        if (RecordSession.active || VideoRecordService.isBusy()) return "busy; stop the current recording first"
        var recovered = 0; var retained = 0
        for (f in (RecordSession.dir(ctx).listFiles() ?: emptyArray()) + (videoDir(ctx).listFiles() ?: emptyArray())) {
            if (!f.isFile || f.length() < 16 * 1024L) continue
            if (f.name.endsWith(".m4a", true) && validAudio(f)) {
                if (Build.VERSION.SDK_INT >= 29 && export(ctx, f, false)) { f.delete(); recovered++ } else retained++
            } else if (f.name.endsWith(".mp4", true) && validVideo(f)) {
                if (Build.VERSION.SDK_INT >= 29 && export(ctx, f, true)) { f.delete(); recovered++ } else retained++
            } else if (f.extension.lowercase() in listOf("m4a", "mp4", "part")) retained++
        }
        return "recovered=$recovered; retained=$retained"
    }

    private fun validAudio(f: File): Boolean = try {
        val ex = MediaExtractor(); try { ex.setDataSource(f.absolutePath); (0 until ex.trackCount).any { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } } finally { ex.release() }
    } catch (ignored: Exception) { false }

    private fun validVideo(f: File): Boolean = try {
        val mr = MediaMetadataRetriever(); try { mr.setDataSource(f.absolutePath); !mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).isNullOrBlank() } finally { mr.release() }
    } catch (ignored: Exception) { false }

    private fun durationFromAudio(f: File): Long = try {
        val mr = MediaMetadataRetriever(); try { mr.setDataSource(f.absolutePath); mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { mr.release() }
    } catch (ignored: Exception) { 0L }
    private fun durationFromVideo(f: File): Long = try {
        val mr = MediaMetadataRetriever(); try { mr.setDataSource(f.absolutePath); mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { mr.release() }
    } catch (ignored: Exception) { 0L }

    private fun export(ctx: Context, f: File, video: Boolean): Boolean { return try {
        val cr = ctx.contentResolver
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, f.name); put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "audio/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/NOVA" else "Music/NOVA")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = cr.insert(base, cv) ?: return false
        var ok = false
        try { cr.openOutputStream(uri)?.use { out -> f.inputStream().use { input -> input.copyTo(out) }; ok = true } } catch (ignored: Exception) { ErrLog.add("RecordingManager", ignored.toString()) }
        if (ok && Build.VERSION.SDK_INT >= 29) cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        if (!ok) cr.delete(uri, null, null)
        ok
    } catch (ignored: Exception) { false } }

    private fun deleteOld(ctx: Context, days: Int) {
        val cutoff = System.currentTimeMillis() - days.toLong() * 86_400_000L
        list(ctx).filter { it.date > 0 && it.date < cutoff }.forEach { item -> delete(ctx, item.id) }
    }
}
