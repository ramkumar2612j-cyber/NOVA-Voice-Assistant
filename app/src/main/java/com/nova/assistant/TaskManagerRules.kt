package com.nova.assistant

/**
 * v7.191 Part L3: JARVIS KAAM-MANAGER (add-only, pure Kotlin, unit-tested, no Android classes).
 *
 * The boss says a job in his own words. This registry lists every agent / automation JARVIS already has: what it does, WHEN to use it and
 * one example in the exact style its local command understands. The brain AI (JarvisBrain) is given the list, picks the right entry and rewrites
 * the boss's sentence into that example style ("say"). The service then runs that sentence through the normal local command path
 * (Logic.classify -> runCommand), so the existing, tested tool does the work. Anything unclear falls back to the old road unchanged.
 *
 * HARD DENY (owner decision, never changed): payment, bank, password, OTP, PIN, CVV. This file never acts by itself.
 */
internal object TaskManagerRules {
    enum class Device { PHONE, LAPTOP, WEB, BUSINESS }

    class Entry(val id: String, val name: String, val does: String, val whenUse: String, val device: Device, val keywords: List<String>, val example: String)

    val ENTRIES: List<Entry> = listOf(
        Entry("site", "Website banana", "restaurant / shop / portfolio ki poori website banata hai", "boss ko website, landing page ya online page chahiye", Device.BUSINESS,
            listOf("website", "site", "landing page", "web page"), "jarvis ek restaurant ki website banao"),
        Entry("publish", "Website publish", "bani hui website ko internet par live karta hai", "boss bole website online / live / publish karo", Device.BUSINESS,
            listOf("publish", "live karo", "online daalo", "online karo"), "jarvis website publish karo"),
        Entry("leads", "Leads dhoondhna", "kisi shehar / dhandhe ke asli customers aur unke contact dhoondta hai", "boss ko naye customers, leads ya clients chahiye", Device.BUSINESS,
            listOf("lead", "leads", "customer dhoond", "client dhoond", "grahak"), "jarvis delhi me restaurant ke genuine leads dhoondo"),
        Entry("orders", "Orders / dhandha status", "website aur business ke orders dikhata, status badalta, biz status batata", "boss ko orders, bikri ya business ka haal jaanna hai", Device.BUSINESS,
            listOf("order", "orders", "biz", "business status", "bikri", "dhandha"), "jarvis orders dikhao"),
        Entry("offer", "Offer / coupon", "website par offer ya discount code lagata hai", "boss bole offer, discount ya coupon lagao", Device.BUSINESS,
            listOf("offer", "discount", "coupon", "sale"), "jarvis offer 20% off code SK20"),
        Entry("autopilot", "Autopilot", "business ke kaam khud, har din apne aap karta hai (leads, replies, report)", "boss bole sab kuch khud chalta rahe / autopilot", Device.BUSINESS,
            listOf("autopilot", "auto pilot", "khud chalao", "apne aap chalao"), "jarvis autopilot setup karo"),
        Entry("callagent", "Call agent", "boss ki taraf se phone karke baat karta hai aur sandesh pahunchata hai", "boss bole kisi ko call karke kuch bolo", Device.PHONE,
            listOf("call karke", "phone karke", "bol dena", "bolo ki"), "jarvis papa ko call karke bolo main sham tak aaunga"),
        Entry("insta", "Instagram search", "Instagram par kisi ka profile / account dhoondta hai", "boss bole instagram par kisi ko dhoondo", Device.PHONE,
            listOf("instagram", "insta", "profile"), "jarvis instagram par rahul_sharma search karo"),
        Entry("story", "Story / post lagana", "WhatsApp / Instagram story ya post lagata hai", "boss bole story ya post lagao", Device.PHONE,
            listOf("story", "post lagao", "status lagao"), "jarvis story lagao"),
        Entry("screen", "Screen dekhna", "phone ki screen padhkar batata hai ki kya chal raha hai", "boss bole ye kya hai / screen dekho / ye padho", Device.PHONE,
            listOf("screen", "ye kya hai", "kya chal raha", "padh kar"), "jarvis screen dekho"),
        Entry("video", "Video analyse", "video / reel dekhkar batata hai kya hai, kaun si movie hai", "boss bole ye video ya reel samjhao / movie batao", Device.PHONE,
            listOf("video", "reel", "movie", "film"), "jarvis ye video analyse karo"),
        Entry("camera", "Camera se dekhna", "peeche / aage ke camera se saamne ki cheez pehchanta hai", "boss bole camera se dekho ye kya hai", Device.PHONE,
            listOf("camera se", "peeche wale camera", "saamne kya"), "jarvis peeche wale camera se dekho, ye kya hai"),
        Entry("image", "Photo banana", "AI se tasveer banata hai", "boss ko koi nayi photo / tasveer chahiye", Device.WEB,
            listOf("photo banao", "tasveer", "image banao", "picture banao"), "jarvis ek billi ki photo banao"),
        Entry("appbuild", "App banana", "chhoti app / calendar / tool app banata hai", "boss ko koi nayi chhoti app chahiye", Device.WEB,
            listOf("app banao", "application banao"), "jarvis chhath calendar app banao"),
        Entry("learn", "Kuch seekhna", "kisi topic ke baare mein seekh kar yaad rakhta hai", "boss bole is topic ke baare mein seekho", Device.PHONE,
            listOf("seekho", "sikho", "learn"), "jarvis solar panel ke baare mein seekho"),
        Entry("openweb", "Kai apps / sites kholna", "ek saath kai app ya website kholta hai", "boss bole ye sab kholo", Device.PHONE,
            listOf("kholo", "open karo", "chrome pe"), "jarvis youtube google aur flipkart chrome pe kholo"),
        Entry("laptop", "Laptop ka kaam", "jude hue laptop par kaam bhejta hai", "boss ka kaam laptop par hona hai", Device.LAPTOP,
            listOf("laptop"), "jarvis laptop par chrome kholo"),
        Entry("wa", "WhatsApp message", "kisi contact ko WhatsApp par message bhejta hai", "boss bole kisi ko whatsapp karo / message bhejo", Device.PHONE,
            listOf("whatsapp", "msg bhej", "message bhej"), "jarvis rahul ko whatsapp karo ki main late aaunga"),
        Entry("gmail", "Gmail", "mail padhta, dikhata aur likhne me madad karta hai", "boss bole mail dekho / inbox / email", Device.PHONE,
            listOf("gmail", "email", "mail dekho", "inbox"), "jarvis gmail me naye mail dikhao"),
        Entry("music", "Music", "gaana ya song chalata hai", "boss bole gaana chalao / song lagao", Device.PHONE,
            listOf("gaana", "song", "music", "gana"), "jarvis arijit ka gaana chalao"),
        Entry("agent", "Lamba kaam (agent)", "app ya website mein kai kadam ka kaam khud karta hai (dhoondo, compare karo, bharo)", "upar koi tool fit na ho aur kaam mein 2+ kadam hon", Device.WEB,
            listOf("dhundh", "compare", "sasta", "search karke"), "")
    )

