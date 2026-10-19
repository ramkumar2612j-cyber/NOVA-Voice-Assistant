package com.nova.assistant

/**
 * v7.83 UNREAD QUERY: "aaj whatsapp pe koi new message to nhi aya?", "naye messages batao", "kisi ne message bheja kya".
 * Before this, such a question had no local meaning, travelled to the cloud AI (which has no tool for it) and came back as
 * "samajh nahi aaya". Now it is answered on the phone from the notifications that are still in the shade.
 *
 * Pure Kotlin (no Android classes, JUnit-testable). READ-ONLY: it never sends, replies, deletes or opens anything.
 * Honest limit: only notifications that are still visible can be counted; cleared ones are invisible to NOVA.
 * Code / OTP-like messages are counted but never read out (Driving.makeMsg already marks them hidden).
 */
object UnreadQueryRules {
    class Req(val app: String?)   // null = any messaging app, else a Driving.APPS display name prefix

    private val MSG_ONLY = setOf(
        "message", "messages", "msg", "msgs", "mess", "massage", "mesage", "messej", "sms", "notification", "notifications", "notif", "notifs",
        "मैसेज", "मेसेज", "संदेश", "नोटिफिकेशन"
    )
    private val APP_WORDS = mapOf(
        "whatsapp" to "WhatsApp", "watsapp" to "WhatsApp", "whatsap" to "WhatsApp", "whtsapp" to "WhatsApp", "whatsup" to "WhatsApp", "wa" to "WhatsApp",
        "व्हाट्सएप" to "WhatsApp", "व्हाट्सऐप" to "WhatsApp", "वॉट्सऐप" to "WhatsApp", "वॉट्सएप" to "WhatsApp", "व्हाट्सअप" to "WhatsApp", "वाट्सएप" to "WhatsApp",
        "telegram" to "Telegram", "टेलीग्राम" to "Telegram", "signal" to "Signal"
    )
    private val AAYA = setOf(
        "aaya", "aya", "aayi", "aai", "aaye", "aye", "ayi", "ayaa", "aaya?", "आया", "आई", "आए", "आये", "आयी"
    )
    private val NEW = setOf("naya", "nayi", "naye", "nya", "nye", "new", "latest", "recent", "नया", "नए", "नई", "नये")
    private val UNREAD = setOf("unread", "pending", "अनपढ़े")
    private val BHEJA = setOf("bheja", "bheje", "bheji", "mila", "mile", "mili", "भेजा", "भेजे", "भेजी", "मिला", "मिले")
    private val READ_VERB = setOf("padho", "padh", "sunao", "suna", "dikhao", "dikha", "batao", "bata", "check", "dekho", "dekh", "पढ़ो", "सुनाओ", "दिखाओ", "बताओ", "देखो")
    /** Anything that SENDS / changes / calls is not a question: the normal command rules own it. Mail words stay with GmailRules. */
    private val BLOCK = setOf(
        "bhejo", "bhej", "bhejna", "bhijwa", "send", "likho", "likh", "likhna", "write", "reply", "jawab", "delete", "remove", "hatao", "mitao", "mita",
        "call", "forward", "schedule", "bolo", "kehna", "kaho", "mail", "email", "gmail", "भेजो", "लिखो", "जवाब", "डिलीट", "कॉल", "ईमेल"
    )

    private fun tokens(raw: String): List<String> =
        Regex("[\\p{L}\\p{M}]+").findAll(raw.lowercase()).map { it.value }.toList()

    /** null = not an "any new messages?" question. */
    fun parse(raw: String): Req? {
        val w = tokens(raw)
        if (w.isEmpty() || w.size > 20) return null
        if (w.any { it in BLOCK }) return null
        val app = w.firstNotNullOfOrNull { APP_WORDS[it] }
        val hasMsgOnly = w.any { it in MSG_ONLY }
        val hasAnyMsg = hasMsgOnly || app != null
        if (!hasAnyMsg) return null
        val aaya = w.any { it in AAYA }
        val isNew = w.any { it in NEW }
        val unread = w.any { it in UNREAD }
        val bheja = w.any { it in BHEJA }
        val readVerb = w.any { it in READ_VERB }
        val ok = (aaya && hasAnyMsg) || (unread && hasAnyMsg) ||
            ((isNew || bheja || readVerb) && hasMsgOnly)
        return if (ok) Req(app) else null
    }

    fun noAccessText(en: Boolean): String = if (en)
        "I cannot see your notifications yet. Turn on Notification access for NOVA in Settings, then ask again."
    else
        "Mujhe abhi aapke notification dikhte nahi. Settings me NOVA ke liye Notification access ON karo, phir poochho."

    fun notReadyText(en: Boolean): String = if (en)
        "The notification reader is not connected right now. Open NOVA once and ask again in a moment."
    else
        "Notification padhne wala hissa abhi jud nahi paya. Ek baar NOVA kholo aur thodi der baad dobara poochho."

    private fun appMatches(item: Driving.Msg, app: String?): Boolean = app == null || item.app.startsWith(app)

    /** Newest-last list in, one short spoken answer out. */
    fun reply(all: List<Driving.Msg>, req: Req, en: Boolean): String {
        val items = all.filter { appMatches(it, req.app) }.sortedBy { it.ts }
        val appName = req.app ?: (if (en) "your messaging apps" else "messaging apps")
        if (items.isEmpty()) return if (en)
            "No new $appName message is showing in your notifications right now. Notifications you already cleared are not visible to me."
        else
            "Abhi $appName ka koi naya message notification me nahi dikh raha. Jo notification aap hata chuke ho, wo mujhe nahi dikhte."
        val n = items.size
        val head = if (en) "$n new message${if (n == 1) "" else "s"} on $appName. " else "$appName par $n naya message hai. ".let { if (n == 1) it else "$appName par $n naye message hain. " }
        if (n <= 3) {
            val body = items.joinToString(" ") { m ->
                if (m.hidden || m.text.isBlank()) {
                    if (en) "${m.sender} sent one that looks like a code, so I will not read it." else "${m.sender} ka ek message code jaisa lag raha hai, wo main nahi padhunga."
                } else {
                    val t = m.text.take(120)
                    if (en) "${m.sender} says: $t." else "${m.sender} ne kaha: $t."
                }
            }
            return head + body
        }
        val bySender = LinkedHashMap<String, Int>()
        for (m in items.asReversed()) bySender[m.sender] = (bySender[m.sender] ?: 0) + 1
        val top = bySender.entries.take(4).joinToString(", ") { if (en) "${it.key} ${it.value}" else "${it.key} ke ${it.value}" }
        val more = bySender.size - 4
        val tail = if (more > 0) (if (en) " and $more more people." else " aur $more logon ke.") else "."
        return head + (if (en) "From: " else "Kaun: ") + top + tail
    }
}
