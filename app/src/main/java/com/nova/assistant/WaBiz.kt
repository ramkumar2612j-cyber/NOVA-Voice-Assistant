package com.nova.assistant

/**
 * WhatsApp / WhatsApp Business watch for the owner's OWN business chats (SK Fashion).
 * Pure Kotlin, no Android classes, so every rule is unit-testable.
 *
 * Owner says e.g. "business watch 10 ghante": for that window NOVA triages every new chat message into
 *   URGENT  -> owner is told (order / payment / complaint / return / cancel / angry customer)
 *   FAQ     -> answered ONLY from the owner's own catalogue notes (rate, size, colour, delivery ...)
 *   IGNORE  -> hi/hello/thanks/forwards: no action
 * Safety (kept from the rest of NOVA):
 *  - Default OFF, never persisted across restart, hard cap of [MAX_HOURS] hours.
 *  - Auto-reply is OFF by default (approval mode: reply is only drafted). Owner must switch it on explicitly.
 *  - Messages that look like OTP / password / code are never read, stored or answered.
 *  - Money, discount, refund, complaint, legal or unknown questions are NEVER auto-answered: they go to the owner.
 *  - Rate limits: per-sender cooldown, per-sender cap, global cap, human-like delay.
 *  - Text lives in memory only, cleared when the window ends.
 */
object WaBiz {
    const val MAX_HOURS = 12
    const val DEFAULT_HOURS = 10
    const val COOLDOWN_MS = 60_000L
    const val MAX_REPLIES_PER_SENDER = 3
    const val MAX_REPLIES_TOTAL = 150
    const val MIN_DELAY_S = 5
    const val MAX_DELAY_S = 20
    const val MAX_ITEMS = 300

    enum class Cat { URGENT, FAQ, IGNORE, SENSITIVE }

    data class Item(
        val sender: String,
        val text: String,
        val ts: Long,
        val cat: Cat,
        val draft: String,
        val replied: Boolean
    )

    @Volatile var untilMs: Long = 0L
        private set
    /** false = approval mode (draft only). true = NOVA sends FAQ answers by itself. */
    @Volatile var autoReply: Boolean = false
        private set

    private val items = ArrayList<Item>()
    private val perSender = HashMap<String, Int>()
    private val lastReplyAt = HashMap<String, Long>()
    private var totalReplies = 0
    private val seen = HashMap<String, Long>()
    const val DEDUPE_MS = 120_000L
    private val SUMMARY_LINE = Regex("^(\\d+ (new )?messages?( from \\d+ chats?)?|checking for new messages|\\d+ new messages?|backup in progress.*|waiting for this message.*)$")

    /** Owner-written answers: keyword(s) -> reply. Lower-case keys. Set from the app, memory only here. */
    @Volatile var catalogue: Map<String, String> = emptyMap()

    fun clampHours(h: Int): Int = h.coerceIn(1, MAX_HOURS)

    @Synchronized fun start(now: Long, hours: Int, auto: Boolean = false) {
        clear()
        untilMs = now + clampHours(hours) * 3_600_000L
        autoReply = auto
    }

    @Synchronized fun stop() {
        untilMs = 0L
        autoReply = false
    }

    @Synchronized fun setAuto(on: Boolean) { if (untilMs != 0L) autoReply = on }

    fun active(now: Long): Boolean = untilMs != 0L && now < untilMs

    @Synchronized fun clear() {
        items.clear(); perSender.clear(); lastReplyAt.clear(); seen.clear(); totalReplies = 0
    }

    // ---------------------------------------------------------------- classification

    private val SECRET = Regex("\\b(otp|password|passcode|cvv|pin|verification code|one time password)\\b|\\b\\d{4,8}\\b.*\\b(code|otp)\\b|\\b(code|otp)\\b.*\\b\\d{4,8}\\b")

