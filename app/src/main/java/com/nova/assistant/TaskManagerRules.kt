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
        Entry("agent", "Lamba kaam (agent)", "app ya website mein kai kadam ka kaam khud karta hai (dhoondo, compare karo, bharo)", "upar koi tool fit na ho aur kaam mein 2+ kadam hon", Device.WEB,
            listOf("dhundh", "compare", "sasta", "search karke"), "")
    )

    private val DENY = Regex("\\b(otp|cvv|passcode|password|pin code|paise bhej\\w*|paisa bhej\\w*|money transfer|bank transfer|upi pin)\\b", RegexOption.IGNORE_CASE)

    /** Words that are never acted on (owner decision). */
    fun isDenied(said: String): Boolean = DENY.containsMatchIn(said)

    fun byId(id: String): Entry? = ENTRIES.firstOrNull { it.id == id }

    /** Compact list added to the brain prompt (kept short: one line per tool). */
    fun catalogForAi(): String {
        val sb = StringBuilder()
        sb.append("\n\nJARVIS TOOL LIST. If one of these fits what the owner wants, ALSO return \"agent\":\"<id>\" and \"say\":\"<his request rewritten in the SAME style as that tool's example, keeping every name, number and topic>\". ")
        sb.append("If none fits use \"agent\":\"none\" and \"say\":\"\". If he asks for payment, bank, password, OTP, PIN or CVV use \"agent\":\"denied\".\n")
        for (e in ENTRIES) if (e.example.isNotBlank()) sb.append(e.id).append(" | ").append(e.does).append(" | when: ").append(e.whenUse).append(" | example: ").append(e.example).append('\n')
        sb.append("Return ONE JSON object: {\"mode\":\"answer|tools|agent\",\"understood\":\"...\",\"agent\":\"<id|none|denied>\",\"say\":\"...\"}")
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
