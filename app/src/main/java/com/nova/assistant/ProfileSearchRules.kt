package com.nova.assistant

import java.net.URLEncoder

/**
 * v7.89 PROFILE SEARCH - "nova instagram par rahul_sharma search karo" (pure Kotlin, no Android classes, JUnit-able).
 *
 * The owner says a NAME or a USERNAME (and optionally the site: Instagram, Facebook, X / Twitter, LinkedIn, YouTube, TikTok, Snapchat). NOVA opens
 * the public profile page (username) or the site's public people search (name) in the phone's browser / app. Nothing is scraped, nothing is sent to
 * any AI, no key is needed and no background work runs, so it cannot hang the app.
 *
 * NEVER built, on purpose: finding a person from a face / photo / selfie, and finding a person's phone number, address, location or password.
 * Those sentences get an honest refusal line. A request must carry a social site (or a profile / account word) AND a search word, so it never
 * takes over "instagram kholo", "youtube par gaana search karo" or "wifi ka password dhundo".
 */
object ProfileSearchRules {
    enum class Site(val domain: String) {
        INSTAGRAM("instagram.com"), FACEBOOK("facebook.com"), X("x.com"), LINKEDIN("linkedin.com"),
        YOUTUBE("youtube.com"), TIKTOK("tiktok.com"), SNAPCHAT("snapchat.com"), WEB("google.com")
    }

    /** [refusal] = null for a real search; otherwise "face" | "private" | "hack" | "noname". */
    class Req(val sites: List<Site>, val query: String, val direct: Boolean, val refusal: String?) {
        val site: Site get() = if (sites.size == 1) sites[0] else Site.WEB
    }

    const val MAX_WORDS = 14
    const val MAX_QUERY_WORDS = 6
    const val MAX_QUERY_CHARS = 60

    private fun tokens(text: String?): List<String> =
        (text ?: "").lowercase().replace(Regex("[^a-z0-9@_. ]"), " ").split(' ')
            .map { w ->
                val x = w.trim('.', '_')
                if (x.startsWith("@")) "@" + x.trimStart('@').trim('.', '_') else x
            }
            .filter { it.isNotEmpty() && it != "@" && it != "nova" }

    private val SITE_WORDS: Map<String, Site> = mapOf(
        "instagram" to Site.INSTAGRAM, "insta" to Site.INSTAGRAM, "ig" to Site.INSTAGRAM, "instagrm" to Site.INSTAGRAM,
        "facebook" to Site.FACEBOOK, "fb" to Site.FACEBOOK, "facebok" to Site.FACEBOOK,
        "twitter" to Site.X, "linkedin" to Site.LINKEDIN, "linkdin" to Site.LINKEDIN,
        "youtube" to Site.YOUTUBE, "yt" to Site.YOUTUBE, "tiktok" to Site.TIKTOK, "snapchat" to Site.SNAPCHAT, "snap" to Site.SNAPCHAT
    )
    private val SEARCH = setOf(
        "search", "serch", "find", "lookup", "dhundo", "dhundho", "dhundh", "dhund", "dhoondo", "dhoondh", "khojo", "khoj", "nikalo", "nikaalo", "pata", "dikhao", "dikha"
    )
    private val ACCT = setOf("profile", "account", "acount", "akaunt", "id", "handle", "username", "channel", "page")
    private val HANDLE_WORDS = setOf("username", "handle", "id")
    private val X_NEXT = setOf("par", "pe", "me", "mein", "ka", "ki")
    private val ALL_SITES_WORDS = setOf("sab", "sabhi", "har", "everywhere", "all")

