package com.nova.assistant

import java.net.URLEncoder

/**
 * v6.97 wip14 (Part 1): pure text rules for
 *  - "<site> chrome pe kholo", "youtube, google aur flipkart kholo" (several websites, one Chrome tab each),
 *  - "<app> download karo" (Play Store).
 * No Android classes: unit tested. Nothing here opens anything; NovaService does that and reports the real result.
 *
 * Ownership rule: a sentence that belongs to another handler (play / search / message / call / alarm / negation ...) returns null,
 * and a plain single "youtube kholo" also returns null, so the existing "open the installed app" path stays exactly as it was.
 */
object OpenWebRules {
    /** [web] = open as a website in Chrome. false = installed app first (a known website only when no such app exists). */
    data class Target(val name: String, val label: String, val url: String?, val web: Boolean)
    data class OpenReq(val targets: List<Target>, val chrome: Boolean, val truncated: Boolean)
    /** [pkg] = known Play Store package (details page). null = unknown app: only the Play Store search can be opened. [rest] = more apps named in the same sentence. */
    data class DownloadReq(val name: String, val label: String, val pkg: String?, val rest: List<String>)

    const val MAX_TARGETS = 8

    private class Site(val label: String, val url: String, val webOnly: Boolean = false)

    private val SITES: Map<String, Site> = linkedMapOf(
        "youtube" to Site("YouTube", "https://www.youtube.com"),
        "google" to Site("Google", "https://www.google.com"),
        "gmail" to Site("Gmail", "https://mail.google.com"),
        "google maps" to Site("Google Maps", "https://maps.google.com"),
        "maps" to Site("Google Maps", "https://maps.google.com"),
        "google drive" to Site("Google Drive", "https://drive.google.com"),
        "drive" to Site("Google Drive", "https://drive.google.com"),
        "google meet" to Site("Google Meet", "https://meet.google.com"),
        "google translate" to Site("Google Translate", "https://translate.google.com"),
        "google news" to Site("Google News", "https://news.google.com"),
        "facebook" to Site("Facebook", "https://www.facebook.com"),
        "instagram" to Site("Instagram", "https://www.instagram.com"),
        "twitter" to Site("Twitter", "https://x.com"),
        "linkedin" to Site("LinkedIn", "https://www.linkedin.com"),
        "github" to Site("GitHub", "https://github.com"),
        "amazon" to Site("Amazon", "https://www.amazon.in"),
        "flipkart" to Site("Flipkart", "https://www.flipkart.com"),
        "myntra" to Site("Myntra", "https://www.myntra.com"),
        "meesho" to Site("Meesho", "https://www.meesho.com"),
        "snapdeal" to Site("Snapdeal", "https://www.snapdeal.com"),
        "ajio" to Site("Ajio", "https://www.ajio.com"),
        "nykaa" to Site("Nykaa", "https://www.nykaa.com"),
        "swiggy" to Site("Swiggy", "https://www.swiggy.com"),
        "zomato" to Site("Zomato", "https://www.zomato.com"),
        "netflix" to Site("Netflix", "https://www.netflix.com"),
        "hotstar" to Site("Hotstar", "https://www.hotstar.com"),
        "prime video" to Site("Prime Video", "https://www.primevideo.com"),
        "spotify" to Site("Spotify", "https://open.spotify.com"),
        "gaana" to Site("Gaana", "https://gaana.com"),
        "jiosaavn" to Site("JioSaavn", "https://www.jiosaavn.com"),
        "wikipedia" to Site("Wikipedia", "https://www.wikipedia.org"),
        "whatsapp web" to Site("WhatsApp Web", "https://web.whatsapp.com", webOnly = true),
        "telegram web" to Site("Telegram Web", "https://web.telegram.org", webOnly = true),
        "chatgpt" to Site("ChatGPT", "https://chatgpt.com"),
        "claude" to Site("Claude", "https://claude.ai"),
        "gemini" to Site("Gemini", "https://gemini.google.com"),
        "bing" to Site("Bing", "https://www.bing.com"),
        "duckduckgo" to Site("DuckDuckGo", "https://duckduckgo.com"),
        "yahoo" to Site("Yahoo", "https://www.yahoo.com"),
        "reddit" to Site("Reddit", "https://www.reddit.com"),
        "pinterest" to Site("Pinterest", "https://www.pinterest.com"),
        "quora" to Site("Quora", "https://www.quora.com"),
        "discord" to Site("Discord", "https://discord.com"),
        "canva" to Site("Canva", "https://www.canva.com"),
        "paytm" to Site("Paytm", "https://paytm.com"),
        "irctc" to Site("IRCTC", "https://www.irctc.co.in"),
        "redbus" to Site("redBus", "https://www.redbus.in"),
        "makemytrip" to Site("MakeMyTrip", "https://www.makemytrip.com"),
        "bookmyshow" to Site("BookMyShow", "https://in.bookmyshow.com"),
        "olx" to Site("OLX", "https://www.olx.in"),
        "naukri" to Site("Naukri", "https://www.naukri.com"),
        "indeed" to Site("Indeed", "https://in.indeed.com"),
        "cricbuzz" to Site("Cricbuzz", "https://www.cricbuzz.com"),
        "espncricinfo" to Site("ESPNcricinfo", "https://www.espncricinfo.com"),
        "ndtv" to Site("NDTV", "https://www.ndtv.com"),
        "times of india" to Site("Times of India", "https://timesofindia.indiatimes.com"),
        "hindustan times" to Site("Hindustan Times", "https://www.hindustantimes.com"),
        "aaj tak" to Site("Aaj Tak", "https://www.aajtak.in"),
        "dainik bhaskar" to Site("Dainik Bhaskar", "https://www.bhaskar.com"),
        "lovable" to Site("Lovable", "https://lovable.dev"),
        "vercel" to Site("Vercel", "https://vercel.com"),
        "netlify" to Site("Netlify", "https://www.netlify.com"),
        "supabase" to Site("Supabase", "https://supabase.com"),
        "notion" to Site("Notion", "https://www.notion.so"),
        "play store" to Site("Play Store", "https://play.google.com/store")
    )

