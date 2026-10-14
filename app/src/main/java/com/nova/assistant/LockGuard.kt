package com.nova.assistant

/**
 * v6.98 LOCK GUARD (owner request 15 Oct 2026). Pure Kotlin (no Android classes), unit tested.
 *
 * While the phone is LOCKED NOVA is quiet:
 *  - it takes NO voice commands (the service gate uses [quietNow]);
 *  - it does NOT read normal messages (announcements are held back);
 *  - it speaks ONLY these important things, with a loud beep first (LockAlert.kt):
 *      1. a CALL from anyone (normal call, WhatsApp / Instagram / Telegram / Messenger ... voice or video) while the phone is SILENT;
 *      2. a call from Papa or Mummy, silent or not;
 *      3. a message from Papa or Mummy (never its text: a locked phone must not read private text aloud; only "Papa ka message aaya").
 *  - one press of a volume key stops the alert (Accessibility key filter, see NovaAccessibilityService.onKeyEvent).
 *
 * Family = contact names in [PAPA] / [MUMMY] (same words as the battery SOS, plus Hindi script).
 */
object LockGuard {
    enum class Kind { CALL_RINGING, CALL_MISSED, MESSAGE }

    class Alert(val kind: Kind, val who: String, val family: String?, val app: String, val video: Boolean) {
        /** Same call / sender is not announced again inside [DEDUPE_MS]. */
        val key: String get() = (if (kind == Kind.MESSAGE) "m|" else "c|") + (family ?: who.lowercase().trim()) + "|" + app
    }

    const val DEDUPE_MS = 45_000L          // ringing notifications are re-posted many times: one alert per call
    const val MISSED_AFTER_RING_MS = 3 * 60_000L   // "missed" after a ring alert for the same person: already told
    const val MAX_FAMILY_TOKENS = 3        // "Papa", "Papa Jio", "Mummy ❤️" are family; "Mama Ji Sweet Shop Delhi" is not

    private val PAPA = BatteryRules.PAPA + listOf("पापा", "पिताजी", "पिता", "बाबा", "abbu", "abba", "baba", "appa", "papaji")
    private val MUMMY = BatteryRules.MUMMY + listOf("मम्मी", "माँ", "मां", "मॉम", "अम्मी", "ammi", "amma", "mumy", "mummyji")

    private val GROUP_WORDS = setOf("family", "group", "parivar", "gang", "friends", "team", "cousins", "ghar", "परिवार", "ग्रुप")

    private fun tokens(s: String): List<String> =
        s.lowercase().split(Regex("[^\\p{L}\\p{M}]+")).filter { it.isNotBlank() }

    /** "Papa" / "Mummy" when [name] is a family contact name, else null. Groups must be filtered by the caller. */
    fun familyOf(name: String): String? {
        val t = tokens(name)
        if (t.isEmpty() || t.size > MAX_FAMILY_TOKENS) return null
        if (t.any { it in GROUP_WORDS }) return null                  // "Family Papa Mummy" is a group name, not Papa
        if (t.any { it in PAPA }) return "Papa"
        if (t.any { it in MUMMY }) return "Mummy"
        return null
    }

    /** Packages whose notifications can be a CALL. Used only to label the app; the decision comes from category / action words. */
    private val APP_LABELS = mapOf(
        "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp Business",
        "com.instagram.android" to "Instagram", "org.telegram.messenger" to "Telegram",
        "com.facebook.orca" to "Messenger", "com.facebook.katana" to "Facebook",
        "org.thoughtcrime.securesms" to "Signal", "com.google.android.apps.tachyon" to "Meet",
        "com.google.android.apps.meetings" to "Meet", "com.skype.raider" to "Skype",
        "us.zoom.videomeetings" to "Zoom", "com.snapchat.android" to "Snapchat", "com.imo.android.imoim" to "imo"
    )

