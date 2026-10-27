package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class UltronFxTest {
    private fun tone(rate: Int, ms: Int, hz: Double): ShortArray =
        ShortArray(rate * ms / 1000) { (sin(2.0 * PI * hz * it / rate) * 12000).toInt().toShort() }

    @Test fun amountIsClamped() {
        assertEquals(UltronFx.MIN_AMOUNT, UltronFx.clampAmount(-3f), 0f)
        assertEquals(UltronFx.MAX_AMOUNT, UltronFx.clampAmount(9f), 0f)
        assertEquals(UltronFx.DEFAULT_AMOUNT, UltronFx.clampAmount(Float.NaN), 0f)
        assertEquals(0.5f, UltronFx.clampAmount(0.5f), 0f)
    }

    @Test fun emptyOrBadRateComesBackUnchanged() {
        val e = ShortArray(0)
        assertSame(e, UltronFx.process(e, 22050, 0.7f))
        val x = tone(22050, 100, 200.0)
        assertSame(x, UltronFx.process(x, 4000, 0.7f))
    }

    @Test fun outputIsLongerByTheEchoTailAndStaysInRange() {
        val x = tone(22050, 500, 180.0)
        val y = UltronFx.process(x, 22050, 1.0f)
        assertTrue(y.size > x.size)
        assertTrue(y.size - x.size < 22050)          // tail under one second
        assertTrue(y.all { abs(it.toInt()) <= 32767 })
    }

    @Test fun peakIsNormalisedBelowClipping() {
        val y = UltronFx.process(tone(16000, 400, 150.0), 16000, 0.75f)
        val peak = y.maxOf { abs(it.toInt()) }
        assertTrue(peak in 28000..32500)
    }

    @Test fun effectReallyChangesTheVoiceAndStrengthMatters() {
        val x = tone(22050, 400, 200.0)
        val soft = UltronFx.process(x, 22050, 0.2f)
        val hard = UltronFx.process(x, 22050, 1.0f)
        var diff = 0L
        for (i in soft.indices) diff += abs(soft[i] - hard[i])
        assertTrue(diff > 0)
    }

    @Test fun silenceStaysSilent() {
        val y = UltronFx.process(ShortArray(8000), 16000, 1.0f)
        assertTrue(y.all { it.toInt() == 0 })
    }

    @Test fun toShortsReadsLittleEndianAndIgnoresOddByte() {
        val s = UltronFx.toShorts(byteArrayOf(0x34, 0x12, 0xFF.toByte(), 0xFF.toByte(), 0x7))
        assertEquals(2, s.size)
        assertEquals(0x1234, s[0].toInt())
        assertEquals(-1, s[1].toInt())
    }

    @Test fun pitchShiftMakesTheVoiceDeeper() {
        val rate = 16000
        val x = FloatArray(rate / 2) { sin(2.0 * PI * 200.0 * it / rate).toFloat() * 0.5f }
        val y = UltronFx.pitchShift(x, 0.7f, rate)
        fun crossings(a: FloatArray): Int { var c = 0; for (i in 3000 until a.size) if (a[i - 1] <= 0f && a[i] > 0f) c++; return c }
        assertEquals(x.size, y.size)
        assertTrue(crossings(y) < crossings(x))
    }

    @Test fun strongerAmountGoesDeeper() {
        val rate = 16000
        val x = FloatArray(rate / 2) { sin(2.0 * PI * 200.0 * it / rate).toFloat() * 0.5f }
        fun crossings(a: FloatArray): Int { var c = 0; for (i in 3000 until a.size) if (a[i - 1] <= 0f && a[i] > 0f) c++; return c }
        assertTrue(crossings(UltronFx.pitchShift(x, 0.72f, rate)) < crossings(UltronFx.pitchShift(x, 0.94f, rate)))
    }

    @Test fun voiceStaysLoudAndNotHarshOnSpeechLikeSound() {
        // v7.124: harmonic "vowel" at 120 Hz with a 2.5 kHz consonant burst, quiet and loud input must come out at the same level
        val rate = 22050
        fun speech(gain: Double) = ShortArray(rate) { i ->
            val t = i.toDouble() / rate
            val vowel = (1..12).sumOf { h -> sin(2.0 * PI * 120.0 * h * t) / h }
            val burst = if (i % 5000 < 400) 0.6 * sin(2.0 * PI * 2500.0 * t) else 0.0
            ((vowel * 0.35 + burst) * gain * 9000).toInt().coerceIn(-32768, 32767).toShort()
        }
        fun rms(a: ShortArray): Double = kotlin.math.sqrt(a.sumOf { it.toDouble() * it } / a.size)
        val quiet = UltronFx.process(speech(0.25), rate, 0.75f)
        val loud = UltronFx.process(speech(1.0), rate, 0.75f)
        assertTrue(rms(quiet) > 9000)
        assertTrue(rms(loud) > 9000)
        assertTrue(Math.abs(rms(quiet) / rms(loud) - 1.0) < 0.25)
        assertTrue(quiet.maxOf { abs(it.toInt()) } <= 32500)
    }
}
