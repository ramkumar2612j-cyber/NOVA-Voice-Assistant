package com.nova.assistant

/**
 * v6.93 GMAIL (no login, no token): NOVA reads Gmail's own notifications (Notification access, the same switch driving mode uses),
 * so it connects by itself the moment that access is ON. Pure text rules here (no Android classes), unit tested.
 *  - which new mail is important,
 *  - the owner's watch list ("Jio bill" every month, "GST notice" every week): NOVA says it when that mail arrives,
 *  - spoken commands: send a mail, add / list / remove a watch, ask for important mail, save an e-mail address under a name.
 */
object GmailRules {
    sealed class Cmd {
        object Connect : Cmd()
        object Check : Cmd()
        object WatchList : Cmd()
        data class Send(val to: String, val subject: String, val body: String) : Cmd()
        data class WatchAdd(val name: String, val period: String) : Cmd()
        data class WatchRemove(val name: String) : Cmd()
        data class AliasAdd(val name: String, val email: String) : Cmd()
    }

    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"

    val EMAIL_RE = Regex("[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}", RegexOption.IGNORE_CASE)

    private val MAIL_WORDS = setOf("gmail", "mail", "email", "emails", "mails", "gmails")
    private val SEND_WORDS = setOf("bhejo", "bhej", "bhejna", "bhejdo", "bhejde", "send", "likho", "likh", "likhna")
    private val WEEK_WORDS = setOf("weekly", "hafte", "haftewar", "hafta", "saptah", "week")
    private val MONTH_WORDS = setOf("monthly", "mahine", "mahina", "mahinewar", "month")
    private val FILLER = setOf(
        "nova", "har", "ek", "ka", "ki", "ke", "ko", "me", "mein", "main", "mujhe", "mere", "meri", "hume", "humein", "hame", "hamein",
        "jab", "tab", "aaye", "aaya", "aaye", "aata", "aati", "aane", "aa", "jaye", "jaaye", "to", "toh", "bata", "batana", "batao", "dena", "do", "de",
        "karo", "kro", "kar", "karna", "karde", "kardo", "add", "watch", "track", "yaad", "rakho", "rakhna", "inform", "alert", "check", "dhyan", "nazar",
        "list", "naam", "name", "the", "a", "wala", "wali", "wale", "please", "pls", "zara", "new", "naya", "hai", "h", "hota", "hoti", "jaise", "jese",
        "hatao", "hata", "band", "delete", "remove", "gmail", "mail", "email", "emails", "mails",
        "koi", "kya", "is", "iss", "us", "abhi", "important", "zaruri", "jaruri", "unread", "aayi"
    )

    private fun words(s: String) = Logic.norm(s).split(" ").filter { it.isNotEmpty() }

    fun period(ws: Collection<String>): String? = when {
        ws.any { it in WEEK_WORDS } -> WEEKLY
        ws.any { it in MONTH_WORDS } -> MONTHLY
        else -> null
    }

    fun parse(input: String): Cmd? {
        val raw = input.trim()
        if (raw.length < 6) return null
        val ws = words(Logic.stripWake(Logic.norm(raw)))
        if (ws.none { it in MAIL_WORDS }) return null
        // 1) save an address: "mom ka email abc@gmail.com save karo"
        val mailAddr = EMAIL_RE.find(raw)?.value?.lowercase()
        if (mailAddr != null && ws.any { it == "save" || it == "yaad" || it == "add" || it == "rakho" } && ws.none { it in SEND_WORDS }) {
            val before = raw.lowercase().substringBefore(mailAddr)
            val nm = words(before).filter { it !in FILLER && it != "save" && it != "ka" }.joinToString(" ").trim()
            if (nm.isNotEmpty() && nm.split(" ").size <= 3) return Cmd.AliasAdd(nm, mailAddr)
        }
        // 2) send
        if (ws.any { it in SEND_WORDS } && ws.none { it == "watch" || it == "list" }) {
            val send = parseSend(raw)
            if (send != null) return send
        }
        // 3) watch list / remove / add
        if (ws.contains("watchlist") || (ws.contains("watch") && ws.contains("list"))) return Cmd.WatchList
        val per = period(ws)
        val removing = ws.any { it == "hatao" || it == "hata" || it == "delete" || it == "remove" || it == "band" }
        if (per != null || (removing && ws.contains("watch"))) {
            val name = ws.filter { it !in FILLER && it !in WEEK_WORDS && it !in MONTH_WORDS }.joinToString(" ").trim()
            if (name.isEmpty() || name.split(" ").size > 4) return null
            return if (removing) Cmd.WatchRemove(name) else Cmd.WatchAdd(name, per ?: WEEKLY)
        }
        // 4) connect / check
        if (ws.any { it == "connect" || it == "jodo" || it == "jodna" || it == "judo" || it == "status" }) return Cmd.Connect
        if (ws.any { it == "important" || it == "zaruri" || it == "jaruri" || it == "unread" || it == "aaya" || it == "aayi" } &&
            ws.size <= 7) return Cmd.Check
        return null
    }