    /** v7.198: HOW each agent works, in plain words (answers "kaun sa agent kab, kaise"). Fallback = its example. */
    private val HOW = mapOf(
        "site" to "poochta nahi, seedha design + likhai karke front screen par dikhata hai, phir publish bol sakte ho",
        "publish" to "GitHub se jodkar website ko internet par live karta hai, link deta hai",
        "leads" to "web par dhoondta hai, asli aur genuine contacts hi rakhta hai, list dikhata hai",
        "orders" to "store / website ke orders padhta hai (sirf padhna, paisa nahi), card par dikhata hai",
        "offer" to "website par code aur discount lagata hai",
        "autopilot" to "roz apne aap leads, replies aur report chalata hai, aap rok sakte ho",
        "callagent" to "call lagata hai, aapka sandesh bolta hai, jawab ka saar batata hai",
        "insta" to "Instagram kholkar naam search karta hai, sahi profile dikhata hai",
        "story" to "app kholkar story / post lagane ke kadam khud karta hai",
        "screen" to "screen padhta hai aur batata hai kya chal raha hai",
        "video" to "video ke frame dekhkar batata hai kya hai",
        "camera" to "camera kholkar saamne ki cheez pehchanta hai",
        "image" to "AI se tasveer banakar gallery me daalta hai",
        "appbuild" to "pehle research, phir code, phir build aur install; personal app (scheduler, reminder) par research nahi chahiye",
        "learn" to "topic web se padhkar yaad rakhta hai, baad me puchh sakte ho",
        "openweb" to "ek saath kai app / site kholta hai",
        "laptop" to "jude hue laptop par kaam bhejta hai",
        "wa" to "contact dhoondta hai, message likhta hai, bhejne se pehle aapse haan / nahi poochta",
        "gmail" to "inbox padhta hai, naye mail batata hai",
        "music" to "gaana dhoondkar seedha chalata hai",
        "agent" to "screen par dekhta, tap karta, likhta hai; har kadam ke baad check karta hai aur atke to dusra tareeka try karta hai"
    )
    fun howOf(e: Entry): String = HOW[e.id] ?: e.example

