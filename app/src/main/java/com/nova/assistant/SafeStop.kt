package com.nova.assistant

/**
 * v7.39 PART 4: SAFE STOP rules, pure (no Android) so JUnit can run them.
 * STOP only stops: talking, the running job, a waiting confirmation and a half-heard command. It never switches NOVA (service, microphone, wake word) off.
 * Switching NOVA off stays a separate, deliberate act (the ACTIVATE button), unless the owner turns "STOP also powers off" ON in Settings (PART 17 adds that switch; default OFF).
 */
object SafeStop {
    enum class HudStop { SOFT, POWER_OFF }

    /** What the floating card's STOP button does. */
    fun hudStop(stopPowersOff: Boolean): HudStop = if (stopPowersOff) HudStop.POWER_OFF else HudStop.SOFT

    /** The microphone loop drops what it has captured so far when STOP arrives: a command that is being captured, or words already collected. */
    fun dropsHeard(capturing: Boolean, collectedText: Boolean): Boolean = capturing || collectedText

    /** After a drop the recognizers are reset like after our own voice (no leftover half sentence can complete a later command). */
    fun resetsRecognizers(dropped: Boolean): Boolean = dropped
}
