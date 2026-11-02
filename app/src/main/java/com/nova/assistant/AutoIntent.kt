package com.nova.assistant

/**
 * v7.30 PART 1 AUTO INTENT: NOVA decides BY ITSELF whether the owner wants a PICTURE, a WEBSITE or a PHOTO EDIT.
 * No IMAGE ON/OFF or WEBSITE button is needed any more: the chat and the voice path both call this.
 * It only runs AFTER the old strict routes (SiteIntent / MediaIntent / ImageIntent) said "not mine", so nothing that worked before changes.
 * Free wording it understands (Roman Hinglish / English, Devanagari is converted by HindiRoman before):
 *   "mujhe ek logo chahiye"  "i need a poster for my cafe"  "gym ke liye site chahiye"  "ek billi draw karo"  "online store banwana hai"
 * Pure Kotlin (no Android classes) so it runs in plain JVM unit tests. It never sends anything anywhere.
 * tools/test_auto_intent_contract.py reads the sets below straight from this file: keep one set per line, words only [a-z0-9].
 */
object AutoIntent {
    enum class Kind { SITE, IMAGE, PHOTO_EDIT, WRITE, NONE }
    class Result(val kind: Kind, val subject: String, val blocked: Boolean, val why: String)

    private val BUILD = setOf("banao", "bana", "banado", "banade", "banana", "banaiye", "banaye", "bnao", "bna", "bnado", "banaao", "banvao", "banwao", "banwa", "banwado", "banani", "banane", "bnani", "bnane", "banwani", "banwane", "banwana", "generate", "create", "build", "make", "design", "develop", "draw", "paint", "sketch", "render")
    private val WISH = setOf("chahiye", "chahie", "chaiye", "chahiyee", "chahta", "chahti", "chahte", "need", "want", "dedo", "dijiye", "give", "tayyar", "ready")
    private val EN_VERBS = setOf("generate", "create", "build", "make", "design", "develop", "draw", "paint", "sketch", "render", "need", "want", "give")
    private val DRAW = setOf("draw", "paint", "sketch", "render")
    private val SITE_NOUNS = setOf("website", "websites", "webpage", "webpages", "site", "sites", "portfolio", "landing", "homepage", "webapp")
    private val SITE_AFTER_WEB = setOf("page", "site", "app", "design")
    private val SITE_AFTER_ONLINE = setOf("store", "shop", "dukaan", "menu", "portfolio", "resume", "cv", "catalog", "catalogue", "booking")
    private val IMG_NOUNS = setOf("photo", "photos", "foto", "fotos", "image", "images", "picture", "pictures", "pic", "pics", "tasveer", "tasvir", "tasveeren", "poster", "posters", "logo", "logos", "wallpaper", "wallpapers", "illustration", "artwork", "sketch", "drawing", "painting", "cartoon", "avatar", "thumbnail", "banner", "dp", "flyer", "sticker", "icon")
    private val NO_MEDIA = setOf("bina", "without", "no", "nahi", "nhi", "mat", "sirf", "only")
    private val COMPONENT = setOf("sath", "saath", "with", "jisme", "jismein", "aur", "and", "along", "wali", "wala", "wale", "bhi", "jaisi", "jaisa")
    // Another feature owns the sentence (apps, sending, camera, music, editing a site, publishing).
    private val OTHER = setOf("kholo", "khol", "open", "delete", "hatao", "hatana", "bhejo", "send", "share", "whatsapp", "instagram", "call", "chrome", "browser", "gallery", "camera", "selfie", "record", "recording", "screenshot", "download", "install", "play", "chalao", "gaana", "song", "music", "volume", "brightness", "torch", "alarm", "timer", "sms", "edit", "badlo", "badal", "change", "crop", "filter", "publish", "upload", "deploy", "host", "chatgpt", "gemini", "gpt")
    // A question about the topic, not a request: "website kaise banate hain", "what is a logo".
    private val HARD_INFO = setOf("kaise", "kese", "how", "kyu", "kyun", "why", "matlab", "meaning", "explain", "samjhao", "difference", "fark", "tutorial", "steps", "tarika", "tareeka", "sikhao", "sikha", "learn")
    private val FILLER = setOf("nova", "ek", "mujhe", "mere", "meri", "mera", "hamare", "ki", "ka", "ke", "ko", "se", "me", "mein", "par", "pe", "ye", "wo", "please", "plz", "pls", "zara", "kripya", "kar", "karo", "kardo", "do", "dena", "de", "hai", "ho", "hain", "wala", "wali", "wale", "a", "an", "the", "of", "for", "my", "i", "can", "you", "could", "would", "free", "mujhko", "humko", "hume", "liye", "naya", "nayi", "new", "web", "page", "pages", "app", "online", "jisme", "jismein", "saath", "sath", "with", "and", "aur", "bhi", "sab", "full", "complete", "ai", "kya", "tum", "aap", "bhai", "yaar", "jaldi", "abhi", "koi", "kuch", "to", "toh", "na", "sakte", "sakta", "skta", "skte", "banwana", "hoga", "hona", "waali")
    // v7.31 PART 2: text jobs the AI chat does itself (story, poem, letter, code...). Only a hint for the chip: the normal AI chat path answers them, nothing is routed away.
    private val WRITE_VERBS = setOf("likh", "likho", "likhdo", "likhna", "likhwao", "likhwana", "write", "draft", "compose")
    private val WRITE_NOUNS = setOf("kahani", "story", "poem", "kavita", "shayari", "essay", "letter", "email", "caption", "script", "speech", "code", "program", "resume", "cv", "application", "bio", "slogan", "joke", "lyrics", "paragraph", "article", "blog", "post")
    private val BLOCK = setOf("nude", "naked", "nsfw", "porn", "porno", "sex", "sexy", "xxx", "nangi", "nanga", "erotic", "boobs", "gore", "phishing", "deepfake")

