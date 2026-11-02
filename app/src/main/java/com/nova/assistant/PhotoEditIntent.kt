package com.nova.assistant

/**
 * v7.05 I: "is photo ko professional banao", "background hatao", "black and white karo", "square crop karo".
 * Pure Kotlin (no Android classes), Roman text. It only decides what edit is wanted; it never sends anything anywhere.
 * It never steals "ek billi ki photo banao" (ImageIntent), sharing / sending / camera / gallery sentences.
 */
object PhotoEditIntent {
    enum class Kind { ENHANCE, BRIGHT, FILTER, CROP, BG_REMOVE, BG_REPLACE, REMOVE_OBJECT, RESTYLE, ORIGINAL, AGAIN }
    /** preset = offline preset name (PhotoPro.Preset), ratio = crop "w:h" or "". needsAi = Level 2 would help (the phone-only result is the honest fallback). */
    class Parsed(val kind: Kind, val preset: String, val ratio: String, val blocked: Boolean, val needsAi: Boolean)

    private val REF = setOf("photo", "foto", "image", "pic", "picture", "tasveer", "isko", "ise", "iski", "isse", "yeh", "ye")
    private val NOT = setOf("banao", "bana", "banado", "generate", "create", "draw", "paint", "bhejo", "send", "share", "whatsapp", "instagram", "call", "camera", "kheencho", "click", "gallery", "chuno", "kholo", "open", "website", "site", "video", "reel", "record")
    private val BLOCK = setOf("nude", "naked", "nsfw", "porn", "porno", "sex", "sexy", "xxx", "nangi", "nanga", "erotic", "boobs", "gore", "deepfake")
    private val BG = setOf("background", "bg", "backdrop", "peeche")
    private val REMOVE = setOf("hatao", "hata", "hatado", "remove", "erase", "mitao", "mita", "nikalo", "nikal", "delete")
    private val CHANGE = setOf("badlo", "badal", "badlo", "change", "replace", "naya", "nayi", "studio", "white", "safed", "blur")
    private val PRO = setOf("professional", "pro", "saaf", "sundar", "badhiya", "badiya", "enhance", "sharp", "hd", "glow", "behtar", "better", "improve", "fix", "theek", "clear", "quality", "edit", "sudhaar", "sudhar", "mast")
    private val BRIGHT = setOf("bright", "roshan", "andhera", "dark", "ujala", "brighten", "light")
    private val BW = setOf("bw", "monochrome", "grayscale", "greyscale")
    private val SEPIA = setOf("sepia", "vintage", "purani", "retro")
    private val CINE = setOf("cinematic", "cinema", "movie", "film")
    private val WARM = setOf("warm", "garam", "golden")
    private val COOL = setOf("cool", "thanda", "thandi")
    private val VIVID = setOf("vivid", "colorful", "colourful", "rangeen", "colors", "colours", "color", "colour")
    private val SOFT = setOf("soft", "smooth", "naram", "dreamy")
    private val CROP = setOf("crop", "kato", "kaato", "kaat", "cut", "trim")
    private val RESTYLE = setOf("cartoon", "anime", "sketch", "painting", "ghibli", "oil", "style", "pixar", "3d")
    private val ORIG = setOf("pehle", "original", "undo", "wapas", "purana")
    private val AGAIN = setOf("aur", "more", "again", "phir", "dobara", "zyada")

    fun tokens(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) { if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ') }
        val out = ArrayList<String>()
        for (w in sb.toString().split(' ')) if (w.isNotEmpty()) out.add(w)
        return out
    }

    private fun ratioOf(text: String, t: List<String>): String {
        val m = Regex("(\\d{1,2})\\s*[:x]\\s*(\\d{1,2})").find(text.lowercase())
        if (m != null) {
            val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
            if (a in 1..32 && b in 1..32) return "$a:$b"
        }
        if ("square" in t) return "1:1"
        if ("wide" in t || "landscape" in t) return "16:9"
        if ("portrait" in t) return "4:5"
        return "1:1"
    }

    /** null = not a photo-edit sentence. The caller must also have a picked photo. */
    fun parse(text: String): Parsed? {
        val t = tokens(text)
        if (t.isEmpty() || t.size > 30) return null
        // v7.12: "is photo ko professional banao" = edit (object marker "ko" + photo word + quality word); "banao" alone still means generate.
        val makeOk = ("ko" in t || "ki" in t && "quality" in t) && t.any { it in REF } && t.any { it in PRO }
        for (w in t) if (w in NOT && !(makeOk && (w == "banao" || w == "bana" || w == "banado"))) return null
        val blocked = t.any { it in BLOCK }
        val ref = t.any { it in REF }
        fun has(s: Set<String>) = t.any { it in s }
        fun p(k: Kind, preset: String = "", ratio: String = "", ai: Boolean = false) = Parsed(k, preset, ratio, blocked, ai)
        if (has(BG) && (has(REMOVE))) return p(Kind.BG_REMOVE, "ENHANCE", ai = true)
        if (has(BG) && has(CHANGE)) return p(Kind.BG_REPLACE, "ENHANCE", ai = true)
        if (has(RESTYLE)) return p(Kind.RESTYLE, "VIVID", ai = true)
        if (has(CROP) || "square" in t) return p(Kind.CROP, "", ratioOf(text, t))
        if (has(BW) || ("black" in t && "white" in t)) return p(Kind.FILTER, "BW")
        if (has(SEPIA)) return p(Kind.FILTER, "SEPIA")
        if (has(CINE)) return p(Kind.FILTER, "CINEMATIC")
        if (has(WARM)) return p(Kind.FILTER, "WARM")
        if (has(COOL)) return p(Kind.FILTER, "COOL")
        if (has(SOFT) && ref) return p(Kind.FILTER, "SOFT")
        if (has(VIVID) && ref) return p(Kind.FILTER, "VIVID")
        if (has(REMOVE) && ref && !has(BG)) return p(Kind.REMOVE_OBJECT, "ENHANCE", ai = true)
        if (has(ORIG) && (ref || t.size <= 3)) return p(Kind.ORIGINAL)
        if (has(BRIGHT) && ref) return p(Kind.BRIGHT, "BRIGHT")
        if (has(PRO) && ref) return p(Kind.ENHANCE, "ENHANCE")
        if (t.size <= 4 && has(AGAIN) && (has(PRO) || has(setOf("sundar")))) return p(Kind.AGAIN, "ENHANCE")
        return null
    }
}
