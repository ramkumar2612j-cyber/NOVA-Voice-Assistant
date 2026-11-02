package com.nova.assistant

/**
 * v7.114 REEL / INSTAGRAM FRIEND rules (pure Kotlin, no Android classes, JUnit-able).
 *
 * Understands sentences like
 *   "reel scroll karo", "agli reel", "10 reel scroll karo, har reel 5 second dekho", "reels scroll karte raho",
 *   "is reel ko like karo", "save karo", "comment karo mast", "share karo rahul ko",
 *   "instagram pe rahul_sharma ki id pe jao aur uski 5 reels pe like aur comment karo nice",
 *   "rahul_sharma jaise hi post kare to like aur comment kar do".
 * The rules ONLY turn the sentence into a [Plan]. Nothing is done here. [ReelRunner] does it with the Accessibility service.
 *
 * Decisions made HERE (safety):
 *  - A sentence needs a reel / post / video word (or the owner must already be inside a video app) AND an action word, so "reel me kya chal raha hai" or "instagram kholo" stay with their old routes.
 *  - Comments are capped (COMMENT_CAP per run) and shares are capped (SHARE_CAP) so a wrong hearing can never spam someone.
 *  - A comment / share / watch plan is read back to the owner and runs only after "haan" ([Plan.needsConfirm]). Like, save and scroll run at once.
 *  - "share" needs a NAME. Without one nothing is sent ([Plan.shareAsked] with shareTo == null: NOVA asks who).
 *  - A friend name that is not a valid Instagram username is never guessed ([Plan.friendAsked]: NOVA asks for the username).
 */
object ReelRules {
    const val MAX_WORDS = 40
    const val MAX_ITEMS = 30            // reels / posts per run when any like / save / comment / share is asked
    const val MAX_SCROLL_ONLY = 300     // plain scrolling ("karte raho")
    const val COMMENT_CAP = 10
    const val SHARE_CAP = 5
    const val DEFAULT_DWELL_SEC = 3
    const val MAX_DWELL_SEC = 60
    const val MAX_COMMENT_CHARS = 120

    class Plan(
        val friend: String?,          // Instagram username to open first (null = work on the screen that is open now)
        val friendAsked: Boolean,     // the sentence names "dost / friend" but no usable username
        val count: Int,               // reels / posts to go through
        val scroll: Boolean,          // move to the next reel after each one
        val auto: Boolean,            // "karte raho": until STOP
        val dwellSec: Int,
        val like: Boolean,
        val save: Boolean,
        val comment: String?,         // null = no comment, "" = a short friendly default
        val shareTo: String?,
        val shareAsked: Boolean,
        val back: Boolean,            // previous reel instead of next
        val watch: Boolean,           // "jaise hi post kare": act on the NEXT new post of [friend]
        val posts: Boolean            // photos / posts (feed) instead of reels
    ) {
        val acts: Boolean get() = like || save || comment != null || shareTo != null
        /** Something that leaves a visible trace on another person's account or sends something: the owner confirms first. */
        val needsConfirm: Boolean get() = comment != null || shareTo != null || watch
        /** Plain "scroll" or "next reel" with no action and no friend. */
        val scrollOnly: Boolean get() = !acts && friend == null && !watch
    }

    // ------------------------------------------------------------------ words