    /** Known Play Store packages (display label to package). Anything else only gets the Play Store search page. */
    private val PACKAGES: Map<String, Pair<String, String>> = linkedMapOf(
        "whatsapp" to Pair("WhatsApp", "com.whatsapp"),
        "whatsappbusiness" to Pair("WhatsApp Business", "com.whatsapp.w4b"),
        "instagram" to Pair("Instagram", "com.instagram.android"),
        "insta" to Pair("Instagram", "com.instagram.android"),
        "facebook" to Pair("Facebook", "com.facebook.katana"),
        "fb" to Pair("Facebook", "com.facebook.katana"),
        "messenger" to Pair("Messenger", "com.facebook.orca"),
        "telegram" to Pair("Telegram", "org.telegram.messenger"),
        "youtube" to Pair("YouTube", "com.google.android.youtube"),
        "yt" to Pair("YouTube", "com.google.android.youtube"),
        "spotify" to Pair("Spotify", "com.spotify.music"),
        "snapchat" to Pair("Snapchat", "com.snapchat.android"),
        "linkedin" to Pair("LinkedIn", "com.linkedin.android"),
        "netflix" to Pair("Netflix", "com.netflix.mediaclient"),
        "amazon" to Pair("Amazon", "com.amazon.mShop.android.shopping"),
        "flipkart" to Pair("Flipkart", "com.flipkart.android"),
        "myntra" to Pair("Myntra", "com.myntra.android"),
        "meesho" to Pair("Meesho", "com.meesho.supply"),
        "swiggy" to Pair("Swiggy", "in.swiggy.android"),
        "zomato" to Pair("Zomato", "com.application.zomato"),
        "paytm" to Pair("Paytm", "net.one97.paytm"),
        "phonepe" to Pair("PhonePe", "com.phonepe.app"),
        "googlepay" to Pair("Google Pay", "com.google.android.apps.nbu.paisa.user"),
        "gpay" to Pair("Google Pay", "com.google.android.apps.nbu.paisa.user"),
        "chrome" to Pair("Chrome", "com.android.chrome"),
        "gmail" to Pair("Gmail", "com.google.android.gm"),
        "googlemaps" to Pair("Google Maps", "com.google.android.apps.maps"),
        "maps" to Pair("Google Maps", "com.google.android.apps.maps"),
        "googledrive" to Pair("Google Drive", "com.google.android.apps.docs"),
        "googlemeet" to Pair("Google Meet", "com.google.android.apps.tachyon"),
        "truecaller" to Pair("Truecaller", "com.truecaller"),
        "mxplayer" to Pair("MX Player", "com.mxtech.videoplayer.ad"),
        "uber" to Pair("Uber", "com.ubercab"),
        "rapido" to Pair("Rapido", "com.rapido.passenger"),
        "canva" to Pair("Canva", "com.canva.editor"),
        "discord" to Pair("Discord", "com.discord"),
        "zoom" to Pair("Zoom", "us.zoom.videomeetings"),
        "pinterest" to Pair("Pinterest", "com.pinterest"),
        "reddit" to Pair("Reddit", "com.reddit.frontpage"),
        "chatgpt" to Pair("ChatGPT", "com.openai.chatgpt"),
        "gemini" to Pair("Gemini", "com.google.android.apps.bard"),
        "claude" to Pair("Claude", "com.anthropic.claude"),
        "primevideo" to Pair("Prime Video", "com.amazon.avod.thirdpartyclient"),
        "microsoftteams" to Pair("Microsoft Teams", "com.microsoft.teams"),
        "teams" to Pair("Microsoft Teams", "com.microsoft.teams"),
        // v7.06: more known apps (exact Play title = label, so the page check still refuses lookalikes). A wrong package can only fail safe.
        "googlephotos" to Pair("Google Photos", "com.google.android.apps.photos"),
        "photos" to Pair("Google Photos", "com.google.android.apps.photos"),
        "googletranslate" to Pair("Google Translate", "com.google.android.apps.translate"),
        "translate" to Pair("Google Translate", "com.google.android.apps.translate"),
        "googledocs" to Pair("Google Docs", "com.google.android.apps.docs.editors.docs"),
        "googlesheets" to Pair("Google Sheets", "com.google.android.apps.docs.editors.sheets"),
        "googleslides" to Pair("Google Slides", "com.google.android.apps.docs.editors.slides"),
        "googlecalendar" to Pair("Google Calendar", "com.google.android.calendar"),
        "calendar" to Pair("Google Calendar", "com.google.android.calendar"),
        "googlelens" to Pair("Google Lens", "com.google.ar.lens"),
        "lens" to Pair("Google Lens", "com.google.ar.lens"),
        "filesbygoogle" to Pair("Files by Google", "com.google.android.apps.nbu.files"),
        "youtubemusic" to Pair("YouTube Music", "com.google.android.apps.youtube.music"),
        "snapseed" to Pair("Snapseed", "com.niksoftware.snapseed"),
        "capcut" to Pair("CapCut", "com.lemon.lvoverseas"),
        "vlc" to Pair("VLC for Android", "org.videolan.vlc"),
        "onedrive" to Pair("Microsoft OneDrive", "com.microsoft.skydrive"),
        "candycrush" to Pair("Candy Crush Saga", "com.king.candycrushsaga"),
        "candycrushsaga" to Pair("Candy Crush Saga", "com.king.candycrushsaga"),
        "subwaysurfers" to Pair("Subway Surfers", "com.kiloo.subwaysurf"),
        "templerun2" to Pair("Temple Run 2", "com.imangi.templerun2"),
        "clashofclans" to Pair("Clash of Clans", "com.supercell.clashofclans"),
        "clashroyale" to Pair("Clash Royale", "com.supercell.clashroyale"),
        "brawlstars" to Pair("Brawl Stars", "com.supercell.brawlstars"),
        "roblox" to Pair("Roblox", "com.roblox.client"),
        "amongus" to Pair("Among Us", "com.innersloth.spacemafia"),
        "hillclimbracing" to Pair("Hill Climb Racing", "com.fingersoft.hillclimb"),
        "hillclimbracing2" to Pair("Hill Climb Racing 2", "com.fingersoft.hcr2")
    )

