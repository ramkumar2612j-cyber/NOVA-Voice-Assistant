package com.nova.assistant

/**
 * v7.179 LEAD FINDER rules (Part 1). Pure Kotlin, no Android classes: unit-testable, mirrors the
 * style of CallAgentRules / AutoReplyRules. tools/test_lead_finder_contract.py reads the sets
 * below straight from this file: keep one set per line, words only [a-z0-9].
 *
 * What this is: the owner says "jarvis <city> me <niche> ke genuine leads dhoondho" (or a contact name).
 * JARVIS calls a public business-directory search (OpenStreetMap Nominatim + Overpass, Google web via the
 * search box, JustDial / IndiaMART public listing pages that allow anonymous browse), filters the results
 * (junk / spam / duplicates removed), scores each lead (phone present, website present, reviews, recent
 * activity), and returns a clean JSON list the owner can act on (call / WhatsApp / save).
 *
 * HONEST LIMITS (told to the owner, never hidden):
 *  - JARVIS does NOT scrape behind a login wall, does NOT read the owner's WhatsApp contacts, does NOT
 *    buy data from any broker. Every lead is a PUBLIC business listing that anyone could find.
 *  - "Genuine" is a SCORE (0..100), not a promise. A 90-score lead can still be closed/dead; JARVIS
 *    cannot call every business to verify it is open today. The score is what is verifiable from the
 *    public listing: phone present, website present, recent reviews, address complete.
 *  - Rate-limited: at most MAX_SEARCHES_PER_DAY searches per day, MAX_LEADS_PER_SEARCH leads kept.
 *  - Privacy: JARVIS never stores the owner's own contact list. Leads live in their own small store
 *    and the owner can wipe them with one tap.
 *
 * Safety model (enforced here, not by any model):
 *  - Only PUBLIC business directories. No login walls, no CAPTCHAs bypassed, no ToS violated.
 *  - Never: leads for illegal services (DENY list), bulk email harvest, phone-number-only dumps.
 *  - One live search at a time; STOP ("ruko", "bas karo") always kills it.
 */
object LeadFinderRules {

    /** Max leads returned per search (kept small so the owner can act, not scroll). */
    const val MAX_LEADS_PER_SEARCH = 12

    /** Max searches per day (rate limit, honest). */
    const val MAX_SEARCHES_PER_DAY = 20

    /** Max length of the niche / city text taken from the owner's sentence. */
    const val MAX_FIELD = 60

    /** Max length of a lead name / address / phone in the output. */
    const val MAX_LEAD_FIELD = 120

    // ------------------------------------------------------------------ command parsing

    /** Words that make a sentence a LEAD FINDER request. tools contract reads this line. */
    private val LEAD_TRIGGERS = setOf("leads", "lead", "clients", "client", "customer", "customers", "prospects", "business", "businesses", "dukandaar", "vyapar")

    /** "leads dhoondo / lao / nikalo" style: the owner wants JARVIS to search, not just chat. */
    private val LEAD_DO = setOf("dhoondo", "dhundo", "dhoondho", "lao", "nikalo", "nikaalo", "dhundke", "dhundhke", "find", "search", "fetch", "get", "list", "bring")

    /** "genuine / real / quality" filter words: JARVIS applies the score filter when these appear. */
    private val GENUINE_WORDS = setOf("genuine", "asli", "real", "quality", "sahi", "acche", "ache", "best", "top", "verified", "confirm")

    /** Niche hints the agent can search for (used to validate the niche, never to block). */
    private val NICHE_HINTS = setOf("restaurant", "hotel", "cafe", "dhaba", "salon", "parlour", "parlor", "gym", "doctor", "clinic", "hospital", "dentist", "shop", "store", "bakery", "garage", "mechanic", "electrician", "plumber", "carpenter", "tailor", "lawyer", "advocate", "ca", "chartered", "accountant", "realtor", "broker", "school", "coaching", "tutor", "institute", "photographer", "caterer", "decorator", "florist", "pharmacy", "chemist", "hardware", "furniture", "jewellery", "jewelry", "saree", "garments", "mobile", "electronics", "fmcg", "kirana", "wholesale", "manufacturer", "factory", "supplier")

