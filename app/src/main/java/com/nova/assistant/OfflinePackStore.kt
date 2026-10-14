package com.nova.assistant

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * v6.96: installs the OfflinePack models by itself. State lives in filesDir/pack_models.json (id -> path), files in filesDir/models/pack/.
 * Download = existing ModelDownload (resumable, size + sha256 checked, a wrong file is deleted). Network calls: worker thread only.
 * Evidence level: written + statically checked. NOT compiled, NOT run on a phone, Hugging Face API shape taken from its public docs.
 */
object OfflinePackStore {
    private val lock = Any()
    private fun stateFile(ctx: Context) = File(ctx.applicationContext.filesDir, "pack_models.json")
    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "models/pack").also { it.mkdirs() }

    /** id -> path of installed files that still exist. */
    fun installed(ctx: Context): Map<String, String> = synchronized(lock) {
        val out = LinkedHashMap<String, String>()
        try {
            val f = stateFile(ctx)
            if (!f.isFile) return@synchronized out
            val o = JSONObject(f.readText())
            val it = o.keys()
            while (it.hasNext()) {
                val id = it.next()
                val p = o.optString(id, "")
                if (OfflinePack.byId(id) != null && p.isNotEmpty() && File(p).isFile) out[id] = p
            }
        } catch (e: Exception) {
            ErrLog.add("OfflinePackStore", e.toString())
        }
        out
    }

    fun any(ctx: Context): Boolean = installed(ctx).isNotEmpty()

    private fun mark(ctx: Context, id: String, path: String) = synchronized(lock) {
        val cur = installed(ctx)
        val o = JSONObject()
        for ((k, v) in cur) o.put(k, v)
        o.put(id, path)
        val tmp = File(stateFile(ctx).path + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(stateFile(ctx))) { stateFile(ctx).writeText(o.toString()); tmp.delete() }
    }

    /** Asks Hugging Face for the repo's file list. null = no answer / repo gone (the model is skipped this round). */
    private fun fetchTree(m: OfflinePack.Model): String? {
        var c: HttpURLConnection? = null
        return try {
            val u = URL(OfflinePack.treeUrl(m))
            if (u.protocol != "https" || u.host != "huggingface.co") return null
            c = u.openConnection() as HttpURLConnection
            c.requestMethod = "GET"
            c.connectTimeout = 10000
            c.readTimeout = 20000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "NOVA-pack")
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) return null
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            c.inputStream.use { ins ->
                while (out.size() < 1_000_000) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
            out.toString("UTF-8")
        } catch (e: Exception) {
            null
        } finally {
            try { c?.disconnect() } catch (e: Exception) { }
        }
    }

    /**
     * Downloads ONE model. null = installed and verified, otherwise a short reason.
     * Reasons that start with "refused" mean the file was wrong and was deleted (do not retry in a loop).
     */
    fun installOne(ctx: Context, m: OfflinePack.Model, cancelled: () -> Boolean = { false }, progress: (Int) -> Unit = {}): String? {
        val app = ctx.applicationContext
        if (installed(app).containsKey(m.id)) return null
        val tree = fetchTree(m) ?: return "file list not reachable"
        val pf = OfflinePack.pickFile(tree, m) ?: return "refused: no usable Q4_K_M file in " + m.repo
        val d = dir(app)
        val part = File(d, m.id + ".part")
        val r = ModelDownload.download(
            OfflinePack.downloadUrl(m, pf), part, pf.size, pf.sha256,
            { Updater.hostAllowed(it) }, cancelled, progress, d.usableSpace
        )
        return when (r) {
            is ModelDownload.Result.Ok -> {
                val dest = File(d, m.id + ".gguf")
                if (dest.exists()) dest.delete()
                if (!r.file.renameTo(dest)) { r.file.delete(); "could not save the file" }
                else { mark(app, m.id, dest.absolutePath); null }
            }
            is ModelDownload.Result.Fail -> if (r.resumable) r.reason else "refused: " + r.reason
        }
    }

    fun freeBytes(ctx: Context): Long = try { dir(ctx).usableSpace } catch (e: Exception) { 0L }
}
