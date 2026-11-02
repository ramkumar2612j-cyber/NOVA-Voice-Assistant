package com.nova.assistant

/**
 * v7.51 PART 11-A - MEDIA INTAKE RULES. Pure Kotlin: no Android classes, no network, no file access (JUnit-testable).
 *
 * The owner shares a photo / video / link to NOVA (Share -> NOVA) or picks media in the app. This file only DECIDES:
 *  - what kind it is ([classify]): image / video / link / unknown (mime first, file extension second),
 *  - whether it is allowed ([check]): photo <= 25 MB, video <= 300 MB, at most 6 files per share, https links only,
 *  - what it is called ([cleanName]) and what may be shown in chat ([chatLabel]: name + size, NEVER a path),
 *  - which links are safe to read ([extractUrls]): https only, tracking params untouched, no user:pass@, no localhost / raw IP,
 *  - duplicates ([plan] drops the same uri, and the same name + size),
 *  - when a cached copy is stale ([isExpired], 24 hours).
 * 11-B (MediaIntakeStore: the real stream copy + Manifest filter) and 11-C (bridge + card) call these functions; nothing here moves a byte.
 * Honest rule: NOVA does NOT download a reel / video from a link. A link only gives page title / description ([linkNote] says so).
 * Never touches PersonaRules / auto-reply / safety rules.
 */
object MediaIntake {
    enum class Kind { IMAGE, VIDEO, LINK, UNKNOWN }

    const val MAX_PHOTO_BYTES = 25L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 300L * 1024 * 1024
    const val MAX_FILES = 6
    const val MAX_LINKS = 3
    const val TTL_MS = 24L * 60 * 60 * 1000
    const val MAX_NAME = 60
    const val MAX_URL = 2000

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    private val VIDEO_EXT = setOf("mp4", "mov", "mkv", "webm", "3gp", "m4v", "avi")
    private val VIDEO_HOSTS = listOf("instagram.com", "youtube.com", "youtu.be", "tiktok.com", "facebook.com", "fb.watch", "x.com", "twitter.com", "vimeo.com", "snapchat.com")

    /** One thing the system handed us (from ACTION_SEND / picker). [size] is -1 when the provider did not say. */
    data class Candidate(val uri: String, val name: String?, val mime: String?, val size: Long)
    data class Accepted(val uri: String, val kind: Kind, val name: String, val size: Long)
    data class Rejected(val name: String, val reason: String)
    data class Plan(val accepted: List<Accepted>, val links: List<String>, val rejected: List<Rejected>)
    data class Check(val ok: Boolean, val reason: String)

    // ---------------------------------------------------------------- kind

    /** Mime wins; a wrong / missing mime falls back to the file extension. "text/plain" is NOT a file (it carries a link): UNKNOWN here. */
    fun classify(mime: String?, name: String?): Kind {
        val m = (mime ?: "").trim().lowercase().substringBefore(";")
        if (m.startsWith("image/")) return Kind.IMAGE
        if (m.startsWith("video/")) return Kind.VIDEO
        val ext = (name ?: "").trim().lowercase().substringAfterLast('.', "")
        return when (ext) {
            in IMAGE_EXT -> Kind.IMAGE
            in VIDEO_EXT -> Kind.VIDEO
            else -> Kind.UNKNOWN
        }
    }

    /** Only content:// uris from the system share / picker. file:// could point into the app's own private data, so it is refused. */
    fun isSafeUri(uri: String?): Boolean {
        val u = (uri ?: "").trim()
        return u.length in 10..MAX_URL && u.lowercase().startsWith("content://") && !u.contains("..")
    }

    // ---------------------------------------------------------------- size

    fun limitFor(kind: Kind): Long = when (kind) { Kind.IMAGE -> MAX_PHOTO_BYTES; Kind.VIDEO -> MAX_VIDEO_BYTES; else -> 0L }

