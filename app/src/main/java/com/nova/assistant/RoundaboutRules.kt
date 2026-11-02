package com.nova.assistant

/**
 * v7.50 PART 10 - ROUNDABOUT COMMANDS. Pure Kotlin, no network, no AI, no Android classes (JUnit-testable).
 *
 * Three jobs, all deterministic and SAFE:
 *  1. [affirmative]: short Hinglish "yes" phrases that the old strict parser dropped ("ha kr do", "haan karo", "theek hai", "bilkul",
 *     "kar do"). Known bug before this part: "ha kr do" was read as unclear and the confirmation was CANCELLED.
 *     A NO word always wins (Logic.parseAnswer checks it first). Anything with another word ("yes please", "sabko bhej do abhi") stays unclear.
 *  2. [repeat] / [undo]: short follow-ups ("usi ko dobara", "wahi kar do", "ulta kar do") for harmless, reversible commands only.
 *     ContextMemory still refuses call / sms / whatsapp / delete / pay / publish / record by itself.
 *  3. [needsWho]: a risky order that points at someone only by hint ("wo jo kal wale ko bhej do", "usi ko call karo", "call him") is NEVER guessed:
 *     NOVA asks who (name or number). [hint]: a few indirect cues ("andhera hai") mapped to harmless reversible actions.
 *     Persona (Part 9) is not involved: this file never changes how NOVA speaks, only what it understands.
 */
object RoundaboutRules {
    private val YES = setOf("yes", "yeah", "yep", "yup", "okay", "ok", "sure", "haan", "han", "ha", "ji", "haa", "hanji", "haanji")
    /** Words that may sit beside a yes word but never approve anything on their own. NOT included: please, and any verb of sending / calling. */
    private val FILLER = setOf("kar", "kr", "karo", "do", "de", "dena", "dijiye", "dijiyega", "hai", "ji", "bilkul", "theek", "thik", "chalo", "zaroor", "zarur", "abhi")
    private val STRONG = setOf("bilkul", "theek", "thik", "chalo", "zaroor", "zarur", "kardo", "krdo", "dijiye", "dijiyega")

    /** true = clear yes, null = not clear. Never returns false: refusals are Logic.parseAnswer's job (a NO word wins there first). */
    fun affirmative(words: List<String>): Boolean? {
        val w = words.map { it.trim().lowercase() }.filter { it.isNotBlank() && it != "[unk]" }
        if (w.isEmpty() || w.size > 4) return null
        if (w.any { it !in YES && it !in FILLER }) return null
        val hasFiller = w.any { it in FILLER && it !in YES }
        if (!hasFiller) return null                       // plain yes words are Logic's own rule (keeps "yes yes yes yes" unclear)
        val joined = w.joinToString(" ")
        val pairDo = Regex("\\b(kar|kr|karo) do\\b").containsMatchIn(joined)   // "kar do" / "kr do"; a lone "karo" is NOT enough
        val strong = w.any { it in STRONG } || pairDo
        val yes = w.any { it in YES }
        return if (yes || strong) true else null
    }

    // ---------------------------------------------------------------- follow-ups for harmless commands

    private val REPEAT = listOf(
        "usi ko dobara", "usko dobara", "isko dobara", "ise dobara", "wahi kar do", "wahi karo", "wohi kar do", "wahi wala kar do",
        "dobara karo", "dobara kar do", "phir se karo", "phir se kar do", "ek baar aur", "ek aur baar", "once more", "one more time", "do it again", "again please"
    )
    private val UNDO = listOf("ulta kar do", "ulta karo", "wapas kar do", "pehle jaisa kar do", "pehle jaisa karo", "undo", "revert that", "undo that", "go back to before")

    /** v7.51: only polite filler may sit beside the phrase; any other word (e.g. "usi ko dobara call karo kal") is a NEW command, not a repeat. */
    private val POLITE = setOf("please", "plz", "na", "yaar", "ji", "nova", "zara", "bas")
    fun repeat(t: String): Boolean = REPEAT.any { p ->
        t == p || (t.startsWith("$p ") && t.removePrefix("$p ").split(" ").all { it in POLITE }) ||
            (t.endsWith(" $p") && t.removeSuffix(" $p").split(" ").all { it in POLITE })
    }
    fun undo(t: String): Boolean = UNDO.any { t == it }