    /** Chat apps whose notification from a family contact counts as a family message. */
    private val CHAT_PKGS = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms", "com.miui.mms", "org.thoughtcrime.securesms",
        "com.facebook.orca", "com.instagram.android"
    )

    fun appLabel(pkg: String): String = APP_LABELS[pkg] ?: ""

    private val DIALER_FRAGMENTS = listOf("dialer", "telecom", "incallui", "phone", "contacts")
    private fun isDialer(pkg: String): Boolean = pkg.lowercase().let { p -> DIALER_FRAGMENTS.any { it in p } }

    private val ANSWER_WORDS = listOf("answer", "accept", "decline", "reject", "dismiss call", "उठाएं", "उठाओ", "अस्वीकार", "जवाब दें", "उत्तर दें")
    private val RING_TEXT = listOf("incoming", "is calling", "calling you", "calling…", "calling...", "ringing", "आ रही", "कॉल आ", "कॉल कर रहा", "कॉल कर रही")
    private val MISSED_TEXT = listOf("missed", "मिस्ड", "छूटी", "मिस कॉल", "miss call")
    private val CALL_WORD = listOf("call", "कॉल")
    private val HANGUP_WORDS = listOf("hang up", "hangup", "end call", "disconnect", "कॉल समाप्त", "कट करें")

    /**
     * Is this notification a call? null = not a call (or an ongoing, already answered call).
     * [category] = Notification.category ("call", "missed_call", "msg" ...), [actions] = titles of its action buttons.
     * A normal chat message that merely says "call me" is NOT a call: it needs the call category, an Answer/Decline button, or a ringing text.
     */
    fun callKind(pkg: String, category: String?, title: String, text: String, actions: List<String>): Kind? {
        val cat = (category ?: "").lowercase()
        val tx = (title + " " + text).lowercase()
        val acts = actions.map { it.lowercase() }
        val missedText = MISSED_TEXT.any { it in tx } && CALL_WORD.any { it in tx } && text.length <= 40   // a long chat line like "I missed your call" is a message, not a missed call
        if (cat == "missed_call" || missedText) return Kind.CALL_MISSED
        val answerBtn = acts.any { a -> ANSWER_WORDS.any { it in a } }
        val hangBtn = acts.any { a -> HANGUP_WORDS.any { it in a } }
        if (hangBtn && !answerBtn) return null                      // the call is already answered
        val ringText = RING_TEXT.any { it in tx }
        if (answerBtn) return Kind.CALL_RINGING
        if (cat == "call" && (ringText || isDialer(pkg) || pkg in APP_LABELS)) return if (hangBtn) null else Kind.CALL_RINGING
        if (ringText && CALL_WORD.any { it in tx } && pkg in APP_LABELS && cat != "msg") return Kind.CALL_RINGING
        return null
    }

    fun isVideo(title: String, text: String): Boolean {
        val tx = (title + " " + text).lowercase()
        return "video" in tx || "वीडियो" in tx
    }

    fun isChatPackage(pkg: String): Boolean = pkg in CHAT_PKGS

    /** The alert for one notification, or null when it is none of the three important things (see class comment). */
    fun classify(
        pkg: String, category: String?, title: String, text: String, actions: List<String>,
        isGroup: Boolean, silent: Boolean
    ): Alert? {
        val call = callKind(pkg, category, title, text, actions)
        if (call != null) {
            val who = callerName(title, text)
            val fam = if (isGroup) null else familyOf(who)
            if (fam == null && !silent) return null                  // a normal call on a phone that rings: the ring is the alert
            return Alert(call, who, fam, if (isDialer(pkg)) "" else appLabel(pkg), isVideo(title, text))
        }
        if (!isGroup && isChatPackage(pkg) && category != "call") {
            val who = cleanWho(title)
            val fam = familyOf(who) ?: return null
            return Alert(Kind.MESSAGE, who, fam, appLabel(pkg), false)
        }
        return null
    }

    private val GENERIC_TITLE = setOf("incoming", "missed", "calling", "call", "voice", "video", "new", "phone", "ringing", "कॉल", "मिस्ड", "आने", "वाली")

    /** Some dialers put "Incoming call" in the title and the caller's name in the text: then the name is the text. */
    fun callerName(title: String, text: String): String {
        val tt = tokens(title)
        val generic = tt.isEmpty() || tt.all { it in GENERIC_TITLE }
        val textGeneric = tokens(text).all { it in GENERIC_TITLE }
        return if (generic && text.isNotBlank() && !textGeneric) cleanWho(text) else cleanWho(title)
    }

    /** "Papa (3 messages)" -> "Papa"; "Rahul: hi" stays the sender only. */
    fun cleanWho(title: String): String {
        var t = title.trim()
        t = t.replace(Regex("\\(\\d+\\s*(?:new\\s*)?(?:messages?|msgs?)\\)", RegexOption.IGNORE_CASE), "").trim()
        val i = t.indexOf(':')
        if (i in 1..30) t = t.substring(0, i).trim()
        return t.take(40)
    }

    /**
     * Last gate, after [classify]: the phone must be locked and the feature on.
     * A MISSED call right after a RING alert for the same person is not told twice ([lastRingAt] = time of that ring alert, 0 = none).
     */
    fun shouldAlert(a: Alert, on: Boolean, locked: Boolean, now: Long, lastRingAt: Long): Boolean {
        if (!on || !locked) return false
        if (a.kind == Kind.CALL_MISSED && lastRingAt > 0L && now >= lastRingAt && now - lastRingAt < MISSED_AFTER_RING_MS) return false
        return true
    }

    /** What is spoken after the beep. Never contains message text. */
    fun text(a: Alert, silent: Boolean, en: Boolean): String {
        val who = a.family ?: a.who.ifBlank { if (en) "someone" else "koi" }
        val kindWord = if (a.video) (if (en) "video call" else "video call") else (if (en) "call" else "call")
        val via = if (a.app.isBlank()) "" else (if (en) " on ${a.app}" else " ${a.app} par")
        val silentNote = if (silent) (if (en) " The phone is silent." else " Phone silent hai.") else ""
        val silentPast = if (silent) (if (en) " The phone was silent." else " Phone silent tha.") else ""
        return when (a.kind) {
            Kind.CALL_RINGING ->
                if (en) "$who is calling you$via ($kindWord).$silentNote"
                else "$who ki$via $kindWord aa rahi hai.$silentNote"
            Kind.CALL_MISSED ->
                if (en) "$who called you$via ($kindWord).$silentPast"
                else "$who ki$via $kindWord aayi thi.$silentPast"
            Kind.MESSAGE ->
                if (en) "A message from $who$via.$silentNote"
                else "$who ka$via message aaya hai.$silentNote"
        }.replace("  ", " ").trim()
    }

    /** One short line for the chat history / log: the same as the speech. */
    fun logLine(a: Alert, silent: Boolean, en: Boolean): String = text(a, silent, en)

    /** Same call is told once per [DEDUPE_MS]. Times are wall-clock milliseconds. */
    class Gate {
        private val last = HashMap<String, Long>()
        private val ring = HashMap<String, Long>()

        fun allow(a: Alert, now: Long): Boolean {
            val t = last[a.key]
            return !(t != null && now >= t && now - t < DEDUPE_MS)
        }

        fun record(a: Alert, now: Long) {
            last[a.key] = now
            if (a.kind == Kind.CALL_RINGING) ring[personKey(a)] = now
            if (last.size > 60) last.entries.removeAll { now - it.value >= DEDUPE_MS }
            if (ring.size > 60) ring.entries.removeAll { now - it.value >= MISSED_AFTER_RING_MS }
        }

        /** Time of the last RING alert for the same person (0 = none). */
        fun lastRing(a: Alert): Long = ring[personKey(a)] ?: 0L

        private fun personKey(a: Alert): String = a.family ?: a.who.lowercase().trim()
    }

    // ------------------------------------------------------------------ the quiet rule for the voice loop

    /**
     * true = NOVA must not listen for commands and must not read normal messages right now.
     * Driving mode is the owner's own hands-free switch: it stays usable with the screen off.
     */
    fun quietNow(on: Boolean, locked: Boolean, drivingMode: Boolean): Boolean = on && locked && !drivingMode

    /** Phone is silent: ringer mode is silent / vibrate, or the ring volume is zero. */
    fun isSilent(ringerNormal: Boolean, ringVolume: Int): Boolean = !ringerNormal || ringVolume <= 0

    /** Alert volume rule: the alarm stream is raised to at least this fraction while an alert plays (then restored). */
    const val MIN_ALARM_FRACTION = 0.6
    const val ROUNDS = 3                    // the alert repeats up to 3 times, 12 s apart, until a volume key / unlock
    const val ROUND_GAP_MS = 12_000L

    fun targetAlarmVolume(current: Int, max: Int): Int {
        if (max <= 0) return current
        val want = Math.ceil(max * MIN_ALARM_FRACTION).toInt()
        return if (current >= want) current else want.coerceAtMost(max)
    }

    /** Is this key a volume key that stops an alert? */
    fun isStopKey(keyCode: Int): Boolean = keyCode == 24 || keyCode == 25   // KEYCODE_VOLUME_UP / KEYCODE_VOLUME_DOWN
}
