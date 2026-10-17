package com.nova.assistant

/**
 * Part 7 (owner request): AI SMART REPLY. When ON, NOVA talks to the sender by itself (WhatsApp etc.), understands the message,
 * answers like a polite assistant, and tells the owner afterwards (history + phone notification + voice when idle).
 * Pure Kotlin (no Android classes) so every rule is unit-testable. Part 10 (owner request): NO money / link / number blocking any more.
 *  - OFF by default; groups, code / OTP / password messages, summary lines and messages without a Reply button are never answered (AutoReplyRules, unchanged);
 *  - the AI is told it is the owner's ASSISTANT (it never pretends to be the owner) and it talks about anything the person raises (prices, orders, payments too);
 *  - it may state a fact (price, stock, date, address, UPI, link ...) ONLY if the owner gave it for this chat (FACTS) or the person wrote it themselves; it never invents one.
 *    What it does not know it says it will ask the owner, adds "[OWNER]", the owner is told, tells NOVA (voice WhatsApp to that person), and the chat goes on;
 *  - the owner is told about EVERY chat (history + phone notification + voice when idle); "[OWNER]" and money/order words only make that notice say "zaruri";
 *  - one thing is still held back: a reply that contains OTP / password / PIN / CVV words or a card-like number is replaced by a polite waiting line;
 *  - per sender: 1 reply per 15 s and 12 per hour, 80 per day in total; only the last 6 turns of a chat and 6 owner facts are kept, in memory only.
 */
object SmartReplyRules {
    const val MAX_TEXT = 280
    const val SENDER_COOLDOWN_MS = 15_000L
    const val SENDER_PER_HOUR = 12
    const val DAY_CAP = 80
    const val MAX_TURNS = 6
    const val MAX_FACTS = 6
    const val HOLD_LINE = "Aapka message mil gaya. Main iske baare mein pooch kar jaldi batata hoon."
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    class Result(val text: String, val needOwner: Boolean, val skip: Boolean)

    /** Part 8: the owner's name for the AI. Only letters, digits, space . - ' (so a name can never carry instructions), max 30. */
    fun cleanName(raw: String): String =
        raw.map { if (it.isWhitespace()) ' ' else it }.joinToString("").filter { it.isLetterOrDigit() || it == ' ' || it == '.' || it == '-' || it == '\'' }.replace(Regex("\\s+"), " ").trim().take(30)

    fun systemPrompt(ownerName: String, hinglish: Boolean, business: Boolean = false, facts: List<String> = emptyList()): String {
        val who = cleanName(ownerName).ifBlank { "the owner" }
        return "You are NOVA, the AI assistant of $who, replying on WhatsApp while $who is busy. Say once at the start of a chat that you are $who's assistant (never repeat that later). " +
            "READ the whole chat so far and answer exactly what the person just said or asked, in the SAME language and style they wrote (Hinglish if they wrote Hinglish), friendly, at most 2 short sentences, plain text, no markdown. " +
            "Never ignore their question and never repeat an earlier answer: continue the conversation naturally from where it is. " +
            "You may talk about anything the person raises, including prices, payments, orders and meetings. " +
            "You may state a fact (price, rate, stock, date, time, address, bank or UPI detail, link, phone number) ONLY if it is in the FACTS below or the person wrote it themselves earlier in this chat. NEVER invent or guess a fact. " +
            "If the person asks something you do not know, say in one short line that you will ask $who and come back, and add the tag [OWNER] at the end; $who will tell you the answer and then you carry on. " +
            "If no reply is needed (just thanks, ok, an emoji), answer only [SKIP]. Never give or ask for an OTP, password, PIN or card CVV. " +
            (if (business) "This chat is $who's BUSINESS (a clothing shop). You do NOT know stock, sizes, colours, rates, delivery times or offers unless they are in the FACTS: otherwise never state them, say $who will confirm and add [OWNER]. You may greet, thank, and ask what the customer needs. " else "") +
            (if (hinglish) "Default to Roman Hindi (Hinglish)." else "Default to English.") +
            (if (facts.isEmpty()) "" else "\nFACTS $who GAVE FOR THIS CHAT (the only facts you may state):\n" + facts.joinToString("\n") { "- " + it })
    }

