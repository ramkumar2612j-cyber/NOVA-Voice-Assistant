package com.nova.assistant

/**
 * v7.178 CALL AGENT rules (Part 1). Pure Kotlin, no Android classes: unit-testable, mirrors the
 * style of LaptopLinkRules / AgentRules. tools/test_call_agent_contract.py reads the sets below
 * straight from this file: keep one set per line, words only [a-z0-9].
 *
 * What this is: the owner says one of
 *   (a) "jarvis <number> par call karke table book karo"   -> booking script (default, old behaviour)
 *   (b) "jarvis papa ko call karke bolo main sham tak aaunga" -> CUSTOM MESSAGE script (v7.178b)
 *   (c) "jarvis rahul ko call karke bolo callback karenge"  -> "call back karenge" line
 * JARVIS places a REAL phone call (ACTION_CALL, CALL_PHONE permission), turns the speaker ON,
 * speaks that script into the call, tracks the call state (DIALING -> ON THE CALL -> ENDED)
 * and reports back honestly when the call ends.
 *
 * v7.178b also adds the INCOMING side: a call rings -> if AutoAnswer is ON and the caller is in
 * the important set (Papa / Mummy / a saved family number, or any call when AutoAnswer All is on),
 * JARVIS accepts the call (TelecomManager.acceptRingingCall), greets the caller on the speaker
 * ("Namaste, main JARVIS bol raha hoon. Sahab abhi phone par nahi hain, main unhe bata dunga.
 * Aap apna kaam bata dijiye ya thodi der baad wapas call kijiye."), then ends and reports.
 *
 * HONEST LIMITS (told to the owner, never hidden):
 *  - Android 10+ does NOT let a normal app hear the other side of a phone call (no voice-downlink
 *    access for third-party apps). So JARVIS cannot hear the caller's reply. It is a one-way announce.
 *  - What JARVIS DOES: real dial / real accept, real speaker script, real call-state tracking,
 *    real report (duration, what was said, whether the call connected).
 *  - A true two-way conversation agent needs a cloud telephony line (Retell / Vapi style); that
 *    is a paid service and is NOT what this offline phone-side implementation pretends to be.
 *
 * Safety model (enforced here, not by any model):
 *  - A custom message is SANITIZED: deny words removed, length capped, the rest taken verbatim
 *    from the owner's own sentence (JARVIS never invents words to say on a call).
 *  - Never: payments, OTP, bank, password, Aadhaar, or anything in DENY on the call.
 *  - Auto-answer only fires for IMPORTANT callers (or all when the owner turns ALL on). It is
 *    never silent: every accepted call ends with a spoken report to the owner.
 *  - One live call agent at a time; STOP ("ruko", "bas karo") always kills it.
 */
object CallAgentRules {

    /** Max length of anything taken from the owner's sentence into the script. */
    const val MAX_DETAIL = 80

    /** Max length of a CUSTOM MESSAGE the owner asks JARVIS to say ("bolo ..." part). */
    const val MAX_CUSTOM = 160

    /** The whole spoken script is capped so a call never talks for minutes. */
    const val MAX_SCRIPT = 320

    /** How long to keep polling the call state after dialing before giving up (ms). */
    const val DIAL_TIMEOUT_MS = 45_000L

    /** Max time the agent stays on a connected call speaking the script (ms). */
    const val CALL_TIMEOUT_MS = 180_000L

    /** v7.178b: how long to greet the caller on an incoming auto-answered call before hanging up. */
    const val INCOMING_GREET_MS = 12_000L

    // ------------------------------------------------------------------ command parsing

    /** Words that make a sentence a CALL AGENT request. tools contract reads this line. */
    private val AGENT_TRIGGERS = setOf("call", "phone", "telefon", "telephone", "ring", "bol")

    /** "call karke <kaam>" style: the owner wants JARVIS to speak on the call, not just dial. */
    private val AGENT_DO = setOf("karke", "kar", "karo", "kardo", "karde", "karke", "and", "band")

    /** Booking-flavoured jobs the agent can speak on a call. */
    private val BOOKING_NOUNS = setOf("table", "booking", "reservation", "book", "order", "appointment", "slot", "seat", "seats", "table", "reservation")

    /** v7.178b: "bolo" / "bolein" / "kehna" / "keh" / "say" -> the rest of the sentence is the custom message. */
    private val SAY_WORDS = setOf("bolo", "bol", "bolein", "bolege", "kehna", "kehdo", "keh", "kaho", "kahiye", "say", "tell", "inform")

