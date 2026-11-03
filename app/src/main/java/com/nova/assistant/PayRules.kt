package com.nova.assistant

/**
 * v7.170 PAYMENT QR + PAYMENT CHECK - pure rules (no Android imports, JUnit-able).
 *
 * 1) QR: the owner gives ANY UPI id (voice, chat or the PAYMENT QR screen) and an optional amount; NOVA builds the
 *    standard upi://pay link and PayKit draws it as a QR image (zxing, offline).
 * 2) Payment check: Android does not let an app ask a bank "did money come?". The only real signal on the owner's
 *    own phone is the credit alert his UPI app / bank SMS already shows. NOVA reads ONLY those alerts (from UPI apps
 *    and SMS apps), keeps just the amount + name, matches the amount to the QR he made, and says / shows the result.
 *    An SMS can be faked, so SMS-based results always say "bank app me ek baar dekh lo". Nothing is invented: no alert
 *    = "abhi tak nahi aaya", never "aa gaya".
 *
 * Voice (narrow grammar, normal chat is never captured):
 *   "nova qr banao sharma@okhdfc 500"   "nova 500 ka qr banao"   "nova payment qr banao"
 *   "nova payment aaya kya"   "nova payment check on / off"   "nova payment screen kholo"
 */
object PayRules {
    const val K_PAY_QR = "pay_qr"            // arg = "upi|paise" (upi may be blank = use the saved one, paise 0 = any amount)
    const val K_PAY_STATUS = "pay_status"
    const val K_PAY_WATCH = "pay_watch"      // arg = "on" / "off"
    const val K_PAY_SCREEN = "pay_screen"
    val KINDS = setOf(K_PAY_QR, K_PAY_STATUS, K_PAY_WATCH, K_PAY_SCREEN)

    const val WAIT_MS = 30L * 60_000L        // how long one QR waits for its payment
    const val MAX_PAISE = 1_000_000L * 100L  // Rs 10,00,000
    const val WAITING = "WAITING"
    const val RECEIVED = "RECEIVED"
    const val EXPIRED = "EXPIRED"
    const val CANCELLED = "CANCELLED"

    private val UPI_IN_TEXT = Regex("[a-zA-Z0-9._-]{2,64}@[a-zA-Z][a-zA-Z0-9.]{1,40}")
    private val QR_WORD = Regex("\\bqr\\b")
    private val MAKE_WORD = Regex("(banao|bana do|bana de|banado|bana dijiye|generate|create|make|chahiye|chahie|dikhao|de do|dedo|nikalo|bhejo)")
    private val NOT_PAY_QR = Regex("(wifi|wi-fi|wi fi|whatsapp|instagram|scan|padho|read)")

    fun validUpi(id: String): Boolean = BizRules.validUpi(id)

    /** "500", "1,200", "499.50" -> paise; null when not a sane amount. 0 is NOT valid here (use 0 only for "any amount" by omission). */
    fun toPaise(raw: String): Long? {
        val s = raw.trim().replace(",", "")
        val m = Regex("^(\\d{1,7})(?:\\.(\\d{1,2}))?$").find(s) ?: return null
        val rupees = m.groupValues[1].toLong()
        val frac = m.groupValues[2].let { if (it.isEmpty()) 0L else if (it.length == 1) it.toLong() * 10 else it.toLong() }
        val p = rupees * 100 + frac
        return if (p in 1..MAX_PAISE) p else null
    }

    fun rupeeText(paise: Long): String = if (paise % 100 == 0L) (paise / 100).toString() else String.format("%d.%02d", paise / 100, paise % 100)