    private val GUIDE_RE = Regex("(kaun\\s*sa\\s+agent|kaun\\s*se\\s+agent|agent\\s+kab|kab\\s+kaam\\s+aa|kaise\\s+kaam\\s+kar|kis\\s+kaam\\s+me\\s+kya|which\\s+agent|when\\s+do\\s+you\\s+use)", RegexOption.IGNORE_CASE)
    fun isGuideQuestion(said: String): Boolean = said.trim().split(Regex("\\s+")).size <= 14 && GUIDE_RE.containsMatchIn(said)

    /** "Kaun sa agent kab kaam aata hai, kaise?" - answered at once from the registry. */
    fun guideText(en: Boolean): String {
        val sb = StringBuilder()
        sb.append(if (en) "Which tool I use, when and how:\n" else "Kaun sa agent kab aur kaise kaam karta hai:\n")
        for (e in ENTRIES) sb.append("- ").append(e.name).append(": ").append(e.whenUse).append(" -> ").append(howOf(e)).append('\n')
        sb.append(if (en) "Tell me the job in your own words; I pick the tool, do it and report whether it is complete." else "Bas kaam apne shabdon me bolo; tool main chunta hu, kaam karta hu aur bataunga poora hua ya nahi.")
        return sb.toString()
    }

    /** v7.198: closing line after a routed job, so the boss always hears if it is complete. FAILED / error words are never called complete. */
    fun doneLine(agentName: String, understood: String, result: String, en: Boolean): String {
        val r = result.trim()
        val bad = r.isEmpty() || r.startsWith("FAILED", true) || r.contains("nahi ho paya", true) || r.contains("could not", true) || r.contains("failed", true) || r.contains("samajh nahi", true) || r.contains("did not catch", true)
        val what = if (understood.isNotBlank()) understood else agentName
        return if (bad) (if (en) "Not complete: $what ($agentName). Tell me and I will try another way." else "Poora nahi hua: $what ($agentName). Bolo to dusre tareeke se dobara karun.")
        else (if (en) "Done: $what ($agentName)." else "Kaam poora: $what ($agentName).")
    }

    private val DENY = Regex("\\b(otp|cvv|passcode|password|pin code|paise bhej\\w*|paisa bhej\\w*|money transfer|bank transfer|upi pin)\\b", RegexOption.IGNORE_CASE)

    /** Words that are never acted on (owner decision). */
    fun isDenied(said: String): Boolean = DENY.containsMatchIn(said)

    fun byId(id: String): Entry? = ENTRIES.firstOrNull { it.id == id }

