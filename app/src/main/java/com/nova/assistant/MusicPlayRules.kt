package com.nova.assistant

/**
 * v6.60: "spotify open karo aur legacy song play karo" must really search and play, with no AI call (free AI limits can be used up).
 * Pure text rules; NovaService turns the result into Android's standard play-from-search request for that music app.
 */
object MusicPlayRules {
    data class Request(val appKey: String, val appLabel: String, val packages: List<String>, val query: String)

    private class App(val key: String, val label: String, val words: List<String>, val pkgs: List<String>)

    /** wip13: a music app NOVA knows. Used for the "try another app?" question (always asked, never done silently). */
    data class AppInfo(val key: String, val label: String, val packages: List<String>)

    /** Other music apps in a fixed order, never [excludeKey] and never anything in [tried]. */
    fun others(excludeKey: String, tried: Set<String> = emptySet()): List<AppInfo> =
        APPS.filter { it.key != excludeKey && it.key !in tried }.map { AppInfo(it.key, it.label, it.pkgs) }

    /** What really happened after NOVA asked the music app to play. Only [VERIFIED_MATCH] may be reported as "playing X". */
    enum class Outcome { VERIFIED_MATCH, PLAYING_OTHER, PLAYING_UNCONFIRMED, CANNOT_TELL, NOT_PLAYING }

    /** Lower-case letters / digits / Devanagari, single spaces. */
    fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097f ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun tokens(s: String): List<String> = normalize(s).split(" ").filter { it.length >= 2 }

    /** True when most of the asked words appear in what is playing. */
    fun titleMatches(query: String, playing: String): Boolean {
        val q = tokens(query)
        if (q.isEmpty()) return false
        val p = " " + normalize(playing) + " "
        val hit = q.count { p.contains(" $it ") || p.contains(it) }
        return hit * 10 >= q.size * 6
    }

    /**
     * [sessionPlaying]: true / false = NOVA could read the music app's own media session (Notification access ON); null = it could not.
     * [wasActive]: something was already playing BEFORE the request, so "music is active" alone proves nothing.
     */
    fun judge(wasActive: Boolean, activeNow: Boolean, sessionPlaying: Boolean?, nowTitle: String, nowArtist: String, query: String): Outcome {
        if (sessionPlaying == true) {
            val shown = (nowTitle + " " + nowArtist).trim()
            if (shown.isEmpty()) return Outcome.PLAYING_UNCONFIRMED
            return if (titleMatches(query, shown)) Outcome.VERIFIED_MATCH else Outcome.PLAYING_OTHER
        }
        if (sessionPlaying == false) return Outcome.NOT_PLAYING
        if (!activeNow) return Outcome.NOT_PLAYING
        return if (wasActive) Outcome.CANNOT_TELL else Outcome.PLAYING_UNCONFIRMED
    }

    // ---------------------------------------------------------------- similar songs

