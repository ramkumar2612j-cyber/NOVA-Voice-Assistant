package com.nova.assistant

/**
 * v7.81 PART 4 - the 10-SECOND budget of "nova dekho, kya chal raha hai" (pure Kotlin, no Android classes, JUnit-able).
 *
 * Owner (19 Oct 2026): say "nova dekho, batao kya chal raha hai" from anywhere -> NOVA reads the screen / the reel by itself and answers
 * within 5 to 10 seconds, and the laser scan must be REAL (its stage, percent and clock come from the work that is really running).
 *
 * What this file decides: how many frames, how far apart, how long each network call may wait, when a web search is still allowed,
 * which spoken sentences start the scan, and the clock text the laser overlay shows. It never reads, sends or stores anything.
 * Safety is unchanged: protected screens are never captured or sent (ScreenSession.isProtected / NovaAccessibilityService.movieProtected).
 * A budget that runs out gives a shorter honest answer ("not verified"), never an invented one.
 */
object QuickScan {
    const val TOTAL_MS = 10_000L              // the promise: answer in about this long
    const val FRAMES = 3                      // reel frames (was 4)
    const val GAP_MS = 700L                   // between two frames (was 1500) - enough for the scene to move
    const val VISION_READ_MS = 7_000L         // one vision call may wait this long (was 30 s) before the next model / the local reading
    const val VISION_DEADLINE_MS = 8_000L
    const val TEXT_READ_MS = 7_000L           // the first plain screen reading
    const val TEXT_DEADLINE_MS = 9_000L
    const val IDENT_READ_MS = 10_000L         // "ye kaun si movie hai" uses Google Search inside the call, so it may wait a little longer
    const val IDENT_DEADLINE_MS = 12_000L
    const val SEARCH_START_MS = 5_500L        // no NEW web search after this much of the budget is gone
    const val SEARCH_READ_MS = 3_500L
    const val SEARCH_DEADLINE_MS = 4_500L
    const val MAX_QUERIES = 1                 // one short title query is enough inside the budget

    fun elapsed(startMs: Long, nowMs: Long): Long = (nowMs - startMs).coerceAtLeast(0L)

    /** True while a new web search may still start. */
    fun searchAllowed(elapsedMs: Long): Boolean = elapsedMs in 0 until SEARCH_START_MS

    fun overBudget(elapsedMs: Long): Boolean = elapsedMs > TOTAL_MS

    /** The clock the laser overlay shows: real elapsed seconds / the budget, e.g. "3.2 s / 10 s". */
    fun clock(elapsedMs: Long, en: Boolean): String {
        val tenths = (elapsedMs.coerceAtLeast(0L) / 100L)
        val s = (tenths / 10L).toString() + "." + (tenths % 10L).toString()
        return s + " s / " + (TOTAL_MS / 1000L) + " s"
    }

    private fun tokens(text: String?): List<String> =
        (text ?: "").lowercase().replace(Regex("[^a-z0-9]"), " ").split(' ').filter { it.isNotEmpty() && it != "nova" }

    private val LOOK = setOf("dekho", "dekh", "dekhna", "dekhiye", "dekhlo", "look", "see")
    private val GOING = setOf("raha", "rahi", "rha", "rhi", "ra", "ri", "rahe", "rhe", "going")
    private val ASK = setOf("bata", "batao", "btao", "bataiye", "batana", "tell")
    private val POINT = setOf("ye", "yeh", "is", "isme", "yahan", "yaha", "screen", "ispe", "isko", "this", "here")
    /** Other features own these words: "battery dekho batao", "gaana kya chal raha hai", "gallery dekho" are NOT a screen scan. */
    private val OTHER = setOf(
        "battery", "charge", "charging", "wifi", "bluetooth", "torch", "flashlight", "volume", "brightness", "time", "date", "weather", "mausam",
        "news", "location", "map", "music", "song", "gaana", "gana", "gaane", "play", "gallery", "photo", "file", "files", "download", "share",
        "bhejo", "send", "delete", "hatao", "call", "message", "msg", "sms", "kholo", "khol", "open", "alarm", "timer", "contact", "contacts",
        "record", "recording", "shot", "screenshot", "calendar", "reminder", "yaad", "kaun", "kon", "naam"
    )

    /**
     * "nova dekho batao kya chal raha hai", "dekho kya chal ra h", "ye dekho batao": the owner wants the screen / reel in front of him read, without the word "screen".
     * Narrow on purpose (2..10 words, a look word, and either "kya chal raha" or a pointing word + a tell word) so no other command is taken over.
     */
    fun wantsLook(text: String?): Boolean {
        val t = tokens(text)
        if (t.size < 2 || t.size > 10) return false
        if (t.any { it in OTHER }) return false
        val going = t.contains("kya") && (t.contains("chal") || t.contains("ho") || t.any { it in SEEN }) && t.any { it in GOING }
        if (t.none { it in LOOK }) return going && t.any { it in POINT }   // v7.86: "ye kya chal raha hai" (no look word, but it points at the screen)
        val pointAsk = t.any { it in POINT } && t.any { it in ASK }
        return going || pointAsk
    }

    private val SEEN = setOf("dikh", "dikha", "dikhta", "dikhai", "dikhe")
    private val SCAN = setOf("scan", "scanning", "skan", "laser", "lazer")
    /** "qr scan", "virus scan", "scan band karo" are other things, not the screen scan. */
    private val SCAN_NOT = setOf(
        "qr", "barcode", "virus", "malware", "storage", "wifi", "bluetooth", "network", "document", "doc", "pdf", "photo", "file", "files",
        "app", "apps", "memory", "ram", "cache", "fingerprint", "face", "band", "bandh", "off", "stop", "roko", "ruko", "rok", "cancel", "hatao"
    )

    /** v7.86: "nova scan karo", "screen scan", "laser scan chalu karo", "lazer scan": the owner names the scan itself. */
    fun wantsScan(text: String?): Boolean {
        val t = tokens(text)
        if (t.isEmpty() || t.size > 7) return false
        if (t.none { it in SCAN }) return false
        return t.none { it in SCAN_NOT }
    }

    // ------------------------------------------------------------------ v7.86: no listening-by-itself after a video scan
    const val QUIET_FOLLOW_MS = 90_000L   // a reel / movie plays sound: the hands-free follow-up listen stays OFF this long after a video scan (wake word "nova" still works)

    /** Same rule as before (screen session open, or conversation mode with follow-ups left), except during the quiet time after a video scan. */
    fun followAllowed(screenOpen: Boolean, convo: Boolean, followCount: Int, maxFollow: Int, quietUntilMs: Long, nowMs: Long): Boolean {
        if (nowMs < quietUntilMs) return false
        return screenOpen || (convo && followCount < maxFollow)
    }

    /** A follow-up listen (no wake word) that heard only noise / nothing: end quietly. Never "samajh nahi aaya, dobara bolo". */
    fun quietJunkFollow(isFollowTurn: Boolean, said: String?): Boolean = isFollowTurn && Logic.isJunk(said ?: "")

    val PHRASES_HI = listOf("nova ye kya chal raha hai", "nova screen dekho", "nova scan karo", "nova dekho batao kya chal raha hai", "nova ye kaun si movie hai")
    fun phrasesText(en: Boolean): String =
        (if (en) "Say one of these: " else "Ye bolo: ") + PHRASES_HI.joinToString(" / ") { "\"" + it + "\"" }
}
