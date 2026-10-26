package com.nova.assistant

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

/**
 * v7.120: "Movie Ultron" style voice effect. Free, offline, pure Kotlin (no Android classes) so it is unit tested.
 * It works on the phone's own TTS audio (TextToSpeech.synthesizeToFile -> WavPcm) and does NOT clone any real person's voice.
 * Layers: dry voice + slow-wobble doubled copy (metallic chorus) + light ring modulation (dhatu jhankar) + short hall echo + soft clip.
 * Not phone tested here: the owner must listen and tune the strength slider.
 */
object UltronFx {
    const val MIN_AMOUNT = 0.2f
    const val MAX_AMOUNT = 1.0f
    const val DEFAULT_AMOUNT = 0.75f

    /** TTS settings used only for the Ultron path (calm, a little lower and slower; the normal voice settings are untouched). */
    const val PITCH = 0.82f
    const val MAX_RATE = 0.92f

    fun clampAmount(v: Float): Float = if (v.isNaN()) DEFAULT_AMOUNT else v.coerceIn(MIN_AMOUNT, MAX_AMOUNT)

    /** Little-endian PCM16 bytes -> samples (a trailing odd byte is ignored). */
    fun toShorts(b: ByteArray): ShortArray {
        val n = b.size / 2
        val out = ShortArray(n)
        for (i in 0 until n) out[i] = ((b[2 * i + 1].toInt() shl 8) or (b[2 * i].toInt() and 0xff)).toShort()
        return out
    }

    /** Returns a NEW array (longer by the echo tail). Input that is empty or has a bad rate comes back unchanged. */
    fun process(pcm: ShortArray, rate: Int, amount: Float): ShortArray {
        if (pcm.isEmpty() || rate < 8000) return pcm
        val a = clampAmount(amount)
        val n = pcm.size
        val echoD = (rate * 0.075f).toInt().coerceAtLeast(1)       // ~75 ms hall echo
        val tail = echoD * 4
        val total = n + tail
        val dry = FloatArray(total)
        for (i in 0 until n) dry[i] = pcm[i] / 32768f

        val baseDelay = rate * 0.014f                                // ~14 ms doubled layer
        val wobble = rate * 0.003f                                   // +-3 ms slow wobble = metallic shimmer
        val twoPi = 2.0 * PI
        val mix = FloatArray(total)
        for (i in 0 until total) {
            val d = dry[i]
            val di = i - (baseDelay + wobble * sin(twoPi * 5.0 * i / rate)).toFloat()
            val i0 = di.toInt()
            val doubled = if (di >= 1f && i0 + 1 < total) {
                val fr = di - i0
                dry[i0] * (1f - fr) + dry[i0 + 1] * fr
            } else 0f
            val ring = d * sin(twoPi * 55.0 * i / rate).toFloat()    // 55 Hz carrier
            mix[i] = d + a * 0.45f * doubled + a * 0.30f * ring
        }

        val g = 0.30f * a
        for (i in echoD until total) mix[i] += g * mix[i - echoD]    // feedback echo

        var peak = 0f
        for (i in 0 until total) {
            val v = tanh(1.4f * mix[i])                              // soft clip: a little "edge", never harsh
            mix[i] = v
            if (abs(v) > peak) peak = abs(v)
        }
        val scale = if (peak > 0.0001f) 0.9f / peak else 1f
        val out = ShortArray(total)
        for (i in 0 until total) out[i] = (mix[i] * scale * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        return out
    }
}
