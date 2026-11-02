package com.nova.assistant

/**
 * v7.133 owner order: "reel kholo", "instagram reels kholo", "nova reel dekhne ka man kar raha hai" = open Instagram Reels IN THE APP.
 * Only when the sentence says chrome / browser, it opens in Chrome. Pure Kotlin. Sentences with an action (like / save / comment / share / scroll / next / back),
 * a question, a negation, a stop, or a friend's username are left to ReelRules / the other handlers.
 */
object ReelOpenRules {
    const val URL = "https://www.instagram.com/reels/"
    data class Req(val chrome: Boolean)

    private val REEL = setOf("reel", "reels", "ril", "rils")
    private val OPEN = setOf("kholo", "khol", "kholna", "open", "launch", "chalao", "chala", "chalu", "start", "shuru", "dekho", "dekhna", "dekhne", "dekhni", "dekhu", "dekhte", "dekhunga", "dekhungi", "lagao", "laga", "dikhao", "dikha", "play")
    private val BLOCK = setOf("kya", "kaun", "kon", "konsi", "kaunsi", "like", "save", "comment", "share", "scroll", "next", "agli", "agla", "agle", "pichli", "pichla", "previous", "download", "delete", "hatao",
        "band", "stop", "ruko", "mat", "nahi", "nhi", "record", "banao", "bana", "edit", "post", "follow", "unfollow", "report", "block", "karte", "raho", "dost", "friend", "youtube", "shorts", "short")

    fun parse(raw: String?): Req? {
        if (raw.isNullOrBlank() || raw.length > 160) return null
        if (raw.any { it == '_' || it == '@' }) return null   // a username: ReelRules owns it
        val n = Logic.stripWake(Logic.norm(raw))
        val w = n.split(" ").filter { it.isNotBlank() }
        if (w.isEmpty() || w.size > 14) return null
        if (w.none { it in REEL }) return null
        if (w.any { it in BLOCK }) return null
        val wantsOpen = w.any { it in OPEN } || n.contains("man kar") || n.contains("mann kar") || n.contains("dil kar")
        if (!wantsOpen) return null
        return Req(chrome = w.any { it == "chrome" || it == "browser" })
    }
}