    private val DEV = mapOf(
        "रील" to "reel", "रील्स" to "reels", "रिल" to "reel", "स्क्रॉल" to "scroll", "स्क्रोल" to "scroll", "लाइक" to "like", "कमेंट" to "comment", "कमेन्ट" to "comment",
        "सेव" to "save", "शेयर" to "share", "अगली" to "agli", "अगला" to "agla", "पिछली" to "pichli", "पोस्ट" to "post", "इंस्टाग्राम" to "instagram", "करो" to "karo", "कर" to "kar"
    )
    private val REEL = setOf("reel", "reels", "ril", "rils", "rail", "short", "shorts")
    private val POST = setOf("post", "posts", "photo", "photos", "pic", "pics", "picture", "pictures")
    private val VIDEOISH = setOf("video", "videos", "vdo", "clip", "clips")
    private val DEICTIC = setOf("ye", "yeh", "is", "iss", "isko", "ise", "isse", "iski", "iska", "yahan", "yaha", "abhi", "wali", "wala", "jo", "chal", "chalti", "chalta")
    private val SCROLL = setOf("scroll", "scrol", "skrol", "swipe", "aage", "agli", "agla", "agle", "next", "nexti", "dusri", "dusra", "doosri", "doosra", "skip", "badlo", "badal", "badlo", "palto", "palat")
    private val BACK = setOf("pichli", "pichla", "pichle", "previous", "prev", "peeche", "pehle", "wapas", "vapas")
    private val LIKE = setOf("like", "laik", "laike", "pasand", "heart")
    private val SAVE = setOf("save", "sev", "bookmark")
    private val COMMENT = setOf("comment", "coment", "comnt", "commnt", "kament", "kamen")
    private val SHARE = setOf("share", "sher", "shere")
    private val DIRECTION = setOf("up", "down", "left", "right", "upar", "oopar", "neeche", "niche", "nichhe", "left", "right", "dayen", "bayen")
    private val AUTO = setOf("raho", "rahe", "rehna", "lagatar", "continuous", "continue", "nonstop", "auto", "jabtak", "bar", "baar")
    private val ALLW = setOf("sab", "sabhi", "saari", "saare", "sari", "jitni", "jitne", "all", "har", "every")
    private val WATCH_PHRASES = listOf("jaise hi", "jese hi", "jab bhi", "jab wo", "jab ye", "jab vo", "post kare", "post kre", "post karega", "post karegi", "post karta", "post karti", "post dale", "post dalte", "post daale", "naya post", "new post", "upload kare", "upload kre", "story dale", "post aaye", "post aate")
    private val FRIENDISH = setOf("dost", "friend", "doston", "yaar", "buddy", "frnd", "frd", "dosti")
    private val NOT_NAME = setOf(
        "me", "my", "mera", "meri", "mere", "apna", "apni", "apne", "uski", "uske", "uska", "usko", "iski", "iske", "iska", "us", "uss", "is", "ye", "yeh", "wo", "woh", "vo", "kisi", "kuch", "koi",
        "aur", "and", "or", "phir", "fir", "bhi", "sab", "sabhi", "har", "all", "sari", "saari", "nova", "instagram", "insta", "ig", "id", "profile", "account", "reel", "reels", "post", "posts",
        "story", "stories", "video", "videos", "photo", "photos", "the", "ki", "ke", "ka", "ko", "par", "pe", "per", "mein", "karo", "kro", "kar", "do", "de", "dena", "jao", "jaao", "chalo", "khol", "kholo",
        "dost", "friend", "doston", "yaar", "like", "save", "share", "comment", "scroll", "next", "agli", "pichli", "naya", "nayi", "new", "latest", "last", "first", "pehli", "pehla", "puri", "pura"
    )
    private val ID_WORDS = setOf("id", "profile", "account", "username", "handle", "page")
    private val CONTENT_WORDS = setOf("reel", "reels", "post", "posts", "story", "stories", "video", "videos", "photo", "photos", "id", "profile", "account", "short", "shorts")
    private val FILLER = setOf(
        "karo", "kro", "kar", "do", "de", "dena", "dijiye", "likho", "likh", "me", "mein", "ki", "ke", "ka", "ko", "par", "pe", "per", "ye", "yeh", "is", "isko", "isme", "usme", "uspe",
        "reel", "reels", "post", "posts", "video", "nova", "please", "plz", "bhi", "aur", "and", "phir", "fir", "then", "text", "message", "msg", "likhna", "likhdo", "daalo", "dalo", "daal", "dal"
    )
    private val NUMS = mapOf(
        "ek" to 1, "one" to 1, "do" to 2, "two" to 2, "teen" to 3, "three" to 3, "char" to 4, "chaar" to 4, "four" to 4, "paanch" to 5, "panch" to 5, "five" to 5,
        "chhe" to 6, "che" to 6, "six" to 6, "saat" to 7, "seven" to 7, "aath" to 8, "eight" to 8, "nau" to 9, "nine" to 9, "das" to 10, "ten" to 10,
        "barah" to 12, "baarah" to 12, "pandrah" to 15, "bees" to 20, "twenty" to 20, "tees" to 30, "pachas" to 50
    )
    private val SEC = setOf("sec", "second", "seconds", "sekand", "secs", "s")
    private val DEFAULT_COMMENTS_HI = listOf("Mast!", "Bahut badhiya!", "Zabardast!", "Ekdum mast", "Nice one!")
    private val DEFAULT_COMMENTS_EN = listOf("Nice one!", "Love this!", "So good!", "Great!", "Awesome!")