    /** v6.99 Part 3: every spelling speech-to-text gives for download / install (Roman + what HindiRoman makes of the Devanagari words: daunlod, instol). */
    private val DL_SPELLINGS = setOf(
        "download", "dawnload", "downlod", "daunload", "daunlod", "dawnlod", "downlode", "dounload", "dounlod", "donload", "downlaod",
        "dwnload", "dawnlode", "daunlode", "downloda", "dowanload", "dowanlod", "dawanlod", "dawanload", "dounlode",
        "install", "instal", "instol", "installl", "inestall", "insatll", "instaal", "instaall", "intall", "inastall", "insatal", "instl", "instoll"
    )

    private val OPEN_VERBS = setOf("open", "kholo", "khol", "kholna", "khulo", "launch", "kholiye", "kholiyega")
    private val NEGATION = setOf("mat", "nahi", "nahin", "nhi", "dont", "dont", "not", "never", "bina")
    /** Words that mean another handler owns the sentence (music, search, messages, calls, alarms, settings ...). */
    private val BLOCK = setOf(
        "play", "chalao", "search", "dhundo", "dhoondo", "bhejo", "bhej", "send", "message", "msg", "sms", "call", "dial", "phone",
        "download", "install", "uninstall", "alarm", "timer", "remind", "yaad", "record", "recording", "settings", "setting",
        "wifi", "bluetooth", "torch", "flashlight", "camera", "volume", "brightness", "lock", "screenshot", "gaana", "song", "music",
        "banao", "bana", "likho", "write", "create", "make", "delete", "hatao", "contact", "number", "email", "mail"
    ).plus(DL_SPELLINGS)
    private val SITE_WORDS = setOf("website", "websites", "site", "sites", "web", "tab", "tabs", "link", "links", "webpage", "page")
    private val APP_WORDS = setOf("app", "apps", "application")
    private val POST = setOf("in", "on", "me", "mein", "men", "mai", "ma", "par", "pe", "pr", "se", "ko", "ka", "ki", "ke", "to")
    private val FILLER = setOf(
        "please", "zara", "jara", "abhi", "ab", "karo", "kro", "kar", "do", "de", "dena", "dijiye", "kijiye", "karna", "karde", "kardo",
        "ek", "sath", "saath", "sabhi", "sab", "alag", "ye", "yeh", "ek-ek", "sare", "saare", "all", "separate", "different", "new", "naya", "nayi", "nai",
        "the", "a", "my", "mera", "meri", "mere", "turant", "jaldi", "sir", "nova", "hey", "hi"
    )
    private val CONNECT = setOf("aur", "and", "phir", "fir", "then", "tatha", "or", "evam")