    private val URGENT_WORDS = listOf(
        "order", "payment", "paid", "pay kar", "paisa", "paise", "refund", "return", "exchange", "cancel", "complaint",
        "shikayat", "kharab", "galat", "damage", "tuta", "phata", "fraud", "scam", "dhokha", "urgent", "jaldi", "abhi chahiye",
        "advance", "cod", "utr", "screenshot", "tracking", "nahi aaya", "nahi mila", "late", "wapas", "bulk", "wholesale",
        "thok", "discount", "kam karo", "last price", "gst", "bill", "invoice", "legal", "consumer", "police"
    )
    private val FAQ_WORDS = listOf(
        "rate", "price", "kitne", "kitna", "daam", "size", "colour", "color", "rang", "available", "stock", "hai kya",
        "delivery", "kab tak", "shipping", "catalogue", "catalog", "collection", "new arrival", "fabric", "kapda", "timing", "address", "kahan"
    )
    private val GREETING = Regex("^(hi+|hello+|hey+|hlo+|namaste|namaskar|ram ram|good (morning|evening|night)|ok(ay)?|thanks?|thank you|shukriya|dhanyavad|👍|🙏)[ !.🙏👍]*$")

    fun looksSensitive(raw: String): Boolean = SECRET.containsMatchIn(raw.lowercase())

    fun classify(raw: String): Cat {
        val s = raw.lowercase().trim()
        if (s.isBlank()) return Cat.IGNORE
        if (looksSensitive(s)) return Cat.SENSITIVE
        if (GREETING.matches(s)) return Cat.IGNORE
        // Money / complaint / order words always beat FAQ words: owner decides these.
        if (URGENT_WORDS.any { s.contains(it) }) return Cat.URGENT
        if (FAQ_WORDS.any { s.contains(it) }) return Cat.FAQ
        // Unknown but real sentence: do not guess, let the owner see it.
        return if (s.length >= 12) Cat.URGENT else Cat.IGNORE
    }

    /** Catalogue answer, or "" if the owner never wrote one (then the message goes to the owner, not guessed). */
    fun answerFromCatalogue(raw: String): String {
        val s = raw.lowercase()
        for ((k, v) in catalogue) if (k.isNotBlank() && s.contains(k.lowercase()) && v.isNotBlank()) return v.take(500)
        return ""
    }

    /** Human-like delay, deterministic from a seed so it is testable. */
    fun delaySeconds(seed: Int): Int = MIN_DELAY_S + Math.floorMod(seed, MAX_DELAY_S - MIN_DELAY_S + 1)

    // ---------------------------------------------------------------- decision

    data class Decision(val item: Item, val sendNow: Boolean, val delayS: Int, val tellOwner: Boolean)

    /** Called once per new chat message while the window is active. null = ignore completely. */
    @Synchronized fun onMessage(sender: String, text: String, now: Long, canReply: Boolean, seed: Int = 0): Decision? {
        if (!active(now)) return null
        val who = Driving.cleanText(sender).take(40).ifBlank { "Customer" }
        // WhatsApp re-posts the same notification and posts "2 new messages" summaries: never alert or answer those twice.
        if (SUMMARY_LINE.matches(text.lowercase().trim())) return null
        val dk = who + "|" + text.take(80)
        val prev = seen[dk]
        if (prev != null && now - prev < DEDUPE_MS) return null
        seen[dk] = now
        if (seen.size > 500) seen.clear()
        val cat = classify(text)
        if (cat == Cat.SENSITIVE) {
            record(Item(who, "", now, cat, "", false))
            return null
        }
        if (cat == Cat.IGNORE) return null
        val clean = Driving.cleanText(text)
        val draft = if (cat == Cat.FAQ) answerFromCatalogue(text) else ""
        val faqAnswerable = cat == Cat.FAQ && draft.isNotBlank()
        val underCaps = (perSender[who] ?: 0) < MAX_REPLIES_PER_SENDER &&
            totalReplies < MAX_REPLIES_TOTAL &&
            now - (lastReplyAt[who] ?: 0L) >= COOLDOWN_MS
        val send = faqAnswerable && autoReply && canReply && underCaps
        if (send) {
            perSender[who] = (perSender[who] ?: 0) + 1
            lastReplyAt[who] = now
            totalReplies++
        }
        // FAQ without a catalogue answer is promoted to URGENT so the owner sees it instead of NOVA guessing.
        val finalCat = if (cat == Cat.FAQ && !faqAnswerable) Cat.URGENT else cat
        val item = Item(who, clean, now, finalCat, draft, send)
        record(item)
        return Decision(item, send, if (send) delaySeconds(seed) else 0, finalCat == Cat.URGENT)
    }

