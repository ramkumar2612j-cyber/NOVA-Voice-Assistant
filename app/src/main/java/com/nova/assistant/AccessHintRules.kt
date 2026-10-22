package com.nova.assistant

/**
 * v7.111: one honest wording for "Accessibility is not usable". Pure Kotlin.
 * Three real states: OFF (switch not on), LISTED_NOT_CONNECTED (switch ON in Android but the service never connected, common after an APK
 * update / reinstall or battery restriction), CONNECTED. The old texts said "give permission" even when the owner had already given it.
 */
object AccessHintRules {
    enum class State { OFF, LISTED_NOT_CONNECTED, CONNECTED }

    fun state(listedOn: Boolean, connected: Boolean): State =
        if (connected) State.CONNECTED else if (listedOn) State.LISTED_NOT_CONNECTED else State.OFF

    fun usable(s: State): Boolean = s == State.CONNECTED

    /** `need` = what the feature needs, e.g. "Screen padhne ke liye". Returns "" when connected. */
    fun message(s: State, need: String, needEn: String, en: Boolean): String = when (s) {
        State.CONNECTED -> ""
        State.LISTED_NOT_CONNECTED ->
            if (en) "Accessibility shows ON but Android has not connected NOVA to it, so $needEn. Open Settings > Accessibility > NOVA Phone Control, switch it OFF and ON once."
            else "Accessibility ON dikh raha hai par Android ne NOVA ko judne nahi diya, isliye $need. Settings > Accessibility > NOVA Phone Control ko ek baar OFF karke phir ON karo."
        State.OFF ->
            if (en) "$needEn needs Accessibility. Turn on NOVA Phone Control in Settings > Accessibility."
            else "$need Accessibility chahiye. Settings > Accessibility > NOVA Phone Control ON karo."
    }
}
