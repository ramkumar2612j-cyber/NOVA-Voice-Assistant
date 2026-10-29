package com.nova.assistant

/**
 * v7.133 FINAL: reading the calendar by voice: "meeting kab hai", "aaj kya schedule hai", "my next meeting", "kal ki meeting dikhao".
 * Pure Kotlin (JUnit-able). Making an event ("kal 5 baje meeting schedule karo") and cancelling are NOT here: they have no question / show word and stay with their own routes.
 */
object CalendarQueryRules {
    enum class Day { TODAY, TOMORROW, NEXT }

    private val SUBJECT = setOf("meeting", "meetings", "schedule", "calendar", "event", "events", "appointment", "appointments")
    private val CUE = setOf("kab", "kya", "kaun", "kaunsi", "kitni", "kitne", "dikhao", "dikha", "batao", "bata", "bolo", "dekho", "show", "tell", "what", "when", "next", "agli", "agla", "agle",
        "upcoming", "hai", "hain", "list", "padho", "sunao", "whats", "my", "mera", "meri", "mere")
    private val BLOCK = setOf("banao", "bana", "add", "create", "set", "lagao", "laga", "rakho", "rakh", "cancel", "hatao", "delete", "remind", "reminder", "yaad", "alarm", "timer", "bhejo",
        "whatsapp", "call", "message", "sms", "mail", "email", "join", "start", "shuru", "kholo", "khol", "open", "record", "move", "shift", "badlo", "reschedule", "postpone",
        "karna", "karni", "karun", "karu", "karein", "karte", "karo", "kar", "do", "de")
    private val TODAY = setOf("aaj", "today", "abhi")
    private val TOMORROW = setOf("kal", "tomorrow")
    private val NEXTW = setOf("next", "agli", "agla", "agle", "upcoming")

    fun parse(raw: String?): Day? {
        if (raw.isNullOrBlank() || raw.length > 120) return null
        val w = Logic.stripWake(Logic.norm(raw)).split(" ").filter { it.isNotBlank() }
        if (w.isEmpty() || w.size > 10) return null
        if (w.none { it in SUBJECT }) return null
        if (w.any { it in BLOCK }) return null
        if (w.none { it in CUE }) return null
        if (w.any { it in NEXTW }) return Day.NEXT
        if (w.any { it in TODAY }) return Day.TODAY
        if (w.any { it in TOMORROW }) return Day.TOMORROW
        return Day.NEXT   // "meeting kab hai" = the next one
    }
}