    /** "mail bhejo mom ko subject hello message kal aa jana" / "send mail to a@b.com subject x message y" */
    private fun parseSend(raw: String): Cmd.Send? {
        val email = EMAIL_RE.find(raw)?.value?.lowercase()
        val subjRe = Regex("(?i)\\bsubject\\b")
        val bodyRe = Regex("(?i)\\b(?:message|body|saying)\\b")
        val sIdx = subjRe.find(raw)
        val head = (if (sIdx != null) raw.substring(0, sIdx.range.first) else raw)
        var to = email ?: ""
        if (to.isEmpty()) {
            val m = Regex("(?i)\\b([a-z]+(?: [a-z]+)?)\\s+ko\\b").find(head) ?: Regex("(?i)\\bto\\s+([a-z]+(?: [a-z]+)?)").find(head)
            if (m != null) to = words(m.groupValues[1]).filter { it !in FILLER && it !in SEND_WORDS }.joinToString(" ")
        }
        if (to.isEmpty()) return null
        var subject = ""
        var body = ""
        if (sIdx != null) {
            val after = raw.substring(sIdx.range.last + 1)
            val b = bodyRe.find(after)
            if (b != null) { subject = after.substring(0, b.range.first).trim(); body = after.substring(b.range.last + 1).trim() }
            else subject = after.trim()
        } else {
            val b = bodyRe.find(raw.substring(head.length)) ?: bodyRe.find(raw)
            if (b != null) body = raw.substring(raw.indexOf(b.value, 0, true) + b.value.length).trim()
        }
        subject = subject.trim(' ', ',', '.')
        body = body.trim(' ', ',')
        if (subject.isEmpty() && body.isEmpty()) return null
        if (subject.isEmpty()) subject = body.take(40)
        return Cmd.Send(to, subject.take(150), body.take(2000))
    }

    // ------------------------------------------------------------------ importance + watch matching
    private val SPAM = listOf("unsubscribe", "% off", "percent off", "sale", "discount", "newsletter", "webinar", "deal", "offers for you", "coupon", "cashback", "promo")
    private val IMPORTANT = listOf(
        "urgent", "important", "zaruri", "asap", "invoice", "payment", "bill", "due", "deadline", "interview", "meeting", "appointment",
        "order", "shipped", "delivered", "refund", "statement", "security alert", "action required", "failed", "gst", "tax", "notice",
        "confirm", "confirmation", "verification", "otp", "ticket", "booking", "salary", "offer letter", "reminder", "expire"
    )

    fun isImportant(sender: String, subject: String): Boolean {
        val t = (sender + " " + subject).lowercase()
        if (SPAM.any { t.contains(it) }) return false
        return IMPORTANT.any { t.contains(it) }
    }

    /** Every word of the watch name must appear in sender + subject. */
    fun matchesWatch(name: String, sender: String, subject: String): Boolean {
        val have = " " + Logic.norm(sender + " " + subject) + " "
        val want = Logic.norm(name).split(" ").filter { it.isNotEmpty() }
        return want.isNotEmpty() && want.all { have.contains(it) }
    }

    /** Codes and long numbers are never read aloud: 4+ digit runs become "...". */
    fun maskNumbers(s: String): String = s.replace(Regex("\\d{4,}"), "...")

    fun periodLabel(p: String, en: Boolean): String = when (p) {
        MONTHLY -> if (en) "monthly" else "har mahine"
        else -> if (en) "weekly" else "har hafte"
    }
}