    fun tokens(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ')
        }
        val out = ArrayList<String>()
        for (w in sb.toString().split(' ')) if (w.isNotEmpty()) out.add(w)
        return out
    }

    private fun none(why: String) = Result(Kind.NONE, "", false, why)

    /** True when a media word has a "without / only text" word or a component word (with / wali / aur) within two tokens: it is not the thing asked for. */
    private fun sideWord(t: List<String>, i: Int, set: Set<String>): Boolean {
        for (j in maxOf(0, i - 2)..minOf(t.size - 1, i + 2)) if (j != i && t[j] in set) return true
        return false
    }

    /** Indexes of website nouns, including the pairs "web page", "landing page", "online store". */
    private fun siteIdx(t: List<String>): List<Int> {
        val out = ArrayList<Int>()
        for (i in t.indices) {
            val w = t[i]
            if (w in SITE_NOUNS) out.add(i)
            else if (i > 0 && t[i - 1] == "web" && w in SITE_AFTER_WEB) out.add(i)
            else if (i > 0 && t[i - 1] == "online" && w in SITE_AFTER_ONLINE) out.add(i)
            else if (i > 0 && t[i - 1] == "home" && w == "page") out.add(i)
        }
        return out
    }

    /** Indexes of picture nouns that really are the request (not "bina photo", not "photos wali website"). */
    private fun imgIdx(t: List<String>): List<Int> {
        val out = ArrayList<Int>()
        for (i in t.indices) {
            if (t[i] !in IMG_NOUNS) continue
            if (sideWord(t, i, NO_MEDIA)) continue
            out.add(i)
        }
        return out
    }

    /**
     * hasPhoto = the owner already attached a photo in the chat. Then an edit sentence ("background hatao", "cartoon banao") is PHOTO_EDIT
     * (PhotoEditIntent decides which edit). NONE = not a media sentence: normal chat / old routes go on.
     */
    fun detect(text: String, hasPhoto: Boolean = false): Result {
        val t = tokens(text)
        if (t.isEmpty()) return none("empty")
        if (t.size > 60) return none("long brief: SiteIntent owns it")
        if (t.size < 2) return none("one word is not a request")
        if (hasPhoto) {
            val pe = PhotoEditIntent.parse(text)
            if (pe != null) return Result(Kind.PHOTO_EDIT, pe.kind.name, pe.blocked, "photo attached + edit words")
        }
        if (t.any { it in HARD_INFO }) return none("question, not a request")
        if (t.any { it in OTHER }) return none("another feature owns it")
        if (t.any { it in WRITE_VERBS } && t.any { it in WRITE_NOUNS } && t.none { it in IMG_NOUNS || it in SITE_NOUNS }) {
            val wsubj = ArrayList<String>()
            for (w in t) if (w !in WRITE_VERBS && w !in WRITE_NOUNS && w !in FILLER) wsubj.add(w)
            return Result(Kind.WRITE, wsubj.joinToString(" "), t.any { it in BLOCK }, "text job for the AI chat")
        }
        val verbAt = t.indexOfFirst { it in BUILD || it in WISH }
        if (verbAt < 0) return none("no build / want word")
        val hasBuild = t.any { it in BUILD }
        if ("kya" in t && !hasBuild) return none("kya without a build word")
        val sites = siteIdx(t)
        val imgs = ArrayList<Int>()
        for (i in imgIdx(t)) {
            // a picture word next to a website word and a component word ("restaurant website photos ke sath") is a part of the site
            if (sites.isNotEmpty() && sideWord(t, i, COMPONENT)) continue
            imgs.add(i)
        }
        val blocked = t.any { it in BLOCK }
        // the verb in the first three tokens + an English verb = English order: the object is the first noun AFTER the verb; otherwise Hindi order: the nearest noun BEFORE the verb
        val english = verbAt <= 2 && t[verbAt] in EN_VERBS
        val nouns = ArrayList<Pair<Int, Kind>>()
        for (i in sites) nouns.add(Pair(i, Kind.SITE))
        for (i in imgs) nouns.add(Pair(i, Kind.IMAGE))
        nouns.sortBy { it.first }
        var kind = Kind.NONE
        var why = ""
        if (nouns.isNotEmpty()) {
            val before = nouns.filter { it.first < verbAt }
            val after = nouns.filter { it.first > verbAt }
            val pick = if (english) (after.firstOrNull() ?: before.lastOrNull()) else (before.lastOrNull() ?: after.firstOrNull())
            if (pick != null) { kind = pick.second; why = if (english) "english order" else "hindi order" }
        } else if (t.any { it in DRAW }) {
            kind = Kind.IMAGE; why = "draw word"
        }
        if (kind == Kind.NONE) return none("no picture / website word")
        val drop = HashSet<String>()
        drop.addAll(BUILD); drop.addAll(WISH); drop.addAll(SITE_NOUNS); drop.addAll(IMG_NOUNS); drop.addAll(FILLER); drop.addAll(NO_MEDIA)
        val keep = ArrayList<String>()
        for (w in t) if (w !in drop) keep.add(w)
        var subject = keep.joinToString(" ")
        if (subject.length < 3) subject = ""
        if (kind == Kind.IMAGE && subject.isNotEmpty()) {
            // reuse the old Hindi -> English word map of ImageIntent (billi -> cat); keep the plain words when it declines
            val p = ImageIntent.parse("banao image $subject")
            if (p != null && p.subject.isNotEmpty()) subject = p.subject
        }
        val siteBlocked = kind == Kind.SITE && SiteIntent.isBlocked(t)
        return Result(kind, subject, blocked || siteBlocked, why)
    }
}
