package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WavPcmTest {
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    private fun wav(rate: Int, ch: Int, samples: ShortArray): ByteArray {
        val data = ByteArray(samples.size * 2)
        for (i in samples.indices) { data[2 * i] = samples[i].toInt().toByte(); data[2 * i + 1] = (samples[i].toInt() shr 8).toByte() }
        return "RIFF".toByteArray() + le32(36 + data.size) + "WAVE".toByteArray() + "fmt ".toByteArray() + le32(16) +
            le16(1) + le16(ch) + le32(rate) + le32(rate * ch * 2) + le16(ch * 2) + le16(16) + "data".toByteArray() + le32(data.size) + data
    }

    @Test fun parsesMonoWav() {
        val w = WavPcm.parse(wav(16000, 1, shortArrayOf(1, -2, 300, -400)))!!
        assertEquals(16000, w.rate)
        assertEquals(8, w.pcm.size)
        assertEquals(8, WavPcm.to16k(w).size)
    }

    @Test fun stereoKeepsFirstChannel() {
        val w = WavPcm.parse(wav(16000, 2, shortArrayOf(5, 99, 6, 98)))!!
        assertEquals(4, w.pcm.size)
        assertEquals(5, w.pcm[0].toInt())
        assertEquals(6, w.pcm[2].toInt())
    }

    @Test fun resamplesDownTo16k() {
        val s = ShortArray(2205) { (it % 100).toShort() }
        val out = WavPcm.to16k(WavPcm.parse(wav(22050, 1, s))!!)
        assertTrue(out.size / 2 in 1598..1602)
    }

    @Test fun badFilesAreRejected() {
        assertNull(WavPcm.parse(ByteArray(10)))
        assertNull(WavPcm.parse(ByteArray(100)))
    }
}