    /** [size] -1 = unknown: allowed now, but the copy loop must stop with [streamOk] when it passes the limit. size 0 = empty file = refused. */
    fun check(kind: Kind, size: Long): Check {
        if (kind != Kind.IMAGE && kind != Kind.VIDEO) return Check(false, "Ye photo ya video nahi lagti, isliye nahi li.")
        if (size == 0L) return Check(false, "File khaali hai (0 byte).")
        if (size < 0L) return Check(true, "")
        val lim = limitFor(kind)
        if (size > lim) {
            val what = if (kind == Kind.IMAGE) "Photo" else "Video"
            return Check(false, "$what bahut badi hai (${humanSize(size)}). Limit ${humanSize(lim)} hai.")
        }
        return Check(true, "")
    }

    /** For the stream copy: bytes copied so far must stay inside the limit, whatever the provider claimed. */
    fun streamOk(kind: Kind, copiedBytes: Long): Boolean = copiedBytes <= limitFor(kind)

    fun humanSize(bytes: Long): String {
        if (bytes < 0L) return "?"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return fmt1(kb) + " KB"
        val mb = kb / 1024.0
        if (mb < 1024.0) return fmt1(mb) + " MB"
        return fmt1(mb / 1024.0) + " GB"
    }

    private fun fmt1(v: Double): String {
        val t = Math.round(v * 10.0)
        return (t / 10).toString() + "." + (t % 10).toString()
    }

    // ---------------------------------------------------------------- names

    /** Safe display / cache name: no folders, no control chars, <= 60 chars (extension kept), never empty. */
    fun cleanName(raw: String?, kind: Kind = Kind.UNKNOWN, index: Int = 1): String {
        var n = (raw ?: "").substringAfterLast('/').substringAfterLast('\\')
        n = n.filter { it.code >= 32 && it.code != 127 && it !in "<>:\"|?*" }.replace(Regex("\\s+"), " ").trim()
        n = n.trimStart('.')
        if (n.isBlank()) return fallbackName(kind, index)
        if (n.length > MAX_NAME) {
            val dot = n.lastIndexOf('.')
            val ext = if (dot > 0 && n.length - dot <= 6) n.substring(dot) else ""
            n = n.substring(0, MAX_NAME - ext.length).trimEnd() + ext
        }
        return n
    }

    private fun fallbackName(kind: Kind, index: Int): String = when (kind) {
        Kind.IMAGE -> "photo_$index.jpg"
        Kind.VIDEO -> "video_$index.mp4"
        Kind.LINK -> "link_$index"
        else -> "media_$index"
    }

    /** What chat history may keep: name + size only. A uri or path never goes into chat. */
    fun chatLabel(name: String, size: Long): String = cleanName(name) + " (" + humanSize(size) + ")"

    // ---------------------------------------------------------------- links

    /**
     * Pulls https links out of shared text, in order, without duplicates, at most [MAX_LINKS].
     * The URL text is kept exactly (tracking params like ?igsh= are NOT touched); only trailing sentence punctuation is cut.
     * Refused: http://, user:pass@host, localhost / *.local / *.internal, raw IP addresses, hosts without a dot, > 2000 chars.
     */
    fun extractUrls(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val out = ArrayList<String>()
        for (m in Regex("https://[^\\s<>\"'`]+", RegexOption.IGNORE_CASE).findAll(text)) {
            val u = trimTail(m.value)
            if (isSafeLink(u) && out.none { it.equals(u, ignoreCase = true) }) out.add(u)
            if (out.size >= MAX_LINKS) break
        }
        return out
    }

    private fun trimTail(s: String): String {
        var u = s
        while (u.isNotEmpty()) {
            val c = u.last()
            val cut = when (c) {
                '.', ',', ';', ':', '!', '?', '…' -> true
                ')' -> u.count { it == ')' } > u.count { it == '(' }
                ']' -> u.count { it == ']' } > u.count { it == '[' }
                '}' -> true
                else -> false
            }
            if (!cut) break
            u = u.dropLast(1)
        }
        return u
    }

