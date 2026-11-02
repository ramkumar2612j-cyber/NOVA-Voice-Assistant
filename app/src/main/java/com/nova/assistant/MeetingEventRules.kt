package com.nova.assistant

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * v7.90: "3:30 ka schedule banao ki hume RSS meeting me jana hai" must CREATE an event (title + time),
 * not just open an empty calendar screen. Pure Kotlin, no Android classes, so JUnit can test it.
 * Runs after ScheduledTaskRules (which needs the word "baje"), so old schedule phrases keep working.
 */
object MeetingEventRules {
    data class Spec(val title: String, val startMillis: Long, val dayOffset: Int, val hour: Int, val minute: Int)

    private val NOUNS = setOf("meeting", "meetings", "meating", "meting", "mitting", "mtg", "appointment", "event", "interview", "class", "call", "presentation")
    private val INTENT = setOf("schedule", "calendar", "calender", "meeting", "meating", "meting", "mitting", "mtg", "appointment", "event", "interview", "presentation")
    private val ACTION = setOf("banao", "bana", "bnao", "bnana", "banana", "add", "set", "lagao", "laga", "daalo", "dalo", "dal", "create", "save", "likho", "likh", "jana", "jaana", "jaunga", "jaungi", "hai", "rakho", "rakh", "fix", "book", "karo", "kar", "kardo", "do", "de")
    private val BLOCK = setOf("record", "recording", "delete", "hatao", "hata", "cancel", "band", "list", "dikhao", "dikha", "kya", "kab", "kitne", "kaun", "join", "start", "shuru", "bhejo", "bhej", "send", "weather", "mausam")
    private val STOP = setOf(
        "ka", "ki", "ke", "ko", "me", "mein", "main", "se", "par", "pe", "pr", "abhi", "aaj", "ab", "kal", "tomorrow", "today", "parso",
        "schedule", "banao", "bana", "bnao", "do", "de", "hume", "humein", "hamein", "mujhe", "mere", "meri", "mera", "hamara", "hamari",
        "jana", "jaana", "jaunga", "jaungi", "hai", "ha", "hain", "nova", "please", "add", "set", "karo", "kar", "kardo", "lagao", "laga", "daalo", "dalo",
        "calendar", "calender", "liye", "ek", "naya", "new", "baje", "bje", "subah", "shaam", "sham", "raat", "dopahar", "morning", "evening", "night", "afternoon", "am", "pm",
        "saade", "sade", "sawa", "paune", "ki", "taki", "ke", "wala", "wali", "hona", "hoga", "hogi", "he", "to", "toh", "or", "aur", "bhi", "yaad", "rakhna", "rakho", "reminder",
        "create", "save", "likho", "likh", "event", "schedule", "isme", "usme", "yaha", "yahan", "wahan", "iska", "uska", "naam", "name", "title", "text", "with", "at", "for", "the", "a", "my", "i", "have", "to", "go"
    )
    private val HINDI_NUM = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "chheh" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "barah" to 12, "baarah" to 12, "pandrah" to 15, "bees" to 20, "tees" to 30, "chalis" to 40, "paitalis" to 45, "pachas" to 50
    )

    private fun num(tok: String?): Int? = if (tok == null) null else tok.toIntOrNull() ?: HINDI_NUM[tok]

    /** hour/minute from tokens. Returns (hour, minute, firstIndex, lastIndex) or null. */
    private data class Clock(val hour: Int, val minute: Int, val from: Int, val to: Int, val explicit24: Boolean)

    private fun findClock(t: List<String>): Clock? {
        // "saade teen" = 3:30, "sawa teen" = 3:15, "paune teen" = 2:45
        for (i in 0 until t.size - 1) {
            val h = num(t[i + 1]) ?: continue
            if (h !in 1..12) continue
            when (t[i]) {
                "saade", "sade" -> return Clock(h, 30, i, i + 1, false)
                "sawa" -> return Clock(h, 15, i, i + 1, false)
                "paune" -> return Clock(if (h == 1) 12 else h - 1, 45, i, i + 1, false)
            }
        }
        // "3 30" / "15 30" / "3 30 baje" (norm() turns "3:30" into "3 30")
        for (i in 0 until t.size - 1) {
            val h = t[i].toIntOrNull() ?: continue
            val mTok = t[i + 1]
            val m = if (mTok.length == 2 && mTok.all { it.isDigit() }) mTok.toIntOrNull() else null
            if (m != null && h in 0..24 && m in 0..59 && !(i + 2 < t.size && t[i + 2] in setOf("minute", "minutes", "min", "log", "people", "din", "days", "ghante"))) {
                return Clock(if (h == 24) 0 else h, m, i, i + 1, h >= 13 || h == 0)
            }
        }
        // "3 baje" / "teen baje" / "3 pm" / "teen bje"
        for (i in 1 until t.size) {
            if (t[i] in setOf("baje", "bje", "pm", "am", "oclock")) {
                val h = num(t[i - 1]) ?: continue
                if (h !in 0..24) continue
                // "3 baje 30" / "teen baje tees"
                val extra = num(t.getOrNull(i + 1))
                return if (t[i] in setOf("baje", "bje") && extra != null && extra in 1..59)
                    Clock(if (h == 24) 0 else h, extra, i - 1, i + 1, h >= 13)
                else Clock(if (h == 24) 0 else h, 0, i - 1, i, h >= 13)
            }
        }
        // Hindi words for hour and minute: "teen tees"
        for (i in 0 until t.size - 1) {
            val h = HINDI_NUM[t[i]] ?: continue
            val m = HINDI_NUM[t[i + 1]] ?: continue
            if (h in 1..12 && m in setOf(10, 15, 20, 30, 40, 45, 50) && i + 2 < t.size && t[i + 2] in NOUNS + setOf("ka", "ki", "ke", "par", "pe", "baje", "wali", "wala")) return Clock(h, m, i, i + 1, false)
        }
        return null
    }

    fun parse(raw: String, now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Spec? {
        val n = Logic.norm(raw)
        val t = n.split(" ").filter { it.isNotBlank() }
        if (t.size < 3 || t.size > 40) return null
        if (t.any { it in BLOCK }) return null
        if (t.none { it in INTENT }) return null
        if (t.none { it in ACTION }) return null
        // "schedule" alone is not enough: a meeting-like noun, or the word schedule/calendar/event plus an explicit action word
        val clock = findClock(t) ?: return null
        var hour = clock.hour
        val minute = clock.minute
        if (hour !in 0..23 || minute !in 0..59) return null
        val words = t.toSet()
        val morning = words.any { it in setOf("subah", "savere", "morning", "am") }
        val evening = words.any { it in setOf("shaam", "sham", "raat", "evening", "night", "pm", "dopahar", "afternoon") }
        var offset = when {
            "parso" in words -> 2
            "kal" in words || "tomorrow" in words -> 1
            else -> 0
        }
        if (hour in 1..12 && !clock.explicit24) {
            hour = when {
                morning -> if (hour == 12) 0 else hour
                evening -> if (hour == 12) 12 else hour + 12
                else -> {
                    val a = hour % 12
                    val b = a + 12
                    if (offset > 0) (if (hour in 1..6) hour + 12 else hour)
                    else listOf(a, b).firstOrNull { it * 60 + minute > now.hour * 60 + now.minute } ?: a.also { offset = 1 }
                }
            }
        }
        if (hour !in 0..23) return null
        var date = now.toLocalDate().plusDays(offset.toLong())
        var start = date.atTime(hour, minute)
        if (offset == 0 && !start.isAfter(now)) { offset = 1; date = date.plusDays(1); start = date.atTime(hour, minute) }

        val content = t.filterIndexed { i, w -> i !in clock.from..clock.to && w !in STOP && w.toIntOrNull() == null }
        val nounAt = content.indexOfFirst { it in NOUNS }
        val rawTitle = when {
            nounAt >= 0 -> content.subList(maxOf(0, nounAt - 2), nounAt + 1)
            content.isNotEmpty() -> content.take(5)
            else -> emptyList()
        }
        val title = rawTitle.joinToString(" ") { w -> if (w.length <= 3 && w !in setOf("mtg")) w.uppercase() else w.replaceFirstChar { it.uppercase() } }
            .replace("|", " ").take(80).ifBlank { "Meeting" }
        return Spec(title, start.atZone(zone).toInstant().toEpochMilli(), offset, hour, minute)
    }

    fun encode(s: Spec): String = s.title.replace("|", " ") + "|" + s.startMillis + "|" + s.dayOffset + "|" + s.hour + "|" + s.minute

    fun decode(arg: String): Spec? {
        val p = arg.split("|")
        if (p.size != 5) return null
        return try { Spec(p[0], p[1].toLong(), p[2].toInt(), p[3].toInt(), p[4].toInt()) } catch (e: Exception) { null }
    }

    fun dayWord(offset: Int): String = when (offset) { 0 -> "aaj"; 1 -> "kal"; 2 -> "parso"; else -> "$offset din baad" }
}
