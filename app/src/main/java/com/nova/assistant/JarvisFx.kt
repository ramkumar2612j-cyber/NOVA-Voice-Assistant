package com.nova.assistant

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * v7.173: "Jarvis" style voice effect: calm, clean, a little synthetic. Free, offline, pure Kotlin (no Android classes) so it is unit tested.
 * It works on the phone's own TTS audio and does NOT clone any real person's voice or use any film audio.
 * Layers: extra clear high band (presence) + a light 220 Hz ring (synthetic edge) + a very short comb (digital shimmer) + a soft room echo + level-then-gentle-clip.
 * No pitch drop (that is the Ultron voice). Not phone tested: the owner must listen.
 */
object JarvisFx {
    /** TTS settings used only for the Jarvis path (normal pitch, calm pace). */
    const val PITCH = 1.0f
    const val MAX_RATE = 0.95f

    /** Returns a NEW array (longer by the echo tail). Input that is empty or has a bad rate comes back unchanged. */
    fun process(pcm: ShortArray, rate: Int, amount: Float): ShortArray {
        if (pcm.isEmpty() || rate < 8000) return pcm
        val a = UltronFx.clampAmount(amount)
        val n = pcm.size
        val echoD = (rate * 0.11f).toInt().coerceAtLeast(1)          // ~110 ms soft room echo
        val total = n + echoD * 3
        val mix = FloatArray(total)
        val twoPi = 2.0 * PI
        val lpK = (twoPi * 1800.0 / rate).toFloat().coerceIn(0.01f, 0.9f)
        var lp = 0f
        for (i in 0 until total) {
            val d = if (i < n) pcm[i] / 32768f else 0f
            lp += lpK * (d - lp)
            val air = d - lp                                          // everything above ~1.8 kHz = clear, crisp words
            val ring = d * sin(twoPi * 220.0 * i / rate).toFloat()    // light ring = synthetic edge
            mix[i] = d + a * 0.30f * air + a * 0.12f * ring
        }
        val comb = (rate * 0.004f).toInt().coerceAtLeast(1)           // 4 ms = digital shimmer
        for (i in comb until total) mix[i] += 0.22f * a * mix[i - comb]
        for (i in echoD until total) mix[i] += 0.16f * a * mix[i - echoD]
        var sq = 0.0
        for (i in 0 until total) sq += (mix[i] * mix[i]).toDouble()
        val rms = sqrt(sq / total).toFloat()
        val pre = if (rms > 0.00001f) 0.30f / rms else 1f
        var peak = 0f
        for (i in 0 until total) {
            val v = tanh(1.6f * mix[i] * pre)
            mix[i] = v
            if (abs(v) > peak) peak = abs(v)
        }
        val scale = if (peak > 0.0001f) 0.95f / peak else 1f
        val out = ShortArray(total)
        for (i in 0 until total) out[i] = (mix[i] * scale * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        return out
    }
}
