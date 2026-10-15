package com.nova.assistant

/**
 * v16 WEBSITE BY VOICE: "nova ek restaurant ki website banao" / "make a website for my gym".
 * Pure Kotlin (no Android classes), so it runs in plain JVM unit tests. It only decides (a) is this a request to BUILD a
 * website and (b) what the site is about. It never sends anything anywhere.
 * Input is Roman text (Devanagari is converted by HindiRoman before this is called).
 * tools/test_site_contract.py reads the sets below straight from this file, so keep one set per line.
 */
object SiteIntent {
    class Parsed(val subject: String, val blocked: Boolean, val raw: String = "", val followUp: Boolean = false)

    private val VERBS = setOf("banao", "bana", "banado", "banade", "banana", "banaiye", "banaye", "bnao", "bna", "banwao", "banwa", "banani", "banane", "bnani", "bnane", "banwani", "banwane", "banwana", "generate", "create", "build", "make", "design", "develop")
    private val NOUNS = setOf("website", "websites", "webpage", "webpages", "site", "portfolio", "landing")
    // Words that mean another feature owns the sentence (pictures, opening apps, agent jobs, sharing, deleting).
    private val NOT = setOf("kholo", "khol", "open", "photo", "foto", "image", "picture", "pic", "tasveer", "tasvir", "wallpaper", "chatgpt", "gemini", "gpt", "delete", "hatao", "bhejo", "send", "share", "whatsapp", "instagram", "call", "kaam", "agent", "chrome", "browser")
    private val FILLER = setOf("nova", "ek", "mujhe", "mere", "meri", "mera", "hamare", "ki", "ka", "ke", "ko", "se", "me", "mein", "par", "pe", "ye", "wo", "please", "plz", "pls", "zara", "kripya", "kar", "karo", "kardo", "do", "dena", "de", "dijiye", "chahiye", "chahie", "chaiye", "hai", "ho", "wala", "wali", "wale", "a", "an", "the", "of", "for", "my", "i", "want", "need", "can", "you", "could", "would", "free", "mujhko", "humko", "hume", "liye", "naya", "nayi", "new", "web", "page", "pages", "ek")
    private val PUBVERBS = setOf("publish", "publis", "deploy", "upload", "host")
    private val BLOCK = setOf("nude", "naked", "nsfw", "porn", "porno", "sex", "xxx", "nangi", "nanga", "erotic", "phishing", "hack", "hacking", "scam", "fake")

    const val MAX_RAW = 8000

    /** v7.0: a long, detailed brief ("Build a world-class ... website ... photos ... images ...") is still a build request when the build verb and the website noun
     *  are in its first 40 words. Words like photo/image inside the brief no longer make another feature steal it. */
    private fun parseLong(text: String, t: List<String>): Parsed? {
        if (t.size > 3000) return null
        val head = t.take(40)
        if (head.none { it in VERBS } || head.none { it in NOUNS }) return null
        if (head.take(6).any { it in setOf("kholo", "khol", "open", "whatsapp", "call", "delete", "hatao", "bhejo", "send", "share") }) return null
        val blocked = t.any { it in BLOCK }
        val keep = head.filter { it !in VERBS && it !in NOUNS && it !in FILLER }
        val subject = keep.take(8).joinToString(" ").let { if (it.length < 3) "website" else it }
        return Parsed(subject, blocked, text.trim().take(MAX_RAW))
    }

    // v7.0: edits of the site NOVA already built: "background colour white chahiye", "hero mein meri photo lagao", "button ka rang lal karo".
    // Strong targets only: colour words, "dark", "text", "photo" alone are too common in normal chat ("dark mode on karo") to steal a sentence.
    private val EDIT_TARGETS = setOf("background", "bg", "colour", "color", "rang", "theme", "font", "heading", "button", "buttons", "section", "navbar", "header", "footer", "hero", "logo", "banner", "gallery", "layout", "gradient", "animation", "animations", "3d", "4d", "website", "site", "webpage", "page")
    private val EDIT_WISH = setOf("chahiye", "chahie", "chaiye", "karo", "kar", "do", "dedo", "lagao", "laga", "lagado", "add", "hatao", "hata", "remove", "change", "badlo", "badal", "badaldo", "rakho", "dikhao", "daalo", "dalo", "bana", "banao", "make", "set", "use", "show", "put", "insert", "replace", "fix")
    private val EDIT_HARD_NOT = setOf("kholo", "khol", "open", "whatsapp", "instagram", "call", "delete", "bhejo", "send", "share", "chrome", "browser", "gemini", "chatgpt", "gpt", "wallpaper", "publish", "upload", "deploy", "host")

