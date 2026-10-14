package com.nova.assistant

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Part 6A: BACKGROUND AUDIO RECORDING. Pure Kotlin (no Android classes), unit tested in RecordRulesTest.
 *
 * What lives here:
 *  - parse(): is this sentence a recording order (start / stop / status), and did the owner also say "mobile off / lock kar do"?
 *    Anything that is not clearly one of these three (questions, "recording sunao", "screen recording", "video", call recording,
 *    negations like "recording band mat karo") returns null, so it can never start or stop a recording by accident.
 *  - limits: segment length, maximum length, free space needed to start / to keep going.
 *  - file names, durations, and every message the owner sees (Hinglish or English).
 *
 * Honest design rules (the owner's orders): recording is never hidden. The foreground notification (with a STOP button) is always
 * shown by Android, and the phone's own mic indicator is not touched. Nothing here sends audio anywhere.
 */
object RecordRules {
    enum class Action { START, STOP, STATUS }

    /** [alsoLock] = the owner also said "mobile off kar do" / "screen lock karo" in the same sentence. */
    class Req(val action: Action, val alsoLock: Boolean)

    const val RATE = 16000
    const val BITRATE = 32000                        // AAC mono, speech quality: about 14 MB per hour
    const val SEGMENT_MS = 30L * 60L * 1000L         // a new file every 30 minutes, so a crash can only damage the current part
    const val MAX_MS = 3L * 60L * 60L * 1000L        // hard stop after 3 hours (then the owner can simply start again)
    const val MIN_START_FREE = 150L * 1024L * 1024L  // refuse to start with less free space than this
    const val STOP_FREE = 60L * 1024L * 1024L        // stop (and save) when free space falls under this
    const val REL_PATH = "Music/NOVA"                // where the finished files are shown on Android 10+

    private val REC = setOf(
        "record", "recording", "recordings", "recorder", "recoding", "rikard", "rikaard", "rikord", "rikording", "rikarding", "rekord", "rakord"
    )
    private val DEVICE = setOf("mobile", "mobail", "phone", "fone", "screen", "display", "skrin")
    private val LOCKW = setOf("off", "band", "bandh", "lock", "lok", "sleep", "bujha", "bujhao")
    private val GAP = setOf("ko", "ka", "ki", "ke", "ek", "ya", "ab", "bhi", "turant", "jaldi", "fir", "phir", "the", "my", "mera", "mere", "meri")

    // another feature owns these sentences (screen / video / call recording, playing, deleting, sharing, questions)
    private val NOT = setOf(
        "screen", "screens", "camera", "video", "videos", "vedio", "selfie", "front", "call", "calls",
        "kholo", "khol", "open", "play", "sunao", "suno", "sun", "delete", "hatao", "mita", "mitao", "remove",
        "share", "bhejo", "send", "whatsapp", "gallery", "list", "dikhao", "dikha", "show", "kahan", "kaha", "where", "file", "files", "folder"
    )
    private val QUESTION = setOf("kaise", "kese", "kya", "kyun", "kyu", "kab", "how", "what", "why", "when", "which", "kaun", "kon", "konsi", "kaunsi")
    private val NEGATION = setOf("mat", "nahi", "nahin", "nhi", "dont", "don", "not", "never")
    private val STOP = setOf("band", "bandh", "stop", "rok", "roko", "rokdo", "ruko", "off", "khatam", "end", "finish", "pura", "poora", "complete", "save")
    private val START = setOf(
        "on", "shuru", "start", "begin", "chalu", "chalao", "chala", "lagao", "laga", "enable",
        "kar", "karo", "kardo", "karna", "kijiye", "kariye", "do", "de", "dena"
    )
    private val VOICEY = setOf("voice", "audio", "awaaz", "awaz", "sound")
    private val RUNNING = setOf("chal", "chalu", "chalra", "raha", "rahi", "rha", "rhi", "on", "running")

    fun tokens(s: String): List<String> {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else sb.append(' ')
        }
        val out = ArrayList<String>()
        for (w in sb.toString().split(' ')) if (w.isNotEmpty()) out.add(w)
        return out
    }

    /** Cuts "mobile off kar do" / "phone ko lock karo" / "lock kar do" out of the sentence. Returns the rest and whether a lock was asked. */
    private fun stripLock(t: List<String>): Pair<List<String>, Boolean> {
        val out = ArrayList<String>()
        var lock = false
        var i = 0
        while (i < t.size) {
            val w = t[i]
            if (w in DEVICE) {
                var j = i + 1
                var found = -1
                while (j < t.size && j <= i + 3) {
                    if (t[j] in LOCKW) { found = j; break }
                    if (t[j] !in GAP) break
                    j++
                }
                if (found >= 0) { lock = true; i = found + 1; continue }
            }
            if (w == "lock" || w == "lok") { lock = true; i++; continue }
            out.add(w)
            i++
        }
        return Pair(out, lock)
    }

    /** null = not a recording order. */
    fun parse(text: String): Req? {
        val all = tokens(text)
        if (all.isEmpty() || all.size > 30) return null
        val sp = stripLock(all)
        val t = sp.first
        val lock = sp.second
        if (t.none { it in REC }) return null
        val s = t.toSet()
        fun has(vararg w: String): Boolean = w.any { it in s }
        if (t.any { it in NOT }) return null
        // status: "recording chal rahi hai kya", "recording status", "kitni der se recording ho rahi hai"
        val status = has("status") || has("running") || (has("kya") && t.any { it in RUNNING }) ||
            (has("kitni", "kitna") && has("der", "deer", "lambi", "hui", "hua"))
        if (status) return Req(Action.STATUS, false)
        if (t.any { it in QUESTION }) return null
        if (t.any { it in NEGATION }) return null
        val stop = t.any { it in STOP }
        val startWord = has("on", "shuru", "start", "begin", "chalu", "chalao", "chala", "lagao", "laga", "enable")
        val helper = t.any { it in START }
        if (stop) {
            if (startWord) return null   // "chalu ... band" in one sentence: unclear, never guess
            return Req(Action.STOP, lock)
        }
        if (helper || (has("record") && t.any { it in VOICEY })) return Req(Action.START, lock)
        return null
    }

    // ---------------------------------------------------------------- limits

    /** "max" (3 hours reached), "space" (phone is nearly full), or null = keep recording. */
    fun autoStopReason(elapsedMs: Long, freeBytes: Long): String? = when {
        elapsedMs >= MAX_MS -> "max"
        freeBytes < STOP_FREE -> "space"
        else -> null
    }

    /** null = ok to start, otherwise the message code. */
    fun startCheck(freeBytes: Long): String? = if (freeBytes < MIN_START_FREE) "low_space" else null

    fun estimateBytes(ms: Long): Long = ms * BITRATE / 8000L

    /** Which 30-minute file this moment belongs to (1, 2, 3 ...). */
    fun partOf(elapsedMs: Long): Int = (elapsedMs / SEGMENT_MS).toInt() + 1

    fun ptsUs(totalSamples: Long): Long = totalSamples * 1_000_000L / RATE

    private val NAME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /** NOVA_audio_20261011_153012.m4a, later parts NOVA_audio_20261011_153012_p2.m4a. */
    fun segName(startMs: Long, zone: ZoneId, part: Int): String {
        val stamp = NAME_FMT.format(Instant.ofEpochMilli(startMs).atZone(zone))
        return "NOVA_audio_" + stamp + (if (part > 1) "_p$part" else "") + ".m4a"
    }

    fun fmtDur(ms: Long, en: Boolean): String {
        val total = if (ms < 0) 0L else ms / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val sec = total % 60L
        return when {
            h > 0 -> if (en) "$h h $m min" else "$h ghanta $m min"
            m > 0 -> "$m min $sec sec"
            else -> "$sec sec"
        }
    }

    // ---------------------------------------------------------------- messages

    fun text(code: String, en: Boolean, arg: String = ""): String = when (code) {
        "started" -> if (en) "Recording started. Notification has a STOP button. Say: nova stop recording."
            else "Recording shuru ho gayi. Notification mein STOP ka button hai. Band karne ke liye bolo: nova recording band karo."
        "lock_ok" -> if (en) " Locking the screen now." else " Ab screen lock kar raha hoon."
        "lock_fail" -> if (en) " I could not lock the screen: $arg" else " Screen lock nahi kar paya: $arg"
        "already" -> if (en) "A recording is already running ($arg)." else "Recording pehle se chal rahi hai ($arg)."
        "not_running" -> if (en) "No recording is running right now." else "Abhi koi recording nahi chal rahi."
        "status_on" -> if (en) "Recording is running: $arg." else "Recording chal rahi hai: $arg."
        "no_nova" -> if (en) "Press ACTIVATE first: recording uses NOVA's microphone." else "Pehle NOVA ko ACTIVATE karo: recording NOVA ke mic se hoti hai."
        "low_space" -> if (en) "Not enough free space (at least 150 MB needed). Recording did not start."
            else "Phone mein jagah kam hai (kam se kam 150 MB chahiye). Recording shuru nahi hui."
        "start_failed" -> if (en) "Recording could not start: $arg" else "Recording shuru nahi ho payi: $arg"
        "saved" -> if (en) "Recording stopped and saved: $arg" else "Recording band ho gayi aur save ho gayi: $arg"
        "saved_none" -> if (en) "Recording stopped but nothing could be saved (too short or the phone refused)." else "Recording band ho gayi par kuch save nahi ho paya (bahut chhoti thi ya phone ne mana kiya)."
        "auto_max" -> if (en) "3 hours reached, so I stopped and saved. Say start again to continue." else "3 ghante poore ho gaye, isliye rok kar save kar diya. Dobara shuru karne ke liye bolo."
        "auto_space" -> if (en) "The phone is almost full, so I stopped and saved." else "Phone lagbhag full hai, isliye rok kar save kar diya."
        "auto_error" -> if (en) "The recorder had a problem, so I stopped and saved what I had." else "Recorder mein dikkat aayi, isliye jo record hua tha wo save kar diya."
        else -> arg
    }

    /** Where the saved files are, for the "saved" message. */
    fun whereMedia(n: Int, en: Boolean): String = if (en) "$n file(s) in Music > NOVA" else "$n file Music > NOVA folder mein"

    fun whereApp(n: Int, path: String, en: Boolean): String =
        if (en) "$n file(s) in the NOVA app folder: $path" else "$n file NOVA ke app folder mein: $path"
}