    /** v7.178b: "call back" request phrase, both directions. */
    private val CALLBACK_WORDS = setOf("callback", "call back", "wapas call", "wapas phone", "call karunga", "call karungi", "call karenge", "phone karunga", "phone karenge")

    /** v7.178b: caller-to-be-told-that-owner-will-call-back. Maps any of these to a fixed safe line. */
    private fun isCallbackRequest(detail: String): Boolean = CALLBACK_WORDS.any { detail.lowercase().replace("-", " ").contains(it) }

    /** Places this is meant for (used only to build the script greeting, never to block a dial). */
    private val PLACE_HINTS = setOf("restaurant", "hotel", "cafe", "dhaba", "salon", "doctor", "clinic", "hospital", "shop", "store", "bakery", "gym", "parlour", "parlor", "office", "dentist", "shadi", "band")

    /** Never spoken on a call and never part of a script. Enforced, not advisory. */
    private val DENY = setOf("otp", "pin", "password", "cvv", "card", "aadhaar", "aadhar", "pan", "bank", "paytm", "gpay", "upi", "payment", "paisa", "rupees", "transfer", "subscribe", "subscription", "renew", "insurance", "loan", "emi", "kyc", "debit", "credit", "netbanking", "refund", "wallet", "crypto", "bitcoin", "scheme", "invest", "profit", "trading")

    // ------------------------------------------------------------------ INCOMING CALL importance

    /** v7.178b: callers that are ALWAYS worth telling the owner about (or auto-answering when on). */
    private val FAMILY_HINTS = setOf("papa", "papa2", "pappa", "papaji", "mummy", "mummy2", "mom", "mumy", "mummyji", "amma", "ammi", "maa", "dadi", "dada", "nana", "nani", "bua", "chacha", "bhai", "didi", "beta")

    /** True when the caller name looks like a family member -> always important. */
    fun isImportantCaller(name: String): Boolean {
        val t = tokenize(name)
        return t.any { it in FAMILY_HINTS }
    }

    /**
     * v7.178b INCOMING call importance decision.
     *  autoAll = owner turned "Auto-answer ALL" on in Settings.
     *  autoFam = owner turned "Auto-answer family only" on (default).
     * Returns one of: "answer" (auto-answer + greet), "tell" (tell the owner, do not answer), "skip" (silent).
     */
    fun incomingDecision(callerName: String, autoAll: Boolean, autoFam: Boolean): String {
        if (autoAll) return "answer"
        if (autoFam && isImportantCaller(callerName)) return "answer"
        if (isImportantCaller(callerName)) return "tell"   // family always at least reported
        return "skip"
    }

    // ------------------------------------------------------------------ number / parse

    /** A plain direct call (no agent) stays with the OLD path; only agent-style requests come here. */
    class Parsed(val number: String, val detail: String, val place: String, val kind: String)

    /**
     * Extracts a phone number (digits) from free text. Accepts +91, spaces, dashes, brackets.
     * Returns "" when no number-like run of 7..15 digits is found.
     */
    fun extractNumber(text: String): String {
        val digits = StringBuilder()
        var best = ""
        for (ch in text) {
            if (ch.isDigit()) digits.append(ch)
            else {
                if (digits.length in 7..15 && digits.length > best.length) best = digits.toString()
                digits.setLength(0)
            }
        }
        if (digits.length in 7..15 && digits.length > best.length) best = digits.toString()
        if (best.length == 12 && best.startsWith("91")) best = best.substring(2)
        return best
    }

    /** True when the sentence looks like a CALL AGENT job ("call karke table book karo" OR "call karke bolo ..."). */
    fun isAgentJob(text: String): Boolean {
        val t = tokenize(text)
        if (t.isEmpty()) return false
        val hasCall = t.any { it in AGENT_TRIGGERS }
        if (!hasCall) return false
        val hasDo = t.any { it in AGENT_DO }
        if (!hasDo) return false
        // v7.178b: "call karke bolo <msg>" -> custom message agent job.
        if (t.any { it in SAY_WORDS }) return true
        // old: "call karke table book karo".
        return t.any { it in BOOKING_NOUNS }
    }

    /** True when the sentence is a STOP for the live call agent (used by the service loop). */
    fun isStop(text: String): Boolean = StopRules.isStop(text)