    private val DL_VERBS = DL_SPELLINGS
    private val DL_BLOCK = setOf(
        "play", "gaana", "song", "music", "video", "movie", "film", "photo", "image", "pic", "file", "pdf", "zip", "apk",
        "update", "upgrade", "nova", "model", "offline", "pack", "vosk", "report", "status", "reel", "reels", "document",
        "notes", "data", "dp", "wallpaper", "ringtone", "backup", "uninstall", "chat", "message", "msg", "bhejo", "send",
        "call", "open", "kholo", "khol", "mat", "nahi", "nahin", "nhi", "dont", "not", "never", "bina"
    )
    private val DL_FILLER = FILLER + setOf("phone", "mobile", "wala", "wali", "latest", "app", "apps", "application", "mujhe", "chahiye", "hai")

    // ------------------------------------------------------------------ normalising

    private fun norm(raw: String): String {
        // HindiRoman turns every punctuation mark (also the dot of "flipkart.com") into a space: protect the dots inside web addresses.
        val guarded = raw.replace(Regex("(?<=[A-Za-z0-9])\\.(?=[A-Za-z0-9])"), "qdotq")
        var s = HindiRoman.toRoman(guarded).lowercase().replace("qdotq", ".").replace("'", "").replace("’", "")
        s = s.replace(Regex("(?<=[a-z0-9]) dot (?=[a-z]{2,12}\\b)"), ".")   // spoken "flipkart dot com"
        s = s.replace(Regex("[,;]"), " aur ")
        s = s.replace(Regex("[^a-z0-9. ]"), " ")
        s = s.replace(Regex("\\.+(?=\\s|$)"), " ").replace(Regex("(?<=\\s|^)\\.+"), " ")
        s = s.replace(Regex("\\s+"), " ").trim()
        // speech-to-text splits and spellings
        s = s.replace("you tube", "youtube").replace("face book", "facebook").replace("insta gram", "instagram")
            .replace("whats app", "whatsapp").replace("g mail", "gmail").replace("flip kart", "flipkart")
            .replace("chat gpt", "chatgpt").replace("web site", "website").replace("play store", "playstore")
        s = s.replace(Regex("\\b(?:crome|chorme|krome|chrom)\\b"), "chrome")
        s = s.replace(Regex("\\bdown +lo+a?d\\b"), "download")   // v6.99: "down load" / "down lod" (two words) -> one word
        return s
    }

