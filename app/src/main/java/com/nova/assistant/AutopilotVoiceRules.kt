package com.nova.assistant

/**
 * v7.158 AUTOPILOT VOICE (task H) + ORDER STATUS (task G4) - pure rules, no Android imports, JUnit-able.
 *
 * Voice grammar (all through Logic -> NovaService, exactly like JarvisDeepRules):
 *   "nova autopilot on / off / status"      - the master switch + honest status line
 *   "nova aaj kya post hua"                 - today's posts (real queue states only)
 *   "nova orders dikhao"                    - the orders list (confirmed first)
 *   "nova offer 20% off code SK20"          - sets the website offer (nova-offer.json)
 *   "nova isko abhi post karo"              - one full pass NOW (scan -> enqueue -> pump)
 *   "nova story lagao"                      - the last group photo goes out as a story right away
 *   "nova autopilot setup karo"             - opens the first-run wizard
 *   "nova order <naam> <status>"            - the owner stores an order status (customers only hear THIS)
 *
 * Order-status rule (G4): a customer asking "mera order kab aayega" is answered ONLY from what the owner
 * stored - no invented delivery dates, ever. When nothing is stored, the customer gets an honest line and
 * the owner is told.
 */
object AutopilotVoiceRules {

    const val K_AP_ON = "apon"
    const val K_AP_OFF = "apoff"
    const val K_AP_STATUS = "apstatus"
    const val K_AP_TODAY = "aptoday"
    const val K_AP_ORDERS = "aporders"
    const val K_AP_OFFER = "apoffer"
    const val K_AP_NOW = "apnow"
    const val K_AP_STORY = "apstory"
    const val K_AP_SETUP = "apsetup"
    const val K_ORDER_STATUS = "aporderstatus"

    val KINDS = setOf(K_AP_ON, K_AP_OFF, K_AP_STATUS, K_AP_TODAY, K_AP_ORDERS, K_AP_OFFER, K_AP_NOW, K_AP_STORY, K_AP_SETUP, K_ORDER_STATUS)

