package com.nova.assistant

/**
 * v7.00 AUTO MEDIA: NOVA decides by itself what the owner wants, no buttons.
 *  - SITE : a website request, even when it also says photo / image / video ("restaurant ki website banao jisme photos ho").
 *           The old SiteIntent refuses every sentence that has a photo word, and ImageIntent then wrongly took it as "make a picture".
 *  - VIDEO: "ek video banao" (make a video). Free AI video is not available without a paid key, so NOVA says so honestly.
 *  - IMAGE: a plain picture request (still handled by ImageIntent / FreeImage; listed here so the decision lives in one place).
 * Pure Kotlin (no Android classes), so it runs in plain JVM unit tests. It never sends anything anywhere.
 * Input is Roman text (Devanagari is converted by HindiRoman before this is called).
 * tools/test_auto_media_contract.py reads the sets below straight from this file, so keep one set per line.
 */
object MediaIntent {
    enum class Kind { SITE, IMAGE, VIDEO }
    class Result(val kind: Kind, val subject: String, val blocked: Boolean, val raw: String)

    private val BUILD = setOf("banao", "bana", "banado", "banade", "banana", "banaiye", "banaye", "bnao", "bna", "banwao", "banwa", "banani", "banane", "bnani", "bnane", "banwani", "banwane", "banwana", "generate", "create", "build", "make", "design", "develop")
    private val SITE_NOUNS = setOf("website", "websites", "webpage", "webpages", "site", "portfolio", "landing")
    private val IMG_WORDS = setOf("photo", "photos", "foto", "fotos", "image", "images", "picture", "pictures", "pic", "pics", "tasveer", "tasvir", "tasveeren", "tasviren", "banner", "logo", "gallery")
    private val VIDEO_WORDS = setOf("video", "videos", "vedio", "vedios", "reel", "reels", "clip", "clips")
    // Words that mean another feature owns the sentence (opening apps, recording, sharing, calls, agent jobs).
    private val OTHER = setOf("kholo", "khol", "open", "chatgpt", "gemini", "gpt", "delete", "hatao", "bhejo", "send", "share", "whatsapp", "instagram", "call", "kaam", "agent", "chrome", "browser", "record", "recording", "camera", "selfie")
    private val NO_MEDIA = setOf("bina", "without", "no", "nahi", "nhi", "mat", "sirf", "only")
    private val FILLER = setOf("nova", "ek", "mujhe", "mere", "meri", "mera", "hamare", "ki", "ka", "ke", "ko", "se", "me", "mein", "par", "pe", "ye", "wo", "please", "plz", "pls", "zara", "kripya", "kar", "karo", "kardo", "do", "dena", "de", "dijiye", "chahiye", "chahie", "chaiye", "hai", "ho", "wala", "wali", "wale", "a", "an", "the", "of", "for", "my", "i", "want", "need", "can", "you", "could", "would", "free", "mujhko", "humko", "hume", "liye", "naya", "nayi", "new", "web", "page", "pages", "jisme", "jismein", "saath", "sath", "with", "and", "aur", "bhi", "sab", "full", "complete", "poori", "puri", "pura", "ai", "generated", "generate")
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

    /**
     * Websites get AI pictures by default; the owner can opt out ("bina photo ke website banao", "website without images", "sirf text").
     * "no" + a picture word within two words of each other = opt out.
     */
    fun wantsImages(text: String): Boolean {
        val t = tokens(text)
        for (i in t.indices) {
            if (t[i] in NO_MEDIA) {
                for (j in maxOf(0, i - 2)..minOf(t.size - 1, i + 2)) if (t[j] in IMG_WORDS) return false
            }
        }
        if ("text" in t && ("sirf" in t || "only" in t)) return false
        return true
    }

    /** True when the owner mentions video for the site. NOVA cannot make free AI video, so the page gets an animated (CSS/canvas) background instead. */
    fun wantsVideo(text: String): Boolean = tokens(text).any { it in VIDEO_WORDS }

    /**
     * null = not a media sentence, or the old routes (SiteIntent / ImageIntent) already own it.
     * SITE is returned ONLY when SiteIntent.parse would have said "no" because of a photo / video word.
     */
    fun classify(text: String): Result? {
        val t = tokens(text)
        if (t.isEmpty() || t.size > 60) return null
        var build = false
        var site = false
        var img = false
        var vid = false
        for (w in t) {
            if (w in OTHER) return null
            if (w in BUILD) build = true
            if (w in SITE_NOUNS) site = true
            if (w in IMG_WORDS) img = true
            if (w in VIDEO_WORDS) vid = true
        }
        if (!build) return null
        val blocked = t.any { it in BLOCK }
        if (site) {
            if (!img && !vid) return null   // no media word: the old SiteIntent route handles it exactly as before
            val keep = ArrayList<String>()
            for (w in t) {
                if (w in BUILD || w in SITE_NOUNS || w in FILLER || w in IMG_WORDS || w in VIDEO_WORDS || w in NO_MEDIA) continue
                keep.add(w)
            }
            var subject = keep.joinToString(" ")
            if (subject.length < 3) subject = ""
            return Result(Kind.SITE, subject, blocked, text.trim().take(1500))
        }
        if (vid && !img) {
            // "ek video banao" / "make a video of a sunset". Recording phrases were already excluded through OTHER.
            val keep = ArrayList<String>()
            for (w in t) if (!(w in BUILD || w in FILLER || w in VIDEO_WORDS)) keep.add(w)
            return Result(Kind.VIDEO, keep.joinToString(" "), blocked, text.trim().take(300))
        }
        return null
    }
}
