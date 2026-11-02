package com.nova.assistant

/**
 * ADD-ONLY (wip15): a cleaner in front of [MusicPlayRules.parse]. The old rules are not touched.
 * Problem it fixes: "spotify open kr ke song play kro, song ka naam tum hi ho bta diya" was searched as the
 * words "kr naam tum hi ho bta diya" (or understood as nothing), because "kr", "naam", "bta", "diya", "kplay" were
 * not known filler. Here those words are removed and the cleaned sentence goes through the same old rules.
 * Pure text, no Android, no AI call.
 */
object MusicPlayRulesPlus {
    /** Spoken / typed spellings of "play" that the old list does not know. */
    private val PLAY_VARIANTS = setOf("kplay", "plae", "pley", "plya", "bajaao", "chalaao", "chalaye", "chalaiye", "chalana", "bajana", "bajaye")

    /** "song ka NAAM x": the marker words themselves are never part of the song. */
    private val NAME_MARKERS = setOf("naam", "nam", "name", "named", "title", "called")

    /** Words that only talk about the request, never part of a song name. Words like "hi", "ho", "tum" are NOT here (song names). */
    private val NOISE = setOf(
        "kr", "krke", "karke", "krdo", "krde", "kardo", "karna", "krna", "krenge", "karenge",
        "bta", "bata", "batao", "btao", "btana", "bataya", "btaya", "diya", "diye", "dia", "dya",
        "hai", "h", "hu", "hoon", "bhi", "boss", "sir"
    )

    /** Same as the old parse when nothing needed cleaning; otherwise the old parse of the cleaned sentence. */
    fun parseBest(input: String): MusicPlayRules.Request? {
        val n = Logic.stripWake(Logic.norm(input))
        if (n.length < 8) return MusicPlayRules.parse(input)
        val words = n.split(" ").filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var changed = false
        for (w in words) {
            when {
                w in NAME_MARKERS || w in NOISE -> changed = true
                w in PLAY_VARIANTS -> { out.add("play"); changed = true }
                else -> out.add(w)
            }
        }
        if (!changed) return MusicPlayRules.parse(input)
        // something was cleaned: the cleaned sentence decides (no song left = null = normal open/resume path, never a search for junk)
        return MusicPlayRules.parse(out.joinToString(" "))
    }
}
