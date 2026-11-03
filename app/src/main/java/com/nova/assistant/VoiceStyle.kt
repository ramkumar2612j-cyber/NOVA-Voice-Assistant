package com.nova.assistant

/** v7.173: which robot voice NOVA speaks with: "ultron" (deep, heavy, default) or "jarvis" (calm, clean). Pure Kotlin, unit tested. */
object VoiceStyle {
    const val ULTRON = "ultron"
    const val JARVIS = "jarvis"

    fun normalize(s: String?): String = if (s == JARVIS) JARVIS else ULTRON
    fun isJarvis(s: String?): Boolean = normalize(s) == JARVIS
    fun next(s: String?): String = if (isJarvis(s)) ULTRON else JARVIS
    fun pitch(s: String?): Float = if (isJarvis(s)) JarvisFx.PITCH else UltronFx.PITCH
    fun maxRate(s: String?): Float = if (isJarvis(s)) JarvisFx.MAX_RATE else UltronFx.MAX_RATE
    fun process(s: String?, pcm: ShortArray, rate: Int, amount: Float): ShortArray =
        if (isJarvis(s)) JarvisFx.process(pcm, rate, amount) else UltronFx.process(pcm, rate, amount)
}
