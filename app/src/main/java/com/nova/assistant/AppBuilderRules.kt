package com.nova.assistant

/**
 * v7.177 APP BUILDER - pure rules (no Android imports, JUnit-able).
 *
 * "jarvis ek chhath calendar app banao": JARVIS (1) PLANS what a complete app on that topic must cover, (2) does DEEP WEB RESEARCH
 * (one search query per section + reads the top pages), (3) turns it into a numbered FACT SHEET where every fact is pinned to a source
 * and any fact whose numbers are not found in its cited sources is thrown away, (4) writes the app (one HTML app inside a fixed,
 * known-good Android WebView shell), (5) checks the result (years / phone numbers / e-mails / links that are not in the fact sheet are
 * flagged and repaired once), (6) pushes the project to the owner's own GitHub, builds the APK with GitHub Actions and (7) offers the install.
 *
 * Honesty rules baked in here: nothing is invented to fill a gap (a gap is shown as a gap), a topic with too few verified facts is NOT built,
 * and the app carries its own SOURCES screen + the date the facts were read.
 *
 * Voice grammar (narrow, normal chat is never captured):
 *   "jarvis chhath calendar app banao"   "jarvis mummy ke liye medicine reminder app bana do"   "jarvis app builder kholo"
 *   "jarvis app banana band karo"        "jarvis banayi hui app install karo"
 */
object AppBuilderRules {
    const val K_APP_BUILD = "app_build"          // arg = the idea, as spoken
    const val K_APP_STOP = "app_stop"
    const val K_APP_SCREEN = "app_screen"
    const val K_APP_INSTALL = "app_install"
    val KINDS = setOf(K_APP_BUILD, K_APP_STOP, K_APP_SCREEN, K_APP_INSTALL)

    const val MAX_IDEA = 600
    const val MAX_SECTIONS = 8
    const val MIN_FACTS = 6          // fewer verified facts than this on a fact topic = NOT built
    const val MIN_SOURCES = 2
    const val MAX_SRC = 18
    const val PAGE_CHARS = 3500
    const val MAX_PAGES = 6
    const val MAX_NAME = 30

    // ---------------------------------------------------------------- voice grammar

    private const val L = "(?<![\\p{L}])"
    private const val R = "(?![\\p{L}])"
    private val APP_WORD = Regex(L + "(?:app|apps|application|apk|ऐप|एप)" + R)
    private val MAKE_WORD = Regex(L + "(?:bana|banao|banaao|banado|banade|bnao|banana|banane|banani|banakar|banaiye|banaiyega|build|create|make|develop|generate|taiyaar|tayyar|बनाओ|बना|बनाना|बनाइए)" + R)
    private val SITE_WORD = Regex(L + "(?:website|web site|webpage|landing page|portfolio site)" + R)
    private val NOT_Q = Regex(L + "(?:kaise|kese|how to|how do|kyun|kyu|why|kya|sakte|sakta|sakti|can you|could you|can u)" + R)
    private val OWN_APP = Regex(L + "(?:jarvis|apni|iss|is|isi|yahi|ye|yeh|this)\\s+app" + R)
    private val OTHER_JOB = Regex(L + "(?:play store|playstore|download|install|kholo|open)" + R)
    private val OPEN_WORD = Regex(L + "(?:kholo|khol do|khol de|open|dikhao|dikha do|show)" + R)
    private val STOP_WORD = Regex(L + "(?:band|stop|ruko|ruk|rok|cancel|mat)" + R)
    private val MADE_WORD = Regex(L + "(?:banayi|banai|bani|banaya|banaye|banayee|ready)" + R)
    private val INSTALL_WORD = Regex(L + "(?:install|lagao|laga do|laga de|download)" + R)
    private val BUILDING = Regex(L + "(?:bana|banana|banane|build|building)" + R)

    fun parse(text: String): Logic.Cmd? {
        if (text.isBlank() || text.length > 900) return null
        val low = text.lowercase().replace(Regex("\\s+"), " ").trim()
        val builderWord = Regex("app\\s*builder").containsMatchIn(low)

        // screen ("app builder kholo", "terminal kholo")
        if ((builderWord || Regex(L + "terminal" + R).containsMatchIn(low) ||
                Regex("app\\s*(?:banane|banana)\\s*(?:wali|wala)\\s*(?:screen|page)").containsMatchIn(low)) &&
            OPEN_WORD.containsMatchIn(low) && !STOP_WORD.containsMatchIn(low)) return Logic.Cmd(K_APP_SCREEN)

        // stop (only the build; never switches JARVIS off)
        if (STOP_WORD.containsMatchIn(low) && (builderWord || (APP_WORD.containsMatchIn(low) && BUILDING.containsMatchIn(low))))
            return Logic.Cmd(K_APP_STOP)

        // install the app JARVIS already built ("banayi hui app install karo")
        if (APP_WORD.containsMatchIn(low) && INSTALL_WORD.containsMatchIn(low) && MADE_WORD.containsMatchIn(low) && !MAKE_WORD.containsMatchIn(low))
            return Logic.Cmd(K_APP_INSTALL)

        // build
        if (!APP_WORD.containsMatchIn(low) || !MAKE_WORD.containsMatchIn(low)) return null
        if (SITE_WORD.containsMatchIn(low) || NOT_Q.containsMatchIn(low) || OWN_APP.containsMatchIn(low) || OTHER_JOB.containsMatchIn(low)) return null
        return Logic.Cmd(K_APP_BUILD, ideaOf(text))
    }

