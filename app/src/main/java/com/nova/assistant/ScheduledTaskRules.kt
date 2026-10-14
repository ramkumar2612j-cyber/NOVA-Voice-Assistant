package com.nova.assistant

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64

/** Pure Hinglish schedule parser. A model is never allowed to decide the scheduled action or approve sends. */
object ScheduledTaskRules {
    enum class Kind { WEATHER, REMINDER, SEND_MESSAGE }
    data class Spec(
        val hour: Int,
        val minute: Int,
        val repeatDaily: Boolean,
        val kind: Kind,
        val label: String,
        val recipient: String = "",
        val channel: String = "whatsapp",
        val message: String = "",
        /** v6.40: weekdays bitmask, Monday = 1 ... Sunday = 64. 0 = no day filter (every day / the next time it is that clock time). */
        val days: Int = 0,
        /** v6.40: how important this job is: 0 normal, 1 zaruri, 2 bahut zaruri. */
        val prio: Int = 0,
        /** v6.40: the owner's own words about how important it is / why (free text, max 120 chars). */
        val note: String = ""
    )

    private val HINDI_NUM = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4, "paanch" to 5, "panch" to 5,
        "chhe" to 6, "che" to 6, "saat" to 7, "aath" to 8, "nau" to 9, "das" to 10,
        "gyarah" to 11, "barah" to 12, "tera" to 13, "terah" to 13, "chaudah" to 14,
        "pandrah" to 15, "solah" to 16, "satrah" to 17, "atharah" to 18, "unnis" to 19,
        "bees" to 20, "ikkees" to 21, "baees" to 22, "teis" to 23, "chaubees" to 24,
        "tees" to 30, "paintis" to 35, "chalis" to 40, "paitalis" to 45, "pachas" to 50
    )
    private val MORNING = setOf("subah", "savere", "morning", "am")
    private val EVENING = setOf("shaam", "sham", "raat", "evening", "night", "pm", "dopahar", "afternoon")
    private val DAY_BITS = mapOf(
        "somvar" to 1, "somvaar" to 1, "sombar" to 1, "monday" to 1,
        "mangalvar" to 2, "mangalvaar" to 2, "mangal" to 2, "tuesday" to 2,
        "budhvar" to 4, "budhvaar" to 4, "budh" to 4, "wednesday" to 4,
        "guruvar" to 8, "guruvaar" to 8, "brihaspativar" to 8, "veervar" to 8, "thursday" to 8,
        "shukravar" to 16, "shukrawar" to 16, "shukravaar" to 16, "friday" to 16,
        "shanivar" to 32, "shanivaar" to 32, "shaniwar" to 32, "saturday" to 32,
        "ravivar" to 64, "ravivaar" to 64, "itwar" to 64, "itvaar" to 64, "sunday" to 64
    )
    private val DAY_NAMES = listOf("Somvar", "Mangalvar", "Budhvar", "Guruvar", "Shukravar", "Shanivar", "Ravivar")
    private val NOTE_WORDS = setOf("note", "likh", "likho", "likhna", "remind", "yaad", "dilao", "dilana", "reminder", "schedule", "scheduled", "plan")

    /** Bitmask of the weekdays named in [t] ("somvar", "friday", "weekdays", "weekend"). */
    fun daysOf(t: Collection<String>): Int {
        var m = 0
        for (w in t) m = m or (DAY_BITS[w] ?: 0)
        if ("weekdays" in t) m = m or 31
        if ("weekend" in t || "weekends" in t) m = m or 96
        return m
    }

    fun dayText(mask: Int): String =
        if (mask == 0) "" else DAY_NAMES.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.joinToString(", ")

    private val IMPORTANT = setOf("zaruri", "jaruri", "zaroori", "jaroori", "important")
    private val VERY = setOf("bahut", "bohot", "bahot", "very", "urgent", "bahutzaruri")
    private val NOTE_MARK = setOf("kyunki", "kyuki", "kyonki", "isliye", "because")
    private val WAKE_OFF = setOf("alarm band", "alarm band karo", "alarm bandh karo", "alarm off", "alarm stop", "alarm stop karo", "subah ka alarm band", "main uth gaya", "main uth gayi", "uth gaya", "uth gayi", "i am awake", "im awake")
    private val WAKE_SNOOZE = setOf("dismiss", "alarm dismiss", "snooze", "alarm snooze", "ek minute baad", "ek minute baad yaad dilao", "thodi der baad", "baad me yaad dilana", "baad mein yaad dilana")

    fun prioText(p: Int): String = when (p) { 2 -> "bahut zaruri"; 1 -> "zaruri"; else -> "" }

    private val SEND_WORDS = setOf("bhej", "bhejna", "bhejo", "send", "sending")

    /** Returns null for ordinary timers/alarms; only explicit task + clock phrases become schedules. */
    fun parse(raw: String, now: LocalTime = LocalTime.now()): Spec? {
        val n = Logic.norm(raw)
        val t0 = n.split(" ").filter { it.isNotBlank() }
        val ni = t0.indexOfFirst { it in NOTE_MARK }
        val note = if (ni >= 0) t0.drop(ni + 1).joinToString(" ").take(120) else ""
        val t = if (ni >= 0) t0.take(ni) else t0
        val prio = when {
            t.any { it in IMPORTANT } && t.any { it in VERY } -> 2
            "urgent" in t -> 2
            t.any { it in IMPORTANT } && "kam" !in t -> 1
            else -> 0
        }
        if (t.size < 3 || t.size > 40 || "baje" !in t) return null
        val timeIndex = t.indexOf("baje")
        if (timeIndex == 0) return null
        val hTok = t[timeIndex - 1]
        val parsedH = hTok.toIntOrNull() ?: HINDI_NUM[hTok] ?: return null
        var minute = 0
        var usedBefore = 2
        val minTok = t.getOrNull(timeIndex + 1)
        if (minTok != null && minTok !in setOf("weather", "mausam", "bolo", "bhejna", "bhejo", "message", "yaad", "dilana", "dilao")) {
            val m = minTok.toIntOrNull() ?: HINDI_NUM[minTok]
            if (m != null && m in 0..59) { minute = m; usedBefore = 3 }
        }
        if (parsedH !in 0..24 || minute !in 0..59) return null

        val days = daysOf(t)
        val dayRepeat = days != 0 && (("har" in t || "every" in t || "roz" in t || "weekdays" in t || "weekend" in t || "weekends" in t) || Integer.bitCount(days) > 1)
        val isRepeat = "roz" in t || "daily" in t || ("every" in t && "day" in t) || ("har" in t && "din" in t) || dayRepeat
        val hasScheduleIntent = isRepeat || "schedule" in t || "scheduled" in t || (days != 0 && t.any { it in NOTE_WORDS }) ||
            (t.any { it in setOf("weather", "mausam", "message", "messages", "remind", "yaad", "bhej", "bhejna", "send") } &&
                t.any { it in setOf("bolo", "batao", "remind", "yaad", "dilao", "dilana", "bhej", "bhejna", "send", "schedule", "scheduled") })
        if (!hasScheduleIntent) return null

        var hour = parsedH
        val words = t.toSet()
        if (hour == 24) hour = 0
        if (hour in 1..12) {
            if (words.any { it in MORNING }) hour = if (hour == 12) 0 else hour
            else if (words.any { it in EVENING }) hour = if (hour == 12) 12 else hour + 12
            else if (isRepeat) hour = if (hour == 12) 0 else hour // recurring ambiguous clock defaults to morning and says so in reply
            else {
                val a = hour % 12
                val b = a + 12
                hour = listOf(a, b).firstOrNull { candidate -> candidate * 60 + minute > now.hour * 60 + now.minute } ?: a
            }
        }
        if (hour !in 0..23) return null

        val kind = when {
            "weather" in words || "mausam" in words || "forecast" in words -> Kind.WEATHER
            (words.any { it in SEND_WORDS } && words.any { it in setOf("message", "messages", "whatsapp", "sms", "telegram", "text") }) -> Kind.SEND_MESSAGE
            else -> Kind.REMINDER
        }
        val afterTime = (timeIndex + 1 + if (minute > 0) 1 else 0).coerceAtMost(t.size)
        val clean = t.filterIndexed { idx, w ->
            idx != timeIndex && idx != timeIndex - 1 && !(usedBefore == 3 && idx == timeIndex - 2) &&
                w !in MORNING && w !in EVENING && w !in DAY_BITS && w !in IMPORTANT && w !in VERY && w != "kam" && w !in setOf("weekdays", "weekend", "weekends", "ko", "roz", "daily", "every", "day", "har", "din", "baje", "schedule", "scheduled", "note", "likho", "likh", "karo")
        }
        val label = when (kind) {
            Kind.WEATHER -> "weather"
            Kind.REMINDER -> clean.filterNot { it in setOf("mujhe", "yaad", "dilao", "dilana", "remind", "reminder", "bolo", "batao", "karo", "karna") }.joinToString(" ").take(100).ifBlank { "reminder" }
            Kind.SEND_MESSAGE -> "scheduled message"
        }
        var recipient = ""
        var channel = when {
            "sms" in words -> "sms"
            "telegram" in words -> "telegram"
            else -> "whatsapp"
        }
        var message = ""
        if (kind == Kind.SEND_MESSAGE) {
            val ko = t.indexOf("ko")
            if (ko > 0) recipient = t[ko - 1].take(60)
            val sendAt = t.indexOfLast { it in SEND_WORDS }.let { if (it < 0) t.size else it }
            val par = t.indexOf("par").let { if (it >= 0) it else -1 }
            if (par >= 0 && par + 1 < sendAt) {
                message = t.subList(par + 1, sendAt).filterNot { it in setOf("message", "bhejo", "bhejna", "send", "whatsapp", "sms", "telegram") }.joinToString(" ").take(300)
            }
            if (message.isBlank()) message = t.drop(afterTime).filterNot { it in setOf("ye", "message", "bhejo", "bhejna", "send", "whatsapp", "sms", "telegram", "par", "roz", "daily") }.take(40).joinToString(" ").take(300)
        }
        return Spec(hour, minute, isRepeat, kind, label, recipient, channel, message, days, prio, note)
    }

    fun nextAtMillis(spec: Spec, nowMillis: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
        var target = now.toLocalDate().atTime(spec.hour, spec.minute)
        if (!target.isAfter(now)) target = target.plusDays(1)
        if (spec.days != 0) {
            var guard = 0
            while (spec.days and (1 shl (target.dayOfWeek.value - 1)) == 0 && guard++ < 8) target = target.plusDays(1)
        }
        return target.atZone(zone).toInstant().toEpochMilli()
    }

    fun encode(s: Spec): String {
        val fields = listOf(s.hour.toString(), s.minute.toString(), if (s.repeatDaily) "1" else "0", s.kind.name,
            s.label, s.recipient, s.channel, s.message, s.days.toString(), s.prio.toString(), s.note)
        val enc = Base64.getUrlEncoder().withoutPadding()
        return fields.joinToString(".") { enc.encodeToString(it.toByteArray(Charsets.UTF_8)) }
    }

    fun decode(raw: String): Spec? = try {
        val dec = Base64.getUrlDecoder()
        val f = raw.split(".").map { String(dec.decode(it), Charsets.UTF_8) }
        if (f.size != 8 && f.size != 9 && f.size != 11) null else Spec(
            f[0].toInt(), f[1].toInt(), f[2] == "1", Kind.valueOf(f[3]), f[4].take(100),
            f[5].take(60), f[6].take(16), f[7].take(300), if (f.size >= 9) (f[8].toIntOrNull() ?: 0) and 127 else 0,
            if (f.size == 11) (f[9].toIntOrNull() ?: 0).coerceIn(0, 2) else 0,
            if (f.size == 11) f[10].take(120) else ""
        ).takeIf { it.hour in 0..23 && it.minute in 0..59 }
    } catch (ignored: Exception) { null }

    fun command(raw: String): Logic.Cmd? {
        val n = Logic.norm(raw)
        if (n in setOf("meri schedule list", "schedule list", "schedule dikhao", "meri schedule dikhao", "my schedule", "list schedule")) return Logic.Cmd("schedule_list")
        if (n in setOf("schedule hatao", "schedule cancel", "schedule saaf karo", "cancel schedule", "remove schedule", "schedules hatao")) return Logic.Cmd("schedule_cancel_all")
        if (n.startsWith("schedule hatao ") || n.startsWith("cancel schedule ")) return Logic.Cmd("schedule_cancel", n.substringAfterLast(" ").take(80))
        if (n in WAKE_OFF) return Logic.Cmd("wake_off")
        if (n in WAKE_SNOOZE) return Logic.Cmd("wake_snooze")
        val tk = n.split(" ").filter { it.isNotBlank() }
        if (tk.size >= 3 && tk[0] == "schedule") {
            val rest = tk.drop(1)
            if (rest[0] == "note" && rest.size >= 3) return Logic.Cmd("schedule_set", rest[1] + "|-1|" + rest.drop(2).joinToString(" ").take(120))
            val (p, lab) = when {
                rest.size >= 3 && rest[0] in VERY && rest[1] in IMPORTANT -> 2 to rest.drop(2)
                rest[0] in IMPORTANT -> 1 to rest.drop(1)
                rest[0] == "normal" -> 0 to rest.drop(1)
                else -> -1 to emptyList()
            }
            if (p >= 0 && lab.isNotEmpty()) return Logic.Cmd("schedule_set", lab.joinToString(" ") + "|" + p + "|")
        }
        val spec = parse(n) ?: return null
        return Logic.Cmd("schedule_create", encode(spec))
    }

    fun safeSummary(s: String): String = s.replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ").replace(Regex("\\s+"), " ").trim().take(120)
}