    private fun words(s: String): List<String> = s.split(" ").filter { it.isNotEmpty() }

    private val DOMAIN = Regex("^[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.[a-z]{2,12}$")
    private fun isDomain(t: String) = DOMAIN.matches(t)

    private fun site(name: String): Site? = SITES[name] ?: SITES.entries.firstOrNull { it.key.replace(" ", "") == name.replace(" ", "") }?.value

    private fun known(name: String): Boolean = site(name) != null

    private fun titleCase(s: String) = s.split(" ").filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    // ------------------------------------------------------------------ websites

    /** Web address for a spoken name. [guess] = also accept an unknown one-word name (https://www.<word>.com) or a multi-word name (Google search). */
    fun siteUrl(name: String, guess: Boolean): String? {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return null
        if (isDomain(n)) return "https://$n"
        site(n)?.let { return it.url }
        if (!guess) return null
        val parts = words(n)
        if (parts.size == 1 && parts[0].length >= 2 && parts[0].all { it.isLetterOrDigit() }) return "https://www.${parts[0]}.com"
        if (parts.isNotEmpty() && parts.size <= 4) return "https://www.google.com/search?q=" + URLEncoder.encode(n, "UTF-8")
        return null
    }

    fun labelFor(name: String): String {
        val n = name.trim().lowercase()
        return site(n)?.label ?: if (isDomain(n)) n else titleCase(n)
    }

    private fun segment(t: List<String>): List<String> {
        val out = ArrayList<String>()
        val unk = ArrayList<String>()
        fun flush() { if (unk.isNotEmpty()) { out.add(unk.joinToString(" ")); unk.clear() } }
        var i = 0
        while (i < t.size) {
            var hit = 0
            for (n in minOf(3, t.size - i) downTo 1) {
                if (known(t.subList(i, i + n).joinToString(" "))) { hit = n; break }
            }
            if (hit > 0) { flush(); out.add(t.subList(i, i + hit).joinToString(" ")); i += hit }
            else if (isDomain(t[i])) { flush(); out.add(t[i]); i++ }
            else { unk.add(t[i]); i++ }
        }
        flush()
        return out
    }