    private fun cleanTitle(t: String): String =
        normalize(t.replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex(" - .*$"), ""))

    private fun firstArtist(a: String): String = normalize(a.split(",", "&", " feat", " ft.").first())

    /**
     * Songs that really match [query] and differ from each other (other title or other artist). Fewer than 2 = no ambiguity = empty list.
     * At most [max]. The caller shows these as a tap / voice popup.
     */
    fun choices(query: String, tracks: List<MusicCatalog.Track>, max: Int = 5): List<MusicCatalog.Track> {
        val q = normalize(query)
        val qt = tokens(query)
        if (q.isEmpty() || qt.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<MusicCatalog.Track>()
        for (t in tracks) {
            val title = cleanTitle(t.title)
            val blob = " " + normalize(t.title + " " + t.artist) + " "
            val relevant = title == q || title.contains(q) || qt.all { blob.contains(" $it ") || blob.contains(it) }
            if (!relevant) continue
            if (seen.add(title + "|" + firstArtist(t.artist))) out.add(t)
            if (out.size >= max) break
        }
        return if (out.size >= 2) out else emptyList()
    }

    /** The one clear catalogue match (first relevant song) or null. Used to give the music app a sharper search ("title artist"). */
    fun bestSingle(query: String, tracks: List<MusicCatalog.Track>): MusicCatalog.Track? {
        val q = normalize(query)
        val qt = tokens(query)
        if (q.isEmpty() || qt.isEmpty()) return null
        return tracks.firstOrNull { t ->
            val blob = " " + normalize(t.title + " " + t.artist) + " "
            cleanTitle(t.title) == q || qt.all { blob.contains(" $it ") || blob.contains(it) }
        }
    }

    fun searchQuery(t: MusicCatalog.Track): String = (t.title + " " + t.artist.split(",", "&").first()).trim()

    private val NUM = mapOf(
        "1" to 0, "ek" to 0, "pehla" to 0, "pehle" to 0, "pahla" to 0, "first" to 0, "one" to 0,
        "2" to 1, "do" to 1, "doosra" to 1, "dusra" to 1, "dusre" to 1, "second" to 1, "two" to 1,
        "3" to 2, "teen" to 2, "teesra" to 2, "tisra" to 2, "third" to 2, "three" to 2,
        "4" to 3, "char" to 3, "chaar" to 3, "chautha" to 3, "fourth" to 3, "four" to 3,
        "5" to 4, "paanch" to 4, "panch" to 4, "paanchva" to 4, "fifth" to 4, "five" to 4
    )
    private val PICK_FILLER = setOf("wala", "wali", "wale", "gaana", "gana", "song", "number", "no", "select", "choose", "chuno", "chalao", "play", "karo", "kro", "the", "option", "ye", "yeh", "wo", "woh", "nova")

    /** Spoken pick: a number word ("2", "doosra") or words from exactly one label. null = not a clear pick. */
    fun matchSpoken(text: String, labels: List<String>): Int? {
        val w = normalize(text).split(" ").filter { it.isNotEmpty() && it !in PICK_FILLER }
        if (w.isEmpty()) return null
        if (w.size == 1) {
            val n = NUM[w[0]]
            if (n != null) return if (n < labels.size) n else null
        }
        val hits = labels.indices.filter { i ->
            val blob = " " + normalize(labels[i]) + " "
            w.all { blob.contains(" $it ") || (it.length >= 3 && blob.contains(it)) }
        }
        return if (hits.size == 1) hits[0] else null
    }

    private val APPS = listOf(
        App("spotify", "Spotify", listOf("spotify", "spotifi", "spotifai"), listOf("com.spotify.music", "com.spotify.lite")),
        App("ytmusic", "YouTube Music", listOf("youtube music", "yt music", "you tube music"), listOf("com.google.android.apps.youtube.music")),
        App("saavn", "JioSaavn", listOf("jiosaavn", "jio saavn", "saavn", "savan"), listOf("com.jio.media.jiobeats")),
        App("gaana", "Gaana", listOf("gaana"), listOf("com.gaana")),
        App("wynk", "Wynk Music", listOf("wynk"), listOf("com.bsbportal.music")),
        App("amazonmusic", "Amazon Music", listOf("amazon music"), listOf("com.amazon.mp3")),
        App("resso", "Resso", listOf("resso"), listOf("com.moonvideo.android.resso")),
        App("hungama", "Hungama Music", listOf("hungama"), listOf("com.hungama.myplay.activity"))
    )

    private val PLAY = setOf("play", "chalao", "chala", "chalado", "chalade", "bajao", "baja", "bajado", "lagao", "laga", "lagado", "sunao", "suna", "sunna", "sunana", "search")
    private val FILLER = setOf(
        "nova", "open", "kholo", "khol", "kholna", "kholo", "launch", "start", "shuru", "karo", "kro", "kar", "karna", "karde", "kardo", "kardena", "do", "de", "dena",
        "aur", "or", "phir", "fir", "and", "then", "usme", "usse", "uspe", "usme", "isme", "ke", "baad", "me", "mein", "main", "par", "pe", "per", "on", "in", "se", "from",
        "song", "songs", "gana", "gaana", "gaane", "music", "track", "ko", "ka", "ki", "ye", "wo", "woh", "wala", "wali", "wale", "please", "pls", "zara", "ek", "app",
        "mujhe", "mere", "liye", "bhi", "na", "hai", "h", "the", "a", "to"
    )

    fun parse(input: String): Request? {
        var n = Logic.stripWake(Logic.norm(input))
        if (n.length < 8) return null
        val padded = " $n "
        // longest app word wins ("youtube music" before "youtube")
        var hit: App? = null
        var hitWord = ""
        for (a in APPS) for (w in a.words) if (padded.contains(" $w ") && w.length > hitWord.length) { hit = a; hitWord = w }
        val app = hit ?: return null
        val words = n.split(" ").filter { it.isNotEmpty() }
        if (words.none { it in PLAY }) return null
        val appParts = hitWord.split(" ").toSet()
        val rest = words.filter { it !in PLAY && it !in FILLER && it !in appParts }
        val query = rest.joinToString(" ").trim()
        if (query.isEmpty()) return null   // "spotify kholo" / "spotify play karo" -> normal open or resume paths
        return Request(app.key, app.label, app.pkgs, query)
    }
}
