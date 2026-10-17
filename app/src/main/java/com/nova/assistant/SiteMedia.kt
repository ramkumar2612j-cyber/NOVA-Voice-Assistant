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
        places.clear()
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
                places[it.token]?.let { pl -> append("  (the owner wants this one in the ").append(MediaPlace.label(pl)).append(" section)\n") }
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

    // ===== v7.08 PART 5 (add-only, pure Kotlin, no Android classes): AI picture placeholders + owner-picked media =====
    class UserApplied(val html: String, val placed: List<String>, val missing: List<String>)

    private val IMG_PH = Regex("NOVA_IMG:([^\"'<>)]+)")

    /** Distinct NOVA_IMG:<description> placeholders of a page, in order of first appearance. */
    fun placeholders(html: String): List<String> {
        val out = LinkedHashSet<String>()
        for (m in IMG_PH.findAll(html)) { val d = clean(m.groupValues[1]); if (d.isNotEmpty()) out.add(d) }
        return out.toList()
    }

    /** One short line (max 160 chars) for a picture prompt. */
    fun clean(s: String): String = s.replace(Regex("\\s+"), " ").trim().take(160)

    /** Extra rule text for the website prompt. Mentions video only when asked. */
    fun promptBlock(images: Boolean, video: Boolean): String {
        val sb = StringBuilder()
        if (images) sb.append("PICTURES: where a photo fits, write src=\"NOVA_IMG:<short English description of the picture>\" (or url('NOVA_IMG:...') in CSS). NOVA makes each one with AI. Use 3 to 6 different descriptions, never real brand or person names.\n")
        else sb.append("The owner does not want photos: use no <img> and no NOVA_IMG placeholders, only text, colours and CSS shapes.\n")
        if (video) sb.append("The owner asked for VIDEO: NOVA cannot make AI video, so give the page an animated CSS/canvas background instead of a <video> tag.\n")
        return sb.toString()
    }

    private fun mediaTag(it: SiteMediaStore.Item, style: String): String =
        if (it.kind == "video") "<video src=\"${it.uri}\" controls muted playsinline loop style=\"$style\"></video>"
        else "<img src=\"${it.uri}\" alt=\"\" style=\"$style\">"

    /** Prompt text listing the owner's picked media so the AI puts NOVA_USER_MEDIA:<id> where it belongs. "" when none. */
    fun userBlock(items: List<SiteMediaStore.Item>): String {
        if (items.isEmpty()) return ""
        val sb = StringBuilder("OWNER MEDIA (use each one exactly once as src=\"NOVA_USER_MEDIA:<id>\", never invent other ids):\n")
        for (i in items) {
            sb.append("- NOVA_USER_MEDIA:").append(i.id).append("  ").append(i.id).append(" = ").append(i.kind)
            if (i.place != null) sb.append(", place: ").append(i.place!!.name.lowercase())
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Swap NOVA_USER_MEDIA:<id> for the real data: URI; items the AI forgot are injected by their place. Dollar signs in data are safe (literal replace). */
    fun applyUser(html: String, items: List<SiteMediaStore.Item>): UserApplied {
        if (items.isEmpty()) return UserApplied(html, emptyList(), emptyList())
        var h = html
        val placed = ArrayList<String>()
        val missing = ArrayList<String>()
        for (it in items) {
            val tok = "NOVA_USER_MEDIA:" + it.id
            if (!h.contains(tok)) { missing.add(it.id); continue }
            placed.add(it.id)
            if (it.kind == "video") {
                h = Regex("<img\\b[^>]*" + Regex.escape(tok) + "[^>]*>").replace(h) { _ -> mediaTag(it, "max-width:100%") }
            }
            h = h.replace(tok, it.uri)
        }
        for (it in items) {
            if (it.id !in missing) continue
            val place = it.place
            when (place) {
                MediaPlace.Place.BACKGROUND -> {
                    val css = "<style>body{background-image:url('${it.uri}');background-size:cover;background-attachment:fixed}</style>"
                    h = if (h.contains("</head>", true)) insertAt(h, "</head>", css, false) else css + h
                }
                MediaPlace.Place.LOGO -> h = afterBodyOpen(h, mediaTag(it, "height:56px;width:auto;display:block;margin:8px auto"))
                MediaPlace.Place.HERO -> {
                    val tag = mediaTag(it, "width:100%;max-height:60vh;object-fit:cover;display:block")
                    h = if (h.contains("</header>", true)) insertAt(h, "</header>", tag, true) else afterBodyOpen(h, tag)
                }
                else -> {
                    val sec = "<section style=\"padding:16px;text-align:center\">" + mediaTag(it, "max-width:100%;border-radius:12px") + "</section>"
                    h = if (h.contains("</body>", true)) insertAt(h, "</body>", sec, false) else h + sec
                }
            }
        }
        h = Regex("<img\\b[^>]*NOVA_USER_MEDIA:[A-Za-z0-9_]+[^>]*>").replace(h, "")
        h = Regex("NOVA_USER_MEDIA:[A-Za-z0-9_]+").replace(h, "")
        return UserApplied(h, placed, missing)
    }

    /** Insert text before (after=false) or after (after=true) the first case-insensitive marker. Literal, so $ is safe. */
    private fun insertAt(h: String, marker: String, text: String, after: Boolean): String {
        val i = h.indexOf(marker, 0, true)
        if (i < 0) return h + text
        val cut = if (after) i + marker.length else i
        return h.substring(0, cut) + text + h.substring(cut)
    }

    private fun afterBodyOpen(h: String, tag: String): String {
        val m = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(h)
        return if (m == null) tag + h else h.substring(0, m.range.last + 1) + tag + h.substring(m.range.last + 1)
    }

    // ===== v7.12: where the owner wants each picked photo / video ("dusri photo gallery mein lagao"), kept in memory per token =====
    private val places = java.util.concurrent.ConcurrentHashMap<String, MediaPlace.Place>()

    /** Pure: which of [n] items (0-based) a sentence points at, and the place. null = nothing to do. Never overrides an item that already has a place unless it is named. */
    fun placeTarget(n: Int, text: String, hasPlace: (Int) -> Boolean): Pair<Int, MediaPlace.Place>? {
        if (n <= 0) return null
        val place = MediaPlace.parse(text) ?: return null
        val idx = MediaPlace.itemIndex(text)
        val i = when (idx) {
            null -> (n - 1 downTo 0).firstOrNull { !hasPlace(it) } ?: return null
            -1 -> n - 1
            else -> idx - 1
        }
        if (i !in 0 until n) return null
        return Pair(i, place)
    }

    /** Applies the sentence to the media files in the app folder. Returns how many items got a place. */
    fun placeText(ctx: Context, text: String): Int {
        val items = list(ctx)
        val t = placeTarget(items.size, text) { places.containsKey(items[it].token) } ?: return 0
        places[items[t.first].token] = t.second
        return 1
    }

    // ===== v7.12 (add-only, pure): AI picture placeholders -> data URIs =====
    private val PIC_WORDS = setOf("photo", "photos", "foto", "fotos", "image", "images", "picture", "pictures", "pic", "pics", "tasveer", "tasveeren", "tasviren")

    /** True when the owner explicitly asked for pictures in the site (and did not opt out): only then NOVA spends AI image calls. */
    fun wantsAiPictures(text: String): Boolean = MediaIntent.wantsImages(text) && MediaIntent.tokens(text).any { it in PIC_WORDS }

    /** Replaces each NOVA_IMG:<description> with its data URI from [done] (key = clean(description)); an unknown one becomes a 1x1 pixel, so no broken link stays. */
    fun fillImages(html: String, done: Map<String, String>): String {
        if (!html.contains("NOVA_IMG:")) return html
        return IMG_PH.replace(html) { m -> done[clean(m.groupValues[1])] ?: PIXEL_GIF }
    }

    const val PIXEL_GIF = "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="
}