    /** The standard UPI deep link. Amount only when paise > 0. Works in every UPI app's scanner. */
    fun upiLink(upi: String, payee: String, paise: Long, note: String): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val pn = payee.trim().ifBlank { "Store" }.take(60)
        val sb = StringBuilder("upi://pay?pa=").append(upi.trim()).append("&pn=").append(enc(pn))
        if (paise > 0) sb.append("&am=").append(String.format("%d.%02d", paise / 100, paise % 100)).append("&cu=INR")
        val tn = note.trim().take(40)
        if (tn.isNotEmpty()) sb.append("&tn=").append(enc(tn))
        return sb.toString()
    }

    // ---------------------------------------------------------------- voice grammar

    fun parse(text: String): Logic.Cmd? {
        if (text.isBlank() || text.length > 160) return null
        val raw = text.trim().replace(Regex("\\s+"), " ")
        var low = raw.lowercase()
        if (Regex("\\bnova\\s+biz\\b").containsMatchIn(low)) return null
        // spoken "sharma at the rate okhdfc" -> "sharma@okhdfc" (only when a QR word is present, so normal chat is untouched)
        if (!low.contains('@') && QR_WORD.containsMatchIn(low))
            low = low.replace(Regex("([a-z0-9._-]{2,64})\\s+at\\s+(?:the\\s+rate\\s+)?([a-z][a-z0-9.]{1,40})\\b"), "$1@$2")

        // screen
        if (Regex("(payment|pay|upi)\\s*(qr)?\\s*(screen|page|tab)").containsMatchIn(low) && Regex("(kholo|khol do|open|dikhao|dikha do)").containsMatchIn(low))
            return Logic.Cmd(K_PAY_SCREEN)

        // watch on/off
        if (Regex("(payment|paisa|paise|upi)\\s*(check|watch|nigrani|alert)").containsMatchIn(low) || Regex("(auto)\\s*(payment|paisa)").containsMatchIn(low)) {
            if (Regex("\\b(on|chalu|start|shuru|laga do|lagao)\\b").containsMatchIn(low)) return Logic.Cmd(K_PAY_WATCH, "on")
            if (Regex("\\b(off|band|stop|ruko|hata do|hatao)\\b").containsMatchIn(low)) return Logic.Cmd(K_PAY_WATCH, "off")
        }

        // QR make
        if (QR_WORD.containsMatchIn(low) && !NOT_PAY_QR.containsMatchIn(low) &&
            (MAKE_WORD.containsMatchIn(low) || Regex("(payment|pay|upi|paisa)\\s*qr").containsMatchIn(low) || Regex("qr\\s*(payment|pay|upi)").containsMatchIn(low))) {
            val upiM = UPI_IN_TEXT.find(low)
            val upi = upiM?.value.orEmpty()
            val rest = if (upiM != null) low.removeRange(upiM.range) else low
            val amt = Regex("(?:₹|rs\\.?|rupaye|rupees|rupay)?\\s*\\b(\\d{1,7}(?:[.,]\\d{1,2})?)\\b").find(rest)
            val paise = amt?.let { toPaise(it.groupValues[1]) } ?: 0L
            return Logic.Cmd(K_PAY_QR, upi + "|" + paise)
        }

        // status: "payment aaya kya", "paisa mila", "payment aa gaya?"
        if (Regex("(payment|paisa|paise|rupaye|amount|rakam)").containsMatchIn(low) &&
            Regex("\\b(aaya|aya|aayi|ayi|aa gaya|aa gayi|aagaya|mila|mili|received|status|check kar|check karo|pahunch|pohoch)\\b").containsMatchIn(low) &&
            !Regex("(kaise|kyun|kyu|kaun|how|why|karna|karun|karu|bhejna|bhejo|bhej do)").containsMatchIn(low)) {
            return Logic.Cmd(K_PAY_STATUS)
        }
        return null
    }

    // ---------------------------------------------------------------- credit alert parsing

    data class Source(val name: String, val sms: Boolean)

    private val SMS_PKGS = setOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging", "com.miui.mms", "com.oneplus.mms", "com.coloros.mms", "com.android.messaging")
    private val UPI_HINTS = listOf("phonepe", "paytm", "paisa", "npci", "bhim", "mobikwik", "cred.club", "freecharge", "slice")

    /** Which alerts may be read: the UPI apps and the SMS apps. Anything else is ignored. */
    fun sourceOf(pkg: String): Source? {
        val p = pkg.lowercase()
        if (p in SMS_PKGS) return Source("SMS", true)
        val h = UPI_HINTS.firstOrNull { p.contains(it) } ?: return null
        return Source(when (h) { "phonepe" -> "PhonePe"; "paytm" -> "Paytm"; "paisa" -> "Google Pay"; "npci", "bhim" -> "BHIM"; else -> h }, false)
    }

    data class Credit(val paise: Long, val from: String)

    private val BAD = listOf("debited", "debit ", "paid to", "sent to", "you paid", "you sent", "request", "otp", "failed", "declined",
        "unsuccessful", "reminder", "cashback", "reward", "scratch", "offer", "will be credited", "to be credited", "pending",
        "credit card", "statement", "autopay", "mandate", "bill", "refund", "reversed", "withdraw")
    private val GOOD = listOf("received", "credited", "paid you", "sent you", "has sent", "deposited")
    private val BAL = Regex("(?:avl|avail(?:able)?|total|clear)?\\.?\\s*bal(?:ance)?\\.?\\s*(?:is|:|-|of)?\\s*(?:₹|rs\\.?|inr)?\\s*[0-9][0-9,]*(?:\\.[0-9]{1,2})?")
    private val AMT1 = Regex("(?:₹|rs\\.?|inr)\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)")
    private val AMT2 = Regex("([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)\\s*(?:rs|inr|rupees)\\b")
    private val FROM1 = Regex("(?i)\\bfrom\\s+([A-Za-z][A-Za-z .'-]{1,28}?)(?=\\s+(?:on|via|using|ref|upi|at|vpa)\\b|[.,(\\n]|$)")
    private val FROM2 = Regex("^([A-Za-z][A-Za-z .'-]{1,28}?)\\s+(?:paid|sent|has sent)\\b")

    /** A money-IN alert -> amount + (best effort) who. null for debits, requests, OTPs, offers, balances, anything unclear. */
    fun parseCredit(text: String): Credit? {
        val t = text.replace('\u00a0', ' ').replace(Regex("\\s+"), " ").trim()
        if (t.length < 6 || t.length > 700) return null
        val low = t.lowercase() + " "
        if (BAD.any { low.contains(it) }) return null
        if (GOOD.none { low.contains(it) }) return null
        val noBal = low.replace(BAL, " ")
        val raw = (AMT1.find(noBal) ?: AMT2.find(noBal))?.groupValues?.get(1) ?: return null
        val paise = toPaise(raw) ?: return null
        val from = (FROM1.find(t)?.groupValues?.get(1) ?: FROM2.find(t)?.groupValues?.get(1)).orEmpty().trim().take(30)
        return Credit(paise, from)
    }

    // ---------------------------------------------------------------- matching + wording

    data class Pending(val id: String, val paise: Long, val createdAt: Long, val expiresAt: Long, val state: String, val receivedAt: Long, val from: String, val note: String = "")

    /** Oldest still-waiting QR whose amount equals the credit (a QR made without an amount takes the first credit). -1 = none. */
    fun matchIndex(list: List<Pending>, c: Credit, now: Long): Int {
        var best = -1
        for ((i, p) in list.withIndex()) {
            if (p.state != WAITING || now > p.expiresAt) continue
            if (p.paise > 0 && p.paise != c.paise) continue
            if (best < 0 || p.createdAt < list[best].createdAt) best = i
        }
        return best
    }

    private fun who(c: Credit, en: Boolean) = if (c.from.isBlank()) "" else (if (en) " from ${c.from}" else " ${c.from} se")

    fun receivedText(c: Credit, src: Source, matched: Boolean, en: Boolean = false): String {
        val amt = "Rs " + rupeeText(c.paise)
        val sure = if (src.sms) (if (en) " This came from an SMS, so check your bank app once to be sure." else " Ye SMS se pata chala, pakka karne ke liye bank app ek baar dekh lo.")
                   else (if (en) " (${src.name} alert)" else " (${src.name} ka alert)")
        return if (matched) (if (en) "Payment received: $amt${who(c, en)}.$sure" else "Payment aa gayi: $amt${who(c, en)}.$sure")
        else (if (en) "$amt received${who(c, en)}, but it does not match any QR you made.$sure" else "$amt aaye${who(c, en)}, par ye aapke banaye kisi QR ki rakam se match nahi hua.$sure")
    }

    fun qrDoneText(paise: Long, upi: String, en: Boolean = false): String {
        val a = if (paise > 0) "Rs " + rupeeText(paise) else (if (en) "any amount" else "koi bhi rakam")
        return if (en) "QR is ready for $a on $upi. It is on your screen. I will tell you when the payment alert comes (waiting 30 minutes)."
        else "$upi ka QR taiyaar hai ($a). Screen par dikh raha hai. Payment ka alert aate hi bol dunga (30 minute tak intezaar)."
    }

    fun statusText(list: List<Pending>, now: Long, watchOn: Boolean, listenerOn: Boolean, en: Boolean = false): String {
        val last = list.lastOrNull() ?: return if (en) "No payment QR made yet. Say: nova qr banao." else "Abhi koi payment QR nahi bana. Bolo: nova qr banao."
        val a = if (last.paise > 0) "Rs " + rupeeText(last.paise) else (if (en) "any amount" else "koi bhi rakam")
        val warn = if (!listenerOn) (if (en) " Notification access is OFF, so I cannot see any payment alert. Turn it on in Settings." else " Notification access band hai, isliye main payment ka alert nahi dekh sakta. Settings me ON karo.")
                   else if (!watchOn) (if (en) " Auto check is OFF." else " Auto check band hai.") else ""
        return when {
            last.state == RECEIVED -> (if (en) "Yes: $a received${if (last.from.isBlank()) "" else " from ${last.from}"}." else "Haan: $a aa gaya${if (last.from.isBlank()) "" else " (${last.from})"}.") + (if (en) " Check your bank app once to be sure." else " Bank app me ek baar dekh lena.")
            last.state == WAITING && now <= last.expiresAt -> {
                val left = ((last.expiresAt - now) / 60_000L).coerceAtLeast(1)
                if (en) "Not yet. Waiting for $a, $left min left.$warn" else "Abhi tak nahi aaya. $a ka intezaar hai, $left minute bache.$warn"
            }
            last.state == CANCELLED -> if (en) "That QR was cancelled." else "Wo QR cancel kar diya tha."
            else -> if (en) "Not received. $a did not arrive within 30 minutes.$warn" else "Nahi aaya. $a 30 minute me nahi aaya.$warn"
        }
    }

    fun expiredText(p: Pending, en: Boolean = false): String {
        val a = if (p.paise > 0) "Rs " + rupeeText(p.paise) else (if (en) "the payment" else "payment")
        return if (en) "$a has not arrived yet (30 minutes passed). If the customer says they paid, check your bank app."
        else "$a abhi tak nahi aaya (30 minute ho gaye). Customer paid bole to bank app me dekh lo."
    }
}