    /** Cities / regions commonly searched (used only to suggest, never to block). */
    private val REGION_HINTS = setOf("delhi", "mumbai", "bangalore", "bengaluru", "hyderabad", "chennai", "kolkata", "pune", "ahmedabad", "jaipur", "lucknow", "kanpur", "nagpur", "indore", "bhopal", "patna", "surat", "vadodara", "rajkot", "raipur", "ranchi", "gurgaon", "gurugram", "noida", "faridabad", "ghaziabad", "varanasi", "agra", "meerut", "dehradun", "chandigarh", "amritsar", "ludhiana", "jalandhar")

    /** Never searched and never returned. Enforced, not advisory. */
    private val DENY = setOf("illegal", "smuggling", "drug", "drugs", "weapon", "weapons", "gambling", "betting", "escort", "adult", "fake", "counterfeit", "forgery", "hacking", "espionage", "blackmoney", "hawala")

    // ------------------------------------------------------------------ parse + classify

    /** A parsed lead-finder job: niche (what), city (where), genuineOnly (filter). */
    class Parsed(val niche: String, val city: String, val genuineOnly: Boolean)

    /** True when the sentence looks like a LEAD FINDER job ("delhi me restaurant ke leads dhoondo"). */
    fun isLeadJob(text: String): Boolean {
        val t = tokenize(text)
        if (t.isEmpty()) return false
        if (t.any { it in DENY }) return false
        val hasLead = t.any { it in LEAD_TRIGGERS }
        if (!hasLead) return false
        // "leads dhoondo" / "clients lao" / "business dhundke la" -> lead job.
        return t.any { it in LEAD_DO }
    }

    /** Pulls the niche out of the sentence: the noun right before "leads/clients" or a NICHE_HINT word. */
    fun niche(text: String): String {
        val t = tokenize(text)
        val leadIdx = t.indexOfFirst { it in LEAD_TRIGGERS }
        // try the word just before "leads"
        if (leadIdx > 0) {
            val w = t[leadIdx - 1]
            if (w in NICHE_HINTS) return w
        }
        // else the first NICHE_HINT in the sentence
        return NICHE_HINTS.firstOrNull { it in t } ?: ""
    }

    /** Pulls the city / region out of the sentence. "" = no city (JARVIS asks). */
    fun city(text: String): String {
        val t = tokenize(text)
        return REGION_HINTS.firstOrNull { it in t } ?: ""
    }

    /** True when the owner said "genuine / real / quality" -> JARVIS applies the score filter (>= GENUINE_MIN). */
    fun genuineOnly(text: String): Boolean {
        val t = tokenize(text)
        return t.any { it in GENUINE_WORDS }
    }

    /** Full parse in one call. */
    fun parse(text: String): Parsed {
        val n = niche(text).take(MAX_FIELD)
        val c = city(text).take(MAX_FIELD)
        val g = genuineOnly(text)
        return Parsed(n, c, g)
    }

    /** True when a deny word is anywhere in the job: the agent refuses before searching. */
    fun denied(text: String): Boolean {
        val t = tokenize(text)
        return t.any { it in DENY }
    }

    // ------------------------------------------------------------------ filtering + scoring

    /** A single lead as parsed from a directory listing. */
    data class Lead(
        val name: String, val phone: String, val website: String, val address: String,
        val category: String, val reviews: Int, val rating: Double, val recentActivityDays: Int,
        val source: String, val sourceUrl: String
    ) {
        /** 0..100 score. Higher = more genuine (verifiable from the public listing). */
        fun score(): Int {
            var s = 0
            if (name.isNotBlank()) s += 10
            if (phone.isNotBlank() && phone.length >= 7) s += 25
            if (website.isNotBlank()) s += 20
            if (address.isNotBlank()) s += 10
            if (reviews > 0) s += 15
            if (rating >= 4.0) s += 10 else if (rating > 0) s += 5
            if (recentActivityDays in 1..180) s += 10   // active in the last 6 months
            return s.coerceIn(0, 100)
        }
        /** A short reason string for the score (so the owner knows WHY this lead scored N). */
        fun scoreReason(): String {
            val parts = ArrayList<String>()
            if (phone.isBlank()) parts.add("no phone")
            if (website.isBlank()) parts.add("no website")
            if (reviews == 0) parts.add("no reviews")
            if (recentActivityDays > 180 || recentActivityDays == 0) parts.add("stale listing")
            return if (parts.isEmpty()) "phone+web+reviews+active" else parts.joinToString(", ")
        }
    }

    /** Minimum score for the "genuine" filter. */
    const val GENUINE_MIN = 55