    /** Only meaningful while a website exists (caller checks). Needs an edit target AND a wish/action word, and no other feature's word. */
    fun parseEdit(text: String, hasMedia: Boolean = false): Parsed? {
        val t = tokens(text)
        if (t.size < 2 || t.size > 400) return null
        if (t.any { it in EDIT_HARD_NOT || it in BLOCK }) return null
        if (t.any { it in VERBS } && t.any { it in NOUNS } && t.size > 12) return null   // a fresh long brief is a new site, not an edit
        val target = t.any { it in EDIT_TARGETS }
        val wish = t.any { it in EDIT_WISH }
        val mediaWord = t.any { it in setOf("photo", "photos", "image", "images", "video", "videos", "pic", "pics") }
        if (!(target && wish) && !(hasMedia && mediaWord)) return null
        return Parsed("", false, text.trim().take(MAX_RAW), true)
    }

    fun tokens(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ')
        }
        val out = ArrayList<String>()
        for (w in sb.toString().split(' ')) if (w.isNotEmpty()) out.add(w)
        return out
    }

    /** True for "nova website publish karo" / "publish my site". The caller still asks a spoken yes before anything is sent. */
    fun isPublish(text: String): Boolean {
        val t = tokens(text)
        if (t.isEmpty() || t.size > 30) return false
        var pub = false
        var noun = false
        for (w in t) {
            if (w in NOT) return false
            if (w in VERBS) return false   // a build verb wins: "ek website banani hai jisse ... upload kar sake" is a build request, not publish
            if (w in PUBVERBS) pub = true
            if (w in NOUNS) noun = true
        }
        return pub && noun
    }

    /** null = this is not a "build a website" sentence. Otherwise the subject (may be empty) and whether it must be refused. */
    fun parse(text: String): Parsed? {
        val t = tokens(text)
        if (t.isEmpty()) return null
        if (t.size > 60) return parseLong(text, t)
        var verb = false
        var noun = false
        for (w in t) {
            if (w in NOT) return null
            if (w in VERBS) verb = true
            if (w in NOUNS) noun = true
        }
        if (!verb || !noun) return null
        var blocked = false
        val keep = ArrayList<String>()
        for (w in t) {
            if (w in BLOCK) blocked = true
            if (w in VERBS || w in NOUNS || w in FILLER) continue
            keep.add(w)
        }
        var subject = keep.joinToString(" ")
        if (subject.length < 3) subject = ""
        return Parsed(subject, blocked, text.trim().take(MAX_RAW))
    }

    // v6.90 follow-up on the website NOVA already built: "isko professional banao", "website ko dark mode ke saath behtar karo".
    private val REF_FIRST = setOf("isko", "issko", "ise", "isse", "usko", "use", "iska", "iske", "this", "it", "make", "improve", "upgrade")
    private val SITE_NOUNS = setOf("website", "site", "webpage", "page")
    private val IMPROVE = setOf("professional", "behtar", "better", "improve", "sundar", "modern", "upgrade", "redesign", "polish", "animation", "animations", "responsive", "attractive", "stylish", "premium", "badhiya", "theek", "fix", "update", "badlo", "badal", "jodo", "add")
    private val ACT = setOf("banao", "bana", "bnao", "bna", "karo", "kar", "do", "make", "improve", "upgrade", "update", "badlo", "badal", "jodo", "add", "fix", "redesign", "polish")

    /** Only meaningful while a website exists (caller checks). Conservative: needs a reference word, an improve word and an action word. */
    fun parseFollowUp(text: String): Parsed? {
        val t = tokens(text)
        if (t.size < 2 || t.size > 40) return null
        for (w in t) if (w in NOT || w in PUBVERBS) return null
        val firstOk = t.first() in REF_FIRST || t.take(3).any { it in REF_FIRST } || t.any { it in SITE_NOUNS }
        if (!firstOk) return null
        if (t.none { it in IMPROVE }) return null
        if (t.none { it in ACT }) return null
        if (t.any { it in BLOCK }) return null
        return Parsed("", false, text.trim().take(MAX_RAW), true)
    }
}