    /** The idea as spoken, without the wake word, one line, capped. */
    fun ideaOf(text: String): String {
        var s = text.trim().replace(Regex("\\s+"), " ")
        s = s.replace(Regex("^(?:hey\\s+|ok\\s+|okay\\s+)?jarvis[\\s,.:;-]*", RegexOption.IGNORE_CASE), "")
        return s.trim().take(MAX_IDEA)
    }

    private val NAME_STOP = setOf(
        "jarvis", "hey", "mere", "meri", "mera", "mujhe", "mujhko", "hame", "humein", "ek", "ka", "ki", "ke", "ko", "liye", "ye", "wali", "wala", "wale",
        "nayi", "naya", "achhi", "achha", "acha", "accha", "sundar", "best", "a", "an", "the", "my", "for", "me", "please", "zara", "ab", "jo", "aisi", "aisa",
        "can", "you", "could", "build", "create", "make", "develop", "generate", "new", "simple", "bana", "banao", "bana", "do", "de", "karo", "kar", "tum", "aap", "ek",
        "small", "good", "mobile", "android", "ek", "kya", "se", "me", "mein", "par", "pe"
    )
    private val APP_TOKENS = setOf("app", "apps", "application", "apk", "ऐप", "एप")

    /** Short display name from the idea: "mummy ke liye chhath calendar app banao" -> "Chhath Calendar". "" when no name can be found. */
    fun appName(idea: String): String {
        val words = idea.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        val ai = words.indexOfFirst { it in APP_TOKENS }
        var head = if (ai >= 0) words.subList(0, ai) else words
        val liye = head.lastIndexOf("liye")
        if (liye >= 0) head = head.subList(liye + 1, head.size)
        head = head.filter { it !in NAME_STOP }.takeLast(4)
        if (head.isEmpty()) return ""
        val name = head.joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercaseChar() } }
        return cleanName(name)
    }

    /** Letters, digits, space and '-' only (safe inside the manifest), at most MAX_NAME chars. */
    fun cleanName(raw: String): String {
        val s = raw.replace(Regex("[^\\p{L}\\p{N} -]"), " ").replace(Regex("\\s+"), " ").trim()
        return s.take(MAX_NAME).trim()
    }

    /** ASCII slug for the package / file names. */
    fun slugOf(name: String): String {
        val sb = StringBuilder()
        for (ch in name.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else if (sb.isNotEmpty() && sb.last() != '_') sb.append('_')
        }
        var s = sb.toString().trim('_')
        if (s.length > 18) s = s.substring(0, 18).trim('_')
        return if (s.isEmpty()) "app" else s
    }

    /** Unique package id per build (a debug key differs between CI runs, so a reused id could never be updated). Segments always start with a letter. */
    fun packageName(name: String, stampMs: Long): String =
        "com.novaapps." + slugOf(name).let { if (it[0] in '0'..'9') "a$it" else it } + ".b" + ((stampMs / 1000L) % 1_000_000L)

    // ---------------------------------------------------------------- research plan

    class Section(val id: String, val name: String, val query: String)
    class Plan(val title: String, val needsFacts: Boolean, val sections: List<Section>, val features: List<String> = emptyList(), val style: String = "")

    const val PLAN_SYSTEM = "You plan web research for a mobile app. Reply ONLY with JSON, no explanation: " +
        "{\"title\":\"app name, 2 to 4 words\",\"needs_facts\":true,\"style\":\"look and feel in one line: accent colour, mood\",\"features\":[\"one concrete feature\"],\"sections\":[{\"id\":\"s1\",\"name\":\"what the app must cover\",\"query\":\"one specific web search query\"}]}. " +
        "features = 8 to 14 concrete things the finished app does so that a non-technical owner needs nothing more (every screen, every list with add / edit / delete / search, reminders or counters, settings with light and dark theme, share or copy buttons, empty states). " +
        "5 to 8 sections that together cover EVERYTHING a complete, accurate app on this topic needs: meanings, rules, steps, dates, numbers, names, prices, " +
        "official sources, common mistakes, safety notes, frequent questions. needs_facts is false for any tool that only manages the owner's OWN data and needs no outside facts (calculator, timer, notes, to-do, scheduler, appointment or booking reminder, tracker, diary, ledger, simple game); true only when the app must teach or state real-world facts (rules, dates, prices, meanings). " +
        "Each query is specific, 3 to 12 words, in the language that finds the best sources, and never contains personal data."

    private val PERSONAL_TOOL = Regex(L + "(?:schedul\\w*|shyduled|sheduled|reminder|remind|yaad|appointment|booking|bookings|tracker|track|to-?do|todo|diary|notes?|hisaab|hisab|ledger|calculator|timer|planner|organi[sz]er)" + R)
    private val FACT_TOPIC = Regex(L + "(?:rules?|niyam|vidhi|puja|pooja|recipe|guide|history|itihas|meaning|matlab|festival|tyohar|law|kanoon|syllabus|prices?|rates?)" + R)

    /** A personal organiser (scheduler / reminder / booking tracker ...) holds the owner's own data: it needs NO web facts, so the 6-facts gate must not block it. */
    fun isPersonalTool(idea: String): Boolean {
        val low = idea.lowercase()
        return PERSONAL_TOOL.containsMatchIn(low) && !FACT_TOPIC.containsMatchIn(low)
    }

    fun forcePersonal(plan: Plan, idea: String): Plan =
        if (plan.needsFacts && isPersonalTool(idea)) Plan(plan.title, false, plan.sections, plan.features, plan.style) else plan

    fun planPrompt(idea: String, today: String): String =
        "Today's date: $today\nOwner's request for the app: ${idea.take(MAX_IDEA)}\nPlan the research."

    /** Pure: the model's plan answer -> Plan, or null when it is not usable. */
    fun parsePlan(raw: String?): Plan? {
        val root = extractJson(raw) ?: return null
        val list = MiniJson.field(root, "sections") as? List<*> ?: return null
        val out = ArrayList<Section>()
        val ids = HashSet<String>()
        for (x in list) {
            val name = clip(MiniJson.field(x, "name"), 80)
            val q = clip(MiniJson.field(x, "query"), 120)
            if (name.isEmpty() || q.length < 3) continue
            var id = clip(MiniJson.field(x, "id"), 12).replace(Regex("[^A-Za-z0-9_]"), "")
            if (id.isEmpty() || !ids.add(id)) { id = "s" + (out.size + 1); if (!ids.add(id)) continue }
            out.add(Section(id, name, q))
            if (out.size >= MAX_SECTIONS) break
        }
        if (out.size < 3) return null
        val nf = MiniJson.field(root, "needs_facts")
        val title = cleanName(clip(MiniJson.field(root, "title"), 60))
        val feats = ((MiniJson.field(root, "features") as? List<*>) ?: emptyList<Any?>()).map { clip(it, 120) }.filter { it.length >= 4 }.take(14)
        return Plan(title, nf != false, out, feats, clip(MiniJson.field(root, "style"), 120))
    }

    /** When the planner AI is not available: seven generic sections around the topic (still real searches). */
    fun defaultPlan(idea: String): Plan {
        val topic = appName(idea).ifEmpty { idea.take(60).trim() }.ifEmpty { "topic" }
        val s = listOf(
            "Overview and meaning" to "$topic complete guide",
            "Details and rules" to "$topic rules details",
            "Steps and how to" to "$topic step by step",
            "Dates, numbers, prices" to "$topic dates timings prices",
            "Official or authoritative source" to "$topic official",
            "Common mistakes and safety" to "$topic common mistakes precautions",
            "Frequent questions" to "$topic FAQ"
        )
        return Plan(appName(idea), true, s.mapIndexed { i, p -> Section("s" + (i + 1), p.first, p.second) })
    }

    // ---------------------------------------------------------------- sources + fact sheet

    class Src(val n: Int, val title: String, val url: String, val snippet: String, val page: String = "") {
        fun text(): String = (title + "\n" + snippet + "\n" + page)
    }

    private fun canon(u: String): String = u.lowercase().substringBefore('#').removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    /** Merges several result lists (title, url, snippet) in round-robin order, one entry per address, numbered 1..n, at most MAX_SRC. */
    fun mergeSources(lists: List<List<Triple<String, String, String>>>): List<Src> {
        val out = ArrayList<Src>()
        val seen = HashSet<String>()
        var round = 0
        while (out.size < MAX_SRC) {
            var any = false
            for (l in lists) {
                if (round >= l.size) continue
                any = true
                val (t, u, sn) = l[round]
                if (!u.startsWith("https://") || !seen.add(canon(u))) continue
                out.add(Src(out.size + 1, t.replace(Regex("\\s+"), " ").trim().take(140), u, sn.replace(Regex("\\s+"), " ").trim().take(600)))
                if (out.size >= MAX_SRC) break
            }
            if (!any) break
            round++
        }
        return out
    }

    /** Page text: tags, scripts and styles removed, entities simplified, whitespace collapsed, capped. */
    fun pageText(html: String): String {
        var s = html.take(400_000)
        s = Regex("<(script|style|noscript|svg|head)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).replace(s, " ")
        s = Regex("<[^>]*>").replace(s, " ")
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        s = Regex("&#?[A-Za-z0-9]+;").replace(s, " ")
        return Regex("\\s+").replace(s, " ").trim().take(PAGE_CHARS)
    }

    const val FACTS_SYSTEM = "You extract VERIFIED facts for an app from numbered web sources. Reply ONLY with JSON, no explanation: " +
        "{\"sections\":[{\"id\":\"s1\",\"facts\":[{\"t\":\"one self-contained fact, numbers dates and names exactly as the source says\",\"src\":[1,3]}],\"gap\":\"what the sources did NOT give for this section, or empty\"}]}. " +
        "Rules: use ONLY what the sources literally say. Every fact needs the numbers of the sources that really contain it. If two sources disagree, write both values and cite both. " +
        "NEVER fill a gap from your own memory. No guessing, no rounding, no invented numbers, dates, names, addresses, phone numbers or prices. " +
        "If a fact depends on a date or year, write the date or year the source gives. Skip marketing talk. 3 to 10 facts per section when the sources allow; write the gap honestly when they do not."

    fun factsPrompt(idea: String, plan: Plan, srcs: List<Src>, today: String): String = buildString {
        append("Today's date: ").append(today).append("\nApp request: ").append(idea.take(MAX_IDEA)).append("\nSECTIONS:\n")
        for (s in plan.sections) append(s.id).append(" = ").append(s.name).append('\n')
        append("\nSOURCES:\n")
        for (s in srcs) {
            append('[').append(s.n).append("] ").append(s.title).append(" | ").append(s.url).append('\n')
            if (s.snippet.isNotEmpty()) append(s.snippet).append('\n')
            if (s.page.isNotEmpty()) append("PAGE: ").append(s.page).append('\n')
            append('\n')
        }
    }

    class Fact(val text: String, val src: List<Int>)
    class SecFacts(val id: String, val name: String, val facts: List<Fact>, val gap: String)
    class Sheet(val sections: List<SecFacts>, val dropped: Int) {
        val total: Int get() = sections.sumOf { it.facts.size }
        fun usedSources(): List<Int> = sections.flatMap { s -> s.facts.flatMap { it.src } }.distinct().sorted()
    }

    private val NUM = Regex("[0-9][0-9,.:/]*[0-9]")

    /** Every number of 2+ digits in [fact] must appear (commas ignored) in the text of at least one cited source. */
    fun numbersSupported(fact: String, cited: List<Src>): Boolean {
        val hay = cited.joinToString("\n") { it.text() }.replace(",", "")
        for (m in NUM.findAll(fact)) {
            val tok = m.value.replace(",", "").trimEnd('.', ':', '/')
            if (tok.length < 2) continue
            if (!hay.contains(tok)) return false
        }
        return true
    }

    /** Pure: the model's fact answer -> Sheet. Facts without a valid source, or with a number that no cited source contains, are dropped (counted in [Sheet.dropped]). */
    fun parseSheet(raw: String?, plan: Plan, srcs: List<Src>): Sheet? {
        val root = extractJson(raw) ?: return null
        val list = MiniJson.field(root, "sections") as? List<*> ?: return null
        val byId = HashMap<String, Any?>()
        for (x in list) { val id = clip(MiniJson.field(x, "id"), 12); if (id.isNotEmpty()) byId[id] = x }
        var dropped = 0
        val out = ArrayList<SecFacts>()
        for (sec in plan.sections) {
            val x = byId[sec.id]
            val facts = ArrayList<Fact>()
            val seen = HashSet<String>()
            val fl = MiniJson.field(x, "facts") as? List<*> ?: emptyList<Any?>()
            for (f in fl) {
                val t = clip(MiniJson.field(f, "t"), 400)
                val ids = ((MiniJson.field(f, "src") as? List<*>) ?: emptyList<Any?>()).mapNotNull { (it as? Double)?.toInt() }.filter { it in 1..srcs.size }.distinct()
                if (t.length < 8 || ids.isEmpty()) { dropped++; continue }
                if (!numbersSupported(t, ids.map { srcs[it - 1] })) { dropped++; continue }
                if (!seen.add(t.lowercase())) continue
                facts.add(Fact(t, ids))
            }
            out.add(SecFacts(sec.id, sec.name, facts, clip(MiniJson.field(x, "gap"), 300)))
        }
        return Sheet(out, dropped)
    }

    /** null = enough to build. Otherwise a short reason: "sources" or "facts". A pure tool (needs_facts false) needs no facts. */
    fun notEnough(plan: Plan, sheet: Sheet?, srcCount: Int): String? {
        if (!plan.needsFacts) return null
        if (srcCount < MIN_SOURCES) return "sources"
        if (sheet == null || sheet.total < MIN_FACTS) return "facts"
        return null
    }

    fun sheetText(sheet: Sheet, srcs: List<Src>): String = buildString {
        for (s in sheet.sections) {
            append('[').append(s.name).append("]\n")
            for (f in s.facts) append("- ").append(f.text).append(" [").append(f.src.joinToString(",")).append("]\n")
            if (s.gap.isNotEmpty() || s.facts.isEmpty()) append("GAP (not verified, do not fill): ").append(s.gap.ifEmpty { "no verified facts found for this section" }).append('\n')
            append('\n')
        }
        append("SOURCES:\n")
        for (n in sheet.usedSources()) { val s = srcs.getOrNull(n - 1) ?: continue; append('[').append(n).append("] ").append(s.title).append(" - ").append(s.url).append('\n') }
    }

    /** SOURCES.md pushed into the generated repo (transparency). */
    fun sourcesMd(name: String, today: String, sheet: Sheet?, srcs: List<Src>): String = buildString {
        append("# ").append(name).append(" - sources\n\nResearched by JARVIS on ").append(today).append(". Facts below are what the web pages said at that time; dates, prices and rules can change, so check the official source before relying on them.\n\n")
        if (sheet != null) { append("## Facts\n\n"); append(sheetText(sheet, srcs)) }
        else { append("## Sources\n\n"); for (s in srcs) append("- [").append(s.n).append("] ").append(s.title).append(" - ").append(s.url).append('\n') }
    }

    // ---------------------------------------------------------------- writing the app

    const val APP_SYSTEM = "You are a senior mobile product designer and front-end engineer. The owner is NOT technical, so the app must be 100% complete and beautiful on the first try. " +
        "Reply with ONE complete, self-contained HTML5 file and nothing else: start with <!DOCTYPE html>, end with </html>, CSS in <style>, JavaScript in an inline <script>. " +
        "It runs OFFLINE as the whole screen of an Android app (WebView). " +
        "MODERN UI (required): CSS variables for colour, radius and spacing (8px grid); one accent colour that suits the topic plus a soft gradient app bar; system-ui font stack with a clear type scale; " +
        "rounded cards (16 to 24px) with soft shadows; bottom tab bar with inline SVG icons and an active pill when there are 2 or more screens; sticky top bar; bottom sheets or modals for add / edit; " +
        "toasts for feedback; friendly empty states with a small inline SVG picture; smooth 200ms transitions, :active press feedback, no layout jumps; " +
        "light AND dark theme (prefers-color-scheme plus a saved toggle in Settings); meta viewport and theme-color; safe-area padding; touch targets of at least 44px; no horizontal scrolling; readable contrast; " +
        "Android back button support: use hash routing with history.pushState so Back goes to the previous screen. " +
        "COMPLETE (required): build EVERY feature in the request and in the FEATURE LIST fully. Every button works, every screen is reachable, forms validate with friendly messages, " +
        "lists have add / edit / delete with undo, and search or filter when there are many items; user data persists in localStorage inside try/catch; a Settings screen (theme, reset data with confirmation, About + sources); " +
        "first-run shows at most one short welcome, never a long tutorial. No placeholders, no 'coming soon', no TODO, no lorem ipsum, no dead buttons, no fake data presented as real. " +
        "No network calls, no external scripts or images (draw icons with inline SVG or CSS), no login, no tracking. " +
        "TRUTH RULES: every factual statement (meaning, rule, step, date, number, name, price, address) must come from the FACT SHEET. " +
        "Never invent or 'remember' any number, date, year, name, phone number, e-mail, link or price. Where the fact sheet has a GAP, show a clear card saying that this information is not verified yet and what to check - never fill it. " +
        "Date logic (today, countdowns) uses the phone's JavaScript Date, never a hard-coded year."

    fun appPrompt(idea: String, name: String, today: String, plan: Plan, sheet: Sheet?, srcs: List<Src>): String = buildString {
        append("App name: ").append(name).append("\nToday's date: ").append(today).append("\nOwner's request (same language for all text in the app): ").append(idea.take(MAX_IDEA)).append('\n')
        if (sheet != null && sheet.total > 0) {
            append("\nFACT SHEET (verified from web sources on ").append(today).append("). Use only these facts for factual content:\n").append(sheetText(sheet, srcs))
            append("\nSOURCES RULES: show a small tappable source chip like [1] on every fact card; add a SOURCES screen that lists every source number, its title and its address as plain text; ")
            append("show a note \"Jaankari ").append(today).append(" ko web sources se li gayi. Taareekh, daam aur niyam badal sakte hain: official source se confirm karo.\" on the home and sources screens.\n")
        } else {
            append("\nNo outside facts are needed for this tool: do not state any factual claim about the world.\n")
        }
        append("Sections the app should cover: ").append(plan.sections.joinToString("; ") { it.name }).append('\n')
        if (plan.features.isNotEmpty()) append("FEATURE LIST (build all of them, fully working):\n").append(plan.features.joinToString("\n") { "- " + it }).append('\n')
        if (plan.style.isNotEmpty()) append("Look and feel: ").append(plan.style).append('\n')
    }

    private val STYLE = Regex("<style[^>]*>.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val SCRIPT = Regex("<script[^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val STRLIT = Regex("\"(?:[^\"\\\\\\n]|\\\\.)*\"|'(?:[^'\\\\\\n]|\\\\.)*'")
    private val TAG = Regex("<[^>]*>")
    private val YEAR = Regex("(?<![0-9A-Za-z._#/-])((?:19|20)[0-9]{2})(?![0-9A-Za-z])")
    private val PHONE = Regex("(?<![0-9])(?:\\+?91[- ]?)?[6-9][0-9]{9}(?![0-9])")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val URLHOST = Regex("https?://([A-Za-z0-9.-]+)")
    private val OK_HOSTS = setOf("www.w3.org", "fonts.googleapis.com", "fonts.gstatic.com")

    /** What the owner and the user will READ: page text + JavaScript string literals (so data arrays count, CSS and code numbers do not). */
    fun visibleText(html: String): String {
        val noStyle = STYLE.replace(html, " ")
        val sb = StringBuilder()
        var last = 0
        for (m in SCRIPT.findAll(noStyle)) {
            sb.append(TAG.replace(noStyle.substring(last, m.range.first), " ")).append(' ')
            for (lit in STRLIT.findAll(m.groupValues[1])) sb.append(lit.value).append(' ')
            last = m.range.last + 1
        }
        sb.append(TAG.replace(noStyle.substring(last), " "))
        return sb.toString()
    }

    /**
     * Problems found in the finished app. [allowed] = the text the app may legitimately contain (fact sheet + sources + owner's request + today).
     * Empty list = clean. Only checks what code can check: years, phone numbers, e-mails, web addresses, data-leaking forms.
     */
    fun checkHtml(html: String, allowed: String, needsFacts: Boolean, srcs: List<Src>): List<String> {
        val out = ArrayList<String>()
        val low = html.lowercase()
        if (!low.contains("name=\"viewport\"") && !low.contains("name='viewport'")) out.add("viewport meta tag is missing")
        if (Regex("<form[^>]+action=[\"']?https?://", RegexOption.IGNORE_CASE).containsMatchIn(html)) out.add("a form sends data to the internet: remove it")
        if (Regex("type=[\"']?password", RegexOption.IGNORE_CASE).containsMatchIn(html)) out.add("a password field is not allowed")
        val vis = visibleText(html)
        val allowedLow = allowed.lowercase()
        val allowedDigits = allowed.replace(Regex("[^0-9]"), "")
        val badYears = YEAR.findAll(vis).map { it.groupValues[1] }.filter { !allowed.contains(it) }.distinct().take(8).toList()
        if (badYears.isNotEmpty()) out.add("these years are not in the fact sheet or the request (remove them or use the phone's Date): " + badYears.joinToString(", "))
        val badPhones = PHONE.findAll(vis).map { it.value.replace(Regex("[^0-9]"), "").takeLast(10) }.filter { !allowedDigits.contains(it) }.distinct().take(5).toList()
        if (badPhones.isNotEmpty()) out.add("phone numbers not in the fact sheet: " + badPhones.joinToString(", "))
        val badMails = EMAIL.findAll(vis).map { it.value.lowercase() }.filter { !allowedLow.contains(it) }.distinct().take(5).toList()
        if (badMails.isNotEmpty()) out.add("e-mail addresses not in the fact sheet: " + badMails.joinToString(", "))
        val badHosts = URLHOST.findAll(vis).map { it.groupValues[1].lowercase() }.filter { it !in OK_HOSTS && !allowedLow.contains(it) }.distinct().take(5).toList()
        if (badHosts.isNotEmpty()) out.add("web addresses not in the sources: " + badHosts.joinToString(", "))
        if (html.length < 6000) out.add("the app is too small to be complete: build every screen and feature fully")
        if (Regex("lorem ipsum|coming soon|(?<![A-Za-z])TODO(?![A-Za-z])|your text here|<<|\\[placeholder", RegexOption.IGNORE_CASE).containsMatchIn(vis)) out.add("placeholder text (lorem ipsum / coming soon / TODO) is not allowed: write the real content or remove it")
        if (!low.contains("prefers-color-scheme") && !low.contains("data-theme")) out.add("light and dark theme are missing")
        if (!low.contains("transition") || !low.contains(":active")) out.add("smooth transitions and :active press feedback are missing")
        if (!low.contains("safe-area-inset") && !low.contains("theme-color")) out.add("safe-area padding / theme-color are missing")
        if (!low.contains("localstorage")) out.add("user data must be saved with localStorage")
        if (needsFacts && srcs.isNotEmpty()) {
            val shown = srcs.count { s -> val h = Regex("https?://([^/]+)").find(s.url)?.groupValues?.get(1)?.lowercase().orEmpty(); h.isNotEmpty() && vis.lowercase().contains(h) }
            if (shown < minOf(2, srcs.size)) out.add("the SOURCES screen is missing: list every source number, title and address as text")
        }
        return out
    }

    fun repairPrompt(problems: List<String>, html: String, prompt: String): String = buildString {
        append("Repair this app. Return ONE complete corrected HTML5 document only, same design and features.\nPROBLEMS FOUND BY THE CHECKER (fix each; never fix by inventing facts, remove the claim instead):\n")
        problems.forEach { append("- ").append(it).append('\n') }
        append("\nORIGINAL BRIEF:\n").append(prompt).append("\nCURRENT HTML:\n").append(html.take(SiteGen.MAX_HTML))
    }

    // ---------------------------------------------------------------- the Android project (fixed, known-good shell; only index.html comes from the AI)

    private const val D = '§'   // stands for a dollar sign inside the templates (keeps Kotlin from reading it as a template)

    private val SETTINGS_TPL = """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "JarvisApp"
include(":app")
"""

    private val ROOT_TPL = """plugins {
    id("com.android.application") version "8.5.2" apply false
}
"""

    private val APP_GRADLE_TPL = """plugins {
    id("com.android.application")
}

android {
    namespace = "__PKG__"
    compileSdk = 34
    defaultConfig {
        applicationId = "__PKG__"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
"""

    private val MANIFEST_TPL = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="__NAME__"
        android:theme="@android:style/Theme.DeviceDefault.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden|screenLayout">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""

    private val ICON_TPL = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="__COLOR__" android:pathData="M54,54m-50,0a50,50 0,1 1,100 0a50,50 0,1 1,-100 0" />
    <path android:fillColor="#FFFFFF" android:pathData="M54,26l7,19l20,1l-16,12l6,20l-17,-12l-17,12l6,-20l-16,-12l20,-1z" />
</vector>
"""

    private val ACTIVITY_TPL = """package __PKG__;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b0b14"));
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("file".equals(u.getScheme())) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    // no app can open this link
                }
                return true;
            }
        });
        if (state == null) {
            web.loadUrl("file:///android_asset/index.html");
        } else {
            web.restoreState(state);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (web != null) {
            web.saveState(out);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
"""

    private val WORKFLOW_TPL = """name: Build APK
on:
  workflow_dispatch:
permissions:
  contents: write
jobs:
  build:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Install Android SDK 34
        run: |
          yes | sdkmanager --licenses > /dev/null || true
          sdkmanager 'platforms;android-34' 'build-tools;34.0.0' || echo 'sdkmanager skipped, using preinstalled SDK'
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'
      - name: Build debug APK
        run: gradle --no-daemon assembleDebug
      - name: Publish the APK as a release
        env:
          GH_TOKEN: §{{ github.token }}
        run: |
          gh release create "build-§GITHUB_RUN_NUMBER" app/build/outputs/apk/debug/app-debug.apk --repo "§GITHUB_REPOSITORY" --title "App build §GITHUB_RUN_NUMBER" --notes "Built by JARVIS"
""".replace(D, '$')

    private val ICON_COLORS = listOf("#4B2BD6", "#0F7B6C", "#C2410C", "#B4236B", "#1D4ED8", "#7A5C00")

    /** All files of the Android project, path -> content. [html] is the finished app. */
    fun projectFiles(name: String, pkg: String, html: String, sourcesMd: String): List<Pair<String, String>> {
        val nm = cleanName(name).ifEmpty { "My App" }
        val color = ICON_COLORS[Math.floorMod(nm.hashCode(), ICON_COLORS.size)]
        val pkgPath = pkg.replace('.', '/')
        return listOf(
            Pair(".github/workflows/build.yml", WORKFLOW_TPL),
            Pair("settings.gradle.kts", SETTINGS_TPL),
            Pair("build.gradle.kts", ROOT_TPL),
            Pair("gradle.properties", "org.gradle.jvmargs=-Xmx2g\n"),
            Pair("app/build.gradle.kts", APP_GRADLE_TPL.replace("__PKG__", pkg)),
            Pair("app/src/main/AndroidManifest.xml", MANIFEST_TPL.replace("__NAME__", nm)),
            Pair("app/src/main/res/drawable/ic_launcher.xml", ICON_TPL.replace("__COLOR__", color)),
            Pair("app/src/main/java/$pkgPath/MainActivity.java", ACTIVITY_TPL.replace("__PKG__", pkg)),
            Pair("app/src/main/assets/index.html", html),
            Pair("SOURCES.md", sourcesMd),
            Pair("README.md", "# $nm\n\nBuilt by JARVIS. The app is one HTML file (app/src/main/assets/index.html) inside a small Android WebView shell. Build: GitHub Actions > Build APK > Run workflow.\n")
        )
    }

    // ---------------------------------------------------------------- GitHub answers (pure parsing)

    class RunState(val status: String, val conclusion: String, val url: String)

    /** GET /actions/runs?per_page=1 -> the newest run. null = no run yet. */
    fun parseRun(body: String?): RunState? {
        val root = MiniJson.parse(body ?: return null) ?: return null
        val run = MiniJson.at(MiniJson.field(root, "workflow_runs"), 0) ?: return null
        return RunState(clip(MiniJson.field(run, "status"), 20), clip(MiniJson.field(run, "conclusion"), 20), clip(MiniJson.field(run, "html_url"), 200))
    }

    /** GET /actions/runs/{id}/jobs -> name of the first step that failed, "" when unknown. */
    fun parseFailedStep(body: String?): String {
        val root = MiniJson.parse(body ?: return "") ?: return ""
        val jobs = MiniJson.field(root, "jobs") as? List<*> ?: return ""
        for (j in jobs) for (s in (MiniJson.field(j, "steps") as? List<*> ?: emptyList<Any?>())) if (MiniJson.field(s, "conclusion") == "failure") return clip(MiniJson.field(s, "name"), 60)
        return ""
    }

    fun parseRunId(body: String?): String {
        val root = MiniJson.parse(body ?: return "") ?: return ""
        val run = MiniJson.at(MiniJson.field(root, "workflow_runs"), 0) ?: return ""
        val id = MiniJson.field(run, "id")
        return if (id is Double) id.toLong().toString() else ""
    }

    /** GET /releases/latest -> the APK address, only if it is a github.com release asset. */
    fun parseApkUrl(body: String?): String? {
        val root = MiniJson.parse(body ?: return null) ?: return null
        val assets = MiniJson.field(root, "assets") as? List<*> ?: return null
        for (a in assets) {
            val n = clip(MiniJson.field(a, "name"), 80)
            val u = clip(MiniJson.field(a, "browser_download_url"), 400)
            if (n.endsWith(".apk") && u.startsWith("https://github.com/")) return u
        }
        return null
    }

    // ---------------------------------------------------------------- helpers + words

    fun extractJson(raw: String?): Any? {
        if (raw == null) return null
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return MiniJson.parse(raw.substring(a, b + 1))
    }

    private fun clip(v: Any?, max: Int): String = (v as? String)?.replace(Regex("\\s+"), " ")?.trim()?.take(max) ?: ""

    private fun t(en: Boolean, hi: String, e: String) = if (en) e else hi

    fun askIdeaText(en: Boolean) = t(en, "Kaisi app banani hai? Aise bolo: jarvis chhath calendar app banao.", "What app should I build? Say for example: jarvis build a chhath calendar app.")
    fun startedText(name: String, en: Boolean) = t(en,
        "Theek hai, $name app par kaam shuru. Pehle poori research karungi, phir code, build aur install. App Builder screen par live dikhega, rokne ke liye bolo: app banana band karo.",
        "OK, starting the $name app. First deep research, then code, build and install. Watch it live on the App Builder screen; say stop building the app to cancel.")
    fun busyText(en: Boolean) = t(en, "Ek app pehle se ban rahi hai. Uske liye App Builder screen dekho ya bolo: app banana band karo.", "An app is already being built. See the App Builder screen or say: stop building the app.")
    fun noTokenText(en: Boolean) = t(en, "App build karne ke liye GitHub token chahiye (repo aur workflow permission ke saath). CONNECT tab mein GitHub jodo, phir dobara bolo.", "To build an app I need a GitHub token (repo and workflow permission). Connect GitHub in the CONNECT tab, then ask again.")
    fun noAiText(en: Boolean) = t(en, "App likhne ke liye AI key chahiye. Settings mein Gemini ya Groq key daalo.", "I need an AI key to write the app. Add a Gemini or Groq key in Settings.")
    fun notEnoughText(reason: String, found: Int, en: Boolean) =
        if (reason == "sources") t(en, "Is topic par web par bharosemand source hi nahi mile ($found), isliye app nahi banayi. Fake detail daalke app nahi banaungi. Topic thoda aur saaf bolo ya internet check karo.",
            "I could not find enough trustworthy web sources ($found), so I did not build the app. I will not fill it with made-up details. Say the topic more clearly or check the internet.")
        else t(en, "Is topic par sirf $found pakki detail source ke saath mili (kam se kam $MIN_FACTS chahiye), isliye app nahi banayi. Fake detail daalke app nahi banaungi.",
            "Only $found facts could be verified against sources (at least $MIN_FACTS needed), so I did not build the app. I will not fill it with made-up details.")
    fun stoppedText(en: Boolean) = t(en, "App banana rok diya. Baaki sab waise hi hai.", "Stopped building the app. Everything else is as it was.")
    fun idleStopText(en: Boolean) = t(en, "Abhi koi app nahi ban rahi.", "No app is being built right now.")
    fun noApkText(en: Boolean) = t(en, "Abhi koi bani hui app taiyaar nahi hai. Pehle bolo: jarvis ... app banao.", "There is no built app ready yet. First say: jarvis build a ... app.")
    fun screenText(en: Boolean) = t(en, "App Builder screen khol di.", "Opened the App Builder screen.")
    fun doneText(name: String, facts: Int, sources: Int, dropped: Int, gaps: Int, flagged: List<String>, en: Boolean): String {
        val base = t(en, "$name app taiyaar hai. $sources sources se $facts verify ki hui details lagayi.", "$name is ready. It uses $facts verified details from $sources sources.")
        val d = if (dropped > 0) t(en, " $dropped detail source se match nahi hui, isliye hata di.", " $dropped detail(s) did not match their source and were removed.") else ""
        val g = if (gaps > 0) t(en, " $gaps hisse ki jaankari verify nahi ho payi, app mein wahi likha hai.", " $gaps part(s) could not be verified; the app says so instead of guessing.") else ""
        val f = if (flagged.isNotEmpty()) t(en, " Dhyan do: ye cheezein poori tarah theek nahi hui, ek baar dekh lena: ", " Please look at these, they are not fully right: ") + flagged.joinToString("; ").take(200) else ""
        return base + d + g + f + t(en, " INSTALL KARO dabao.", " Tap INSTALL.")
    }

    // ---------------------------------------------------------------- terminal log words (shown in the App Builder screen)

    fun today(year: Int, month: Int, day: Int): String = String.format("%04d-%02d-%02d", year, month, day)
}
