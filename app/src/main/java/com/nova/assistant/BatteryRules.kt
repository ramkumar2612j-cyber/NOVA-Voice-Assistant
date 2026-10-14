package com.nova.assistant

/**
 * v6.40 battery watch. Pure Kotlin, unit tested.
 *  - an alert is spoken once each time the level drops below 50, 40, 30, 20, 10 percent (not while charging)
 *  - at 5 percent or less (not charging) the owner's family gets ONE WhatsApp: "phone is about to switch off"
 *  - plugging in resets everything; rising back above 55 % re-arms the alerts, above 15 % re-arms the family message
 */
object BatteryRules {
    const val SOS_AT = 5
    const val SOS_REARM = 15
    const val ALERT_REARM = 55
    val STEPS = intArrayOf(50, 40, 30, 20, 10)

    data class State(val lowestAnnounced: Int = 101, val sosSent: Boolean = false)
    data class Decision(val state: State, val alertStep: Int?, val sos: Boolean)

    fun decide(s: State, pct: Int, plugged: Boolean): Decision {
        if (pct !in 0..100) return Decision(s, null, false)
        if (plugged) return Decision(State(), null, false)
        var lowest = s.lowestAnnounced
        var sos = s.sosSent
        if (pct >= ALERT_REARM) lowest = 101
        if (pct >= SOS_REARM) sos = false
        var alert: Int? = null
        val crossed = STEPS.filter { pct < it && it < lowest }
        val low = crossed.minOrNull()
        if (low != null) { alert = low; lowest = low }
        val send = pct <= SOS_AT && !sos
        if (send) sos = true
        return Decision(State(lowest, sos), alert, send)
    }

    val PAPA = listOf("papa", "pappa", "papaji", "pitaji", "dad", "daddy", "father", "pita")
    val MUMMY = listOf("mummy", "mumma", "mummyji", "mom", "mommy", "mother", "maa", "mama", "mata")

    fun sosText(pct: Int): String = "Mobile ki battery sirf $pct% bachi hai, mobile off hone wala hai. (NOVA se auto message)"
}
