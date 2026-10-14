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
        if (t.isEmpty() || t.size > 60) return null
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
        return Parsed(subject, blocked, text.trim().take(1500))
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
        return Parsed("", false, text.trim().take(1500), true)
    }
}