    fun isSafeLink(url: String): Boolean {
        if (url.length > MAX_URL || !url.lowercase().startsWith("https://")) return false
        val rest = url.substring(8)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty() || authority.contains('@')) return false
        val host = authority.substringBefore(':').lowercase().trimEnd('.')
        if (host.length < 4 || !host.contains('.')) return false
        if (!host.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' }) return false   // also refuses [ipv6]
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost")) return false
        if (Regex("^[0-9.]+$").matches(host)) return false                                            // raw IPv4
        if (host.startsWith(".") || host.startsWith("-") || host.contains("..")) return false
        return true
    }

    private fun hostOf(url: String): String =
        url.substring(8).takeWhile { it != '/' && it != '?' && it != '#' }.substringBefore(':').lowercase().trimEnd('.')

    /** true when the page is on a known video / reel site (only used to choose the honest wording; nothing is downloaded). */
    fun isVideoLink(url: String): Boolean {
        if (!isSafeLink(url)) return false
        val h = hostOf(url)
        return VIDEO_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    /** The honest sentence the card / voice uses for a link. */
    fun linkNote(url: String): String =
        if (isVideoLink(url)) "Video file nahi mili, sirf link ki jankari (page ka title / description) se kaam karunga. Asli video chahiye to gallery se share karo."
        else "Ye sirf link hai. Page ka title / description padh sakta hoon; koi file download nahi hogi."

    // ---------------------------------------------------------------- one share = one plan

    /**
     * Turns what the system sent into a decision. Order is kept. Duplicates (same uri, or same name + known size) are dropped silently;
     * files past [MAX_FILES] (counting [alreadyHave] cached items) are rejected with a clear reason; unsafe uris, wrong types and
     * too-big files are rejected with a reason the card can show. [sharedText] (text/plain part of the same share) only yields links.
     */
    fun plan(candidates: List<Candidate>, sharedText: String? = null, alreadyHave: Int = 0): Plan {
        val acc = ArrayList<Accepted>()
        val rej = ArrayList<Rejected>()
        val seenUri = HashSet<String>()
        val seenKey = HashSet<String>()
        var idx = 0
        for (c in candidates) {
            idx++
            val uri = c.uri.trim()
            val kind = classify(c.mime, c.name)
            val name = cleanName(c.name, kind, idx)
            if (!seenUri.add(uri)) continue
            if (!isSafeUri(uri)) { rej.add(Rejected(name, "Ye file ka raasta surakshit nahi hai, isliye nahi li.")); continue }
            val key = name.lowercase() + "|" + c.size
            if (c.size > 0L && !seenKey.add(key)) continue
            val ck = check(kind, c.size)
            if (!ck.ok) { rej.add(Rejected(name, ck.reason)); continue }
            if (alreadyHave + acc.size >= MAX_FILES) {
                rej.add(Rejected(name, "Ek baar mein sirf $MAX_FILES file. Pehle kuch HATAO ya SAAF KARO."))
                continue
            }
            acc.add(Accepted(uri, kind, name, c.size))
        }
        return Plan(acc, extractUrls(sharedText), rej)
    }

    // ---------------------------------------------------------------- 24 hour cache rule

    fun isExpired(createdMs: Long, nowMs: Long): Boolean = createdMs <= 0L || nowMs - createdMs >= TTL_MS || nowMs < createdMs

    /** One honest line for "kya mila" (voice / chat). Never a path. */
    fun statusLine(files: Int, links: Int): String = when {
        files == 0 && links == 0 -> "Abhi koi media nahi mila. Kisi app se Share karke NOVA chuno, ya chat me MEDIA CHUNO dabao."
        links == 0 -> "$files file mili hai."
        files == 0 -> "$links link mila hai."
        else -> "$files file aur $links link mile hain."
    }

    // ---------------------------------------------------------------- 11-D voice ("nova kya mila", "ye video analyse karo")

    enum class Voice { NONE, STATUS, ANALYSE_NOT_YET }

    private fun voiceWords(text: String?): List<String> =
        (text ?: "").lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(' ').filter { it.isNotEmpty() }

    /**
     * Pure. STATUS: "kya mila", "kitni file mili" (short sentence about what was shared). ANALYSE_NOT_YET: a video / reel / media / clip word
     * together with analyse / analyze / analysis. Never matches a sentence about the screen (screen analyse belongs to the screen feature).
     * Photos are NOT matched here: this only answers honestly that video analysis does not exist yet (Part 13).
     */
    fun voiceIntent(text: String?): Voice {
        val w = voiceWords(text)
        if (w.isEmpty() || w.size > 10) return Voice.NONE
        val set = w.toSet()
        if ("screen" in set || "skrin" in set) return Voice.NONE
        val mediaWord = set.any { it in setOf("video", "videos", "reel", "reels", "media", "clip", "vedio") }
        val analyseWord = set.any { it in setOf("analyse", "analyze", "analysis", "analyser", "vishleshan") }
        if (mediaWord && analyseWord) return Voice.ANALYSE_NOT_YET
        val joined = w.joinToString(" ")
        val askWord = set.any { it in setOf("kya", "kitni", "kitne", "kaun", "kon", "what", "how") }
        val gotWord = set.any { it in setOf("mila", "mili", "mile", "received", "got", "shared") }
        if ((joined.contains("kya mila") || joined.contains("kya mili") || joined.contains("kya mile") ||
                joined.contains("kitni file") || joined.contains("kitne file") || joined.contains("media mila") ||
                joined.contains("file mili") || joined.contains("what did i share") || joined.contains("what did you get") ||
                joined.contains("kya share")) && (askWord || gotWord)) return Voice.STATUS
        return Voice.NONE
    }

    /** Honest answer to "ye video analyse karo": nothing is analysed yet. No persona touch (the caller uses lastKind "media"). */
    fun analyseNotYetReply(files: Int, links: Int, en: Boolean = false): String {
        val have = if (files == 0 && links == 0) "" else " " + statusLine(files, links)
        return if (en) "Video analysis is not available yet, so I will not pretend to do it. I only keep what you share: SEE, ADD TO CHAT or REMOVE in the MEDIA card." +
            (if (have.isEmpty()) "" else " I have $files file and $links link.")
        else "Video analysis abhi nahi hai, isliye main jhootha daava nahi karunga. Abhi sirf share ki hui file ya link rakhta hoon: MEDIA card me DEKHO, CHAT ME JODO ya HATAO.$have"
    }

    // ---------------------------------------------------------------- 11-B helpers for the store (still pure)

    /** One cached item as the store's index sees it. [file] is the private cache file name ("" for a link). */
    data class Entry(val id: String, val kind: Kind, val name: String, val size: Long, val createdMs: Long, val file: String, val url: String)

    /** Ids whose 24 h are over. The store deletes exactly these (file + index line). */
    fun expiredIds(entries: List<Entry>, nowMs: Long): List<String> = entries.filter { isExpired(it.createdMs, nowMs) }.map { it.id }

    /** Short safe extension for the cache file name: 1..5 letters / digits, lower case, else fixed by kind. Never from a path. */
    fun safeExt(name: String?, kind: Kind): String {
        val e = (name ?: "").substringAfterLast('.', "").lowercase()
        if (e.length in 1..5 && e.all { it in 'a'..'z' || it in '0'..'9' }) return e
        return when (kind) { Kind.IMAGE -> "jpg"; Kind.VIDEO -> "mp4"; else -> "bin" }
    }

    /** Cache file name: id + extension only (the display name never becomes a path). */
    fun cacheFileName(id: String, name: String?, kind: Kind): String = id + "." + safeExt(name, kind)

    /** An id is only letters / digits / underscore, so bridge input can never reach another path. */
    fun isValidId(id: String?): Boolean = !id.isNullOrEmpty() && id.length <= 40 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

    /** How many more files fit right now (6 total, links not counted). */
    fun room(entries: List<Entry>): Int = (MAX_FILES - entries.count { it.kind == Kind.IMAGE || it.kind == Kind.VIDEO }).coerceAtLeast(0)
}