    // ---- Part 8: switch AI SMART / Auto-reply by voice (NovaService always asks HAAN/NAHI first)
    private val SMART_SUBJECT = Regex("\\b(smart reply|ai reply|ai smart|smart jawab|ai jawab)\\b")
    private val AUTO_SUBJECT = Regex("\\b(auto reply|autoreply|auto jawab)\\b")
    private val ON_WORD = Regex("\\b(on|chalu|shuru|start|enable|laga|lagao)\\b")
    private val OFF_WORD = Regex("\\b(off|band|bandh|stop|disable|hata|hatao)\\b")
    private val QUESTION_WORD = Regex("\\b(kya|kaise|kyun|kyu|kab|how|what|why|when|status|batao|haal)\\b")

    /** "smart reply on", "ai reply band karo", "auto reply off". null = not for us (questions, Business Watch phrases and unclear sentences never switch anything). */
    fun command(n: String): Logic.Cmd? {
        val s = n.lowercase().trim()
        if (s.length > 60) return null
        val smart = SMART_SUBJECT.containsMatchIn(s)
        val auto = AUTO_SUBJECT.containsMatchIn(s)
        if (!smart && !auto) return null
        if (s.contains("business watch") || s.contains("chat watch") || s.contains("whatsapp watch")) return null
        if (QUESTION_WORD.containsMatchIn(s)) return null
        val on = ON_WORD.containsMatchIn(s)
        val off = OFF_WORD.containsMatchIn(s)
        if (on == off) return null   // neither word or both: never guess
        return Logic.Cmd(if (smart) (if (on) "smart_on" else "smart_off") else (if (on) "autoreply_on" else "autoreply_off"))
    }

    private val TAG_OWNER = Regex("\\[\\s*owner\\s*\\]", RegexOption.IGNORE_CASE)
    private val TAG_SKIP = Regex("\\[\\s*skip\\s*\\]", RegexOption.IGNORE_CASE)

    /** null = the AI text is not usable. */
    fun parse(raw: String?): Result? {
        if (raw == null) return null
        val skip = TAG_SKIP.containsMatchIn(raw)
        val owner = TAG_OWNER.containsMatchIn(raw)
        var t = raw.replace(TAG_OWNER, " ").replace(TAG_SKIP, " ").replace("*", "").replace("`", "")
        t = t.replace(Regex("\\s+"), " ").trim().trim('"').trim()
        if (t.length > MAX_TEXT) t = t.take(MAX_TEXT).trimEnd()
        if (!skip && t.isBlank()) return null
        return Result(t, owner, skip)
    }

    private val SECRET = Regex("\\b(otp|password|passcode|cvv|pin)\\b", RegexOption.IGNORE_CASE)
    private val CARD = Regex("\\d{13,19}")

    /** Part 10: only secrets are held back (OTP / password / PIN / CVV words, card-like numbers). Money, links and phone numbers are allowed. null = safe to send; otherwise the caller sends [HOLD_LINE] and tells the owner. */
    fun unsafeReason(text: String): String? = when {
        SECRET.containsMatchIn(text) -> "secret"
        CARD.containsMatchIn(text.replace(Regex("[ -]"), "")) -> "card"
        else -> null
    }

    private val OWNER_WORDS = listOf("order", "payment", "paid", "paisa", "paise", "refund", "return", "exchange", "cancel", "complaint", "shikayat",
        "fraud", "scam", "urgent", "jaldi", "advance", "rate", "price", "daam", "discount", "bill", "invoice", "police", "legal", "emergency", "accident", "hospital")

    /** Messages about money, orders, complaints, emergencies etc. make the owner notice say "zaruri". The AI still replies (Part 10). */
    fun ownerMust(incoming: String): Boolean {
        val s = incoming.lowercase()
        return OWNER_WORDS.any { s.contains(it) }
    }

