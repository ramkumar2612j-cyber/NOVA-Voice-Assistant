package com.nova.assistant

import android.content.Context
import java.io.File

/**
 * v7.0 WEBSITE MEDIA: photos and short videos the owner attaches for the NEXT website (or edit).
 * Files live in the app's private folder (filesDir/site_media). The AI never receives the bytes: it only gets short tokens
 * (__NOVA_IMG_1__, __NOVA_VID_1__) and the app swaps them for data: URIs after the page is written, so the final
 * index.html is self-contained and works offline / on GitHub Pages. Also remembers the last built page so an edit still
 * works after the app was closed.
 */
object SiteMedia {
    class Item(val kind: String, val index: Int, val mime: String, val bytes: ByteArray, val w: Int, val h: Int) {
        val token: String get() = "__NOVA_" + (if (kind == "img") "IMG" else "VID") + "_" + index + "__"
    }

    const val MAX_ITEMS = 12
    const val MAX_IMG_BYTES = 700_000
    const val MAX_VID_BYTES = 6_000_000
    const val MAX_TOTAL = 8_000_000

    private fun dir(ctx: Context): File = File(ctx.filesDir, "site_media").also { it.mkdirs() }

    private fun mimeOf(ext: String) = when (ext) {
        "jpg" -> "image/jpeg"
        "webm" -> "video/webm"
        else -> "video/mp4"
    }

    /** File name: kind_index_WxH.ext, so the list survives a restart with no extra index file. */
    fun list(ctx: Context): List<Item> {
        val out = ArrayList<Item>()
        val files = dir(ctx).listFiles() ?: return out
        for (f in files.sortedBy { it.name }) {
            val m = Regex("^(img|vid)_(\\d+)_(\\d+)x(\\d+)\\.(jpg|mp4|webm)$").find(f.name) ?: continue
            try {
                out.add(Item(m.groupValues[1], m.groupValues[2].toInt(), mimeOf(m.groupValues[5]), f.readBytes(), m.groupValues[3].toInt(), m.groupValues[4].toInt()))
            } catch (e: Exception) { }
        }
        return out.sortedWith(compareBy({ it.kind }, { it.index }))
    }

    fun count(ctx: Context): Int = (dir(ctx).listFiles() ?: emptyArray()).count { it.name.startsWith("img_") || it.name.startsWith("vid_") }

    private fun totalBytes(ctx: Context): Long = (dir(ctx).listFiles() ?: emptyArray()).filter { it.name.startsWith("img_") || it.name.startsWith("vid_") }.sumOf { it.length() }

    /** Returns null when added, otherwise a short Hinglish reason. */
    fun add(ctx: Context, kind: String, ext: String, bytes: ByteArray, w: Int, h: Int): String? {
        if (kind != "img" && kind != "vid") return "Ye file type website ke liye nahi chalta"
        if (count(ctx) >= MAX_ITEMS) return "Ek baar mein sirf $MAX_ITEMS photo/video. Pehle X dabake purani hatao"
        if (kind == "img" && bytes.size > MAX_IMG_BYTES) return "Photo bahut bhaari hai"
        if (kind == "vid" && bytes.size > MAX_VID_BYTES) return "Video 6 MB se chhota chuno (10-15 second ka clip)"
        if (totalBytes(ctx) + bytes.size > MAX_TOTAL) return "Total media bahut ho gaya (8 MB limit). Kuch hatao"
        val next = (list(ctx).filter { it.kind == kind }.maxOfOrNull { it.index } ?: 0) + 1
        return try {
            File(dir(ctx), kind + "_" + next + "_" + w + "x" + h + "." + ext).writeBytes(bytes)
            null
        } catch (e: Exception) { "Media save nahi ho paya" }
    }

    fun clear(ctx: Context) {
        try { dir(ctx).listFiles()?.forEach { if (it.name.startsWith("img_") || it.name.startsWith("vid_")) it.delete() } } catch (e: Exception) { }
    }

    /** What the model is told about the attached media. Empty when none. */
    fun describe(items: List<Item>): String {
        if (items.isEmpty()) return ""
        return buildString {
            append("\n\nOWNER'S UPLOADED MEDIA (MUST be used visibly; they are the real photos/videos of this business):\n")
            for (it in items) {
                if (it.kind == "img") append("- Photo ").append(it.index).append(" (").append(it.w).append('x').append(it.h).append(") -> use as src=\"").append(it.token).append("\" in <img>, or inside CSS url(").append(it.token).append(")\n")
                else append("- Video ").append(it.index).append(" (short clip) -> use <video autoplay muted loop playsinline controls preload=\"metadata\"><source src=\"").append(it.token).append("\" type=\"").append(it.mime).append("\"></video>\n")
            }
            append("Write each token EXACTLY as shown (the app replaces it with the real file). Put photos where they fit best (hero, gallery, about, cards); use every photo at least once. ")
            append("Give images alt text, object-fit:cover and sensible aspect-ratio so they never stretch. Do not invent other image URLs.\n")
        }
    }

    private const val PIXEL = "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="

    private fun b64(b: ByteArray): String = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP)

    /** Replaces every token with its data: URI; unknown tokens become a 1x1 pixel so nothing shows a broken link. */
    fun substitute(template: String, items: List<Item>): String {
        if (!template.contains("__NOVA_")) return template
        var html = template
        for (it in items) {
            if (html.contains(it.token)) html = html.replace(it.token, "data:" + it.mime + ";base64," + b64(it.bytes))
        }
        return Regex("__NOVA_(IMG|VID)_\\d+__").replace(html, PIXEL)
    }

    /** Pure: which tokens of the attached media the page actually uses. */
    fun unusedTokens(template: String, items: List<Item>): List<String> = items.filter { !template.contains(it.token) }.map { it.token }

    // ------------------------------------------------------------------ last built page (survives app restarts)
    private fun lastFile(ctx: Context) = File(ctx.filesDir, "last_site_template.html")
    private fun lastSubj(ctx: Context) = File(ctx.filesDir, "last_site_subject.txt")

    fun saveLast(ctx: Context, subject: String, template: String) {
        try { lastFile(ctx).writeText(template, Charsets.UTF_8); lastSubj(ctx).writeText(subject.take(300), Charsets.UTF_8) } catch (e: Exception) { }
    }

    /** Returns Pair(subject, template) or null. Only when younger than maxAgeMs. */
    fun loadLast(ctx: Context, maxAgeMs: Long): Pair<String, String>? = try {
        val f = lastFile(ctx)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > maxAgeMs) null
        else {
            val t = f.readText(Charsets.UTF_8)
            if (t.length < 200) null else Pair(try { lastSubj(ctx).readText(Charsets.UTF_8) } catch (e: Exception) { "website" }, t)
        }
    } catch (e: Exception) { null }
}