    /** Sentences about the CONTENT of a site, or about doing something to a person, belong to other features. */
    private val CONTENT = setOf(
        "reel", "reels", "video", "videos", "song", "songs", "gaana", "gana", "music", "post", "posts", "story", "stories", "hashtag", "trending", "movie", "film",
        "news", "recipe", "tutorial", "meme", "memes", "live", "short", "shorts", "follow", "unfollow", "block", "like", "comment", "message", "msg", "dm", "bhejo",
        "call", "share", "download", "upload", "delete", "logout", "signup", "register"
    )
    private val NOT_SOCIAL = setOf(
        "bank", "upi", "paytm", "gpay", "phonepe", "loan", "card", "aadhaar", "aadhar", "pan", "wifi", "github", "play", "store", "app", "apps", "file", "folder",
        "contact", "contacts", "whatsapp", "gmail", "google", "chrome", "settings", "gallery", "calendar", "alarm"
    )
    private val FACE_STRONG = setOf("face", "faces", "chehra", "chehre", "chehara", "selfie", "tasveer")
    private val FACE_WEAK = setOf("photo", "pic", "picture", "image")
    private val DEMO = setOf("ye", "yeh", "is", "iski", "iska", "isko", "uski", "uska", "this")
    private val PRIVATE = setOf(
        "number", "address", "ghar", "location", "locate", "track", "kahan", "kaha", "rehta", "rehti", "email", "mail", "aadhaar", "aadhar"
    )
    private val HACK = setOf("hack", "hacker", "password", "pass", "otp", "login", "crack", "pin")
    private val FILLER = setOf(
        "par", "pe", "me", "mein", "ko", "ka", "ki", "ke", "kaa", "ek", "zara", "please", "plz", "pls", "karo", "kar", "do", "de", "dena", "batao", "bata", "kholo", "khol",
        "mujhe", "mera", "meri", "mere", "hai", "hain", "wala", "wali", "wale", "ye", "yeh", "is", "iska", "uska", "uski", "iski", "se", "aur", "ya", "the", "a", "an",
        "on", "for", "of", "to", "in", "my", "official", "real", "original", "dono", "kaun", "kon", "kya", "naam", "name", "wo", "woh", "uska", "kisi", "kisika"
    )

    private val USER_RE = Regex("^[a-z0-9._]{2,30}$")

