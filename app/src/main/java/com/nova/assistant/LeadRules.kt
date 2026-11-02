package com.nova.assistant

/** Pure rules for explicit, owner-started capture of public business details and outreach drafts. */
object LeadRules {
    data class Candidate(
        val name: String,
        val phone: String = "",
        val place: String = "",
        val url: String = "",
        val sourcePackage: String = ""
    )

    private val ALLOWED_PUBLIC_SOURCES = setOf(
        "com.google.android.apps.maps", "com.google.android.maps",
        "com.android.chrome", "com.google.android.chrome", "com.microsoft.emmx",
        "org.mozilla.firefox", "com.brave.browser", "com.sec.android.app.sbrowser",
        "com.opera.browser", "com.linkedin.android", "com.instagram.android", "com.twitter.android"
    )
    private val PHONE = Regex("(?<![A-Za-z0-9])(?:\\+?91[ -]?)?(?:0?[6-9]\\d{9}|[1-9]\\d{7,14})(?!\\d)")
    private val URL = Regex("(?i)(?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(?:/[^\\s]*)?")
    private val SKIP = setOf(
        "search", "directions", "reviews", "review", "photos", "photo", "website", "call", "save", "share", "menu",
        "open", "closed", "open now", "more", "less", "route", "nearby", "overview", "updates", "posts", "contact"
    )

    fun isAllowedPublicSource(pkg: String): Boolean = pkg in ALLOWED_PUBLIC_SOURCES

    /** Capture only the currently visible, owner-selected public business page; never reads chat or notifications. */
    fun parseVisible(pkg: String, rawLines: List<String>): Candidate? {
        if (!isAllowedPublicSource(pkg)) return null
        val lines = rawLines.asSequence().map { clean(it) }.filter { it.length >= 2 }
            .distinct().take(300).toList()
        if (lines.isEmpty()) return null
        val phone = lines.asSequence().mapNotNull { line -> PHONE.find(line)?.value?.let(::cleanPhone) }
            .firstOrNull { it.filter(Char::isDigit).length in 8..15 }.orEmpty()
        val url = lines.asSequence().flatMap { URL.findAll(it).map { m -> m.value } }
            .map { if (it.startsWith("http", true)) it else "https://$it" }
            .firstOrNull { it.startsWith("https://") && !it.contains("google.com/maps", true) }.orEmpty()
        val name = lines.firstOrNull { line ->
            val low = line.lowercase()
            line.length in 3..100 && low !in SKIP && PHONE.containsMatchIn(line).not() && URL.containsMatchIn(line).not() &&
                !Regex("\\b[0-5]\\.[0-9]\\b.*(?:star|rating|review)", RegexOption.IGNORE_CASE).containsMatchIn(line) &&
                !low.contains("directions") && !low.contains(" km ") && !low.contains(" miles")
        }.orEmpty()
        if (name.isBlank() || (phone.isBlank() && url.isBlank() && lines.size < 3)) return null
        val place = lines.firstOrNull { line ->
            val low = line.lowercase()
            (low.contains("road") || low.contains("street") || low.contains("sector") || low.contains("nagar") ||
                low.contains("market") || low.contains("uttar pradesh") || low.contains("india") || Regex("\\b\\d{6}\\b").containsMatchIn(line)) &&
                line != name && !PHONE.containsMatchIn(line) && !URL.containsMatchIn(line)
        }.orEmpty().take(140)
        return Candidate(name.take(100), phone, place, url.take(220), pkg)
    }

    fun draft(name: String, place: String, language: String, aiDraft: String = ""): String {
        val safeName = clean(name).take(80).ifBlank { "aapke business" }
        val fromAi = cleanDraft(aiDraft)
        if (fromAi.isNotBlank()) return fromAi
        return if (language == "en") {
            "Hello, I came across $safeName${if (place.isBlank()) "" else " in ${clean(place).take(60)}"} and would like to learn more about your services. Could you share the details? Thank you."
        } else {
            "Namaste, mujhe $safeName ke services ke baare mein jaankari chahiye. Kya aap details share kar sakte hain? Dhanyavaad."
        }
    }

    fun cleanDraft(raw: String): String = clean(raw).replace(Regex("(?i)\\b(api key|password|otp|verification code)\\b[^.!?]{0,60}"), "")
        .trim().take(450)

    fun clean(raw: String): String = raw.replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ")
        .replace(Regex("\\s+"), " ").trim().take(240)

    private fun cleanPhone(raw: String): String = raw.filter { it.isDigit() || it == '+' }.take(18)

    /** Detect an explicit lead-acquisition request so it cannot fall through to the website builder or generic agent.
     * This does not claim to fetch leads automatically; the app currently captures owner-selected public listings.
     */
    fun isLeadAcquisitionRequest(raw: String): Boolean {
        val n = Logic.norm(raw).removePrefix("nova ").trim()
        if (n.isBlank() || n.length > 220) return false
        if (n.contains("website") || n.contains("web site") || n.contains("landing page") || n.contains("portfolio")) return false
        val leadTerms = listOf("lead", "leads", "customer", "customers", "client", "clients", "grahak", "business contact")
        val actionTerms = listOf("lao", "lana", "lane", "dhundo", "dhoondo", "find", "search", "generate", "nikalo", "chahiye", "collect", "bring", "mila", "dilao")
        return leadTerms.any { n.split(Regex("\\s+")).contains(it) || n.contains(it) } &&
            actionTerms.any { n.split(Regex("\\s+")).contains(it) || n.contains(it) }
    }

    fun command(raw: String): Logic.Cmd? {
        val n = Logic.norm(raw).removePrefix("nova ").trim()
        if (n in setOf("lead capture", "lead save", "save lead", "save this lead", "is business ko lead mein save karo", "is business ko lead me save karo", "ye business save karo", "lead jodo", "business save karo", "is page ko lead mein save karo", "is page ko lead me save karo")) return Logic.Cmd("lead_capture")
        if (n in setOf("lead list", "leads list", "leads dikhao", "meri leads", "lead report", "lead drafts dikhao", "drafts dikhao", "lead history")) return Logic.Cmd("lead_list")
        if (n in setOf("lead draft", "lead drafts", "lead draft banao", "leads ke draft banao", "lead draft hindi", "lead draft english", "lead draft angrezi")) {
            return Logic.Cmd("lead_draft", if (n.contains("english") || n.contains("angrezi")) "en" else "hi")
        }
        if (n in setOf("lead bhejo", "leads bhejo", "lead send", "send leads", "approved leads send karo")) return Logic.Cmd("lead_send")
        if (n in setOf("lead clear", "leads clear", "leads saaf karo", "lead list saaf karo")) return Logic.Cmd("lead_clear")
        val m = Regex("^(?:lead limit|leads limit) ([0-9]{1,2})$").find(n)
        if (m != null) return Logic.Cmd("lead_limit", num = m.groupValues[1].toIntOrNull() ?: 20)
        return null
    }
}
