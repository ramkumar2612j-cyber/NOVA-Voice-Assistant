package com.nova.assistant

/**
 * v6.40 morning rules. Pure Kotlin, unit tested.
 *  - Greeting: the first time the phone is opened (unlocked) between 06:00 and 11:59 each day.
 *  - Wake alarm (keeps ringing from 25 minutes before the job): ONLY for a job set between 06:00 and 11:59, ONLY while the clock is
 *    between 06:00 and 11:59. Once started it rings again and again until OFF is pressed (or 12:00 noon); DISMISS only silences it for 1 minute.
 *    After noon / at night it is never used. No time-based give-up.
 *  - DISMISS = ring again after 1 minute. OFF = no more wake alarm for the rest of that day.
 */
object WakeRules {
    const val ACTION_OFF = "com.nova.assistant.WAKE_OFF"
    const val ACTION_SNOOZE = "com.nova.assistant.WAKE_SNOOZE"
    const val GREET_FROM = 6 * 60
    const val NOON = 12 * 60
    const val RING_FROM = 6 * 60
    const val SNOOZE_MS = 60_000L

    fun inGreetingWindow(nowMin: Int) = nowMin in GREET_FROM until NOON

    /** nowMin / taskMin = minute of the day. */
    fun alarmAllowed(nowMin: Int, taskMin: Int, awakeToday: Boolean): Boolean =
        !awakeToday && taskMin in GREET_FROM until NOON && nowMin in RING_FROM until NOON

    private val GREETINGS = listOf(
        "Good morning! Aaj aapka din shubh ho.",
        "Suprabhat! Naya din, nayi shuruaat. Aaj ka din aapke liye bahut achha ho.",
        "Good morning! Aaj ka din khushiyon aur kamyabi se bhara ho.",
        "Namaste, good morning! Aaj aapka din mangalmay ho.",
        "Good morning! Aaj jo bhi karo, dil se karo, din shubh ho.",
        "Suprabhat! Bhagwan kare aaj aapka har kaam aasaani se ho jaye.",
        "Good morning! Ek aur naya mauka mila hai, aaj ka din shandar banao.",
        "Good morning! Aaj aapka din sehatmand aur safal ho.",
        "Namaste! Subah mubarak, aaj ka din aapke naam.",
        "Good morning! Muskurao, aaj ka din shubh aur khushnuma ho.",
        "Suprabhat! Aaj ka din naye vichaaron aur nayi ummeedon ka ho.",
        "Good morning! Aaj ke saare kaam waqt par aur achhe se pure hon.",
        "Good morning! Aapka din shubh ho, main saath hoon.",
        "Subah mubarak! Aaj ka din aapke liye khaas ho."
    )

    /** A different line every day (by day number), same line if asked twice on the same day. */
    fun greeting(epochDay: Long): String = GREETINGS[Math.floorMod(epochDay, GREETINGS.size.toLong()).toInt()]

    data class Item(val minute: Int, val label: String, val prio: Int, val note: String)

    /** Spoken list of today's jobs in time order, e.g. "Aaj ke kaam: 7:00 AM gym, bahut zaruri (dawai ke saath); ...". */
    fun todayText(items: List<Item>): String {
        if (items.isEmpty()) return "Aaj ka koi schedule save nahi hai."
        val sorted = items.sortedBy { it.minute }.take(8)
        return "Aaj ke kaam: " + sorted.joinToString("; ") { i ->
            Timers.clock(i.minute) + " " + i.label +
                (if (i.prio > 0) ", " + ScheduledTaskRules.prioText(i.prio) else "") +
                (if (i.note.isNotBlank()) " (" + i.note + ")" else "")
        } + "."
    }
}
