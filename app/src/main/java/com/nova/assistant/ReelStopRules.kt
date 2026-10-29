package com.nova.assistant

/**
 * v7.133 FINAL: "reel dekhna band karo", "reels band karo", "reel scroll band karo", "reel pause karo".
 * Pure Kotlin (JUnit-able). STOP ends a running reel job (scroll / like / comment run). PAUSE asks the phone's media key to pause the video.
 * Sentences that carry another reel action (like / save / comment / share / open / download / a friend) are left to ReelRules / ReelOpenRules.
 */
object ReelStopRules {
    enum class Kind { STOP, PAUSE }

    private val REEL = setOf("reel", "reels", "ril", "rils")
    private val STOPW = setOf("band", "stop", "ruko", "roko", "rok", "bas", "khatam", "off")
    private val PAUSEW = setOf("pause", "thehro", "thahro", "thehar")
    private val OTHER = setOf("like", "save", "comment", "share", "kholo", "khol", "open", "chalao", "chalu", "download", "post", "follow", "unfollow", "friend", "dost",
        "watch", "youtube", "shorts", "short", "kya", "kaun", "kon", "agli", "agla", "pichli", "pichla", "next", "previous", "record", "banao", "edit", "delete", "hatao")

    fun parse(raw: String?): Kind? {
        if (raw.isNullOrBlank() || raw.length > 120) return null
        if (raw.any { it == '_' || it == '@' }) return null   // a username: ReelRules owns it
        val w = Logic.stripWake(Logic.norm(raw)).split(" ").filter { it.isNotBlank() }
        if (w.isEmpty() || w.size > 10) return null
        if (w.none { it in REEL }) return null
        if (w.any { it in OTHER }) return null
        if (w.any { it in PAUSEW }) return Kind.PAUSE
        if (w.any { it in STOPW }) return Kind.STOP
        return null
    }
}