    /**
     * Narrow grammar: every command needs the word "autopilot" (or "auto pilot"), or "nova" + an autopilot
     * noun (offer / orders / story / post). Negated orders never match, same rule as the Wi-Fi kit and the
     * JARVIS grammar - normal chat is never captured.
     */
    fun parse(text: String): Logic.Cmd? {
        if (text.isBlank() || text.length > 200) return null
        val l = Logic.norm(text)
        if (l.isEmpty()) return null
        val t = l.split(" ")
        fun has(vararg w: String): Boolean = w.any { it in t }
        val negated = t.any { it in setOf("mat", "nahi", "nahin", "nhi", "nai", "mt") }
        if (negated) return null

        val auto = has("autopilot", "autopilet", "auto-pilot") || l.contains("auto pilot")
        val onW = has("on", "chalu", "chalu)", "start", "enable", "lagao", "laga")
        val offW = has("off", "band", "bandh", "stop", "disable", "hatao")

        // "nova offer 20% off code SK20" - the offer text rides in cmd.arg
        if (l.startsWith("nova offer ") || l.startsWith("offer ") || l.contains(" offer ") && l.startsWith("nova")) {
            val arg = l.removePrefix("nova offer").removePrefix("offer").trim()
            if (arg.length in 3..140) return Logic.Cmd(K_AP_OFFER, arg)
        }
        if (auto) {
            if (has("setup", "wizard", "pehli", "shuru", "suruat")) return Logic.Cmd(K_AP_SETUP)
            if (has("status", "kaisa", "kaisi", "haal", "haalchaal", "sab")) return Logic.Cmd(K_AP_STATUS)
            if (has("aaj", "today") && has("post", "posting", "hva", "hua", "gaya")) return Logic.Cmd(K_AP_TODAY)
            if (has("orders", "order", "ordr") && has("dikhao", "dikha", "list", "batao", "suno", "bata")) return Logic.Cmd(K_AP_ORDERS)
            if (has("abhi", "now", "turant") && has("post", "karo", "bhejo", "lagao", "chalao", "chala")) return Logic.Cmd(K_AP_NOW)
            if (has("story", "stori") && has("lagao", "laga", "karo", "dalo", "daal", "bhejo")) return Logic.Cmd(K_AP_STORY)
            if (onW) return Logic.Cmd(K_AP_ON)
            if (offW) return Logic.Cmd(K_AP_OFF)
            return Logic.Cmd(K_AP_STATUS)
        }
        // "nova orders dikhao" / "nova aaj kya post hua" / "nova story lagao" without the word autopilot
        if (l.startsWith("nova")) {
            if (has("orders", "ordr", "order") && has("dikhao", "dikha", "list", "batao", "suno", "bata")) return Logic.Cmd(K_AP_ORDERS)
            if (has("aaj") && has("post", "posting")) return Logic.Cmd(K_AP_TODAY)
            if (has("story", "stori") && has("lagao", "laga", "karo", "dalo", "daal", "bhejo")) return Logic.Cmd(K_AP_STORY)
            // "nova order <naam> <status>" - the owner stores what will be told to the customer.
            // A DISPLAY ask ("list batao") never lands here (checked above).
            if (l.startsWith("nova order ")) {
                val arg = l.removePrefix("nova order").trim()
                if (arg.length in 3..160 && !has("dikhao", "dikha", "list", "batao")) return Logic.Cmd(K_ORDER_STATUS, arg)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- spoken replies (JARVIS tone, honest)

    fun replyOn(on: Boolean, armedLine: String, en: Boolean): String =
        if (!on) (if (en) "Autopilot is off." else "Autopilot band hai.")
        else (if (en) "Autopilot is on. $armedLine" else "Autopilot chalu hai. $armedLine")

    fun replyToday(posts: List<Pair<String, String>>, en: Boolean): String {
        if (posts.isEmpty()) return if (en) "Nothing was posted today, Sir." else "Sir, aaj kuch post nahi hua."
        val lines = posts.take(5).joinToString("; ") { (name, state) -> "$name ($state)" }
        val more = if (posts.size > 5) (if (en) " and ${posts.size - 5} more." else " aur ${posts.size - 5} aur.") else ""
        return (if (en) "Today, Sir, ${posts.size} posts: " else "Sir, aaj ${posts.size} post hue: ") + lines + more
    }

    fun replyOrders(orders: List<Pair<String, Boolean>>, en: Boolean): String {
        if (orders.isEmpty()) return if (en) "No orders yet, Sir." else "Sir, abhi koi order nahi aaya."
        val confirmed = orders.count { it.second }
        val lines = orders.take(4).joinToString("; ") { (name, done) -> name + (if (done) (if (en) " (confirmed)" else " (confirm)") else "") }
        return (if (en) "Orders, Sir: " else "Sir, orders: ") + lines + (if (orders.size > 4) " +" + (orders.size - 4) else "") +
            (if (confirmed > 0) (if (en) ". $confirmed confirmed." else ". $confirmed confirm ho chuke.") else "")
    }

    fun replyNow(started: Boolean, en: Boolean): String =
        if (started) (if (en) "Scanning now, Sir. I'll report when it's live." else "Sir, abhi scan kar rahi hoon. Live hote hi bataungi.")
        else (if (en) "Autopilot is off, Sir." else "Sir, autopilot abhi band hai.")

    fun replyStory(runAt: Long, en: Boolean): String =
        if (runAt > 0) (if (en) "Done, the story is on its way." else "Sir, story lagane ke liye queue me daal diya.")
        else (if (en) "Autopilot is off, Sir." else "Sir, autopilot abhi band hai.")

    // ---------------------------------------------------------------- order status (task G4)

    /** A customer's "order kab aayega" family. true only for clear status questions. */
    fun isOrderStatusQuestion(text: String): Boolean {
        val l = Logic.norm(text)
        if (l.isEmpty()) return false
        val low = l.lowercase()
        return (low.contains("order") || low.contains("parcel") || low.contains("delivery") || low.contains("deliver")) &&
            (low.contains("kab") || low.contains("status") || low.contains("kahan") || low.contains("kaha") || low.contains("pahunch") || low.contains("aaya") || low.contains("aayega") || low.contains("track"))
    }

    /** The reply comes ONLY from the owner's stored note. No note = honest line + the owner is told. */
    fun orderStatusReply(note: String, item: String, en: Boolean): String {
        val n = note.trim()
        return if (n.isNotEmpty()) {
            if (en) "Your order ${if (item.isNotBlank()) "for $item " else ""}status: $n"
            else "Aapke order ${if (item.isNotBlank()) "($item) " else ""}ka status: $n"
        } else {
            if (en) "The owner will confirm your delivery details personally - I have noted your question."
            else "Owner khud aapko delivery ki detail bata denge - aapka sawaal main note kar rahi hoon."
        }
    }

    /**
     * Splits "nova order <who> <status>" into (who, status). [who] is the FIRST word - the store then matches
     * it against order names / items (contains, both ways), so "Rahul" or "Rahul-Sharma" both find "Rahul Sharma".
     */
    fun parseOwnerOrderStatus(arg: String): Pair<String, String>? {
        val a = arg.trim()
        if (a.length < 3) return null
        val words = a.split(" ").filter { it.isNotBlank() }
        if (words.size < 2) return null
        val who = words.first().take(40)
        val note = words.drop(1).joinToString(" ").take(200)
        if (who.length < 2 || note.length < 2) return null
        return Pair(who, note)
    }
}
