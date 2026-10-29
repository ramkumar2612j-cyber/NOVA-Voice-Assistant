package com.nova.assistant

/**
 * v6.92: "<any installed app> me <song / cheez> search ya play karo" and "ye share karo <app / naam>".
 * Pure text rules (no Android classes). NovaService resolves the app name against the apps really installed
 * and does the work; when no installed app matches, the sentence simply continues through normal routing.
 */
object AppSearchRules {
    enum class Kind { SEARCH, SHARE }

    /** [appPhrase] = spoken app name (SEARCH) or share target such as an app or contact name (SHARE, may be empty). */
    data class Request(val kind: Kind, val appPhrase: String, val query: String, val play: Boolean)

    private val SEARCH_VERBS = setOf("search", "khoj", "khojo", "dhundo", "dhoondo", "dhundna", "find")
    private val PLAY_VERBS = setOf("play", "chalao", "chala", "chalado", "chalade", "bajao", "baja", "bajado", "lagao", "laga", "lagado", "sunao", "suna", "sunna")
    private val CONNECT = setOf("me", "mein", "main", "par", "pe", "per", "on", "in", "andar")
    private val JOIN = setOf("aur", "and", "then", "phir", "fir")
    private val LEFT_FILLER = setOf(
        "nova", "open", "kholo", "khol", "kholna", "launch", "start", "shuru", "karo", "kro", "kar", "karna", "karde", "kardo",
        "app", "ko", "ka", "ki", "ye", "wo", "woh", "please", "pls", "zara", "ek", "mujhe", "mere", "liye", "the", "a"
    )
    private val RIGHT_FILLER = setOf(
        "nova", "karo", "kro", "kar", "karna", "karde", "kardo", "do", "de", "dena", "usme", "isme", "uspe", "ispe", "ke", "ko", "ka", "ki",
        "song", "songs", "gana", "gaana", "gaane", "music", "video", "videos", "track", "wala", "wali", "wale", "please", "pls", "zara", "ek",
        "mujhe", "mere", "liye", "bhi", "na", "hai", "h", "the", "a", "to", "aur", "or", "and", "then", "phir", "fir", "open", "kholo", "khol", "app"
    )
    private val SHARE_FILLER = setOf(
        "nova", "share", "karo", "kro", "kar", "karna", "karde", "kardo", "do", "de", "dena", "ko", "ka", "ki", "ye", "wo", "woh", "isko", "usko",
        "isse", "ise", "is", "us", "pe", "par", "per", "me", "mein", "main", "on", "to", "with", "via", "se", "ke", "through", "please", "pls",
        "zara", "ek", "mujhe", "mere", "liye", "bhi", "the", "a", "song", "gana", "gaana", "video", "post", "link", "photo", "this", "it", "aur", "and", "then", "phir", "app"
    )
    // Sentences that contain "share" but mean something else: never hijack them.
    private val NOT_SHARE = setOf("screen", "location", "hotspot", "internet", "wifi", "data", "stock", "market")

    fun parse(input: String): Request? {
        val n = Logic.stripWake(Logic.norm(input))
        if (n.length < 6) return null
        val words = n.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        if (OpenWebRules.parseDownload(input) != null) return null   // v7.133: "play store pe whatsapp download karo" is the Play Store download route, not an in-app search
        return parseShare(words) ?: parseSearch(words)
    }

    private fun parseShare(words: List<String>): Request? {
        if (!words.contains("share") && !words.contains("shere")) return null
        if (words.any { it in NOT_SHARE }) return null
        val rest = words.filter { it != "shere" && it !in SHARE_FILLER }
        if (rest.size > 3) return null
        return Request(Kind.SHARE, rest.joinToString(" "), "", false)
    }

    private fun parseSearch(words: List<String>): Request? {
        val isPlay = words.any { it in PLAY_VERBS }
        val isSearch = words.any { it in SEARCH_VERBS }
        if (!isPlay && !isSearch) return null
        // the app name ends at the first "me / par / on" or "aur / then" word
        val split = words.indexOfFirst { it in CONNECT || it in JOIN }
        if (split < 1) return null
        val left = words.subList(0, split).filter { it !in LEFT_FILLER && it !in PLAY_VERBS && it !in SEARCH_VERBS }
        if (left.isEmpty() || left.size > 3) return null
        val right = words.subList(split + 1, words.size).filter { it !in RIGHT_FILLER && it !in PLAY_VERBS && it !in SEARCH_VERBS }
        val query = right.joinToString(" ").trim()
        if (query.isEmpty()) return null
        return Request(Kind.SEARCH, left.joinToString(" "), query, isPlay)
    }
}
