package com.nova.assistant

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * v7.120: plays the already processed Ultron PCM (mono, 16 bit). Blocking; call from a worker thread.
 * stop() can be called from any thread (STOP button, a new reply, service shutdown).
 */
object UltronPlayer {
    const val FAILED = 0      // nothing was played: the caller falls back to the normal voice
    const val DONE = 1        // played to the end
    const val STOPPED = 2     // stopped on purpose while playing

    @Volatile private var track: AudioTrack? = null
    @Volatile private var stopFlag = false

    fun isPlaying(): Boolean = track != null

    fun stop() {
        stopFlag = true
        val t = track ?: return
        try { t.pause(); t.flush() } catch (e: Throwable) { ErrLog.add("UltronPlayer", e.toString()) }
    }

    fun play(pcm: ShortArray, rate: Int): Int {
        if (pcm.isEmpty() || rate < 8000) return FAILED
        var t: AudioTrack? = null
        try {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val fmt = AudioFormat.Builder()
                .setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return FAILED
            t = AudioTrack(attr, fmt, maxOf(minBuf, 8192), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
            if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); return FAILED }
            stopFlag = false
            track = t
            t.play()
            var pos = 0
            val chunk = 4096
            while (pos < pcm.size && !stopFlag) {
                val len = minOf(chunk, pcm.size - pos)
                val w = t.write(pcm, pos, len)
                if (w <= 0) break
                pos += w
            }
            // wait until the last written sample has really been played (or we are stopped / out of time)
            val deadline = System.currentTimeMillis() + pcm.size * 1000L / rate + 3000L
            while (!stopFlag && t.playbackHeadPosition < pcm.size && System.currentTimeMillis() < deadline) {
                try { Thread.sleep(20) } catch (e: InterruptedException) { break }
            }
            return if (stopFlag) STOPPED else DONE
        } catch (e: Throwable) {
            ErrLog.add("UltronPlayer", e.toString())
            return if (t == null) FAILED else (if (stopFlag) STOPPED else DONE)
        } finally {
            track = null
            try { t?.stop() } catch (e: Throwable) { }
            try { t?.release() } catch (e: Throwable) { }
        }
    }
}
