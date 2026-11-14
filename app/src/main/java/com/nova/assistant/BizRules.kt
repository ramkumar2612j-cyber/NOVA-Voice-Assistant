package com.nova.assistant

/**
 * v7.169 BUSINESS PROFILE + ADVANCE PAYMENT + DELIVERY TIME - pure rules (no Android imports, JUnit-able).
 *
 * The owner tells NOVA his business ONCE, in plain words (any product, any business):
 *   "nova biz product Cotton kurta"        "nova biz price 799"          "nova biz advance 30"  (percent)
 *   "nova biz upi sharma@okhdfc"           "nova biz payee Sharma Store" "nova biz delivery 3 din"
 *   "nova biz city Noida"                  "nova biz category boutique"  "nova biz leads 8"      (per day, 5..10)
 *   "nova biz status"                      "nova biz leads on / off"
 * NOVA then uses it: lead search (LeadFinder), the pitch message, and - when an order is confirmed - the advance
 * payment message (UPI id + upi:// link) and the delivery due time. Nothing is invented: a missing field stays missing
 * and the status line says exactly what is missing.
 */
object BizRules {
    const val K_BIZ_SET = "biz_set"        // arg = "field|value"
    const val K_BIZ_STATUS = "biz_status"
    const val K_BIZ_LEADS_NOW = "biz_leads_now"
    const val K_BIZ_LEADS_ONOFF = "biz_leads_onoff"   // arg = "on" / "off"
    const val K_BIZ_KEY = "biz_placeskey"              // arg = the Google Places API key (stored encrypted, never echoed)
    val KINDS = setOf(K_BIZ_SET, K_BIZ_STATUS, K_BIZ_LEADS_NOW, K_BIZ_LEADS_ONOFF, K_BIZ_KEY)

    const val MIN_LEADS = 5
    const val MAX_LEADS = 10

    data class Profile(
        val product: String = "", val price: Int = 0, val advancePct: Int = 0,
        val upi: String = "", val payee: String = "", val deliveryText: String = "",
        val city: String = "", val category: String = "", val perDay: Int = 5, val leadsOn: Boolean = false
    )

    private val FIELDS = setOf("product", "price", "advance", "upi", "payee", "delivery", "city", "category", "leads")
    private val UPI = Regex("^[a-zA-Z0-9._-]{2,64}@[a-zA-Z][a-zA-Z0-9.]{1,40}$")

    fun validUpi(id: String): Boolean = UPI.matches(id.trim())

    /** "nova biz <field> <value>" / "nova biz status" / "nova biz leads on|off|now". Returns null for anything else (normal chat is never captured). */
    fun parse(text: String): Logic.Cmd? {
        if (text.isBlank() || text.length > 200) return null
        val raw = text.trim().replace(Regex("\\s+"), " ")
        val m = Regex("(?i)^nova\\s+(?:biz|business)\\s+(.+)$").find(raw) ?: return null
        val rest = m.groupValues[1].trim()
        val parts = rest.split(" ", limit = 2)
        val field = parts[0].lowercase()
        val value = parts.getOrNull(1)?.trim().orEmpty()
        if (field == "placeskey" && value.length in 20..120 && !value.contains(' ')) return Logic.Cmd(K_BIZ_KEY, value)
        if (field == "status" || field == "dikhao" || field == "profile") return Logic.Cmd(K_BIZ_STATUS)
        if (field == "leads") {
            val v = value.lowercase()
            if (v == "now" || v == "abhi" || v == "lao") return Logic.Cmd(K_BIZ_LEADS_NOW)
            if (v == "on" || v == "chalu") return Logic.Cmd(K_BIZ_LEADS_ONOFF, "on")
            if (v == "off" || v == "band") return Logic.Cmd(K_BIZ_LEADS_ONOFF, "off")
        }
        if (field !in FIELDS || value.isBlank()) return null
        return Logic.Cmd(K_BIZ_SET, field + "|" + value.take(120))
    }