    /** v7.178b: the custom message the owner asked JARVIS to say ("bolo ..." part), sanitized. "" = no custom message. */
    fun customMessage(text: String): String {
        val raw = text.lowercase()
        // find the first say-word; everything after it (until end, trimmed of trailing command words) is the message
        val sayIdx = tokenize(raw).indexOfFirst { it in SAY_WORDS }
        if (sayIdx < 0) return ""
        val t = tokenize(raw)
        val msg = ArrayList<String>()
        for (i in (sayIdx + 1) until t.size) {
            val w = t[i]
            if (w in AGENT_TRIGGERS || w in AGENT_DO) break
            if (w in DENY) return ""   // a deny word inside the message kills the whole custom message
            msg.add(w)
            if (msg.joinToString(" ").length > MAX_CUSTOM) break
        }
        val joined = msg.joinToString(" ").trim().take(MAX_CUSTOM)
        // a single deny word already returned "" above; also refuse if message is just a deny-ish word alone
        return joined
    }

    /** Pulls the booking detail out of the sentence: the words after the booking noun, minus deny words. */
    fun detail(text: String): String {
        val t = tokenize(text)
        val start = t.indexOfFirst { it in BOOKING_NOUNS }
        if (start < 0) return ""
        val out = ArrayList<String>()
        for (i in (start + 1) until t.size) {
            val w = t[i]
            if (w in AGENT_TRIGGERS || w in AGENT_DO) break
            if (w in DENY) return ""   // a deny word inside the detail kills the whole script
            if (w !in FILLER) out.add(w)
            if (out.joinToString(" ").length > MAX_DETAIL) break
        }
        return out.joinToString(" ").take(MAX_DETAIL).trim()
    }

    /** A gentle place name for the script greeting ("restaurant" -> "restaurant"), "" = generic. */
    fun place(text: String): String {
        val t = tokenize(text)
        return PLACE_HINTS.firstOrNull { it in t } ?: ""
    }

    /** True when a deny word is anywhere in the job: the agent refuses before dialing. */
    fun denied(text: String): Boolean {
        val t = tokenize(text)
        if (t.any { it in DENY }) return true
        // "bank manager ko call karo" style: a deny word in the target name also refuses.
        return false
    }

    // ------------------------------------------------------------------ script building

    /**
     * The script JARVIS speaks on the call.
     *  custom non-blank  -> "Namaste, main JARVIS bol raha hoon. <custom message>. Shukriya."
     *  callback request  -> a fixed safe line ("abhi phone par nahi hain, main unhe bata dunga, aap thodi der baad wapas call kijiye").
     *  else              -> the booking script (old behaviour).
     * The same text is shown in the chat card and stored in the report, so what was said is never a mystery.
     */
    fun script(place: String, detail: String, en: Boolean, custom: String = ""): String {
        if (custom.isNotBlank()) {
            val c = custom.take(MAX_CUSTOM)
            return if (en)
                ("Hello, this is the JARVIS assistant calling on behalf of the owner. Message: $c. Thank you.").take(MAX_SCRIPT)
            else
                ("Namaste, main JARVIS assistant bol raha hoon, sahab ki taraf se. Paigam: $c. Shukriya.").take(MAX_SCRIPT)
        }
        if (isCallbackRequest(detail)) {
            return if (en)
                ("Hello, this is the JARVIS assistant. The owner cannot take this call right now. They will call you back shortly. Thank you.").take(MAX_SCRIPT)
            else
                ("Namaste, main JARVIS assistant bol raha hoon. Sahab abhi is call ka jawab nahi de sakte. Wapas call karenge, thodi der baad. Shukriya.").take(MAX_SCRIPT)
        }
        val p = if (place.isBlank()) (if (en) "hello" else "namaste") else place
        val d = detail.ifBlank { if (en) "a table for two" else "do log ke liye ek table" }
        return if (en)
            ("Hello, I am calling from the JARVIS assistant to request a booking: $d. "
                + "Could you please confirm if that is possible today? Thank you.").take(MAX_SCRIPT)
        else
            ("Namaste, main JARVIS assistant se bol raha hoon. Booking chahiye: $d. "
                + "Aaj ye possible hai kya? Shukriya.").take(MAX_SCRIPT)
    }

