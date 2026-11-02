package com.nova.assistant

/**
 * v7.52 PART 13A - ANALYSIS RULES. Pure Kotlin (no Android class, no org.json: only [MiniJson]), so JUnit can run it.
 *
 * This file holds every RULE of the real analysis (Part 13). The later sub-parts only wire it:
 *   13B  AnalysisPipeline: frames -> Gemini vision (inlineData) -> [parseVision]           (stage events: AnalysisStage)
 *   13C  web search -> [sanitizeResults] -> [composeUserPrompt] -> [parseCompose] -> [buildReport]
 *   13D  voice / chat commands, ANALYSE button, bridge, chat photo + question, MADAD / SKILLS
 *
 * Hard rules kept here (owner rules 3 and 6):
 *  - Nothing is invented. A claim is either bound to a REAL search result (by its number) or listed as "verify nahi hua".
 *  - The model never writes a URL we show: URLs inside model text are stripped, a source can only be one of the numbered
 *    results the search really returned ([sanitizeResults]); the model only gives NUMBERS ([parseCompose]).
 *  - A real person is never named from the face / body: refused in the question ([gateQuestion]), forbidden in the prompt,
 *    and an identity sentence in the model answer is dropped ([identityClaim]). Written text in the picture (caption, name tag)
 *    stays in text_seen as written text.
 *  - Harmful how-to, OTP / PIN / password / card-number reading, and protected screens are refused before any model call.
 *  - Offline / no key: honest metadata-only report ([metadataReport]), never a made-up description.
 * Limits (honest): the identity filter and the phrase lists are heuristics, not a guarantee; "bound to a source" proves the
 * source is real and was chosen, not that it fully supports the sentence, so the report shows the source title next to the claim.
 */
object AnalysisRules {
    const val MAX_QUESTION = 400
    const val MAX_TEXT = 600
    const val MAX_LINES = 8
    const val MAX_CLAIMS = 6
    const val MAX_QUERIES = 4
    const val MAX_QUERY_CHARS = 80
    const val MAX_SOURCES = 8
    const val MAX_SNIPPET = 300
    const val MAX_RAW = 12_000
    const val MAX_URL = 500
    const val VISION_FRAMES_PER_CALL = 6
    const val MAX_VISION_CALLS = 2

    const val MODE_FULL = "full"
    const val MODE_VISION_ONLY = "vision_only"
    const val MODE_METADATA = "metadata_only"

    const val CONF_LOW = "low"
    const val CONF_MEDIUM = "medium"
    const val CONF_HIGH = "high"

    const val SAFE_NONE = "none"
    const val SAFE_FACE = "face_id"
    const val SAFE_HARM = "harmful"
    const val SAFE_PRIVATE = "private"
    const val SAFE_SECRET = "secret"
    val SAFETY_VALUES = listOf(SAFE_NONE, SAFE_FACE, SAFE_HARM, SAFE_PRIVATE, SAFE_SECRET)

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    // ------------------------------------------------------------------ text helpers

    /** lower case, letters / digits only, single spaces. */
    fun norm(s: String?): String {
        if (s == null) return ""
        val sb = StringBuilder(s.length)
        var space = true
        for (ch in s.lowercase()) {
            if (ch.isLetterOrDigit()) { sb.append(ch); space = false }
            else if (!space) { sb.append(' '); space = true }
        }
        return sb.toString().trim()
    }

    private val URL_RE = Regex("""(?i)(https?://|www\.)\S+""")
    private val SPACES = Regex("""\s+""")