    /** Applies one "field|value". Returns the new profile and the Hinglish reply; an invalid value changes nothing. */
    fun apply(p: Profile, arg: String, en: Boolean = false): Pair<Profile, String> {
        val i = arg.indexOf('|')
        if (i <= 0) return Pair(p, if (en) "I did not understand that setting." else "Ye setting samajh nahi aayi.")
        val f = arg.substring(0, i)
        val v = arg.substring(i + 1).trim()
        fun bad(hi: String, e: String) = Pair(p, if (en) e else hi)
        return when (f) {
            "product" -> if (v.length < 2) bad("Product ka naam likho.", "Give the product name.") else Pair(p.copy(product = v.take(80)), if (en) "Product saved: ${v.take(80)}." else "Product save: ${v.take(80)}.")
            "price" -> { val n = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (n !in 1..10_000_000) bad("Price number me bolo (jaise 799).", "Say the price as a number (like 799).") else Pair(p.copy(price = n), if (en) "Price saved: Rs $n." else "Price save: Rs $n.") }
            "advance" -> { val n = v.filter { it.isDigit() }.toIntOrNull() ?: -1
                if (n !in 0..100) bad("Advance 0 se 100 percent ke beech bolo (0 = advance nahi).", "Advance must be 0-100 percent (0 = none).") else Pair(p.copy(advancePct = n), if (en) "Advance saved: $n%." else "Advance save: $n%.") }
            "upi" -> if (!validUpi(v)) bad("UPI id sahi nahi lagi (jaise naam@bank).", "That UPI id looks wrong (like name@bank).") else Pair(p.copy(upi = v), if (en) "UPI id saved." else "UPI id save.")
            "payee" -> if (v.length < 2) bad("Naam likho.", "Give the name.") else Pair(p.copy(payee = v.take(60)), if (en) "Payee name saved." else "Payee naam save.")
            "delivery" -> if (deliveryMs(v) == null) bad("Delivery aise bolo: 3 din / 5 ghante / kal.", "Say delivery like: 3 days / 5 hours / tomorrow.") else Pair(p.copy(deliveryText = v.take(30)), if (en) "Delivery time saved: ${v.take(30)}." else "Delivery time save: ${v.take(30)}.")
            "city" -> if (v.length < 2) bad("Sheher ka naam likho.", "Give the city.") else Pair(p.copy(city = v.take(50)), if (en) "City saved: ${v.take(50)}." else "Sheher save: ${v.take(50)}.")
            "category" -> if (v.length < 2) bad("Kaunse business/customer chahiye likho (jaise boutique).", "Give the business type you want to reach (like boutique).") else Pair(p.copy(category = v.take(60)), if (en) "Target type saved: ${v.take(60)}." else "Target type save: ${v.take(60)}.")
            "leads" -> { val n = v.filter { it.isDigit() }.toIntOrNull() ?: 0
                if (n <= 0) bad("Roz kitni leads: 5 se 10.", "Leads per day: 5 to 10.") else { val c = n.coerceIn(MIN_LEADS, MAX_LEADS); Pair(p.copy(perDay = c), if (en) "Leads per day: $c." else "Roz leads: $c.") } }
            else -> Pair(p, if (en) "Unknown setting." else "Ye setting nahi pata.")
        }
    }

    /** What is still missing for each feature (empty = ready). */
    fun missingForLeads(p: Profile): List<String> = buildList {
        if (p.product.isBlank()) add("product"); if (p.city.isBlank()) add("city"); if (p.category.isBlank()) add("category")
    }
    fun missingForPayment(p: Profile): List<String> = buildList {
        if (p.price <= 0) add("price"); if (p.advancePct in 1..100 && p.upi.isBlank()) add("upi")
    }

    fun statusLine(p: Profile, en: Boolean = false): String {
        fun v(s: String) = if (s.isBlank()) "-" else s
        val ml = missingForLeads(p); val mp = missingForPayment(p)
        val head = "Product ${v(p.product)} | Rs ${if (p.price > 0) p.price else "-"} | advance ${p.advancePct}% | UPI ${v(p.upi)} | delivery ${v(p.deliveryText)} | ${v(p.city)} / ${v(p.category)} | leads ${p.perDay}/din ${if (p.leadsOn) "ON" else "OFF"}"
        val miss = if (ml.isEmpty() && mp.isEmpty()) (if (en) "Everything needed is set." else "Zaroori sab set hai.") else
            (if (en) "Missing: " else "Baaki bharna hai: ") + (ml + mp).distinct().joinToString(", ")
        return head + ". " + miss
    }

