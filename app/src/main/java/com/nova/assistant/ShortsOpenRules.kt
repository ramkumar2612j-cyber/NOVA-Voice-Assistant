package com.nova.assistant

/**
 * v7.135 owner order: "shorts kholo", "youtube shorts dekhna hai", "nova shorts chalao" = open YouTube Shorts IN THE YOUTUBE APP.
 * Only when the sentence says chrome / browser, it opens in Chrome. Pure Kotlin (JUnit-able). Sentences with another action (like / subscribe / comment / share / scroll / next / back),
 * a question, a negation, a stop, Instagram / reel words or a username are left to ReelRules / ReelOpenRules / the other handlers.
 */
object ShortsOpenRules {
    const val URL = "https://www.youtube.com/shorts"
    data class Req(val chrome: Boolean)

    private val SHORTS = setOf("shorts", "short", "shorts.")
    private val OPEN = setOf("kholo", "khol", "kholna", "open", "launch", "chalao", "chala", "chalu", "start", "shuru", "dekho", "dekhna", "dekhne", "dekhni", "dekhu", "dekhte", "dekhunga", "dekhungi", "lagao", "laga", "dikhao", "dikha", "play")
    private val BLOCK = setOf("kya", "kaun", "kon", "konsi", "kaunsi", "like", "subscribe", "save", "comment", "share", "scroll", "next", "agli", "agla", "agle", "pichli", "pichla", "previous", "download", "delete", "hatao",
        "band", "stop", "ruko", "mat", "nahi", "nhi", "record", "banao", "bana", "edit", "post", "follow", "unfollow", "report", "block", "karte", "raho", "dost", "friend",
        "reel", "reels", "instagram", "insta", "story", "stories")

    fun parse(raw: String?): Req? {
        if (raw.isNullOrBlank() || raw.length > 160) return null
        if (raw.any { it == '_' || it == '@' }) return null   // a username: ReelRules owns it
        val n = Logic.stripWake(Logic.norm(raw))
        val w = n.split(" ").filter { it.isNotBlank() }
        if (w.isEmpty() || w.size > 14) return null
        if (w.none { it in SHORTS }) return null
        if (w.any { it in BLOCK }) return null
        val wantsOpen = w.any { it in OPEN } || n.contains("man kar") || n.contains("mann kar") || n.contains("dil kar")
        if (!wantsOpen) return null
        // a lone "short" is a normal word ("short me batao", "short video"): it needs youtube / yt in the sentence
        val plural = w.any { it == "shorts" || it == "shorts." }
        val yt = w.any { it == "youtube" || it == "yt" || it == "utube" }
        if (!plural && !yt) return null
        if (w.any { it == "video" || it == "videos" || it == "vedio" } && !plural) return null
        return Req(chrome = w.any { it == "chrome" || it == "browser" })
    }
}