    /** The greeting JARVIS speaks on an INCOMING auto-answered call. One-way announce; cannot hear the caller. */
    fun incomingGreeting(ownerName: String, en: Boolean): String {
        val n = ownerName.ifBlank { "the owner" }
        return if (en)
            ("Hello, this is the JARVIS assistant. $n cannot take your call right now. Please leave a message after the tone, or call back in a little while. Thank you.").take(MAX_SCRIPT)
        else
            ("Namaste, main JARVIS assistant bol raha hoon. $n abhi phone par nahi hain. Kripya thodi der baad wapas call karein, ya apna kaam SMS se bata dein. Dhanyavaad.").take(MAX_SCRIPT)
    }

    /** Words dropped when the detail is extracted. */
    private val FILLER = setOf("jarvis", "ek", "mujhe", "mera", "meri", "ka", "ki", "ke", "ko", "koi", "kuch", "hai", "ho", "hoga", "pleez", "please", "plz", "jaldi", "abhi", "do", "de", "leni", "leni", "lo", "lagna", "kar", "karna", "chahiye", "for", "a", "an", "the", "of", "to", "want", "need", "get", "book", "booking", "table", "reservation", "appointment", "slot", "seat", "seats", "order", "the")

    private fun tokenize(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ')
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ state machine

    /** Live states, exactly the ones the card shows. */
    enum class State { IDLE, DIALING, ON_CALL, ENDED, FAILED }

    /** Live state for an INCOMING call (separate so the card shows INCOMING / ANSWERING / GREETING / ENDED). */
    enum class InState { IDLE, RINGING, ANSWERING, GREETING, ENDED }

    /** One line of the live log (ts, state, text). */
    data class Line(val ts: Long, val state: State, val text: String)

    /** Maps a TelephonyManager callState int to our state (documented constants only). */
    fun fromTelephony(callState: Int, wasOnCall: Boolean): State = when (callState) {
        0 -> if (wasOnCall) State.ENDED else State.IDLE   // CALL_STATE_IDLE
        1 -> State.DIALING                                  // CALL_STATE_RINGING (our outgoing dial ringing back) -> we show DIALING while outgoing
        2 -> State.ON_CALL                                  // CALL_STATE_OFFHOOK
        else -> if (wasOnCall) State.ENDED else State.IDLE
    }

    /** The report JARVIS speaks when the call ends. Honest about what it could and could not do. */
    fun report(state: State, name: String, number: String, secs: Long, en: Boolean): String {
        val who = name.ifBlank { number }
        return when (state) {
            State.ENDED -> if (en)
                "The call to $who has ended after $secs seconds. I said the message on the speaker. I could not hear their answer - please call back or check, sir."
            else
                "$who ki call $secs second baad khatam ho gayi. Maine baat speaker par boli. Unka jawab main sun nahi paya - aap confirm kar lena, sir."
            State.FAILED -> if (en)
                "The call to $who could not go through (not answered or busy). I can try again later, sir."
            else
                "$who ki call lag nahi payi (uthai nahi gayi ya busy). Thodi der baad try karun, sir?"
            else -> if (en) "The call agent is still working." else "Call agent abhi chal raha hai."
        }
    }

    /** v7.178b: the report JARVIS speaks after an INCOMING auto-answered call ends. */
    fun incomingReport(caller: String, secs: Long, en: Boolean): String {
        val who = caller.ifBlank { "ek number" }
        return if (en)
            "Incoming call from $who: I answered and greeted them for $secs seconds, then hung up. I could not hear their reply."
        else
            "$who ki call aayi thi: maine utha kar $secs second tak greeting di, phir rakh di. Unka jawab main sun nahi paya."
    }

    /** True when the call never connected within the dial window: the card gets the CALLBACK tag. */
    fun callbackNeeded(state: State): Boolean = state == State.FAILED

    // ------------------------------------------------------------------ v7.180 TWO-WAY TALK (speaker + mic)
    //
    // How the far side is heard without any OS bypass: the call is on SPEAKER, the phone microphone
    // picks the far side up acoustically, Whisper turns it into text, a model writes ONE short reply,
    // the rules below clean it, TTS speaks it. Some phones silence the mic during a call; that is
    // detected (see CallAgent) and reported, never hidden.

    /** Max back-and-forth turns the agent takes on one call. */
    const val MAX_TURNS = 6

    /** Quiet far side: ask "are you there?" once after this, hang up after FAR_SILENCE_END_MS. */
    const val FAR_SILENCE_PROMPT_MS = 18_000L
    const val FAR_SILENCE_END_MS = 40_000L

    /** Mic delivering only digital silence for this long while we are NOT speaking = phone blocks the mic. */
    const val MIC_SILENT_AFTER_MS = 12_000L
    const val MIC_SILENT_RMS = 3.0

    const val MAX_REPLY = 220
    const val MAX_HEARD = 240

    /** One line of the conversation. far=true: the other side said it. */
    data class Turn(val far: Boolean, val text: String)

    /** True when a call should talk (needs a speech-to-text key); booking / message / callback all may. */
    fun talkEnabled(hasHearingKey: Boolean): Boolean = hasHearingKey

    fun goalText(place: String, detail: String, custom: String, en: Boolean): String {
        if (custom.isNotBlank()) return (if (en) "Pass this message: " else "Ye paigam dena: ") + custom.take(MAX_CUSTOM)
        if (isCallbackRequest(detail)) return if (en) "Tell them the owner cannot talk now and will call back shortly." else "Unhe batana ki sahab abhi baat nahi kar sakte, thodi der baad wapas call karenge."
        val d = detail.ifBlank { if (en) "a table for two" else "do log ke liye ek table" }
        val p = if (place.isBlank()) "" else " ($place)"
        return (if (en) "Request a booking$p: " else "Booking maangni hai$p: ") + d + (if (en) ". Ask if it is possible today and which time is free." else ". Poochho aaj possible hai ya nahi, aur kaunsa time khali hai.")
    }

    /** The system prompt of the call model. Rules are enforced again after the reply (cleanReply). */
    fun convSystem(owner: String, goal: String, en: Boolean): String {
        val o = owner.ifBlank { if (en) "the owner" else "sahab" }
        val lang = if (en) "English" else "Hinglish in Roman letters (the way people speak in India)"
        return ("You are the JARVIS voice assistant on a LIVE phone call on behalf of $o. Your goal: $goal. " +
            "Speak $lang. Reply with ONLY the words to say: 1 or 2 SHORT sentences, plain spoken words, no lists, no emoji, no brackets. " +
            "You are an AI assistant calling for $o; say so honestly if asked. " +
            "NEVER give, ask for or agree to payment, card, bank, UPI, OTP, PIN, Aadhaar, password, advance or any money matter. " +
            "NEVER invent names, numbers, addresses or facts that are not in the goal. " +
            "If they ask something you do not know or cannot decide, say $o will call back. " +
            "When the goal is done, or they say no, thank them politely in one short sentence.")
    }

    /** The user turn: goal + the conversation so far. Short on purpose (the helper caps the user text). */
    fun convUser(turns: List<Turn>, en: Boolean): String {
        val sb = StringBuilder()
        val tail = turns.takeLast(8)
        for (t in tail) sb.append(if (t.far) "THEY: " else "YOU: ").append(t.text.take(MAX_HEARD)).append('\n')
        sb.append(if (en) "Now write YOUR next spoken reply." else "Ab apna agla bolne wala jawab likho.")
        return sb.toString()
    }

    fun safeLine(en: Boolean): String =
        if (en) "I cannot talk about payments or personal details on the phone. The owner will call you back. Thank you."
        else "Payment ya personal details main phone par nahi kar sakta. Sahab khud aapko wapas call karenge. Shukriya."

    fun closingLine(en: Boolean): String =
        if (en) "Thank you, goodbye." else "Shukriya, namaste."

    fun unsureLine(en: Boolean): String =
        if (en) "I will let the owner know and they will call you back. Thank you."
        else "Main sahab ko bata dunga, wo aapko wapas call karenge. Shukriya."

    fun stillThereLine(en: Boolean): String =
        if (en) "Hello, are you still there?" else "Hello, aap line par hain?"

    /** A model reply made safe to speak: one line, capped, no markdown; "" when it must not be spoken. */
    fun cleanReply(raw: String?, en: Boolean): String {
        if (raw == null) return ""
        var s = raw.replace(Regex("[\\r\\n]+"), " ").replace(Regex("[*#`_>\\[\\]{}]"), "").replace("\"", "").trim()
        if (s.startsWith("YOU:", true)) s = s.substring(4).trim()
        if (s.isBlank()) return ""
        if (s.length > MAX_REPLY) {
            val cut = s.substring(0, MAX_REPLY)
            val end = maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('?'), cut.lastIndexOf('!'))
            s = if (end > 40) cut.substring(0, end + 1) else cut
        }
        val t = tokenize(s)
        if (t.any { it in DENY }) return safeLine(en)
        if (t.any { it.length >= 6 && it.all { c -> c in '0'..'9' } }) return ""   // a long number (phone / account) is never spoken
        return s
    }