    /**
     * Filters a raw list of leads: removes junk (no name, deny-ish name, duplicate phone), keeps the
     * top MAX_LEADS_PER_SEARCH by score. When [genuineOnly], also drops anything below GENUINE_MIN.
     */
    fun filter(raw: List<Lead>, genuineOnly: Boolean): List<Lead> {
        val seen = HashSet<String>()
        val out = ArrayList<Lead>()
        for (l in raw) {
            if (l.name.isBlank()) continue
            if (l.name.lowercase() in DENY) continue
            val phoneKey = l.phone.replace(Regex("\\D"), "")
            if (phoneKey.length >= 7 && !seen.add(phoneKey)) continue   // duplicate phone
            val nameKey = l.name.lowercase().trim()
            if (!seen.add("n|$nameKey")) continue                       // duplicate name in same city
            out.add(l)
        }
        val scored = out.sortedByDescending { it.score() }
        val capped = scored.take(MAX_LEADS_PER_SEARCH)
        return if (genuineOnly) capped.filter { it.score() >= GENUINE_MIN } else capped
    }

    /** True when the sentence is a STOP for the live lead search (used by the service loop). */
    fun isStop(text: String): Boolean = StopRules.isStop(text)

    // ------------------------------------------------------------------ state + report

    /** Live states, exactly the ones the card shows. */
    enum class State { IDLE, SEARCHING, FETCHING, FILTERING, DONE, FAILED }

    /** One line of the live log (ts, state, text). */
    data class Line(val ts: Long, val state: State, val text: String)

    /** The report JARVIS speaks when the search ends. Honest about what it could and could not do. */
    fun report(state: State, niche: String, city: String, count: Int, en: Boolean): String {
        val what = if (niche.isBlank()) "businesses" else niche
        val where = if (city.isBlank()) "nearby" else city
        return when (state) {
            State.DONE -> if (en)
                "Found $count $what leads in $where. I filtered out junk and duplicates, scored each by phone, website, reviews and recent activity. Tap any lead to call or message. I cannot promise any lead will convert - they are public listings, not verified buyers."
            else
                "$where me $count $what leads mil gaye. Junk aur duplicate hata diye, har lead ko phone / website / reviews / recent activity se score diya hai. Kisi pe tap karke call ya message karo. Koi lead convert hoga iski guarantee nahi - ye public listings hain, verified buyers nahi."
            State.FAILED -> if (en)
                "The lead search could not finish (no internet, or the directory blocked the request). I can try again later, sir."
            else
                "Lead search poora nahi ho paya (internet nahi ya directory ne block kar diya). Thodi der baad try karun, sir?"
            else -> if (en) "The lead finder is still working." else "Lead finder abhi chal raha hai."
        }
    }

    /** True when the search returned no usable leads: the card shows the EMPTY tag. */
    fun emptyResult(state: State, count: Int): Boolean = state == State.DONE && count == 0

    // ------------------------------------------------------------------ tokenizing + JSON

    /** Lowercase alphanumeric tokens, same shape as CallAgentRules.tokenize. */
    private fun tokenize(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ')
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }
    }

    /** Serializes a lead to JSON for the UI card / bridge. */
    fun leadToJson(l: Lead): org.json.JSONObject {
        val s = l.score()
        return org.json.JSONObject()
            .put("name", l.name.take(MAX_LEAD_FIELD))
            .put("phone", l.phone.take(MAX_LEAD_FIELD))
            .put("web", l.website.take(MAX_LEAD_FIELD))
            .put("addr", l.address.take(MAX_LEAD_FIELD))
            .put("cat", l.category.take(MAX_LEAD_FIELD))
            .put("rev", l.reviews)
            .put("rate", l.rating)
            .put("days", l.recentActivityDays)
            .put("src", l.source.take(40))
            .put("url", l.sourceUrl.take(200))
            .put("score", s)
            .put("why", l.scoreReason())
    }

    /** Parses a lead back from JSON (used by the bridge when the card re-opens). */
    fun leadFromJson(o: org.json.JSONObject): Lead = Lead(
        o.optString("name", ""),
        o.optString("phone", ""),
        o.optString("web", ""),
        o.optString("addr", ""),
        o.optString("cat", ""),
        o.optInt("rev", 0),
        o.optDouble("rate", 0.0),
        o.optInt("days", 0),
        o.optString("src", ""),
        o.optString("url", "")
    )
}
