package com.nova.assistant

/**
 * v7.05 H: which place does the owner want the website on? "nova website vercel par publish karo", "lovable par daalo".
 * Pure Kotlin (no Android classes), Roman text. It only decides; the caller asks before anything leaves the phone.
 * GitHub stays the old SiteIntent.isPublish route; this only adds Vercel and Lovable.
 */
object PublishTarget {
    enum class Target { VERCEL, LOVABLE }

    private val VERCEL = setOf("vercel", "versel", "varcel")
    private val LOVABLE = setOf("lovable", "lavable", "lovabel")
    private val PUB = setOf("publish", "publis", "deploy", "upload", "host", "live", "daalo", "dalo", "daal", "rakho", "chalao", "bhejo", "send")
    private val BUILD = setOf("banao", "bana", "banado", "banade", "banana", "banani", "banane", "bnao", "generate", "create", "build", "make", "design")
    private val BLOCK = setOf("delete", "hatao", "remove", "kholo", "open")

    fun tokens(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) { if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ') }
        val out = ArrayList<String>()
        for (w in sb.toString().split(' ')) if (w.isNotEmpty()) out.add(w)
        return out
    }

    fun parse(text: String): Target? {
        val t = tokens(text)
        if (t.isEmpty() || t.size > 30) return null
        var v = false; var l = false; var pub = false
        for (w in t) {
            if (w in BUILD || w in BLOCK) return null
            if (w in VERCEL) v = true
            if (w in LOVABLE) l = true
            if (w in PUB) pub = true
        }
        if (!pub) return null
        return if (v) Target.VERCEL else if (l) Target.LOVABLE else null
    }
}
