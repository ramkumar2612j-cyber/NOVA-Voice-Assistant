package com.nova.assistant

/**
 * v6.74 (Part 5): voice requests that Android does NOT let an ordinary app do. NOVA answers clearly and does nothing, instead of
 * sending the sentence to an AI that may try to drive the Settings app (which NOVA's own safety rules block anyway) or guess.
 *
 *  - power: switch off / restart the phone (no public API for third-party apps)
 *  - reset: factory reset / wipe (also destructive, so NOVA never starts it, even if some API existed)
 *  - radio toggles (mobile data, hotspot, airplane mode, location, do not disturb) are NOT here: they map to Cmd("settings", page, 2),
 *    i.e. NOVA opens the right Settings page and says it cannot flip the switch itself (same as Wi-Fi / Bluetooth since v6.x).
 *
 * Nothing here bypasses anything: no lock screen, no secure setting, no permission. Pure Kotlin, unit tested.
 */
object UnsupportedRules {
    const val KIND = "unsupported"
    const val POWER = "power"
    const val RESET = "reset"
    const val CANCEL_ALARM = "cancel_alarm"
    const val CANCEL_TIMER = "cancel_timer"

    private val POWER_PHRASES = setOf(
        "power off", "power off phone", "phone power off", "phone power off karo", "power off karo",
        "switch off phone", "phone switch off", "phone switch off karo", "switch off phone karo",
        "turn off phone", "phone turn off",
        "shutdown", "shutdown phone", "shut down phone", "phone shutdown", "phone shut down", "phone shutdown karo", "shutdown karo",
        "restart phone", "phone restart", "phone restart karo", "phone ko restart karo",
        "reboot", "reboot phone", "phone reboot", "phone reboot karo", "phone ko reboot karo"
    )

    private val RESET_PHRASES = setOf(
        "factory reset", "factory reset phone", "phone factory reset", "phone factory reset karo", "factory reset karo",
        "reset phone", "phone reset karo", "wipe phone", "phone wipe karo", "erase phone", "phone erase karo", "format phone", "phone format karo"
    )

    /**
     * v6.75: "cancel alarm" / "timer cancel karo". NOVA sets alarms and timers through the Clock app but keeps no handle to cancel
     * one, so it says so and points to the Clock app instead of sending the sentence to the cloud. NOT here on purpose:
     * "alarm band karo" / "alarm off" = the wake-up alarm switch (scheduled task rules), which keeps working.
     */
    private val CANCEL_ALARM_PHRASES = setOf(
        "cancel alarm", "alarm cancel", "alarm cancel karo", "cancel alarm karo", "alarm delete karo", "delete alarm", "alarm hatao", "alarm hata do", "alarm remove karo", "remove alarm"
    )

    private val CANCEL_TIMER_PHRASES = setOf(
        "cancel timer", "timer cancel", "timer cancel karo", "cancel timer karo", "timer delete karo", "delete timer", "timer hatao", "timer hata do", "timer remove karo", "remove timer"
    )

    /** [core] = the sentence without filler words (see Logic.classify). Returns a Cmd("unsupported", id) or null. */
    fun match(core: String): Logic.Cmd? = when (core) {
        in POWER_PHRASES -> Logic.Cmd(KIND, POWER)
        in RESET_PHRASES -> Logic.Cmd(KIND, RESET)
        in CANCEL_ALARM_PHRASES -> Logic.Cmd(KIND, CANCEL_ALARM)
        in CANCEL_TIMER_PHRASES -> Logic.Cmd(KIND, CANCEL_TIMER)
        else -> null
    }

    /**
     * "mobile data off", "hotspot on karo", "airplane mode on", "location band karo", "do not disturb on": returns the Settings page name
     * (as understood by NovaService.openSettings) when the sentence asks to SWITCH one of these, else null. [t] = normalised words.
     */
    fun toggledSettingsPage(t: List<String>): String? {
        if (t.size > 6) return null
        val s = t.toSet()
        val switchWord = s.any { it in setOf("on", "off", "chalu", "band", "bandh", "chalao", "start", "stop", "enable", "disable", "activate") }
        if (!switchWord) return null
        return when {
            "hotspot" in s || ("hot" in s && "spot" in s) || ("mobile" in s && "data" in s) || ("cellular" in s && "data" in s) -> "network"
            ("airplane" in s || "aeroplane" in s || "flight" in s) && "mode" in s -> "airplane"
            ("location" in s || "gps" in s) -> "location"
            ("dnd" in s) || ("disturb" in s && "not" in s && "do" in s) -> "dnd"
            else -> null
        }
    }

    fun reply(id: String, en: Boolean): String = when (id) {
        POWER ->
            if (en) "Android does not let apps switch off or restart the phone. Hold the power button and choose Power off or Restart."
            else "Android apps ko phone band ya restart karne nahi deta. Power button dabakar rakho aur Power off ya Restart chuno."
        RESET ->
            if (en) "I will not factory-reset or wipe the phone, and Android does not allow it for apps. If you really want it, do it yourself in Settings > System > Reset options."
            else "Main phone factory reset ya wipe nahi karunga, aur Android apps ko ye karne bhi nahi deta. Chahiye to khud Settings > System > Reset options me karo."
        CANCEL_ALARM ->
            if (en) "I cannot cancel an alarm yet. Open the Clock app and switch off or delete it there."
            else "Main alarm cancel nahi kar sakta. Clock app kholo aur wahan se alarm off ya delete karo."
        CANCEL_TIMER ->
            if (en) "I cannot cancel a timer yet. Open the Clock app and stop or delete it there."
            else "Main timer cancel nahi kar sakta. Clock app kholo aur wahan se timer stop ya delete karo."
        else ->
            if (en) "Android does not allow me to do that." else "Android mujhe ye karne nahi deta."
    }
}
