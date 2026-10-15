package com.nova.assistant

/**
 * v6.60: "spotify open karo aur legacy song play karo" must really search and play, with no AI call (free AI limits can be used up).
 * Pure text rules; NovaService turns the result into Android's standard play-from-search request for that music app.
 */
object MusicPlayRules {
    data class Request(val appKey: String, val appLabel: String, val packages: List<String>, val query: String)

    private class App(val key: String, val label: String, val words: List<String>, val pkgs: List<String>)

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