    /**
     * "youtube chrome pe kholo", "youtube google aur flipkart kholo", "flipkart.com kholo", "whatsapp web kholo".
     * null = not ours (single plain "youtube kholo" stays the normal open-app command; "chrome kholo" / "google chrome kholo" open the Chrome app).
     */
    fun parseOpen(raw: String): OpenReq? {
        if (raw.isBlank() || raw.length > 300) return null
        val s = Logic.stripWake(norm(raw)).replace(Regex("^(?:please|zara|jara|abhi|ab|ek baar) +"), "").trim()
        val w = words(s)
        if (w.isEmpty() || w.size > 30) return null
        if (w.any { it in NEGATION || it in BLOCK }) return null
        if (w.none { it in OPEN_VERBS }) return null
        var chrome = false
        var siteWord = false
        var appWord = false
        val kept = ArrayList<String>()
        for (x in w) {
            when {
                x == "chrome" || x == "browser" -> {
                    chrome = true
                    if (kept.size == 1 && kept[0] == "google") kept.clear()   // "google chrome" is the browser's name, not the site
                }
                x == "web" && (kept.lastOrNull() == "whatsapp" || kept.lastOrNull() == "telegram") -> kept.add(x)   // "whatsapp web" is one site name
                x in SITE_WORDS -> siteWord = true
                x in APP_WORDS -> appWord = true
                x in OPEN_VERBS || x in FILLER || x in POST -> {}
                x in CONNECT -> if (kept.isNotEmpty() && kept.last() != "|") kept.add("|")
                else -> kept.add(x)
            }
        }
        while (kept.isNotEmpty() && kept.last() == "|") kept.removeAt(kept.size - 1)
        if (kept.isEmpty()) return null
        if (appWord && !chrome && !siteWord) return null   // "youtube app kholo": the owner asked for the app
        val chunks = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        for (x in kept) { if (x == "|") { if (cur.isNotEmpty()) chunks.add(cur); cur = ArrayList() } else cur.add(x) }
        if (cur.isNotEmpty()) chunks.add(cur)
        val names = LinkedHashSet<String>()
        for (c in chunks) for (n in segment(c)) names.add(n)
        if (names.isEmpty()) return null
        val webMode = chrome || siteWord
        val targets = ArrayList<Target>()
        for (n in names) {
            if (n.length > 60 || words(n).size > 4) return null
            val st = site(n)
            val domain = isDomain(n)
            val web = webMode || domain || (st?.webOnly == true)
            val url = if (web) siteUrl(n, guess = true) else siteUrl(n, guess = false)
            targets.add(Target(n, labelFor(n), url, web))
        }
        if (!webMode && targets.size == 1 && !targets[0].web) return null
        val truncated = targets.size > MAX_TARGETS
        return OpenReq(targets.take(MAX_TARGETS), webMode, truncated)
    }

    // ------------------------------------------------------------------ Play Store download

    fun packageFor(name: String): Pair<String, String>? = PACKAGES[name.trim().lowercase().replace(" ", "")]

    /** "whatsapp download karo", "nova instagram download kar do play store se", "install karo telegram". */
    fun parseDownload(raw: String): DownloadReq? {
        if (raw.isBlank() || raw.length > 300) return null
        val s = Logic.stripWake(norm(raw)).replace(Regex("^(?:please|zara|jara|abhi|ab) +"), "")
            .replace("google playstore", " ").replace("playstore", " ").replace("play market", " ").trim()
        val w = words(s)
        if (w.isEmpty() || w.size > 20) return null
        if (w.any { it in DL_BLOCK }) return null
        if (w.none { it in DL_VERBS }) return null
        val kept = ArrayList<String>()
        for (x in w) {
            when {
                x in DL_VERBS || x in DL_FILLER || x in POST -> {}
                x in CONNECT -> if (kept.isNotEmpty() && kept.last() != "|") kept.add("|")
                else -> kept.add(x)
            }
        }
        while (kept.isNotEmpty() && kept.last() == "|") kept.removeAt(kept.size - 1)
        if (kept.isEmpty()) return null
        val names = ArrayList<String>()
        var cur = ArrayList<String>()
        for (x in kept) { if (x == "|") { if (cur.isNotEmpty()) names.add(cur.joinToString(" ")); cur = ArrayList() } else cur.add(x) }
        if (cur.isNotEmpty()) names.add(cur.joinToString(" "))
        if (names.isEmpty() || names.size > 6) return null
        if (names.any { it.length > 40 || words(it).size > 4 || it.length < 2 }) return null
        val first = names[0]
        val pk = packageFor(first)
        return DownloadReq(first, pk?.first ?: titleCase(first), pk?.second, names.drop(1).map { packageFor(it)?.first ?: titleCase(it) })
    }
}