    private fun record(i: Item) {
        items.add(i)
        while (items.size > MAX_ITEMS) items.removeAt(0)
    }

    // ---------------------------------------------------------------- reports

    @Synchronized fun snapshot(): List<Item> = items.toList()

    @Synchronized fun summary(hinglish: Boolean = true): String {
        val urgent = items.count { it.cat == Cat.URGENT }
        val faq = items.count { it.cat == Cat.FAQ }
        val sent = items.count { it.replied }
        val hidden = items.count { it.cat == Cat.SENSITIVE }
        val top = items.filter { it.cat == Cat.URGENT }.takeLast(3).joinToString("; ") { "${it.sender}: ${it.text.take(50)}" }
        return if (hinglish) {
            "Business watch: ${items.size} message dekhe, $urgent zaruri, $faq sawal catalogue se, $sent reply gaye." +
                (if (hidden > 0) " $hidden code/password jaise message nahi padhe." else "") +
                (if (top.isNotBlank()) " Zaruri: $top." else "")
        } else {
            "Business watch: ${items.size} messages, $urgent urgent, $faq FAQ, $sent replies sent." +
                (if (hidden > 0) " $hidden code-like messages skipped." else "") +
                (if (top.isNotBlank()) " Urgent: $top." else "")
        }
    }

    /** Stateless checks for SelfTest (never touches a running watch window). */
    fun selfCheck(): List<Triple<Boolean, String, String>> = listOf(
        Triple(classify("is kurti ka rate kya hai") == Cat.FAQ && classify("mera order nahi aaya") == Cat.URGENT && classify("hi") == Cat.IGNORE,
            "Business watch: chat triage", "FAQ / zaruri / ignore sahi"),
        Triple(classify("rate kam karo last price batao") == Cat.URGENT, "Business watch: paise wale sawal owner ko", "auto-reply nahi"),
        Triple(classify("your otp is 482913") == Cat.SENSITIVE, "Business watch: OTP nahi padhta", "SENSITIVE"),
        Triple(command("business watch 10 ghante")?.arg == "10" && command("business watch 40 ghante")?.arg == "12" && command("business watch band")?.kind == "biz_off",
            "Business watch: voice command", "10 ghante, max 12, band"),
        Triple(clampHours(99) == MAX_HOURS && delaySeconds(7) in MIN_DELAY_S..MAX_DELAY_S, "Business watch: limits", "12 ghante cap, 5-20 sec delay")
    )

    // ---------------------------------------------------------------- voice command parsing

    /** "business watch 10 ghante", "business watch auto on", "business watch band". null = not for us. */
    fun command(n: String): Logic.Cmd? {
        val s = n.lowercase().trim()
        Regex("^(?:business |whatsapp )?catalogue (.+?) (?:matlab|jawab|answer) (.+)$").find(s)?.let {
            return Logic.Cmd("biz_catalogue", it.groupValues[1].trim() + "|" + it.groupValues[2].trim())
        }
        if (!(s.contains("business watch") || s.contains("chat watch") || s.contains("whatsapp watch"))) return null
        if (Regex("\\b(off|band|bandh|stop|khatam)\\b").containsMatchIn(s)) return Logic.Cmd("biz_off")
        if (Regex("\\bauto\\b.*\\b(on|chalu|start)\\b").containsMatchIn(s)) return Logic.Cmd("biz_auto_on")
        if (Regex("\\bauto\\b.*\\b(off|band|bandh|stop)\\b").containsMatchIn(s)) return Logic.Cmd("biz_auto_off")
        if (Regex("\\b(summary|report|kya hua)\\b").containsMatchIn(s)) return Logic.Cmd("biz_summary")
        val h = Regex("(\\d{1,2})\\s*(ghante|ghanta|hour|hours|hr|hrs)").find(s)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_HOURS
        return Logic.Cmd("biz_on", clampHours(h).toString())
    }
}