    /** Compact list added to the brain prompt (kept short: one line per tool). */
    fun catalogForAi(): String {
        val sb = StringBuilder()
        sb.append("\n\nJARVIS TOOL LIST. If one of these fits what the owner wants, ALSO return \"agent\":\"<id>\" and \"say\":\"<his request rewritten in the SAME style as that tool's example, keeping every name, number and topic>\". ")
        sb.append("If none fits use \"agent\":\"none\" and \"say\":\"\". If he asks for payment, bank, password, OTP, PIN or CVV use \"agent\":\"denied\".\n")
        for (e in ENTRIES) if (e.example.isNotBlank()) sb.append(e.id).append(" | ").append(e.does).append(" | when: ").append(e.whenUse).append(" | how: ").append(howOf(e)).append(" | example: ").append(e.example).append('\n')
        sb.append("If the job needs TWO OR MORE different tools one after another (for example make a website, publish it, then send the link by WhatsApp), return \"agent\":\"none\" and ALSO \"steps\":[{\"agent\":\"<id>\",\"say\":\"...\"},...] in the order they must run, at most 5 steps, each say in that tool's example style. Use steps ONLY for a real multi-tool job.\n")
        sb.append("Return ONE JSON object: {\"mode\":\"answer|tools|agent\",\"understood\":\"...\",\"agent\":\"<id|none|denied>\",\"say\":\"...\",\"steps\":[]}")
        return sb.toString()
    }

    class Plan(val agentId: String, val say: String, val denied: Boolean)

    private val AGENT_RE = Regex("\"agent\"\\s*:\\s*\"\\s*([A-Za-z_]+)\\s*\"")
    private val SAY_RE = Regex("\"say\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Reads the extra fields of the brain answer. null = nothing usable (old road). A rewritten sentence always starts with "jarvis ". */
    fun parsePlan(raw: String?, said: String): Plan? {
        if (isDenied(said)) return Plan("denied", "", true)
        if (raw.isNullOrBlank()) return null
        val id = AGENT_RE.find(raw)?.groupValues?.get(1)?.lowercase() ?: return null
        if (id == "denied") return Plan("denied", "", true)
        if (id == "none" || id == "agent" || byId(id) == null) return null
        var say = SAY_RE.find(raw)?.groupValues?.get(1).orEmpty().replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\").trim()
        if (say.isBlank() || say.length > 220 || say.any { it.code < 32 }) return null
        if (isDenied(say)) return Plan("denied", "", true)
        if (!say.lowercase().startsWith("jarvis")) say = "jarvis $say"
        return Plan(id, say, false)
    }

    // ---- v7.199 CHAIN: one job that needs several tools in order (website -> publish -> WhatsApp the link) ----

    /** Tools whose command returns at once while the real work keeps running in the background. A chain pauses after one of these (the next step needs its result). */
    private val BACKGROUND = setOf("site", "publish", "leads", "autopilot", "appbuild", "agent", "video", "learn", "image", "laptop")
    fun isBackground(id: String): Boolean = id in BACKGROUND

    const val MAX_STEPS = 5
    private val STEP_RE = Regex("\\{\\s*\"agent\"\\s*:\\s*\"\\s*([A-Za-z_]+)\\s*\"\\s*,\\s*\"say\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\}")

    /** Reads "steps":[{agent,say},...] from the brain answer. null = no usable chain (single-plan road / old road). A denied word anywhere gives one denied Plan.
     *  Fewer than 2 steps, an unknown tool id, a blank / too long / control-char sentence all give null (never a half chain). */
    fun parseSteps(raw: String?, said: String): List<Plan>? {
        if (raw.isNullOrBlank()) return null
        val at = raw.indexOf("\"steps\"")
        if (at < 0) return null
        if (isDenied(said)) return listOf(Plan("denied", "", true))
        val out = ArrayList<Plan>()
        for (m in STEP_RE.findAll(raw.substring(at))) {
            val id = m.groupValues[1].lowercase()
            var say = m.groupValues[2].replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\").trim()
            if (id == "denied") return listOf(Plan("denied", "", true))
            if (byId(id) == null || id == "agent" || id == "none") return null
            if (say.isBlank() || say.length > 220 || say.any { it.code < 32 }) return null
            if (isDenied(say)) return listOf(Plan("denied", "", true))
            if (!say.lowercase().startsWith("jarvis")) say = "jarvis $say"
            out.add(Plan(id, say, false))
            if (out.size > MAX_STEPS) return null
        }
        return if (out.size >= 2) out else null
    }

    /** A step result that must stop the chain (never call an error complete). */
    fun stepFailed(result: String?): Boolean {
        val r = result?.trim().orEmpty()
        return r.isEmpty() || r.startsWith("FAILED", true) || r.contains("nahi ho paya", true) || r.contains("could not", true) || r.contains("failed", true) || r.contains("samajh nahi", true) || r.contains("did not catch", true)
    }

    /** Pending steps are kept as lines "id<TAB>say" (a say never holds control characters, so TAB / newline are safe separators). */
    fun encodeSteps(steps: List<Plan>): String = steps.joinToString("\n") { it.agentId + "\t" + it.say }
    fun decodeSteps(raw: String?): List<Plan> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<Plan>()
        for (ln in raw.split("\n")) {
            val i = ln.indexOf('\t')
            if (i <= 0) continue
            val id = ln.substring(0, i)
            val say = ln.substring(i + 1).trim()
            if (byId(id) == null || say.isBlank() || isDenied(say)) continue
            out.add(Plan(id, say, false))
        }
        return out
    }

