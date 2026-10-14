package com.nova.assistant

/**
 * v6.25: reads a plain PCM16 WAV (what Android's TextToSpeech.synthesizeToFile writes) into 16 kHz mono 16-bit PCM.
 * Pure Kotlin, so it is unit tested. Used by the hearing self test in NovaService (TTS file -> Vosk). Not phone tested.
 */
object WavPcm {
    class Wav(val pcm: ByteArray, val rate: Int)

    private fun le16(b: ByteArray, i: Int): Int = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
    private fun le32(b: ByteArray, i: Int): Int = le16(b, i) or (le16(b, i + 2) shl 16)

    /** First channel as 16-bit PCM at the file's own rate, or null when this is not a plain PCM16 WAV. */
    fun parse(b: ByteArray): Wav? {
        if (b.size < 44) return null
        if (String(b, 0, 4, Charsets.US_ASCII) != "RIFF" || String(b, 8, 4, Charsets.US_ASCII) != "WAVE") return null
        var pos = 12
        var fmt = 0
        var ch = 0
        var rate = 0
        var bits = 0
        while (pos + 8 <= b.size) {
            val id = String(b, pos, 4, Charsets.US_ASCII)
            var len = le32(b, pos + 4)
            val body = pos + 8
            if (id == "fmt " && body + 16 <= b.size) {
                fmt = le16(b, body)
                ch = le16(b, body + 2)
                rate = le32(b, body + 4)
                bits = le16(b, body + 14)
            } else if (id == "data") {
                if (fmt != 1 || bits != 16 || ch < 1 || rate < 8000) return null
                if (len <= 0 || len > b.size - body) len = b.size - body
                val frames = len / (2 * ch)
                if (frames <= 0) return null
                val out = ByteArray(frames * 2)
                for (f in 0 until frames) {
                    out[2 * f] = b[body + f * 2 * ch]
                    out[2 * f + 1] = b[body + f * 2 * ch + 1]
                }
                return Wav(out, rate)
            }
            if (len < 0 || body + len < body) return null
            pos = body + len + (len and 1)
        }
        return null
    }

    /** Linear resample to 16 kHz (the rate NOVA's voice model works at). */
    fun to16k(w: Wav): ByteArray {
        if (w.rate == 16000) return w.pcm
        val n = w.pcm.size / 2
        val outN = (n.toLong() * 16000L / w.rate).toInt()
        val out = ByteArray(outN * 2)
        for (i in 0 until outN) {
            val src = i.toDouble() * w.rate / 16000.0
            val i0 = src.toInt()
            val i1 = if (i0 + 1 < n) i0 + 1 else i0
            val frac = src - i0
            val s0 = (w.pcm[2 * i0 + 1].toInt() shl 8) or (w.pcm[2 * i0].toInt() and 0xff)
            val s1 = (w.pcm[2 * i1 + 1].toInt() shl 8) or (w.pcm[2 * i1].toInt() and 0xff)
            val v = (s0 + (s1 - s0) * frac).toInt()
            out[2 * i] = (v and 0xff).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }
}
