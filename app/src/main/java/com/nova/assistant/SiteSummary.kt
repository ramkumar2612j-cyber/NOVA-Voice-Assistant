package com.nova.assistant

/** v7.3 "understand first": the one line NOVA says before a long website job (what was asked: subject, style, photo count, new or edit). Pure text rules. */
object SiteSummary {
    private val STYLES = linkedMapOf(
        "professional" to "professional", "modern" to "modern", "minimal" to "minimal", "luxury" to "luxury", "premium" to "premium",
        "dark" to "dark", "colorful" to "colorful", "colourful" to "colorful", "simple" to "simple", "creative" to "creative",
        "corporate" to "corporate", "playful" to "playful", "elegant" to "elegant", "bold" to "bold"
    )
    private val PHOTO = Regex("(\\d{1,2})\\s*(photos?|images?|pics?|pictures?|tasveer)", RegexOption.IGNORE_CASE)

    fun photoCount(raw: String): Int? = PHOTO.find(raw)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..30 }

    fun style(raw: String): String? {
        val l = raw.lowercase()
        return STYLES.entries.firstOrNull { Regex("\\b" + it.key + "\\b").containsMatchIn(l) }?.value
    }

    fun samjha(subject: String, raw: String, followUp: Boolean, en: Boolean): String {
        val parts = ArrayList<String>()
        parts.add(subject.trim())
        style(raw)?.let { parts.add(if (en) "style $it" else "style $it") }
        photoCount(raw)?.let { parts.add(if (en) "$it photos" else "$it photo") }
        parts.add(if (followUp) (if (en) "edit of the last website" else "pichli website mein badlaav") else (if (en) "new website" else "nayi website"))
        return "Samjha: " + parts.joinToString(", ") + "."
    }
}