    private val CONTINUE_RE = Regex("(aage\\s+badh|aage\\s+ka\\s+kaam|agla\\s+kaam|agla\\s+step|baaki\\s+kaam|baki\\s+kaam|next\\s+step|continue\\s+the\\s+job|carry\\s+on)", RegexOption.IGNORE_CASE)
    /** "Aage badho" - only acted on when a paused chain is waiting. */
    fun isContinue(said: String): Boolean = said.trim().split(Regex("\\s+")).size <= 6 && CONTINUE_RE.containsMatchIn(said)

    /** Final line of a chain: what finished, what is running, what was left. */
    fun chainLine(doneCount: Int, total: Int, paused: Boolean, failed: Boolean, en: Boolean): String = when {
        failed -> if (en) "Stopped: $doneCount of $total steps done. The rest did not run; tell me and I will try another way." else "Ruk gaya: $total me se $doneCount kadam hue. Baaki nahi chale; bolo to dusre tareeke se karun."
        paused -> if (en) "$doneCount of $total steps started. A long step is still running; when it finishes say 'aage badho' and I will do the rest." else "$total me se $doneCount kadam shuru hue. Ek lamba kaam abhi chal raha hai; poora hone par 'aage badho' bolo, baaki main kar dunga."
        else -> if (en) "All $total steps done." else "Saare $total kadam poore hue."
    }

    fun denyReply(en: Boolean): String =
        if (en) "I will not do payment, bank, password, OTP, PIN or CVV jobs. You do those yourself."
        else "Paisa, bank, password, OTP, PIN ya CVV wale kaam main nahi karta. Ye aap khud karna."

    private val DIR_RE = Regex("(kya\\s*kya\\s*kar\\s*sak|tum\\s+kya\\s+kar|what can you do|kaam\\s+ki\\s+list|agents?\\s+ki\\s+list|features?\\s+batao|kaun\\s*kaun\\s*se\\s+kaam|saare\\s+kaam)", RegexOption.IGNORE_CASE)

    /** "Jarvis, tum kya kya kar sakte ho?" - answered at once from the registry, no AI call. */
    fun isDirectoryQuestion(said: String): Boolean = said.trim().split(Regex("\\s+")).size <= 9 && DIR_RE.containsMatchIn(said)

    fun directoryText(en: Boolean): String {
        fun names(d: Device) = ENTRIES.filter { it.device == d }.joinToString(", ") { it.name }
        return if (en)
            "I can do these. Phone: " + names(Device.PHONE) + ". Laptop: " + names(Device.LAPTOP) + ". Business: " + names(Device.BUSINESS) + ". Web: " + names(Device.WEB) + ". Just tell me the job in your own words and I pick the right tool."
        else
            "Main ye sab kar sakta hoon. Phone: " + names(Device.PHONE) + ". Laptop: " + names(Device.LAPTOP) + ". Business: " + names(Device.BUSINESS) + ". Web: " + names(Device.WEB) + ". Bas apne shabdon mein kaam bolo, sahi tool main khud chun lunga."
    }
}
