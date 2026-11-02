package com.nova.assistant

/**
 * v7.146 STEP 6 OF 10: "atakna" (hang) evidence. A tiny background watchdog (MainStallWatchdog) notices when the app's main thread does not
 * answer for several seconds and keeps ONE short line per stall: when, how long, and the first NOVA code frame that was running.
 * This file is the pure part (no Android): when a delay counts as a stall, how a line is stored, and the honest spoken summary.
 * It only RECORDS. It never kills, restarts, interrupts or changes anything. Only class and method names are kept, never text the owner said.
 */
object StallRules {
    const val CHECK_MS = 1000L
    const val STALL_MS = 3500L
    /** If the watchdog's own sleep overshot this much the whole phone/process was frozen (doze), so nothing is recorded. */
    const val FROZEN_MS = 2500L
    const val MAX_KEPT = 10

    class Entry(val at: Long, val ms: Long, val frame: String)

    fun isStall(behindMs: Long): Boolean = behindMs >= STALL_MS

    fun wasFrozen(sleepTookMs: Long): Boolean = sleepTookMs >= CHECK_MS + FROZEN_MS

    /** First frame inside NOVA's own code, else the first frame, else "unknown". */
    fun topFrame(frames: List<String>): String {
        val mine = frames.firstOrNull { it.contains("com.nova.assistant") }
        return (mine ?: frames.firstOrNull() ?: "unknown").trim().take(120)
    }

    /** "com.nova.assistant.NovaService.speak(NovaService.kt:641)" -> "NovaService.speak" */
    fun shortName(frame: String): String {
        val head = frame.substringBefore('(').trim()
        val parts = head.split('.').filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts[parts.size - 2] + "." + parts[parts.size - 1] else head.ifEmpty { "unknown" }
    }

    fun encode(e: Entry): String = e.at.toString() + "|" + e.ms + "|" + e.frame.replace('|', '/').replace('\n', ' ').replace('\r', ' ')

    fun decode(line: String): Entry? {
        val p = line.split('|', limit = 3)
        if (p.size < 3) return null
        val at = p[0].toLongOrNull() ?: return null
        val ms = p[1].toLongOrNull() ?: return null
        return Entry(at, ms, p[2])
    }

    fun decodeAll(blob: String): List<Entry> = blob.split('\n').mapNotNull { decode(it) }.takeLast(MAX_KEPT)

    fun encodeAll(list: List<Entry>): String = list.takeLast(MAX_KEPT).joinToString("\n") { encode(it) }

    fun append(list: List<Entry>, e: Entry): List<Entry> = (list + e).takeLast(MAX_KEPT)

    /** One short, honest spoken answer. */
    fun reply(entries: List<Entry>, en: Boolean): String {
        if (entries.isEmpty()) return if (en)
            "No app freeze has been recorded since the watchdog started. Nothing found does not prove a freeze can never happen."
        else "Jabse ye nigrani chalu hui, koi atakna record nahi hua. Record na hona ka matlab ye nahi ki kabhi atkega nahi."
        val worst = entries.maxByOrNull { it.ms }!!
        val common = entries.groupBy { shortName(it.frame) }.maxByOrNull { it.value.size }!!
        val last = entries.last()
        val sec = { ms: Long -> ((ms + 500) / 1000).toString() }
        return if (en)
            "${entries.size} freezes recorded. Most often in ${common.key}. Longest ${sec(worst.ms)} seconds in ${shortName(worst.frame)}. The newest was ${sec(last.ms)} seconds in ${shortName(last.frame)}."
        else "${entries.size} baar atakna record hua. Sabse zyada ${common.key} me. Sabse lamba ${sec(worst.ms)} second, ${shortName(worst.frame)} me. Sabse naya ${sec(last.ms)} second, ${shortName(last.frame)} me."
    }
}
