package com.nova.assistant

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

/**
 * v7.120: "Movie Ultron" style voice effect. Free, offline, pure Kotlin (no Android classes) so it is unit tested.
 * It works on the phone's own TTS audio (TextToSpeech.synthesizeToFile -> WavPcm) and does NOT clone any real person's voice.
 * Layers (v7.123): deeper pitch shift + octave-down dark layer + metal ring + bass + comb + hall echo + soft clip.
 * Not phone tested here: the owner must listen and tune the strength slider.
 */
object UltronFx {
    const val MIN_AMOUNT = 0.2f
    const val MAX_AMOUNT = 1.0f
    const val DEFAULT_AMOUNT = 0.75f

    /** TTS settings used only for the Ultron path (calm, a little lower and slower; the normal voice settings are untouched). */
    const val PITCH = 0.9f
    const val MAX_RATE = 0.92f

    fun clampAmount(v: Float): Float = if (v.isNaN()) DEFAULT_AMOUNT else v.coerceIn(MIN_AMOUNT, MAX_AMOUNT)

    /** Little-endian PCM16 bytes -> samples (a trailing odd byte is ignored). */
    fun toShorts(b: ByteArray): ShortArray {
        val n = b.size / 2
        val out = ShortArray(n)
        for (i in 0 until n) out[i] = ((b[2 * i + 1].toInt() shl 8) or (b[2 * i].toInt() and 0xff)).toShort()
        return out
    }

    /**
     * v7.123: classic two-tap "harmonizer" pitch shift (no FFT). Reads the input through two delay taps that drift at
     * (1 - ratio) per sample and cross-fade with sin^2 / cos^2 windows, so ratio < 1 = deeper voice, same length.
     */
    internal fun pitchShift(src: FloatArray, ratio: Float, rate: Int): FloatArray {
        val n = src.size
        val out = FloatArray(n)
        val w = (rate * 0.03f).coerceAtLeast(64f)            // ~30 ms grain
        val step = (1f - ratio) / w
        var p = 0f
        for (i in 0 until n) {
            val p2 = if (p >= 0.5f) p - 0.5f else p + 0.5f
            val sp = sin(PI * p).toFloat()
            val sq = sin(PI * p2).toFloat()
            out[i] = tap(src, i - p * w) * (sp * sp) + tap(src, i - p2 * w) * (sq * sq)
            p += step
            if (p >= 1f) p -= 1f
            if (p < 0f) p += 1f
        }
        return out
    }

    private fun tap(src: FloatArray, pos: Float): Float {
        if (pos < 0f) return 0f
        val i0 = pos.toInt()
        if (i0 + 1 >= src.size) return 0f
        val fr = pos - i0
        return src[i0] * (1f - fr) + src[i0 + 1] * fr
    }

    /**
     * Returns a NEW array (longer by the echo tail). Input that is empty or has a bad rate comes back unchanged.
     * v7.123 chain: deeper pitch (amount 0.2..1.0 = -5%..-28%) + an octave-down "demon" layer + 70 Hz metal ring +
     * bass body + a short metallic comb + hall echo + soft clip. Strength [amount] scales every layer.
     */
    fun process(pcm: ShortArray, rate: Int, amount: Float): ShortArray {
        if (pcm.isEmpty() || rate < 8000) return pcm
        val a = clampAmount(amount)
        val n = pcm.size
        val echoD = (rate * 0.075f).toInt().coerceAtLeast(1)       // ~75 ms hall echo
        val tail = echoD * 4
        val total = n + tail
        val dry = FloatArray(total)
        for (i in 0 until n) dry[i] = pcm[i] / 32768f

        val main = pitchShift(dry, 1f - 0.28f * a, rate)           // the voice itself, deeper
        val sub = pitchShift(dry, 0.62f, rate)                     // the dark layer underneath
        val twoPi = 2.0 * PI
        val lpK = (twoPi * 250.0 / rate).toFloat().coerceIn(0.01f, 0.5f)
        var lp = 0f
        val mix = FloatArray(total)
        for (i in 0 until total) {
            val d = main[i]
            lp += lpK * (d - lp)                                    // bass body below ~250 Hz
            val ring = d * sin(twoPi * 70.0 * i / rate).toFloat()   // 70 Hz metal ring (dhatu jhankar)
            mix[i] = d + a * 0.40f * sub[i] + a * 0.35f * ring + a * 0.50f * lp
        }

        val comb = (rate * 0.007f).toInt().coerceAtLeast(1)         // 7 ms = metallic resonance around 140 Hz
        val cg = 0.45f * a
        for (i in comb until total) mix[i] += cg * mix[i - comb]
        val g = 0.30f * a
        for (i in echoD until total) mix[i] += g * mix[i - echoD]    // feedback echo

        var peak = 0f
        for (i in 0 until total) {
            val v = tanh(1.8f * mix[i])                              // soft clip: edge, never harsh
            mix[i] = v
            if (abs(v) > peak) peak = abs(v)
        }
        val scale = if (peak > 0.0001f) 0.9f / peak else 1f
        val out = ShortArray(total)
        for (i in 0 until total) out[i] = (mix[i] * scale * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        return out
    }
}