    /** null = not a profile-search request. */
    fun parse(text: String?): Req? {
        val t = tokens(text)
        if (t.isEmpty() || t.size > MAX_WORDS) return null
        val sites = LinkedHashSet<Site>()
        val siteIdx = HashSet<Int>()
        for ((i, w) in t.withIndex()) {
            val s = SITE_WORDS[w]
            if (s != null) { sites.add(s); siteIdx.add(i) }
            else if (w == "x" && i + 1 < t.size && t[i + 1] in X_NEXT) { sites.add(Site.X); siteIdx.add(i) }
        }
        val search = t.any { it in SEARCH }
        val acct = t.any { it in ACCT }
        val hasSite = sites.isNotEmpty()
        val onlyYoutube = hasSite && sites.all { it == Site.YOUTUBE }
        // A social site + a search word (or a profile / account word), or a profile / account word + a search word. YouTube alone needs a channel / profile word.
        val wanted = if (onlyYoutube) acct else if (hasSite) (search || acct) else (acct && search)
        if (!wanted) return null
        val chosen: List<Site> = if (hasSite) sites.toList() else listOf(Site.WEB)
        val faceAsk = t.any { it in FACE_STRONG } || (t.any { it in FACE_WEAK } && t.any { it in DEMO })
        if (faceAsk) return Req(chosen, "", false, "face")
        val hackAsk = t.any { it in HACK }
        val privateAsk = t.any { it in PRIVATE } || (t.contains("pata") && !t.any { it in SEARCH && it != "pata" })
        val contentAsk = t.any { it in CONTENT }
        if (hasSite) {
            if (hackAsk) return Req(chosen, "", false, "hack")
            if (privateAsk) return Req(chosen, "", false, "private")
            if (contentAsk) return null
        } else {
            if (hackAsk || privateAsk || contentAsk || t.any { it in NOT_SOCIAL }) return null
        }

        val rest = ArrayList<String>()
        var handleWord = false
        for ((i, w) in t.withIndex()) {
            if (i in siteIdx) continue
            if (w in HANDLE_WORDS) handleWord = true
            if (w in SEARCH || w in ACCT || w in FILLER || w in ALL_SITES_WORDS || w == "x") continue
            rest.add(w)
        }
        if (rest.isEmpty()) return Req(chosen, "", false, "noname")
        if (rest.size > MAX_QUERY_WORDS) return null
        val q = rest.joinToString(" ").trim().take(MAX_QUERY_CHARS).trim()
        if (q.isEmpty()) return Req(chosen, "", false, "noname")
        val single = if (rest.size == 1) rest[0] else ""
        val at = single.startsWith("@")
        val bare = single.removePrefix("@")
        val looksUser = USER_RE.matches(bare) && (at || handleWord || bare.contains('_') || bare.contains('.') || bare.any { it.isDigit() })
        return if (looksUser) Req(chosen, bare, true, null) else Req(chosen, q.replace("@", ""), false, null)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun google(q: String): String = "https://www.google.com/search?q=" + enc(q)

    /** The https link to open. Usernames only ever go into a path after the [USER_RE] check; names are percent-encoded. */
    fun url(r: Req): String {
        val q = r.query
        if (r.sites.size > 1 || r.site == Site.WEB) {
            val scope = if (r.sites.size > 1) r.sites.joinToString(" OR ") { "site:" + it.domain }
                        else "instagram OR facebook OR linkedin OR twitter"
            return google((if (r.direct) q else "\"" + q + "\"") + " " + scope)
        }
        val u = if (r.direct && USER_RE.matches(q)) q else null
        return when (r.site) {
            Site.INSTAGRAM -> if (u != null) "https://www.instagram.com/$u/" else google("site:instagram.com \"$q\"")
            Site.FACEBOOK -> if (u != null) "https://www.facebook.com/$u" else "https://www.facebook.com/search/people/?q=" + enc(q)
            Site.X -> if (u != null) "https://x.com/$u" else "https://x.com/search?f=user&q=" + enc(q)
            Site.LINKEDIN -> if (u != null) "https://www.linkedin.com/in/$u/" else "https://www.linkedin.com/search/results/people/?keywords=" + enc(q)
            Site.YOUTUBE -> if (u != null) "https://www.youtube.com/@$u" else "https://www.youtube.com/results?sp=EgIQAg%253D%253D&search_query=" + enc(q)
            Site.TIKTOK -> if (u != null) "https://www.tiktok.com/@$u" else "https://www.tiktok.com/search/user?q=" + enc(q)
            Site.SNAPCHAT -> if (u != null) "https://www.snapchat.com/add/$u" else google("site:snapchat.com \"$q\"")
            Site.WEB -> google(q)
        }
    }

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    fun siteName(s: Site): String = when (s) {
        Site.INSTAGRAM -> "Instagram"; Site.FACEBOOK -> "Facebook"; Site.X -> "X (Twitter)"; Site.LINKEDIN -> "LinkedIn"
        Site.YOUTUBE -> "YouTube"; Site.TIKTOK -> "TikTok"; Site.SNAPCHAT -> "Snapchat"; Site.WEB -> "Google"
    }

    fun startedText(r: Req, en: Boolean): String {
        val where = if (r.sites.size > 1) r.sites.joinToString(" aur ") { siteName(it) } else siteName(r.site)
        return if (r.direct) t(en, "$where par @${r.query} ka profile khol raha hoon. Public profile hi dikhega.", "Opening the profile @${r.query} on $where. Only public profiles show.")
        else t(en, "$where par \"${r.query}\" search kar raha hoon. Sahi insaan aap khud chun lena.", "Searching \"${r.query}\" on $where. Pick the right person yourself.")
    }

    fun refusalText(code: String, en: Boolean): String = when (code) {
        "face" -> t(en, "Chehra ya photo dekhkar kisi ko dhundhna main nahi karta, ye privacy ke khilaf hai. Naam ya username bolo, to search kar dunga.",
            "I do not look people up from a face or a photo, it is against privacy. Say a name or a username and I will search it.")
        "private" -> t(en, "Kisi ka number, address ya location main nahi dhundhta. Sirf public profile naam ya username se search karta hoon.",
            "I do not look for anyone's number, address or location. I only search public profiles by name or username.")
        "hack" -> t(en, "Password, login ya hack me main madad nahi kar sakta. Sirf public profile search karta hoon.",
            "I cannot help with passwords, logins or hacking. I only search public profiles.")
        else -> noNameText(en)
    }

    fun noNameText(en: Boolean): String = t(en, "Kiska naam ya username? Bolo: nova instagram par rahul_sharma search karo.",
        "Whose name or username? Say: nova search rahul_sharma on instagram.")
    fun lockedText(en: Boolean): String = t(en, "Phone lock hai. Unlock karke dobara bolo.", "The phone is locked. Unlock it and say it again.")
    fun failedText(en: Boolean): String = t(en, "Link nahi khul paya. Browser ya app allow karke dobara bolo.", "The link did not open. Allow a browser or the app and say it again.")
}