    // ------------------------------------------------------------------ text helpers

    /** Lower case, Devanagari keywords mapped, spoken "underscore" / "dot" joined into the username, only letters, digits, @ _ . and spaces kept. */
    fun norm(text: String?): String {
        var s = (text ?: "").lowercase()
        for ((k, v) in DEV) s = s.replace(k, " $v ")
        s = s.replace(Regex("\\s+(?:under\\s?score|underscore|underscor)\\s+"), "_")
            .replace(Regex("\\s+(?:dot|period|full\\s?stop)\\s+"), ".")
        s = s.replace(Regex("[^a-z0-9@_.\\s]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun toks(s: String): List<String> = s.split(' ').map { it.trim('.', ',') }.filter { it.isNotEmpty() }

    private fun validUser(c: String): Boolean =
        Regex("^[a-z0-9._]{2,30}$").matches(c) && c.any { it.isLetter() } && !c.startsWith(".") && !c.endsWith(".") && !c.contains("..") && c !in NOT_NAME

    private fun numberAt(w: List<String>, i: Int): Int? {
        val t = w[i]
        return t.toIntOrNull() ?: NUMS[t]
    }

    // ------------------------------------------------------------------ parts

    /** The Instagram username named in the sentence, or null. */
    fun friendOf(text: String?): String? {
        val n = norm(text)
        val w = toks(n)
        for (t in w) if (t.startsWith("@")) { val c = t.trimStart('@').trim('.'); if (validUser(c)) return c }
        // "<name> ki / ke / ka id | profile | reels | post ..."
        for (i in 1 until w.size - 1) {
            if (w[i] in setOf("ki", "ke", "ka") && w[i + 1] in CONTENT_WORDS && validUser(w[i - 1])) return w[i - 1]
        }
        // "id <name>" / "username <name>"
        for (i in 0 until w.size - 1) {
            if (w[i] in ID_WORDS && validUser(w[i + 1])) return w[i + 1]
        }
        // "<name> ka account"  covered above; "<name> jaise hi post kare"
        for (i in 0 until w.size - 1) {
            val rest = w.drop(i + 1).joinToString(" ")
            if (validUser(w[i]) && WATCH_PHRASES.any { rest.startsWith(it) } && w[i].length >= 3) return w[i]
        }
        return null
    }

    private fun countOf(w: List<String>): Pair<Int?, Int?> {   // (count, dwellSeconds)
        var count: Int? = null
        var dwell: Int? = null
        for (i in w.indices) {
            val v = numberAt(w, i) ?: continue
            if (v <= 0) continue
            val next = w.getOrNull(i + 1) ?: ""
            if (next in SEC) { if (dwell == null) dwell = v; continue }
            if (next == "min" || next == "minute" || next == "minutes") continue
            val nearReel = next in REEL || next in POST || next in VIDEOISH || next == "baar" || next == "times" || next == "bar" || (w.getOrNull(i - 1) in setOf("agle", "next", "pehli", "first", "last", "sirf"))
            val isNumeric = w[i].toIntOrNull() != null
            if (count == null && (nearReel || isNumeric)) count = v
        }
        return Pair(count, dwell)
    }

    private fun commentText(n: String): String? {
        val w = toks(n)
        val idx = w.indexOfFirst { it in COMMENT }
        if (idx < 0) return null
        val after = ArrayList<String>()
        var i = idx + 1
        while (i < w.size) {
            val t = w[i]
            // the comment ends where the next action starts ("... nice aur like karo")
            if ((t == "aur" || t == "and" || t == "phir" || t == "fir" || t == "then" || t == "or") && w.drop(i + 1).take(3).any { it in LIKE || it in SAVE || it in SHARE || it in SCROLL }) break
            if (t in LIKE || t in SAVE || t in SHARE) break
            after.add(t); i++
        }
        // drop filler words at the start ("karo", "me", "likho" ...) and at the end
        var a = 0; var b = after.size
        while (a < b && after[a] in FILLER) a++
        while (b > a && after[b - 1] in FILLER) b--
        val txt = after.subList(a, b).joinToString(" ").trim()
        return txt.take(MAX_COMMENT_CHARS)
    }

    private fun shareTarget(w: List<String>): String? {
        for (i in 1 until w.size - 1) {
            if (w[i] == "ko" && (w[i + 1] in SHARE || w[i + 1] == "send" || w[i + 1] == "bhejo" || w[i + 1] == "bhej")) {
                val c = w[i - 1]
                if (c.length >= 2 && c !in NOT_NAME && c.all { it.isLetterOrDigit() || it == '_' || it == '.' }) return c
            }
        }
        val s = w.indexOfFirst { it in SHARE }
        if (s >= 0) for (j in s + 1 until minOf(w.size - 1, s + 4)) {
            if (w[j + 1] == "ko") { val c = w[j]; if (c.length >= 2 && c !in NOT_NAME) return c }
        }
        return null
    }

    // ------------------------------------------------------------------ parse

    /**
     * null = not a reel / post sentence. [inVideoApp] = the foreground app is Instagram / Facebook / YouTube / Snapchat (set by the caller from the Accessibility service).
     */
    fun parse(text: String?, inVideoApp: Boolean = false): Plan? {
        val n = norm(text)
        if (n.isBlank()) return null
        val w = toks(n)
        if (w.isEmpty() || w.size > MAX_WORDS) return null
        val s = w.toSet()
        val hasReel = s.any { it in REEL }
        val hasPost = s.any { it in POST }
        val hasVideo = s.any { it in VIDEOISH }
        val insta = s.any { it == "instagram" || it == "insta" || it == "ig" }
        val like = s.any { it in LIKE }
        val save = s.any { it in SAVE }
        val commentWord = s.any { it in COMMENT }
        val shareWord = s.any { it in SHARE }
        val scrollWord = s.any { it in SCROLL }
        val back = s.any { it in BACK } && (hasReel || hasPost || hasVideo || inVideoApp)
        val watch = WATCH_PHRASES.any { n.contains(it) }
        val actionWord = like || save || commentWord || shareWord || scrollWord || back
        if (!actionWord) return null
        // A noun the sentence is about: reel / post, or "video" (only inside a video app), or the owner is inside a video app and says "ye / is".
        val ctx = hasReel || hasPost || (hasVideo && (inVideoApp || insta)) || (inVideoApp && (s.any { it in DEICTIC } || scrollWord || like || save || commentWord)) || (insta && (like || commentWord || save || shareWord))
        if (!ctx) return null
        // Words that make it a different request: a question about the video, a file, a call ...
        if (s.any { it == "kya" || it == "kaun" || it == "kon" || it == "konsi" || it == "kaunsi" } && !(like || save || commentWord || shareWord)) return null
        if (s.any { it == "download" || it == "delete" || it == "hatao" || it == "report" || it == "block" || it == "unfollow" || it == "follow" }) return null

        // "scroll" next to a direction ("neeche scroll karo") is the old plain scroll when no reel word is there
        if (!hasReel && !hasPost && !like && !save && !commentWord && !shareWord && s.any { it in DIRECTION }) return null
        if (!hasReel && !hasPost && !like && !save && !commentWord && !shareWord && !inVideoApp && !insta) return null

        val friend = friendOf(text)
        val friendAsked = friend == null && (like || save || commentWord || shareWord) &&
            ((s.any { it in FRIENDISH } && (hasReel || hasPost || s.any { it in ID_WORDS })) || watch)
        val (cnt, dwell) = countOf(w)
        val all = s.any { it in ALLW } && (hasReel || hasPost)
        val auto = (s.any { it in AUTO } && (scrollWord || hasReel)) || n.contains("jab tak") || n.contains("karte raho") || n.contains("karta reh") || n.contains("lagatar")
        val comment = if (commentWord) (commentText(n) ?: "") else null
        val sharedTo = if (shareWord) shareTarget(w) else null
        val acts = like || save || commentWord || shareWord

        var count = when {
            cnt != null -> cnt
            all -> MAX_ITEMS
            auto -> if (acts) MAX_ITEMS else MAX_SCROLL_ONLY
            else -> 1
        }
        val cap = if (acts) MAX_ITEMS else MAX_SCROLL_ONLY
        count = count.coerceIn(1, cap)
        val scroll = !watch && (scrollWord || back || count > 1 || auto || all)
        val d = (dwell ?: DEFAULT_DWELL_SEC).coerceIn(1, MAX_DWELL_SEC)
        return Plan(
            friend = friend, friendAsked = friendAsked, count = if (watch) 1 else count, scroll = scroll, auto = auto && !watch, dwellSec = d,
            like = like, save = save, comment = comment, shareTo = sharedTo, shareAsked = shareWord && sharedTo == null,
            back = back, watch = watch && friend != null, posts = hasPost && !hasReel
        )
    }

    /** The short comment used when the owner said "comment karo" without any words. Deterministic by [i]. */
    fun defaultComment(en: Boolean, i: Int): String {
        val l = if (en) DEFAULT_COMMENTS_EN else DEFAULT_COMMENTS_HI
        return l[Math.floorMod(i, l.size)]
    }

    /** What one run will really do: comment count after the cap. */
    fun commentsIn(p: Plan): Int = if (p.comment == null) 0 else minOf(p.count, COMMENT_CAP)
    fun sharesIn(p: Plan): Int = if (p.shareTo == null) 0 else minOf(p.count, SHARE_CAP)

    // ------------------------------------------------------------------ saying it

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    fun describe(p: Plan, en: Boolean): String {
        val what = ArrayList<String>()
        if (p.like) what.add(t(en, "like", "like"))
        if (p.save) what.add(t(en, "save", "save"))
        if (p.comment != null) what.add(if (p.comment.isEmpty()) t(en, "comment (chhota mast sa)", "a short friendly comment") else t(en, "comment \"${p.comment}\"", "comment \"${p.comment}\""))
        if (p.shareTo != null) what.add(t(en, "${p.shareTo} ko share", "share to ${p.shareTo}"))
        val kind = if (p.posts) t(en, "post", "post") else t(en, "reel", "reel")
        val whoHi = if (p.friend != null) "${p.friend} ki " else ""
        val whoEn = if (p.friend != null) "${p.friend}'s " else ""
        return if (p.watch) t(en, "${p.friend} jaise hi naya post kare, main uspe ${what.joinToString(" aur ")} kar dunga", "When ${p.friend} posts something new I will ${what.joinToString(" and ")} it")
        else t(en, "$whoHi${p.count} $kind par ${what.joinToString(" aur ")} karunga", "I will ${what.joinToString(" and ")} ${p.count} of $whoEn${kind}s")
    }

    fun confirmQuestion(p: Plan, en: Boolean): String {
        val extra = ArrayList<String>()
        if (p.comment != null && p.count > COMMENT_CAP) extra.add(t(en, "comment sirf $COMMENT_CAP par", "comments on only $COMMENT_CAP"))
        if (p.shareTo != null && p.count > SHARE_CAP) extra.add(t(en, "share sirf $SHARE_CAP par", "shares on only $SHARE_CAP"))
        return describe(p, en) + (if (extra.isEmpty()) "" else " (" + extra.joinToString(", ") + ")") + t(en, ". Karu? Haan ya nahi bolo.", ". Shall I? Say yes or no.")
    }

    fun needFriendText(en: Boolean): String = t(en,
        "Dost ka Instagram username bolo, jaise: nova rahul_sharma ki reels par like karo. Underscore ho to bolo \"underscore\".",
        "Tell me the friend's Instagram username, e.g. nova rahul_sharma's reels like. Say \"underscore\" for _.")
    fun needShareNameText(en: Boolean): String = t(en,
        "Kisko share karna hai? Naam bolo, jaise: share karo Rahul ko. Bina naam ke main kuch nahi bhejta.",
        "Who should I share it with? Say a name, e.g. share to Rahul. I never send anything without a name.")
    fun noAccText(en: Boolean): String = t(en,
        "Reel par kaam karne ke liye Accessibility ON honi chahiye. Settings mein NOVA ko Accessibility do.",
        "I need Accessibility ON to work on reels. Allow it for NOVA in Settings.")
    fun notVideoAppText(en: Boolean): String = t(en,
        "Pehle Instagram (ya YouTube / Facebook) ki reel khol do, phir bolo.",
        "Open a reel in Instagram (or YouTube / Facebook) first, then say it again.")
    fun lockedText(en: Boolean): String = t(en, "Phone lock hai, isliye reel par kaam nahi kar sakta.", "The phone is locked, so I cannot work on reels.")
    fun busyText(en: Boolean): String = t(en, "Reel ka kaam pehle se chal raha hai. STOP bolo ya dabao.", "A reel job is already running. Say or press STOP.")
    fun stoppedText(en: Boolean): String = t(en, "Reel ka kaam ruk gaya.", "The reel job stopped.")
    fun startedScrollText(p: Plan, en: Boolean): String =
        if (p.auto) t(en, "Reel scroll shuru. Rokne ke liye STOP bolo.", "Scrolling reels. Say STOP to end.")
        else if (p.back) t(en, "Pichli reel par ja raha hu.", "Going to the previous reel.")
        else if (p.count <= 1) t(en, "Agli reel.", "Next reel.")
        else t(en, "${p.count} reel scroll karta hu, har reel par ${p.dwellSec} second ruk kar.", "Scrolling ${p.count} reels, ${p.dwellSec} s on each.")
    fun startedText(p: Plan, en: Boolean): String = t(en, "Theek hai: ", "OK: ") + describe(p, en) + t(en, ". Rokne ke liye STOP bolo.", ". Say STOP to end.")
    fun watchSavedText(p: Plan, en: Boolean): String = t(en,
        "Theek hai. ${p.friend} ka naya post aate hi (Instagram notification se) main ${watchActs(p, en)} kar dunga. Notification access aur Accessibility ON rakhna. Band karne ke liye bolo: watch band karo.",
        "OK. When ${p.friend} posts (seen in the Instagram notification) I will ${watchActs(p, en)}. Keep Notification access and Accessibility ON. Say: stop watching.")
    private fun watchActs(p: Plan, en: Boolean): String {
        val l = ArrayList<String>()
        if (p.like) l.add("like"); if (p.save) l.add("save")
        if (p.comment != null) l.add(t(en, "comment", "comment"))
        if (p.shareTo != null) l.add(t(en, "${p.shareTo} ko share", "share to ${p.shareTo}"))
        return l.joinToString(if (en) " and " else " aur ")
    }

    /** "watch band karo" / "stop watching": clears the friend-post watch. */
    fun isWatchOff(text: String?): Boolean {
        val n = norm(text)
        return (n.contains("watch") && (n.contains("band") || n.contains("off") || n.contains("stop") || n.contains("hatao"))) ||
            (n.contains("post") && n.contains("watch") ) || n.contains("auto like band") || n.contains("friend watch band")
    }

    /** An Instagram notification that says [friend] posted a new photo / reel / video. Likes, comments, follows, stories and messages are not posts. */
    fun isFriendPost(friend: String?, title: String?, body: String?): Boolean {
        val f = (friend ?: "").lowercase().trim()
        if (f.length < 2) return false
        val s = ((title ?: "") + " " + (body ?: "")).lowercase()
        if (!(s.contains(f) || s.contains(f.replace('_', ' ').replace('.', ' ')))) return false
        val no = listOf("liked", "commented", "started following", "mentioned", "tagged you", "requested to follow", "replied", "sent you", "story", "stories", "went live", "is live", "birthday", "suggested")
        if (no.any { s.contains(it) }) return false
        val yes = listOf("posted", "shared a", "shared their", "added a", "uploaded", "new post", "new reel", "new photo", "new video")
        return yes.any { s.contains(it) }
    }

    /** The comment to use for the i-th reel, null = no comment. */
    fun commentFor(p: Plan, en: Boolean, i: Int): String? {
        val c = p.comment ?: return null
        if (i >= COMMENT_CAP) return null
        return if (c.isEmpty()) defaultComment(en, i) else c
    }

    /** Result sentence of a finished run. [done] counts per action. */
    fun resultText(items: Int, likes: Int, saves: Int, comments: Int, shares: Int, p: Plan, stopped: Boolean, reason: String, en: Boolean): String {
        val parts = ArrayList<String>()
        if (p.like) parts.add(t(en, "$likes like", "$likes liked"))
        if (p.save) parts.add(t(en, "$saves save", "$saves saved"))
        if (p.comment != null) parts.add(t(en, "$comments comment", "$comments commented"))
        if (p.shareTo != null) parts.add(t(en, "$shares share", "$shares shared"))
        val head = if (stopped) t(en, "Ruk gaya. ", "Stopped. ") else t(en, "Ho gaya. ", "Done. ")
        val body = t(en, "$items reel dekhi", "Went through $items") + (if (parts.isEmpty()) "" else ": " + parts.joinToString(", "))
        return head + body + (if (reason.isEmpty()) "." else ". " + reason)
    }
}