    /** The other side asked for / mentioned money or secrets: the agent says safeLine and ends. */
    fun farSideSensitive(heard: String): Boolean = denied(heard)

    private val GOODBYE = setOf("bye", "goodbye", "alvida", "rakhta", "rakhti", "rakhte")
    private val THANKS = setOf("thanks", "thank", "shukriya", "dhanyavaad", "dhanyavad", "thankyou")

    /** The other side ended the conversation. */
    fun isGoodbye(heard: String): Boolean {
        val t = tokenize(heard)
        if (t.isEmpty()) return false
        if (t.any { it in GOODBYE }) return true
        return t.size <= 4 && t.any { it in THANKS }
    }

    /** Our own speaker voice heard back through the mic: most of the heard words are in what we just said. */
    fun isEcho(heard: String, spoken: String): Boolean {
        val h = tokenize(heard)
        if (h.size < 2 || spoken.isBlank()) return false
        val sp = tokenize(spoken).toSet()
        val same = h.count { it in sp }
        return same * 10 >= h.size * 7
    }

    /** Heard text capped and made one line. */
    fun cleanHeard(heard: String): String = heard.replace(Regex("\\s+"), " ").trim().take(MAX_HEARD)

    /** Last thing the far side said, for a report when no model summary exists. */
    fun fallbackSummary(turns: List<Turn>, en: Boolean): String {
        val far = turns.filter { it.far }.takeLast(2).joinToString(" / ") { it.text.take(90) }
        return if (far.isBlank()) (if (en) "no clear answer" else "koi saaf jawab nahi") else far
    }

