package com.nova.assistant

/**
 * v6.90: a text-only chat answer must never CLAIM that NOVA is building / adding / changing something. The chat AIs have no tools
 * in that path, so "website mein 3D animation add kar raha hoon" is a false claim. This guard replaces such a claim with an honest line.
 * Pure Kotlin, unit tested. Only used when no tool ran for the turn.
 */
object ChatHonesty {
    // Hinglish progressive / past: "add kar raha hoon", "bana diya", "update kar rahi hu"
    private val HI_PROGRESS = Regex("\\b(kar|bana|add|badal|update|create|likh|set|jod|design|build|change|improve|sudhar)\\s*(raha|rahi|rha|rhi|rahe)\\s*(hoon|hu|hun|hoo|hain|hai)\\b")
    private val HI_DONE = Regex("\\b(kar|bana|add|badal|update|create|likh|set|jod|design|build|change|improve|sudhar)\\s*(diya|di|dia|chuka|chuki|liya|lia)\\b")
    private val EN_CLAIM = Regex("\\b(i am|i'm|i will now|i'll now|i have|i've|i just|i already)\\s+(now\\s+)?(adding|building|creating|updating|making|changing|designing|implementing|improving|added|built|created|updated|made|changed|designed|implemented|improved)\\b")
    private val OBJECTS = setOf(
        "website", "site", "webpage", "page", "app", "design", "animation", "animations", "3d", "dark", "typography", "file", "code",
        "feature", "features", "logo", "layout", "theme", "api", "setup", "ui", "button", "menu", "section"
    )

    fun claimsUnbackedAction(reply: String): Boolean {
        val t = reply.lowercase()
        if (!(HI_PROGRESS.containsMatchIn(t) || HI_DONE.containsMatchIn(t) || EN_CLAIM.containsMatchIn(t))) return false
        val words = t.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        return words.any { it in OBJECTS }
    }

    fun honestLine(en: Boolean): String = if (en)
        "I have not done that: in chat I can only answer in text. To build a website say \"nova build a website for ...\"; to improve the one I built say \"make it more professional\". I can also write the steps or code right here."
    else
        "Maine ye kaam abhi nahi kiya: chat mein main sirf text se jawab de sakta hoon. Website banane ke liye bolo \"nova ek ... ki website banao\"; banayi hui website sudharne ke liye bolo \"isko professional banao\". Chaho to steps ya code main yahin likh deta hoon."

    /** [toolRan] = a real tool / builder already did something this turn: then the reply is about a real action and stays as it is. */
    fun guard(reply: String, en: Boolean, toolRan: Boolean = false): String =
        if (!toolRan && claimsUnbackedAction(reply)) honestLine(en) else reply
}
