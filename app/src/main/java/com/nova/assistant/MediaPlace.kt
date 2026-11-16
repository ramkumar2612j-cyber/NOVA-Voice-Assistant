package com.nova.assistant

/**
 * v7.03 G: where on the website does a picked gallery photo / video go? Pure Kotlin (no Android classes), Roman text only
 * (HindiRoman already converts Devanagari). "ye photo hero mein lagao", "dusri photo gallery mein", "m2 logo banao header mein".
 * It only decides; it never sends anything anywhere.
 * tools/test_site_media_contract.py reads the sets below straight from this file, so keep one set per line.
 */
object MediaPlace {
    enum class Place { HERO, ABOUT, GALLERY, LOGO, MENU, FOOTER, BACKGROUND }

    private val HERO = setOf("hero", "banner", "top", "upar", "oopar", "cover", "front")
    private val ABOUT = setOf("about", "aboutus", "baare", "bare")
    private val GALLERY = setOf("gallery", "album", "grid")
    private val LOGO = setOf("logo", "header", "navbar", "brand")
    private val MENU = setOf("menu", "products", "services", "offerings")
    private val FOOTER = setOf("footer", "contact", "neeche", "niche", "bottom")
    private val BACKGROUND = setOf("background", "bg", "wallpaper", "peeche")
    private val ACTS = setOf("lagao", "lagana", "laga", "lagado", "lagade", "rakho", "rakhna", "daalo", "dalo", "daal", "dalna", "use", "set", "add", "jodo", "put", "place")
    private val MEDIA_REF = setOf("ye", "yeh", "is", "isko", "ise", "photo", "photos", "image", "images", "video", "videos", "picture", "pic", "foto", "tasveer", "clip", "reel")
    private val FIRST = setOf("pehli", "pehla", "pahli", "pahla", "first")
    private val SECOND = setOf("dusri", "doosri", "dusra", "doosra", "second")
    private val THIRD = setOf("teesri", "teesra", "third")
    private val FOURTH = setOf("chauthi", "chautha", "fourth")
    private val LAST = setOf("last", "aakhri", "akhri")

    fun tokens(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ')
        }
        val out = ArrayList<String>()
        for (w in sb.toString().split(' ')) if (w.isNotEmpty()) out.add(w)
        return out
    }

    /** First place word in the sentence (sentence order), or null. */
    fun parse(text: String): Place? {
        for (w in tokens(text)) {
            when (w) {
                in HERO -> return Place.HERO
                in ABOUT -> return Place.ABOUT
                in GALLERY -> return Place.GALLERY
                in LOGO -> return Place.LOGO
                in MENU -> return Place.MENU
                in FOOTER -> return Place.FOOTER
                in BACKGROUND -> return Place.BACKGROUND
            }
        }
        return null
    }

    /** 1-based item number ("pehli photo" = 1, "m3" = 3), -1 = the last one, null = not said. */
    fun itemIndex(text: String): Int? {
        for (w in tokens(text)) {
            when {
                w in FIRST -> return 1
                w in SECOND -> return 2
                w in THIRD -> return 3
                w in FOURTH -> return 4
                w in LAST -> return -1
                w.length == 2 && w[0] == 'm' && w[1] in '1'..'6' -> return w[1] - '0'
            }
        }
        return null
    }

    /** "ye photo hero mein lagao": a place word + a put-it verb + a media reference. Caller also checks that picked media exist. */
    fun isPlaceCommand(text: String): Boolean {
        val t = tokens(text)
        if (t.isEmpty() || t.size > 30) return false
        if (parse(text) == null) return false
        if (t.none { it in ACTS }) return false
        return itemIndex(text) != null || t.any { it in MEDIA_REF }
    }

    fun label(p: Place?): String = when (p) {
        Place.HERO -> "hero"
        Place.ABOUT -> "about"
        Place.GALLERY -> "gallery"
        Place.LOGO -> "logo"
        Place.MENU -> "menu"
        Place.FOOTER -> "footer"
        Place.BACKGROUND -> "background"
        null -> "sahi jagah"
    }

    fun fromName(name: String): Place? = Place.values().firstOrNull { it.name.equals(name.trim(), true) }
}