    fun summarySystem(en: Boolean): String =
        if (en) "You summarise a phone call for the owner. ONE short sentence: what the other side answered (yes/no, free time, price, any condition). Facts only, no guessing."
        else "Tum owner ke liye phone call ka summary likhte ho. SIRF EK chhota Hinglish vaakya: samne wale ne kya jawab diya (haan/na, khali time, kya shart). Sirf wahi jo bola gaya, andaza nahi."

    fun summaryUser(turns: List<Turn>): String {
        val sb = StringBuilder()
        for (t in turns.takeLast(12)) sb.append(if (t.far) "THEY: " else "US: ").append(t.text.take(MAX_HEARD)).append('\n')
        return sb.toString()
    }

    /** The report spoken after a TWO-WAY call. Honest: says what they said, and when we could not hear. */
    fun talkReport(name: String, number: String, secs: Long, summary: String, heardAnything: Boolean, micBlocked: Boolean, sensitive: Boolean, en: Boolean): String {
        val who = name.ifBlank { number }
        if (micBlocked) return if (en)
            "The call to $who lasted $secs seconds. I said my part, but this phone blocks the microphone during calls, so I could not hear their answer. Please confirm yourself, sir."
        else
            "$who ki call $secs second chali. Maine apni baat boli, lekin is phone ne call ke dauran mic band rakha, isliye unka jawab sun nahi paya. Aap khud confirm kar lena, sir."
        if (!heardAnything) return if (en)
            "The call to $who lasted $secs seconds. They did not say anything I could understand. Call back needed, sir."
        else
            "$who ki call $secs second chali. Unhone kuch samajh aane layak nahi bola. Dobara call karni padegi, sir."
        val tail = if (sensitive) (if (en) " They brought up money or personal details, so I stopped and said you will call back." else " Unhone paise ya personal baat uthayi, isliye maine rok diya aur kaha ki aap call karenge.") else ""
        return if (en) "Call with $who ended after $secs seconds. They said: $summary.$tail" else "$who se baat $secs second baad khatam hui. Unhone kaha: $summary.$tail"
    }

    /** A model summary of the call made safe to speak / show: one line, capped. "" when empty. */
    fun cleanSummary(raw: String?): String =
        (raw ?: "").replace(Regex("[\\r\\n]+"), " ").replace(Regex("[*#`_>]"), "").replace(Regex("\\s+"), " ").trim().take(220)

}