    /** Amount in rupees for qty items (price * qty). 0 when the price is not set. */
    fun total(p: Profile, qty: Int): Int = if (p.price <= 0) 0 else p.price.toLong().times(qty.coerceIn(1, 999)).coerceAtMost(100_000_000L).toInt()
    fun advanceAmount(p: Profile, qty: Int): Int = (total(p, qty).toLong() * p.advancePct.coerceIn(0, 100) / 100L).toInt()

    /** upi://pay deep link (opens any UPI app). Amount only when > 0. */
    fun upiLink(p: Profile, amount: Int, note: String): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        val payee = p.payee.ifBlank { p.product.ifBlank { "Store" } }
        return "upi://pay?pa=" + p.upi.trim() + "&pn=" + enc(payee) + (if (amount > 0) "&am=$amount&cu=INR" else "") + "&tn=" + enc(note.take(40))
    }

    /** The advance-payment text sent after the customer confirms. Null when the owner asked for no advance or the data is incomplete (honest: nothing invented). */
    fun paymentMessage(p: Profile, qty: Int, orderRef: String, en: Boolean = false): String? {
        if (p.advancePct <= 0 || p.upi.isBlank() || !validUpi(p.upi) || p.price <= 0) return null
        val adv = advanceAmount(p, qty)
        if (adv <= 0) return null
        val tot = total(p, qty)
        val link = upiLink(p, adv, "Order " + orderRef.take(12))
        val deliv = if (p.deliveryText.isBlank()) "" else (if (en) " Delivery: ${p.deliveryText} after payment." else " Delivery: payment ke baad ${p.deliveryText} me.")
        return if (en) "Advance to confirm: Rs $adv (of Rs $tot). Pay to UPI id ${p.upi}${if (p.payee.isBlank()) "" else " (${p.payee})"} or tap: $link . Send the payment screenshot here.$deliv"
        else "Order pakka karne ke liye advance: Rs $adv (total Rs $tot). UPI id: ${p.upi}${if (p.payee.isBlank()) "" else " (${p.payee})"} ya ye link kholo: $link . Payment ka screenshot yahin bhej dena.$deliv"
    }

    /** "3 din" / "2 days" / "5 ghante" / "kal" / "aaj" -> milliseconds, null when not understood. */
    fun deliveryMs(text: String): Long? {
        val t = text.lowercase().trim()
        if (t.isEmpty()) return null
        val DAY = 24L * 60 * 60_000L; val HOUR = 60L * 60_000L
        if (t == "aaj" || t == "today") return 6 * HOUR
        if (t == "kal" || t == "tomorrow") return DAY
        val m = Regex("^(\\d{1,3})\\s*(din|day|days|dino|ghante|ghanta|hour|hours|hr|hrs|week|weeks|hafta|hafte)$").find(t) ?: return null
        val n = m.groupValues[1].toLong()
        if (n <= 0) return null
        return when (m.groupValues[2]) {
            "din", "day", "days", "dino" -> n * DAY
            "week", "weeks", "hafta", "hafte" -> n * 7 * DAY
            else -> n * HOUR
        }
    }

    fun deliveryDue(p: Profile, confirmedAt: Long): Long? = deliveryMs(p.deliveryText)?.let { confirmedAt + it }

    /** Search text for Google Places: "<category> in <city>". */
    fun leadQuery(p: Profile): String = (p.category.trim() + " in " + p.city.trim()).take(120)

    /** Outreach pitch. Honest: introduces the product, no fake discount/claims, asks permission. */
    fun pitch(p: Profile, leadName: String, en: Boolean = false): String {
        val who = leadName.replace(Regex("[\\r\\n]"), " ").trim().take(60).ifBlank { if (en) "there" else "ji" }
        val prod = p.product.ifBlank { if (en) "our products" else "hamare products" }
        val price = if (p.price > 0) (if (en) " Starting at Rs ${p.price}." else " Rs ${p.price} se shuru.") else ""
        return if (en) "Hello $who, we offer $prod.$price Would you like details? Reply YES and I will share them. (Reply STOP to not get messages.)"
        else "Namaste $who, hamare paas $prod hai.$price Kya aapko detail chahiye? Haan bolo, main bhej dunga. (Message nahi chahiye to STOP likh do.)"
    }
}
