package com.nova.assistant

/**
 * v7.63 PART 14B-1 - pure rules (no Android, no network; JUnit-able) for the sentence "nova ye kon si movie hai" while something plays on the screen.
 * This hissa ONLY understands the sentence. The live screen capture (14B-2), the movie guess (14B-3), the system-wide laser overlay (14B-4) and the result card (14B-5) come later.
 *
 * Decisions made HERE:
 *  - MOVIE = a title word (movie / film / series / serial / anime ...) + a "which / name" word (kon si, kaun sa, naam, which, what, name ...).
 *  - It is about the SCREEN, so a sentence that talks about a shared file ("share ki hui", "gallery") is NOT matched (the 13D analysis route owns it).
 *  - A question about PEOPLE (actor, hero, cast, "kaun hai") is NOT matched: NOVA never names a person from a face (Part 13A rule). Such a sentence goes on to the normal chain.
 *  - Recommendation / play / download sentences ("konsi movie dekhu", "movie chalao", "best movie") are NOT matched.
 *  - This route must run BEFORE AnalysisVoiceRules.parse, because "ye reel kon si movie hai" also has a media word + "ye" + "kon" (it would otherwise analyse a shared file).
 */
object MovieScanRules {
    enum class Ask { NONE, MOVIE }

    const val MAX_WORDS = 14

    private val TITLE = setOf(
        "movie", "movies", "film", "films", "filmy", "flim", "muvi", "mooviee", "series", "serial", "anime", "cartoon",
        "मूवी", "फिल्म", "फ़िल्म", "सीरियल", "सीरीज"
    )
    private val WHICH = setOf(
        "kon", "kaun", "koun", "konsi", "kaunsi", "konsa", "kaunsa", "konse", "kaunse", "kis", "kiski", "kiska", "kisse", "naam", "nam", "name", "which", "what", "whats", "pata", "pehchano", "guess",
        "कौन", "कौनसी", "कौनसा", "कोनसी", "कोनसा", "नाम", "किस", "किसकी", "किसका"
    )
    private val PEOPLE = setOf(
        "actor", "actress", "hero", "heroine", "hiroin", "cast", "singer", "star", "ladka", "ladki", "aadmi", "admi", "banda", "aurat", "insaan", "person", "who", "kalakar",
        "एक्टर", "हीरो", "हीरोइन", "आदमी", "लड़का", "लड़की"
    )
    private val SHARED = setOf("share", "shared", "shayar", "bheji", "bheja", "bhejo", "gallery", "upload", "uploaded", "download", "downloaded", "file", "attach", "attached")
    private val NOT_ASK = setOf(
        "chalao", "chala", "play", "lagao", "laga", "download", "dekhu", "dekhun", "dekhe", "dekhna", "dekhni", "dekhte", "watch", "suggest", "recommend",
        "achi", "achhi", "acchi", "best", "top", "naya", "nayi", "new", "list", "banao", "bana", "likho", "review", "rating", "ratings", "release", "kab"
    )
    private val SCREEN = setOf("screen", "skrin", "display")
    /** v7.114: "btao / batao movie name kya hai": a tell-me word counts as a question word, but only together with a "this one / on screen" word (a bare "movie batao" is a recommendation). */
    private val TELL = setOf("btao", "batao", "bata", "bta", "bataiye", "bataye", "btana", "batana", "bolo", "boliye", "pata", "pehchan", "pehchano")
    private val DEICTIC = setOf("ye", "yeh", "is", "iss", "isse", "iska", "iski", "iske", "yahan", "yaha", "abhi", "reel", "reels", "video", "clip", "screen", "chal", "wali", "wala", "jo", "isme", "ismein")
    /** "dekhu / dekhna" = "which movie should I WATCH" (recommendation). "dekho / dekh / dekhiye" next to "ye / is / reel" = "LOOK at this" (the scan). */
    private val WATCH_FUTURE = setOf("dekhu", "dekhun", "dekhe", "dekhna", "dekhni", "dekhne", "dekhte", "dekhenge", "dekhege", "dekhoon", "dekhta", "dekhti", "watch")

    private fun words(text: String?): List<String> =
        (text ?: "").lowercase().replace(Regex("[^\\p{L}\\p{N}\\p{M} ]"), " ").split(' ').filter { it.isNotEmpty() }

    /** What the sentence asks for. NONE for everything else (the normal chain then handles it). */
    fun parse(text: String?): Ask {
        val w = words(text)
        if (w.isEmpty() || w.size > MAX_WORDS) return Ask.NONE
        val s = w.toSet()
        if (s.none { it in TITLE }) return Ask.NONE
        val deictic = s.any { it in DEICTIC }
        if (s.none { it in WHICH } && !(deictic && s.any { it in TELL })) return Ask.NONE
        if (s.any { it in PEOPLE }) return Ask.NONE
        if (s.any { it in SHARED }) return Ask.NONE
        if (w.any { it in NOT_ASK || it in WATCH_FUTURE }) return Ask.NONE
        val isQuestion = s.any { it == "kya" || it == "kyaa" || it == "hai" || it == "h" || it == "he" || it == "hain" || it == "ha" }
        if (!deictic && !isQuestion && w.any { it.startsWith("dekh") }) return Ask.NONE   // v7.114: "dekho, ye movie ka naam batao / dekho btao movie name kya h" is a scan; "konsi movie dekho" is not
        // "kaun hai" with a title word but no which-title phrase ("movie me kaun hai") is a people question
        val joined = w.joinToString(" ")
        if (joined.contains("kaun hai") || joined.contains("kon hai") || joined.contains("koun hai")) return Ask.NONE
        return Ask.MOVIE
    }

    /** True when the sentence itself names the screen ("screen par jo chal raha hai wo kon si movie hai"); nothing else changes, kept for the later hissas / tests. */
    fun mentionsScreen(text: String?): Boolean = words(text).any { it in SCREEN }

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    /**
     * 14B-1 honest line: the sentence is understood, the screen scan is not built yet. It never names a movie and never says a scan started.
     * 14B-2..5 replace this line by the real scan (the parse above stays).
     */
    fun notYetText(en: Boolean): String = t(en,
        "Samajh gaya: tum screen par chal rahi movie ka naam poochh rahe ho. Live screen scan abhi ban raha hai (Part 14B), isliye abhi naam nahi bata sakta.",
        "Understood: you are asking the name of the movie playing on the screen. The live screen scan is still being built (Part 14B), so I cannot name it yet.")
}