    /** Model / user text made safe for a card: control chars gone, every URL removed, one line, capped. */
    fun clean(s: String?, max: Int = MAX_TEXT): String {
        if (s == null) return ""
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch.code < 32 || ch.code == 127) ' ' else ch)
        var out = URL_RE.replace(sb.toString(), " ")
        out = SPACES.replace(out, " ").trim()
        if (out.length > max) out = out.substring(0, max).trimEnd()
        return out
    }

    fun cleanQuestion(q: String?): String = clean(q, MAX_QUESTION)

    private fun hasPhrase(normText: String, list: List<String>): Boolean {
        val p = " $normText "
        return list.any { p.contains(" $it ") }
    }

    // ------------------------------------------------------------------ safety gate (before any model call)

    enum class Gate { OK, REFUSE_FACE_ID, REFUSE_HARM, REFUSE_SECRET, REFUSE_PRIVATE }

    private val FACE_PHRASES = listOf(
        "ye kaun hai", "yeh kaun hai", "ye kon hai", "yeh kon hai", "ye kaun he", "ye kaun hain", "kaun hai ye", "kon hai ye", "kaun hai yeh",
        "isko pehchano", "isko pehchaniye", "pehchan ke batao", "pehchano kaun hai", "chehra pehchano", "shakal se pehchano", "face se pehchano",
        "is aadmi ka naam", "is ladki ka naam", "is insaan ka naam", "is vyakti ka naam", "is aurat ka naam",
        "is photo me kaun hai", "is video me kaun hai", "photo me kaun hai", "video me kaun hai", "kaun hai is photo me", "kaun hai is video me",
        "aadmi kaun hai", "admi kaun hai", "ladka kaun hai", "ladki kaun hai", "aurat kaun hai", "insaan kaun hai", "banda kaun hai", "vyakti kaun hai",
        "who is this", "who is this person", "who is he", "who is she", "who is that man", "who is that woman", "who is the man", "who is the woman",
        "who is the person", "who are they", "identify this person", "identify the person", "identify him", "identify her", "name this person",
        "name the person", "recognize this person", "recognise this person", "recognize the face", "face recognition", "face match",
        "who is in this photo", "who is in this video", "who is in the photo", "who is in the video", "who is in the picture",
        // v7.60 (13D acceptance): add-only, the same asks with other media words / "isme"
        "image kaun hai", "image me kaun hai", "pic kaun hai", "pic me kaun hai", "picture kaun hai", "picture me kaun hai", "tasveer me kaun hai",
        "reel me kaun hai", "reel kaun hai", "clip me kaun hai", "clip kaun hai", "isme kaun hai", "is me kaun hai", "isme kon hai", "isme koun hai",
        "who is in this image", "who is in this picture", "who is in this reel", "who is in this clip", "who s this", "who is that person"
    )

    private val HARM_PHRASES = listOf(
        "bomb banana", "bomb banane", "bomb kaise banate", "bomb kaise banaye", "bomb kaise banta", "bam banana", "make a bomb", "build a bomb",
        "how to make a bomb", "make explosives", "explosive banana", "explosive banane", "hathiyar banana", "hathiyar banane", "bandook banana",
        "gun banana", "make a gun", "3d print a gun", "make a weapon", "zeher banana", "zeher kaise", "make poison", "make a poison", "poison kaise",
        "nasha banana", "drugs banana", "make meth", "cook meth", "hack karna", "kisi ka account hack", "hack someone", "steal password",
        "password chura", "kisi ko maarna", "how to kill a person", "how to kill someone"
    )

    private val SECRET_TOKENS = setOf("otp", "cvv", "cvc", "password", "passwords", "passcode", "pwd")
    private val SECRET_PHRASES = listOf(
        "card number", "account number", "aadhaar number", "aadhar number", "pan number", "upi pin", "atm pin", "secret key", "api key", "seed phrase"
    )

    private fun hasSecretAsk(normText: String): Boolean {
        val toks = normText.split(' ').filter { it.isNotEmpty() }
        if (toks.any { it in SECRET_TOKENS }) return true
        for (i in toks.indices) {
            if (toks[i] == "pin") {
                val nx = toks.getOrNull(i + 1)
                if (nx != "code" && nx != "codes") return true
            }
        }
        return hasPhrase(normText, SECRET_PHRASES)
    }

    /** The owner's question. OK = go on. Order: harm, secret, face identity. Blank question = OK (a plain "analyse this"). */
    fun gateQuestion(q: String?): Gate {
        val n = norm(q)
        if (n.isEmpty()) return Gate.OK
        if (hasPhrase(n, HARM_PHRASES)) return Gate.REFUSE_HARM
        if (hasSecretAsk(n)) return Gate.REFUSE_SECRET
        if (hasPhrase(n, FACE_PHRASES)) return Gate.REFUSE_FACE_ID
        return Gate.OK
    }

    /** The picture / video came from a protected screen (bank, password, OTP): never analysed. */
    fun gateSource(protectedScreen: Boolean): Gate = if (protectedScreen) Gate.REFUSE_PRIVATE else Gate.OK

    /** The model's own safety flag (vision answer). */
    fun gateFromSafety(s: String?): Gate = when (s) {
        SAFE_FACE -> Gate.REFUSE_FACE_ID
        SAFE_HARM -> Gate.REFUSE_HARM
        SAFE_SECRET -> Gate.REFUSE_SECRET
        SAFE_PRIVATE -> Gate.REFUSE_PRIVATE
        else -> Gate.OK
    }

    fun refusalText(g: Gate, en: Boolean): String = when (g) {
        Gate.OK -> ""
        Gate.REFUSE_FACE_ID -> t(en,
            "Main kisi insaan ko chehre ya sharir se pehchaan kar naam nahi batata. Main bata sakta hoon ki kya dikh raha hai, kya likha hua hai, aur sources se kya milta hai.",
            "I do not name a real person from their face or body. I can tell what is visible, what text is written, and what sources say.")
        Gate.REFUSE_HARM -> t(en,
            "Is cheez me madad nahi kar sakta, kyunki isse nuksaan ho sakta hai. Kuch aur poochho.",
            "I cannot help with this because it could cause harm. Ask me something else.")
        Gate.REFUSE_SECRET -> t(en,
            "OTP, PIN, password ya card number main photo se na padhta hoon na yaad rakhta hoon.",
            "I neither read nor remember an OTP, PIN, password or card number from a picture.")
        Gate.REFUSE_PRIVATE -> t(en,
            "Ye screen protected / private hai (bank, password ya OTP), isliye main ise analyse nahi karunga.",
            "This is a protected / private screen (bank, password or OTP), so I will not analyse it.")
    }

    // ------------------------------------------------------------------ identity filter (model answer)

    private val ID_EN = Regex("""\b(?i:(?:the\s+)?(?:man|woman|person|guy|girl|boy|actor|actress|singer|speaker|host|player|politician|leader|he|she)\s+(?:is|was|looks like|appears to be|seems to be|must be|resembles))\s+(?:(?i:the|famous|well-known|popular)\s+)*[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+""")
    private val ID_JOB = Regex("""\b(?i:actor|actress|singer|politician|cricketer|celebrity|minister|president)\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+""")
    private val ID_HI = Regex("""\b(?:[Yy]e|[Yy]eh|[Ww]o|[Ww]oh|[Vv]o)\s+(?:aadmi|admi|ladki|aurat|vyakti|insaan|neta|actor|gayak|khiladi)\s+[A-Z][a-z]+(?:\s+[A-Z][a-z]+)*\s+(?:hai|hain|lagta hai|lagte hain|lag raha hai)\b""")

    /** True when a sentence names a real person as who is shown ("He is Rahul Kumar"). Heuristic. Used on visual lines, never on text_seen. */
    fun identityClaim(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return ID_EN.containsMatchIn(text) || ID_JOB.containsMatchIn(text) || ID_HI.containsMatchIn(text)
    }

    // ------------------------------------------------------------------ prompts (model side, English instructions + Hinglish / English answer)

    private fun langLine(en: Boolean): String = if (en)
        "Write every text field in simple English."
    else
        "Write every text field in simple Hinglish (Hindi in Roman letters). Keep names and on-screen text exactly as they appear."

    fun visionSystemPrompt(en: Boolean): String =
        "You analyse still frames from a photo or a video for a phone assistant.\n" +
        "1. Describe only what is visible in the frames. Never guess what you cannot see.\n" +
        "2. NEVER name or identify a real person from the face or body. Say a man, a woman, a person. A name may be reported only if it is written in the frames (caption, name tag) and then only as written text in text_seen.\n" +
        "3. Copy readable text exactly into text_seen. If nothing is readable use an empty list.\n" +
        "4. If the frames show a bank, payment, password, OTP or ID screen set safety to private. If the question asks who a person is set safety to face_id. If it asks for harmful instructions set safety to harmful. If it asks to read an OTP, PIN, password or card number set safety to secret. In all those cases keep the other fields empty.\n" +
        "5. claims = factual statements worth checking on the web (an event, a product, a place, a number, a news claim). Invent none. At most " + MAX_CLAIMS + ".\n" +
        "6. search_queries = at most " + MAX_QUERIES + " short web search queries (each under " + MAX_QUERY_CHARS + " characters) to check the claims. A query never contains a name guessed from a face.\n" +
        "7. uncertain = things you could not tell.\n" +
        "8. Never write a web address in any field.\n" +
        "Answer with ONE JSON object only, no markdown, with exactly these keys: summary (string), seen (array of strings), text_seen (array of strings), " +
        "claims (array of strings), search_queries (array of strings), uncertain (array of strings), safety (one of none, face_id, harmful, private, secret).\n" +
        langLine(en)

    /** [secs] = second of every attached frame (empty for a photo). The images themselves are attached by the pipeline (inlineData). */
    fun visionUserPrompt(question: String?, isVideo: Boolean, frames: Int, secs: List<Int>, en: Boolean): String {
        val q = cleanQuestion(question)
        val sb = StringBuilder()
        sb.append("Question: ").append(if (q.isEmpty()) "(none, describe it and check what can be checked)" else q).append('\n')
        if (isVideo) {
            sb.append("Media: a video, ").append(frames).append(" frames")
            if (secs.isNotEmpty()) sb.append(" taken at seconds ").append(secs.joinToString(", "))
        } else sb.append("Media: one photo")
        sb.append(".\nAnalyse the attached ").append(if (isVideo) "frames" else "photo").append(" now. ").append(langLine(en))
        return sb.toString()
    }

    fun composeSystemPrompt(en: Boolean): String =
        "You write the final fact check of an analysis for a phone assistant. You get what was seen, the claims, and numbered web results.\n" +
        "1. For each claim: if a numbered result clearly supports it, put it in verified with the result numbers in src. If no result supports it, put it in unverified.\n" +
        "2. Copy each claim text exactly as given. Add no new claim.\n" +
        "3. Use no fact that is not in the claims or the results. Never write a web address; refer to results by number only.\n" +
        "4. Never name a real person from a face. summary = two short sentences at most.\n" +
        "Answer with ONE JSON object only, no markdown, with exactly these keys: summary (string), verified (array of objects with claim (string) and src (array of result numbers)), unverified (array of strings).\n" +
        langLine(en)

    fun composeUserPrompt(question: String?, v: Vision, results: List<Result>, en: Boolean): String {
        val sb = StringBuilder()
        val q = cleanQuestion(question)
        sb.append("Question: ").append(if (q.isEmpty()) "(none)" else q).append('\n')
        sb.append("Seen: ").append(v.summary).append('\n')
        sb.append("Claims:\n")
        if (v.claims.isEmpty()) sb.append("(none)\n") else v.claims.forEachIndexed { i, c -> sb.append(i + 1).append(". ").append(c).append('\n') }
        sb.append("Web results:\n")
        if (results.isEmpty()) sb.append("(none)\n")
        else results.forEachIndexed { i, r ->
            sb.append('[').append(i + 1).append("] ").append(r.title)
            if (r.snippet.isNotEmpty()) sb.append(" - ").append(r.snippet)
            if (r.date.isNotEmpty()) sb.append(" (").append(r.date).append(')')
            sb.append('\n')
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ strict JSON parsing (allow-list)

    private fun unfence(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            val nl = s.indexOf('\n')
            s = if (nl >= 0) s.substring(nl + 1) else s.removePrefix("```")
        }
        s = s.trim()
        if (s.endsWith("```")) s = s.removeSuffix("```").trim()
        return s
    }

    private fun parseObject(raw: String?): Map<*, *>? {
        if (raw == null) return null
        val s = unfence(raw)
        if (s.length < 2 || s.length > MAX_RAW || !s.startsWith("{") || !s.endsWith("}")) return null
        return MiniJson.parse(s) as? Map<*, *>
    }

    /** null = key absent (empty list). A wrong type or a non-string element = null result (whole answer rejected). */
    private fun strList(v: Any?, max: Int): List<String>? {
        if (v == null) return emptyList()
        val l = v as? List<*> ?: return null
        val out = ArrayList<String>()
        for (e in l) {
            val s = e as? String ?: return null
            val c = clean(s)
            if (c.isNotEmpty() && out.size < max && out.none { it.equals(c, true) }) out.add(c)
        }
        return out
    }

    data class Vision(
        val summary: String,
        val seen: List<String>,
        val textSeen: List<String>,
        val claims: List<String>,
        val queries: List<String>,
        val uncertain: List<String>,
        val safety: String,
        /** how many model lines were dropped because they named a person (identity filter) */
        val dropped: Int
    )

    /** One plain JSON object, only the allow-listed keys are read (others are ignored, never copied). Any wrong type = null. */
    fun parseVision(raw: String?): Vision? {
        val root = parseObject(raw) ?: return null
        val summaryRaw = root["summary"] as? String ?: return null
        val seenRaw = strList(root["seen"], MAX_LINES) ?: return null
        val textSeen = strList(root["text_seen"], MAX_LINES) ?: return null
        val claimsRaw = strList(root["claims"], MAX_CLAIMS) ?: return null
        val queriesRaw = strList(root["search_queries"], MAX_QUERIES * 2) ?: return null
        val uncertainRaw = strList(root["uncertain"], MAX_LINES) ?: return null
        val safety = when (val sv = root["safety"]) { null -> SAFE_NONE; is String -> sv; else -> return null }
        if (safety !in SAFETY_VALUES) return null
        if (safety != SAFE_NONE) {
            return Vision("", emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), safety, 0)
        }
        var dropped = 0
        fun keep(l: List<String>): List<String> = l.filter { if (identityClaim(it)) { dropped++; false } else true }
        val seen = keep(seenRaw)
        val claims = keep(claimsRaw)
        val uncertain = keep(uncertainRaw)
        var summary = clean(summaryRaw)
        if (identityClaim(summary)) { dropped++; summary = "" }
        if (summary.isEmpty()) summary = seen.firstOrNull() ?: ""
        val queries = ArrayList<String>()
        for (q in queriesRaw) {
            val c = cleanQuery(q)
            if (c.isNotEmpty() && queries.size < MAX_QUERIES && queries.none { it.equals(c, true) }) queries.add(c)
        }
        return Vision(summary, seen, textSeen, claims, queries, uncertain, SAFE_NONE, dropped)
    }

    /** Two or more batches (13B sends at most [MAX_VISION_CALLS] calls) become one Vision. Safety: the first flag that is not none wins. */
    fun mergeVision(parts: List<Vision>): Vision? {
        if (parts.isEmpty()) return null
        fun union(sel: (Vision) -> List<String>, max: Int): List<String> {
            val out = ArrayList<String>()
            for (p in parts) for (s in sel(p)) if (out.size < max && out.none { it.equals(s, true) }) out.add(s)
            return out
        }
        val safety = parts.map { it.safety }.firstOrNull { it != SAFE_NONE } ?: SAFE_NONE
        val sum = parts.map { it.summary }.filter { it.isNotBlank() }.joinToString(" ")
        var dropped = 0
        for (p in parts) dropped += p.dropped
        return Vision(
            clean(sum), union({ it.seen }, MAX_LINES), union({ it.textSeen }, MAX_LINES), union({ it.claims }, MAX_CLAIMS),
            union({ it.queries }, MAX_QUERIES), union({ it.uncertain }, MAX_LINES), safety, dropped
        )
    }

    /** How many frames go into each vision call: balanced, never more than [per] a call, never more than [MAX_VISION_CALLS] calls (extra frames are not sent). */
    fun batchSizes(total: Int, per: Int = VISION_FRAMES_PER_CALL): List<Int> {
        if (total <= 0 || per <= 0) return emptyList()
        val calls = minOf(MAX_VISION_CALLS, (total + per - 1) / per)
        val base = total / calls
        val extra = total % calls
        val out = ArrayList<Int>()
        for (i in 0 until calls) out.add(minOf(per, base + (if (i < extra) 1 else 0)))
        return out
    }

    // ------------------------------------------------------------------ search queries + real results

    /** One short query: no URL, no quotes, <= 80 chars (cut at a word), no person-from-face sentence, no secret word. "" = unusable. */
    fun cleanQuery(q: String?): String {
        var s = clean(q, 200).replace("\"", " ")
        s = SPACES.replace(s, " ").trim()
        if (s.length > MAX_QUERY_CHARS) {
            s = s.substring(0, MAX_QUERY_CHARS)
            val sp = s.lastIndexOf(' ')
            if (sp >= 20) s = s.substring(0, sp)
            s = s.trim()
        }
        if (s.length < 3) return ""
        if (identityClaim(s)) return ""
        if (hasSecretAsk(norm(s))) return ""
        return s
    }

    /** Up to 4 separate queries (one per thing to check). Model queries first, else the claims, else the question (only when there is something to check). */
    fun buildQueries(v: Vision?, question: String?): List<String> {
        if (v == null || v.safety != SAFE_NONE) return emptyList()
        val out = ArrayList<String>()
        fun add(s: String) {
            val c = cleanQuery(s)
            if (c.isNotEmpty() && out.size < MAX_QUERIES && out.none { it.equals(c, true) }) out.add(c)
        }
        v.queries.forEach { add(it) }
        if (out.isEmpty()) v.claims.forEach { add(it) }
        if (out.isEmpty() && v.claims.isNotEmpty()) {
            val q = cleanQuestion(question)
            if (q.length >= 6) add(q)
        }
        return out
    }

    /** One real search result as the search tool returned it. [url] is the only address ever shown. */
    data class Result(val title: String, val snippet: String, val url: String, val date: String)

    /** https only, a real host (no user info, no raw IP, no localhost), no spaces / quotes. Scheme + host go lower case; path and query stay exactly as they are. null = reject. */
    fun validUrl(u: String?): String? {
        val s = u?.trim() ?: return null
        if (s.length < 12 || s.length > MAX_URL) return null
        if (!s.startsWith("https://", ignoreCase = true)) return null
        if (s.any { it.isWhitespace() || it.code < 32 || it == '"' || it == '<' || it == '>' || it == '\\' || it == '\'' }) return null
        val rest = s.substring(8)
        var hostEnd = rest.length
        for (i in rest.indices) { val c = rest[i]; if (c == '/' || c == '?' || c == '#') { hostEnd = i; break } }
        val auth = rest.substring(0, hostEnd)
        if (auth.isEmpty() || auth.contains('@')) return null
        val host = auth.substringBefore(':').lowercase()
        if (auth.contains(':')) {
            val port = auth.substringAfter(':')
            if (port.isEmpty() || port.length > 5 || !port.all { it.isDigit() }) return null
        }
        if (host == "localhost" || !host.contains('.') || host.startsWith('.') || host.endsWith('.') || host.contains("..")) return null
        if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
        if (host.all { it.isDigit() || it == '.' }) return null
        return "https://" + auth.lowercase() + rest.substring(hostEnd)
    }

    fun hostOf(url: String): String {
        val rest = if (url.length > 8) url.substring(8) else ""
        return rest.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
    }

    /** Same page for duplicate removal: no fragment, no trailing slash. */
    fun canonUrl(url: String): String = url.substringBefore('#').trimEnd('/')

    /** Only valid, distinct results, at most [MAX_SOURCES], text cleaned. The pipeline numbers THIS list 1..n in the compose prompt. */
    fun sanitizeResults(raw: List<Result>): List<Result> {
        val out = ArrayList<Result>()
        val seen = HashSet<String>()
        for (r in raw) {
            val u = validUrl(r.url) ?: continue
            if (!seen.add(canonUrl(u))) continue
            val title = clean(r.title, 160).ifEmpty { hostOf(u) }
            out.add(Result(title, clean(r.snippet, MAX_SNIPPET), u, clean(r.date, 40)))
            if (out.size >= MAX_SOURCES) break
        }
        return out
    }

    // ------------------------------------------------------------------ compose answer (claims bound to NUMBERS of real results)

    /** [src] = 1-based numbers of the real results (never an address). */
    data class Claim(val text: String, val src: List<Int>)

    data class Composed(val summary: String, val verified: List<Claim>, val unverified: List<String>, val dropped: Int)

    fun sameClaim(a: String, b: String): Boolean {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        return x.length >= 12 && y.length >= 12 && (x.contains(y) || y.contains(x))
    }

    /**
     * Strict. A verified item with no valid result number (or with [nResults] = 0) is NOT verified: it moves to unverified.
     * Numbers outside 1..nResults are dropped. Wrong types reject the whole answer (null).
     */
    fun parseCompose(raw: String?, nResults: Int): Composed? {
        val root = parseObject(raw) ?: return null
        val summaryRaw = when (val sv = root["summary"]) { null -> ""; is String -> sv; else -> return null }
        var dropped = 0
        var summary = clean(summaryRaw)
        if (identityClaim(summary)) { dropped++; summary = "" }
        val verified = ArrayList<Claim>()
        val unver = ArrayList<String>()
        val vl = root["verified"]
        if (vl != null) {
            val list = vl as? List<*> ?: return null
            for (e in list) {
                val m = e as? Map<*, *> ?: return null
                val text = clean(m["claim"] as? String ?: return null)
                if (text.isEmpty()) continue
                if (identityClaim(text)) { dropped++; continue }
                val ok = ArrayList<Int>()
                val sv = m["src"]
                if (sv != null) {
                    val sl = sv as? List<*> ?: return null
                    for (x in sl) {
                        val d = x as? Double ?: continue
                        val n = d.toInt()
                        if (n.toDouble() == d && n >= 1 && n <= nResults && n !in ok) ok.add(n)
                    }
                }
                if (ok.isEmpty()) {
                    if (unver.size < MAX_LINES && unver.none { it.equals(text, true) }) unver.add(text)
                } else if (verified.size < MAX_LINES) verified.add(Claim(text, ok.sorted()))
            }
        }
        val ul = root["unverified"]
        if (ul != null) {
            val list = ul as? List<*> ?: return null
            for (e in list) {
                val s = clean(e as? String ?: return null)
                if (s.isEmpty()) continue
                if (identityClaim(s)) { dropped++; continue }
                if (unver.size < MAX_LINES && unver.none { it.equals(s, true) }) unver.add(s)
            }
        }
        unver.removeAll { u -> verified.any { sameClaim(it.text, u) } }
        return Composed(summary, verified, unver, dropped)
    }

    // ------------------------------------------------------------------ report

    data class Report(
        val mode: String,
        val summary: String,
        val seen: List<String>,
        val textSeen: List<String>,
        val verified: List<Claim>,
        val unverified: List<String>,
        val sources: List<Result>,
        val confidence: String,
        val why: String,
        val limits: List<String>
    )

    /** (level, reason). Deterministic, from real counts only. */
    fun confidence(mode: String, frames: Int, verified: Int, unverified: Int, uncertain: Int, en: Boolean): Pair<String, String> {
        if (mode == MODE_METADATA) {
            return Pair(CONF_LOW, t(en, "file ke andar kuch dekha nahi, sirf size / length", "nothing inside the file was looked at, only size / length"))
        }
        val total = verified + unverified
        var level = CONF_LOW
        var why = ""
        if (total == 0) {
            if (uncertain == 0 && frames >= 1) {
                level = CONF_MEDIUM
                why = t(en, "sirf jo dikha uska bayan, jaanchne wale dawe nahi the", "only a description of what was visible, no claims to check")
            } else {
                level = CONF_LOW
                why = t(en, "kuch cheezein saaf nahi dikhi", "some things were not clear")
            }
        } else {
            val ratio = verified * 100 / total
            val counts = verified.toString() + "/" + total
            if (ratio >= 80 && verified >= 2) {
                level = CONF_HIGH
                why = t(en, counts + " dawe sources se mile", counts + " claims found in sources")
            } else if (ratio >= 50) {
                level = CONF_MEDIUM
                why = t(en, counts + " dawe sources se mile", counts + " claims found in sources")
            } else if (verified == 0) {
                level = CONF_LOW
                why = t(en, "koi dawa verify nahi hua", "no claim could be verified")
            } else {
                level = CONF_LOW
                why = t(en, "sirf " + counts + " dawe verify hue", "only " + counts + " claims were verified")
            }
        }
        if (uncertain >= 3 && level != CONF_LOW) {
            level = if (level == CONF_HIGH) CONF_MEDIUM else CONF_LOW
            why += t(en, "; kai cheezein saaf nahi thi", "; several things were not clear")
        }
        return Pair(level, why)
    }

    /** v7.58 13C: the web search ran and found nothing usable (not the same as "did not run"). */
    fun noSearchResultsLine(en: Boolean): String = t(en, "Search me kuch nahi mila, isliye koi dawa verify nahi hua.", "The search found nothing, so no claim is verified.")

    fun limits(mode: String, frames: Int, isVideo: Boolean, claimCount: Int, searchRan: Boolean, dropped: Int, hasSources: Boolean, en: Boolean): List<String> {
        val out = ArrayList<String>()
        out.add(t(en, "Chehre se kisi ki pehchaan nahi ki jaati.", "Nobody is identified from their face."))
        if (mode == MODE_METADATA) {
            out.add(t(en, "Online AI ke bina tasveer / video ka content nahi dekha ja sakta.", "Without an online AI the content of the picture / video cannot be looked at."))
            return out
        }
        if (isVideo && frames > 0) out.add(t(en, "Sirf " + frames + " frames dekhe, poori video nahi.", "Only " + frames + " frames were looked at, not the whole video."))
        if (claimCount > 0 && !searchRan) out.add(t(en, "Web search nahi chali, isliye dawe verify nahi hue.", "Web search did not run, so the claims are not verified."))
        if (claimCount > 0 && searchRan && !hasSources) out.add(noSearchResultsLine(en))   // v7.58 13C: search ran, found nothing: said plainly
        if (dropped > 0) out.add(t(en, "Kuch line hata di gayi kyunki usme kisi ka naam chehre se diya tha.", "Some lines were removed because they named a person from a face."))
        if (hasSources) out.add(t(en, "Source asli hai par wo dawe ko poora sach nahi banata; title padhke khud dekho.", "A source is real but does not prove the claim by itself; read its title yourself."))
        return out
    }

    /**
     * The final report. [results] must be the list sanitized by [sanitizeResults] and numbered 1..n in the compose prompt.
     * Every vision claim ends up in verified (with real result numbers) or in unverified. [composed] null = compose / search did not happen.
     */
    fun buildReport(v: Vision, composed: Composed?, results: List<Result>, frames: Int, isVideo: Boolean, searchRan: Boolean, en: Boolean): Report {
        val res = sanitizeResults(results)
        val aligned = res.size == results.size
        val ver = ArrayList<Claim>()
        val unver = ArrayList<String>()
        fun addUn(s: String) { if (s.isNotEmpty() && unver.size < MAX_LINES && unver.none { sameClaim(it, s) }) unver.add(s) }
        if (composed != null) {
            for (c in composed.verified) {
                if (aligned && c.src.isNotEmpty() && c.src.all { it >= 1 && it <= res.size }) ver.add(c) else addUn(c.text)
            }
            composed.unverified.forEach { addUn(it) }
        }
        for (c in v.claims) {
            val covered = ver.any { sameClaim(it.text, c) } || unver.any { sameClaim(it, c) }
            if (!covered) addUn(c)
        }
        val mode = if (res.isNotEmpty() && composed != null) MODE_FULL else MODE_VISION_ONLY
        val fromCompose = composed?.summary ?: ""
        val fallback = t(en, "Koi saaf cheez nahi dikhi.", "Nothing clear was visible.")
        val summary = if (fromCompose.isNotBlank()) fromCompose else if (v.summary.isNotBlank()) v.summary else (v.seen.firstOrNull() ?: fallback)
        val conf = confidence(mode, frames, ver.size, unver.size, v.uncertain.size, en)
        val dropped = v.dropped + (composed?.dropped ?: 0)
        val lim = limits(mode, frames, isVideo, v.claims.size, searchRan, dropped, res.isNotEmpty(), en)
        return Report(mode, summary, v.seen, v.textSeen, ver, unver, res, conf.first, conf.second, lim)
    }

    fun sizeText(bytes: Long): String {
        if (bytes < 0) return "?"
        if (bytes < 1024L) return bytes.toString() + " B"
        if (bytes < 1048576L) { val tenths = bytes * 10 / 1024L; return (tenths / 10).toString() + "." + (tenths % 10).toString() + " KB" }
        val tenths = bytes * 10 / 1048576L
        return (tenths / 10).toString() + "." + (tenths % 10).toString() + " MB"
    }

    fun durationText(ms: Long): String {
        if (ms <= 0L) return ""
        val sec = (ms + 500L) / 1000L
        return if (sec < 60L) sec.toString() + " sec" else (sec / 60L).toString() + " min " + (sec % 60L).toString() + " sec"
    }

    /** Offline / no key / vision failed: honest metadata only. Never a made-up description. */
    fun metadataReport(isVideo: Boolean, sizeBytes: Long, durationMs: Long, en: Boolean): Report {
        val dur = if (isVideo) durationText(durationMs) else ""
        val facts = (if (isVideo) "video" else "photo") + ", " + sizeText(sizeBytes) + (if (dur.isNotEmpty()) ", " + dur else "")
        val summary = t(en,
            "Images offline analyse nahi ho sakti. Sirf file ki jankari: " + facts + ".",
            "Images cannot be analysed offline. Only file facts: " + facts + ".")
        val conf = confidence(MODE_METADATA, 0, 0, 0, 0, en)
        return Report(MODE_METADATA, summary, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), conf.first, conf.second,
            limits(MODE_METADATA, 0, isVideo, 0, false, 0, false, en))
    }

    fun toJson(r: Report): String {
        fun arr(l: List<String>): String = l.joinToString(",", "[", "]") { MiniJson.quote(it) }
        val ver = r.verified.joinToString(",", "[", "]") { "{\"claim\":" + MiniJson.quote(it.text) + ",\"src\":" + it.src.joinToString(",", "[", "]") + "}" }
        val src = r.sources.mapIndexed { i, s ->
            "{\"n\":" + (i + 1) + ",\"title\":" + MiniJson.quote(s.title) + ",\"url\":" + MiniJson.quote(s.url) + ",\"date\":" + MiniJson.quote(s.date) + "}"
        }.joinToString(",", "[", "]")
        return "{\"mode\":" + MiniJson.quote(r.mode) +
            ",\"summary\":" + MiniJson.quote(r.summary) +
            ",\"seen\":" + arr(r.seen) +
            ",\"text_seen\":" + arr(r.textSeen) +
            ",\"verified\":" + ver +
            ",\"unverified\":" + arr(r.unverified) +
            ",\"sources\":" + src +
            ",\"confidence\":" + MiniJson.quote(r.confidence) +
            ",\"why\":" + MiniJson.quote(r.why) +
            ",\"limits\":" + arr(r.limits) + "}"
    }

    /** Plain text for the chat bubble (no markdown, no address: only title + host; the card opens the page). */
    fun reportText(r: Report, en: Boolean): String {
        val sb = StringBuilder()
        sb.append(r.summary)
        if (r.seen.isNotEmpty()) { sb.append("\n\n").append(t(en, "Kya dikha:", "What I saw:")); r.seen.forEach { sb.append("\n- ").append(it) } }
        if (r.textSeen.isNotEmpty()) { sb.append("\n\n").append(t(en, "Likha hua text:", "Written text:")); r.textSeen.forEach { sb.append("\n- ").append(it) } }
        if (r.verified.isNotEmpty()) {
            sb.append("\n\n").append(t(en, "Sources se mila:", "Found in sources:"))
            r.verified.forEach { sb.append("\n- ").append(it.text).append(" [").append(it.src.joinToString(",")).append(']') }
        }
        if (r.unverified.isNotEmpty()) { sb.append("\n\n").append(t(en, "Verify nahi hua:", "Not verified:")); r.unverified.forEach { sb.append("\n- ").append(it) } }
        if (r.sources.isNotEmpty()) {
            sb.append("\n\n").append(t(en, "Sources:", "Sources:"))
            r.sources.forEachIndexed { i, s -> sb.append("\n[").append(i + 1).append("] ").append(s.title).append(" (").append(hostOf(s.url)).append(')') }
        }
        sb.append("\n\n").append(t(en, "Bharosa: ", "Confidence: ")).append(r.confidence).append(" - ").append(r.why)
        if (r.limits.isNotEmpty()) { sb.append("\n").append(t(en, "Limits:", "Limits:")); r.limits.forEach { sb.append("\n- ").append(it) } }
        return sb.toString()
    }

    /** Short line to speak first (Part 15 reads the rest on "poora padho"). No address, no markdown. */
    fun speechSummary(r: Report, en: Boolean): String =
        clean(r.summary + " " + t(en, "Bharosa " + r.confidence + ".", "Confidence " + r.confidence + "."), 400)

    // ------------------------------------------------------------------ stage lines (real counts only)

    /** Short line for a stage event; the numbers are the real done / total of that stage. */
    fun stageMessage(stage: String, done: Int, total: Int, en: Boolean): String {
        val n = if (total > 0) " " + done.coerceIn(0, total) + "/" + total else ""
        return when (stage) {
            AnalysisStage.INTAKE -> t(en, "File tayyar ho rahi hai", "Getting the file ready")
            AnalysisStage.FRAMES -> t(en, "Frames nikal rahe hain" + n, "Extracting frames" + n)
            AnalysisStage.VISION -> t(en, "AI tasveer dekh raha hai" + n, "The AI is looking at the picture" + n)
            AnalysisStage.SEARCH -> t(en, "Web par jaanch" + n, "Checking on the web" + n)
            AnalysisStage.COMPOSE -> t(en, "Report ban rahi hai", "Writing the report")
            AnalysisStage.DONE -> t(en, "Report taiyar", "Report ready")
            AnalysisStage.FAILED -> t(en, "Analysis nahi ho paya", "The analysis did not work")
            else -> ""
        }
    }
}