    /** The line the owner gets (phone notification + history detail + spoken when idle). */
    fun inform(sender: String, app: String, incoming: String, reply: String, needOwner: Boolean, sent: Boolean, hinglish: Boolean): String {
        val inc = incoming.replace(Regex("\\s+"), " ").trim().take(80)
        val rep = reply.replace(Regex("\\s+"), " ").trim().take(120)
        return if (hinglish) {
            val head = if (!sent) "$sender ko $app par jawab nahi bhej paya" else if (needOwner) "$sender ka zaruri message ($app) - aapko dekhna hai" else "$sender se $app par baat ki"
            "$head. Unhone likha: $inc. NOVA ne jawab diya: $rep" + (if (needOwner && sent) ". Jawab batana ho to bolo: $sender ko WhatsApp karo ... , phir main aage baat sambhal lunga" else "")
        } else {
            val head = if (!sent) "Could not reply to $sender on $app" else if (needOwner) "Important message from $sender ($app) - needs you" else "Chatted with $sender on $app"
            "$head. They wrote: $inc. NOVA replied: $rep"
        }
    }

    /** In-memory rate limits for smart replies. */
    class Gate {
        private val perSender = HashMap<String, ArrayList<Long>>()
        private val day = ArrayList<Long>()

        /** null = allowed (and counted). Otherwise "cooldown" or "limit". */
        @Synchronized fun allow(sender: String, now: Long): String? {
            day.removeAll { now - it >= DAY_MS }
            if (day.size >= DAY_CAP) return "limit"
            val list = perSender.getOrPut(sender.lowercase().take(40)) { ArrayList() }
            list.removeAll { now - it >= HOUR_MS }
            if (list.size >= SENDER_PER_HOUR) return "limit"
            val last = list.lastOrNull()
            if (last != null && now - last < SENDER_COOLDOWN_MS) return "cooldown"
            list.add(now)
            day.add(now)
            if (perSender.size > 300) perSender.clear()
            return null
        }

        @Synchronized fun reset() { perSender.clear(); day.clear() }
    }

    /** Short per-sender memory of the chat, so the AI continues the same conversation. Memory only, never stored. */
    class Convo {
        private val map = HashMap<String, ArrayList<Pair<String, String>>>()
        private val facts = HashMap<String, ArrayList<String>>()

        /** Part 10: what the OWNER told this person through NOVA (voice WhatsApp). The AI may state these facts and nothing else. */
        @Synchronized fun facts(sender: String): List<String> = (facts[sender.lowercase().take(40)] ?: ArrayList()).toList()

        @Synchronized fun addFact(sender: String, text: String) {
            val t = text.replace(Regex("\\s+"), " ").trim().take(300)
            if (t.isBlank()) return
            val l = facts.getOrPut(sender.lowercase().take(40)) { ArrayList() }
            l.add(t)
            while (l.size > MAX_FACTS) l.removeAt(0)
            if (facts.size > 200) facts.clear()
        }

        @Synchronized fun turns(sender: String): List<Pair<String, String>> = (map[sender.lowercase().take(40)] ?: ArrayList()).toList()

        @Synchronized fun lastAssistant(sender: String): String = (map[sender.lowercase().take(40)] ?: ArrayList()).lastOrNull { it.first == "assistant" }?.second ?: ""

        @Synchronized fun add(sender: String, role: String, text: String) {
            val l = map.getOrPut(sender.lowercase().take(40)) { ArrayList() }
            l.add(Pair(role, text.take(300)))
            while (l.size > MAX_TURNS) l.removeAt(0)
            if (map.size > 200) map.clear()
        }

        @Synchronized fun reset() { map.clear(); facts.clear() }
    }

    /** Part 8: the same notification is often posted twice; the same sender + text inside 2 minutes is answered once. */
    class Seen {
        private val map = HashMap<String, Long>()

        /** true = new message (and it is remembered); false = a repeat of the last 2 minutes. */
        @Synchronized fun fresh(sender: String, text: String, now: Long): Boolean {
            val k = sender.lowercase().take(40) + "|" + text.take(80)
            val prev = map[k]
            if (prev != null && now - prev < SEEN_MS) return false
            map[k] = now
            if (map.size > 300) map.clear()
            return true
        }

        @Synchronized fun reset() { map.clear() }
    }

    private const val SEEN_MS = 120_000L

    val gate = Gate()
    val convo = Convo()
    val seen = Seen()
}
