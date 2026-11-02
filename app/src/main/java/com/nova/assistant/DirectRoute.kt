package com.nova.assistant

/**
 * v7.142 STEP 2 OF 10: the direct road. A few very common, harmless jobs ("youtube par X search karo", "X ka rasta batao",
 * "petrol pump near me dhundo") used to go to the AI agent (plan + screen taps, 2 to 5 s per step). This only PARSES the phrase;
 * NovaService then opens one ordinary link (YouTube search page or Google Maps) in a single step. Pure Kotlin, no Android,
 * unit-testable. Add-only: anything it does not recognise, or any chained job ("... aur pehla video chalao"), returns null and
 * the old agent path runs exactly as before. It never plays, sends, pays or taps anything.
 */
object DirectRoute {
    enum class Kind { YT_SEARCH, MAPS_NAV, MAPS_SHOW }

    class Plan(val kind: Kind, val arg: String)

    private val VERB = "(?:search karo|search kar do|search|dhundo|dhoondo|dhundho|khojo|find karo|find)"
    private val SHOW_VERB = "(?:dhundo|dhoondo|khojo|dikhao|dikha do|batao|bata do|search karo|search|find)"
    private val ON = "(?:(?:par|pe|me|mein|on|in) )?"

    private val YT = listOf(
        Regex("^(?:youtube|yt) $ON(.+?) $VERB$"),
        Regex("^(?:youtube|yt) $ON(?:search|find) (?:karo )?(.+)$"),
        Regex("^(?:search|find) (.+?) (?:on|in|par|pe|me|mein) (?:youtube|yt)$"),
        Regex("^(.+?) (?:youtube|yt) (?:par|pe|me|mein|on|in) $VERB$")
    )
    private val NAV = listOf(
        Regex("^(?:start navigation to|navigation to|navigate to|navigate|directions to|directions for|route to|take me to) (.+)$"),
        Regex("^(?:mujhe )?(.+?) (?:jane ka|jaane ka|tak ka) (?:rasta|raasta|route) (?:batao|bata do|dikhao|dikha do)$"),
        Regex("^(.+?) (?:ka|ki|ke) (?:rasta|raasta|route|direction|directions|navigation) (?:batao|bata do|dikhao|dikha do|chalu karo|start karo|lagao|laga do)$"),
        Regex("^(?:mujhe )?(.+?) (?:le chalo|navigate karo|navigate kar do|navigation chalu karo)$")
    )
    private val SHOW = listOf(
        Regex("^(?:show|find|search) (.+?) (?:on|in) (?:google )?maps?$"),
        Regex("^(?:maps?) $ON(.+?) $SHOW_VERB$"),
        Regex("^(.+?) maps? (?:par|pe|me|mein) $SHOW_VERB$")
    )
    private val NEAR_PREFIX = Regex("^(?:find |search |show )?(?:nearest|nearby) (.+?)(?: near me)?(?: $SHOW_VERB)?$")
    private val NEAR_SUFFIX = Regex("^(?:find |search |show )?(.+?) near me(?: $SHOW_VERB)?$")

    private val NEGATION = Regex("\\b(mat|nahi|nahin|nhi|dont|do not|not|never|bina|cancel|stop|ruko)\\b")
    private val CHAIN = Regex("\\b(aur|and|phir|fir|then|uske baad|uske bad|also)\\b")
    private val WAKE_FILL = Regex("^(?:(?:hey |ok )?nova |please |zara |jara |abhi |now )+")

    /** Lower case, letters/digits/spaces only (same idea as Logic.norm, kept here so this file stays Android-free). */
    fun norm(text: String): String {
        val s = text.lowercase().replace("[unk]", " ").replace("-", " ")
        return s.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim().replace("you tube", "youtube")
    }

    private fun clean(a: String): String? {
        val x = a.trim()
        if (x.isEmpty() || x.length > 80 || x.split(" ").size > 8) return null
        if (CHAIN.containsMatchIn(x)) return null
        if (x in setOf("me", "mujhe", "kuch", "something", "it", "ye", "yeh", "wo", "woh", "isko", "usko", "karo", "kar do", "search", "find", "dhundo", "khojo")) return null
        return x
    }

    /** [raw] should already be romanised (HindiRoman.toRoman) by the caller; [parse] only normalises. */
    fun parse(raw: String): Plan? {
        var s = norm(raw)
        if (s.length < 8 || s.length > 160) return null
        s = WAKE_FILL.replace(s, "").trim()
        if (s.isEmpty() || NEGATION.containsMatchIn(s)) return null
        for (r in YT) { val m = r.matchEntire(s) ?: continue; val a = clean(m.groupValues[1]) ?: return null; return Plan(Kind.YT_SEARCH, a) }
        for (r in NAV) { val m = r.matchEntire(s) ?: continue; val a = clean(m.groupValues[1]) ?: return null; return Plan(Kind.MAPS_NAV, a) }
        for (r in SHOW) { val m = r.matchEntire(s) ?: continue; val a = clean(m.groupValues[1]) ?: return null; return Plan(Kind.MAPS_SHOW, a) }
        NEAR_PREFIX.matchEntire(s)?.let { m -> val a = clean(m.groupValues[1]) ?: return null; return Plan(Kind.MAPS_SHOW, "$a near me") }
        NEAR_SUFFIX.matchEntire(s)?.let { m -> val a = clean(m.groupValues[1]) ?: return null; return Plan(Kind.MAPS_SHOW, "$a near me") }
        return null
    }

    fun youtubeUrl(q: String, enc: (String) -> String) = "https://www.youtube.com/results?search_query=" + enc(q)
    fun mapsNavUrl(p: String, enc: (String) -> String) = "https://www.google.com/maps/dir/?api=1&destination=" + enc(p)
    fun mapsShowUrl(p: String, enc: (String) -> String) = "https://www.google.com/maps/search/?api=1&query=" + enc(p)
}
