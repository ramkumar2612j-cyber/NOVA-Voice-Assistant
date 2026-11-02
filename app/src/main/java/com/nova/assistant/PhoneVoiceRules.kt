package com.nova.assistant

/**
 * v6.99 PHONE VOICE RULES. Pure Kotlin (no Android classes), unit tested.
 * Voice commands the audit found missing: answer / reject a call, screenshot, ringer mode, auto-rotation.
 * Result is a [Logic.Cmd] so skills, history and every other path treat it like any other command.
 *
 * Kinds: "call_answer", "call_reject", "screenshot", "ringer" (arg silent|vibrate|normal), "rotation" (arg on|off).
 * Runs BEFORE the "call <name>" rule, so "call uthao" never dials a contact called "uthao".
 */
object PhoneVoiceRules {
    const val K_ANSWER = "call_answer"
    const val K_REJECT = "call_reject"
    const val K_SHOT = "screenshot"
    const val K_RINGER = "ringer"
    const val K_ROTATION = "rotation"

    val KINDS = setOf(K_ANSWER, K_REJECT, K_SHOT, K_RINGER, K_ROTATION)

    /** Kinds that Logic.classify hands out: Part 1 = calls, Part 2 = screenshot / ringer / rotation. */
    val ACTIVE = KINDS

    private val NEG = setOf("mat", "nahi", "nahin", "nhi", "nai", "dont", "don", "not", "never", "bina", "mt")
    private val CALLW = setOf("call", "phone", "fone", "फोन", "कॉल")
    private val ANSWER = setOf("uthao", "utha", "uthalo", "uthana", "uthaiye", "uthayiye", "receive", "recieve", "accept", "answer", "pickup", "pick", "attend", "rieceive", "utho")
    private val REJECT = setOf("kaato", "kaat", "kat", "katt", "cut", "reject", "decline", "disconnect", "hangup", "hang", "cancel", "kaatdo", "katdo", "kato", "end", "drop")
    private val SEND_WORDS = setOf("delete", "hatao", "bhejo", "send", "share", "analyze", "analyse", "dekho", "padho", "read")

    private fun toks(norm: String): List<String> = norm.split(" ").filter { it.isNotBlank() }

    /** [norm] = Logic.norm(text) with the wake word removed. null = not one of these commands. */
    fun parse(norm: String): Logic.Cmd? {
        val t = toks(norm)
        if (t.isEmpty() || t.size > 7) return null
        if (t.any { it in NEG }) return null
        val s = t.toSet()
        fun has(vararg w: String) = w.any { it in s }

        // ---- call answer / reject
        val callWord = has("call", "phone", "fone")
        if (callWord || (has("hang") && has("up")) || (has("pick") && has("up"))) {
            val phoneOnly = !has("call") && has("phone", "fone")
            val ans = has(*ANSWER.toTypedArray()) || (has("le") && has("lo", "lijiye", "lijie"))
            val rej = has(*REJECT.toTypedArray()) || (has("band") && has("call"))
            // "phone band karo" is a power question, not a call: band needs the word call
            if (ans && !rej) return Logic.Cmd(K_ANSWER)
            if (rej && !ans) {
                if (phoneOnly && has("band", "end", "drop", "cancel")) return null
                return Logic.Cmd(K_REJECT)
            }
        }
        if (norm == "hang up" || norm == "end call" || norm == "hangup") return Logic.Cmd(K_REJECT)

        // ---- screenshot
        val shot = has("screenshot", "screenshots") || (has("screen") && has("shot")) || (has("ss") && has("lo", "le", "karo", "take"))
        if (shot && t.none { it in SEND_WORDS } && t.size <= 5) return Logic.Cmd(K_SHOT)

        // ---- ringer mode
        val ringerCtx = has("ringer", "ringtone") || (has("mode") && has("silent", "vibrate", "vibration", "normal", "general", "ring")) ||
            (has("phone", "mobile", "fone") && has("silent", "vibrate", "vibration"))
        if (ringerCtx && !has("replies", "reply", "voice", "quiet", "notification", "notifications", "disturb", "dnd", "volume", "alarm")) {
            val mode = when {
                has("silent", "mute", "off", "band") -> "silent"
                has("vibrate", "vibration", "vib") -> "vibrate"
                has("normal", "general", "ring", "on", "chalu", "loud", "sound") -> "normal"
                else -> null
            }
            if (mode != null) return Logic.Cmd(K_RINGER, mode)
        }

        // ---- auto rotation
        val rot = has("rotation", "rotate", "autorotate", "ghumao") || (has("auto") && has("rotate", "rotation"))
        if (rot && t.size <= 6) {
            if (has("off", "band", "bandh", "disable", "lock", "stop")) return Logic.Cmd(K_ROTATION, "off")
            if (has("on", "chalu", "enable", "start", "unlock")) return Logic.Cmd(K_ROTATION, "on")
        }
        return null
    }

    /**
     * Which notification action button to use. [titles] = the visible button titles of the call notification.
     * Answer words: answer / accept / उठा... ; decline words: decline / reject / dismiss / hang up. -1 = none.
     */
    fun pickAction(titles: List<String>, answer: Boolean): Int {
        val ans = listOf("answer", "accept", "pick up", "pickup", "उठाएं", "उठाओ", "जवाब दें", "उत्तर दें", "स्वीकार")
        val dec = listOf("decline", "reject", "dismiss", "hang up", "hangup", "end call", "अस्वीकार", "अस्वीकृत", "काटें", "कट करें", "कॉल समाप्त")
        val want = if (answer) ans else dec
        val other = if (answer) dec else ans
        titles.forEachIndexed { i, ti ->
            val l = ti.lowercase()
            if (want.any { it in l } && other.none { it in l }) return i
        }
        return -1
    }

    /** Spoken confirmations (Hinglish / English). */
    fun doneText(kind: String, arg: String, en: Boolean): String = when (kind) {
        K_ANSWER -> if (en) "Call picked up" else "Call utha liya"
        K_REJECT -> if (en) "Call rejected" else "Call kaat diya"
        K_SHOT -> if (en) "Screenshot saved to the gallery" else "Screenshot gallery mein save ho gaya"
        K_RINGER -> when (arg) {
            "silent" -> if (en) "Phone is on silent" else "Phone silent par hai"
            "vibrate" -> if (en) "Phone is on vibrate" else "Phone vibrate par hai"
            else -> if (en) "Ringer is on" else "Ringer chalu hai"
        }
        K_ROTATION -> if (arg == "on") (if (en) "Auto-rotate is on" else "Auto-rotate chalu hai") else (if (en) "Auto-rotate is off" else "Auto-rotate band hai")
        else -> if (en) "Done" else "Ho gaya"
    }
}
