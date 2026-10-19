package com.nova.assistant

/**
 * v7.73: VOICE WHILE BUSY. Pure Kotlin (no Android), JUnit-testable.
 * Owner decision (19 Oct 2026): YES, a spoken command said while NOVA is working goes into the command queue.
 * Owner decision (19 Oct 2026): NO, a locked phone still never listens (Lock Guard is NOT touched; [mayListen] is false when locked).
 * NOVA's own voice is still never listened to (echo): while it speaks, audio is dropped exactly as before.
 * Evidence level: written. Not compiled here (no kotlinc / internet), not phone tested.
 */
object BusyListenRules {
    enum class Action { NONE, STOP, QUEUE, EMPTY }

    /** May the mic loop keep listening for the wake word while a turn is busy? Never while speaking, locked, or waiting for a yes/no / dictation / follow-up. */
    fun mayListen(enabled: Boolean, busy: Boolean, ttsActive: Boolean, lockQuiet: Boolean, waitingForAnswer: Boolean): Boolean =
        enabled && busy && !ttsActive && !lockQuiet && !waitingForAnswer

    /** What to do with a command that finished recording while NOVA is busy. [text] = offline (Vosk) text, [isStop] = StopRules.isStop(text). */
    fun decide(busy: Boolean, isAnswerOrDictation: Boolean, text: String, isStop: Boolean): Action {
        if (!busy || isAnswerOrDictation) return Action.NONE
        val t = text.trim()
        if (t.isEmpty()) return Action.EMPTY
        if (isStop) return Action.STOP
        return Action.QUEUE
    }
}