    /** [lastKind]/[lastArg] come from ContextMemory (already filtered: never a risky kind). Returns the reverse command or null. */
    fun reverseOf(lastKind: String, lastArg: String): Logic.Cmd? = when (lastKind) {
        "torch_on" -> Logic.Cmd("torch_off")
        "torch_off" -> Logic.Cmd("torch_on")
        "volume" -> when (lastArg) { "up" -> Logic.Cmd("volume", "down"); "down" -> Logic.Cmd("volume", "up"); "mute" -> Logic.Cmd("volume", "up"); else -> null }
        "brightness_step" -> null
        "media" -> when (lastArg) { "pause" -> Logic.Cmd("media", "play"); "play" -> Logic.Cmd("media", "pause"); "next" -> Logic.Cmd("media", "previous"); "previous" -> Logic.Cmd("media", "next"); else -> null }
        "quiet_on" -> Logic.Cmd("quiet_off")
        "quiet_off" -> Logic.Cmd("quiet_on")
        "silent_on" -> Logic.Cmd("silent_off")
        "silent_off" -> Logic.Cmd("silent_on")
        "rotation" -> when (lastArg) { "auto_on" -> Logic.Cmd("rotation", "auto_off"); "auto_off" -> Logic.Cmd("rotation", "auto_on"); else -> null }
        else -> null
    }

    // ---------------------------------------------------------------- indirect cues -> harmless reversible action

    private val HINTS: List<Pair<List<String>, Logic.Cmd>> = listOf(
        listOf("andhera hai", "bahut andhera hai", "andhera ho gaya", "kuch dikh nahi raha andhera") to Logic.Cmd("torch_on"),
        listOf("kuch sunai nahi de raha", "awaaz nahi aa rahi", "awaaz dheemi hai", "awaaz bahut kam hai") to Logic.Cmd("volume", "up"),
        listOf("awaaz bahut tez hai", "kaan phat rahe hain", "awaaz bahut zyada hai") to Logic.Cmd("volume", "down"),
        listOf("aankhen chub rahi hain", "screen bahut tez hai", "roshni bahut tez hai", "screen ki roshni bahut zyada hai") to Logic.Cmd("brightness_step", "", -10),
        listOf("screen bahut dim hai", "screen dikh nahi rahi", "screen ki roshni kam hai") to Logic.Cmd("brightness_step", "", 10)
    )

    /** Only a SHORT, whole-sentence cue (<= 8 words) counts, so ordinary chat that merely contains these words never moves a setting. */
    fun hint(t: String): Logic.Cmd? {
        if (t.isBlank() || t.split(" ").size > 8) return null
        for ((phrases, cmd) in HINTS) if (phrases.any { t == it || t == "$it yaar" || t == "yaar $it" || t == "$it nova" || t == "nova $it" }) return cmd
        return null
    }

    // ---------------------------------------------------------------- risky order aimed at someone by hint only

    private val REFS = listOf(
        "wo wale", "wo wali", "wo jo", "wohi wale", "wahi wale", "kal wale", "kal wali", "pichle", "pichhle", "last wale", "last wali",
        "usi ko", "usi se", "usko", "unko", "uss ko", "us bande", "us ladke", "us ladki", "jisne", "jisko", "that person", "that guy", "same person", "the one from"
    )
    /** Whole words only ("display" must not count as "pay"). */
    private val RISKY_WORDS = setOf(
        "call", "calls", "msg", "message", "sms", "whatsapp", "bhej", "bhejo", "bhejna", "send", "forward", "delete", "hata", "hatao", "hatana",
        "pay", "payment", "transfer", "paisa", "paise", "rupay", "rupaye", "udhaar", "block", "remove", "text"
    )

    /** true when the order is risky AND names its target only by a hint. The caller must ask who, never guess. */
    fun needsWho(t: String): Boolean {
        if (t.isBlank()) return false
        val tokens = t.split(" ").filter { it.isNotBlank() }
        if ("reply" in tokens) return false                                  // driving / message-mode replies have their own safe flow
        if (Regex("\\d{6,}").containsMatchIn(t)) return false                // a real number was spoken
        val words = " $t "
        val ref = REFS.any { words.contains(" $it") } ||
            Regex("\\b(call|message|msg|text|send|pay|delete) (him|her|them)\\b").containsMatchIn(t)
        if (!ref) return false
        return tokens.any { it in RISKY_WORDS } || t.contains("phone laga")
    }

    fun whoQuestion(en: Boolean): String =
        if (en) "Who exactly? Say the name or number. I never guess who to call, message, pay or delete."
        else "Kisko? Naam ya number bolo. Call, message, payment ya delete mein main andaaze se kaam nahi karta."
}
