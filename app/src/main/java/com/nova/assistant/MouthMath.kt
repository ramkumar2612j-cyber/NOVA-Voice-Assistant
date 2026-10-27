package com.nova.assistant

import kotlin.math.abs
import kotlin.math.sin

/** v7.126: how wide Ultron's mouth is open (0 = closed, 1 = wide) at a given time, in a speech-like rhythm. Pure, unit tested. */
object MouthMath {
    fun open(ms: Long): Float {
        val t = ms / 1000.0
        val syllable = abs(sin(t * 9.0))                 // about 3 syllables per second
        val wobble = 0.5 + 0.5 * sin(t * 5.3 + 1.0)      // not every syllable is equally wide
        val breath = 0.5 + 0.5 * sin(t * 2.1)            // phrases get louder and softer
        val v = syllable * (0.45 + 0.55 * wobble) * (0.55 + 0.45 * breath)
        return v.toFloat().coerceIn(0f, 1f)
    }
}
