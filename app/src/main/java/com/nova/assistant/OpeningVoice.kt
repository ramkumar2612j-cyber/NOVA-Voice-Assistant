package com.nova.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * v7.161: the opening line, spoken with the existing Ultron voice effect (TTS -> WAV -> UltronFx -> UltronPlayer).
 * Own short-lived TextToSpeech, so it works before NovaService is running. It never touches the microphone.
 * onStart(ms) is called when playing begins (ms = length of the line), onStart(0) when the voice could not be made
 * (the opening animation then runs with its normal timing). Not phone tested.
 */
object OpeningVoice {
    const val QUOTE = "Keep your friends rich and your enemies rich, and wait to find out which is which."
    private const val RATE = 0.8f

    @Volatile private var gen = 0

    fun stop() {
        gen++
        try { UltronPlayer.stop() } catch (e: Throwable) { }
    }

    fun start(ctx: Context, text: String, amount: Float, style: String = VoiceStyle.ULTRON, onStart: (Long) -> Unit) {
        val my = ++gen
        val app = ctx.applicationContext
        Thread {
            var tts: TextToSpeech? = null
            val f = File(app.cacheDir, "opening_ultron.wav")
            var started = false
            try {
                val ready = CountDownLatch(1)
                var ok = false
                Handler(Looper.getMainLooper()).post {
                    try {
                        tts = TextToSpeech(app) { st -> ok = st == TextToSpeech.SUCCESS; ready.countDown() }
                    } catch (e: Throwable) { ready.countDown() }
                }
                if (!ready.await(8, TimeUnit.SECONDS) || !ok) return@Thread
                val t = tts ?: return@Thread
                if (my != gen) return@Thread
                try { t.language = if (VoiceStyle.isJarvis(style)) Locale.UK else Locale.US } catch (e: Throwable) { }
                t.setPitch(VoiceStyle.pitch(style))
                t.setSpeechRate(minOf(RATE, VoiceStyle.maxRate(style)))
                val done = CountDownLatch(1)
                var good = false
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { good = true; done.countDown() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { done.countDown() }
                })
                f.delete()
                if (t.synthesizeToFile(text, android.os.Bundle(), f, "nova_open") != TextToSpeech.SUCCESS) return@Thread
                if (!done.await(20, TimeUnit.SECONDS) || !good || my != gen) return@Thread
                val w = WavPcm.parse(f.readBytes()) ?: return@Thread
                val fx = VoiceStyle.process(style, UltronFx.toShorts(w.pcm), w.rate, amount)
                if (my != gen) return@Thread
                started = true
                onStart(fx.size * 1000L / w.rate)
                UltronPlayer.play(fx, w.rate)
            } catch (e: Throwable) {
                ErrLog.add("OpeningVoice", e.toString())
            } finally {
                try { f.delete() } catch (e: Throwable) { }
                try { tts?.shutdown() } catch (e: Throwable) { }
                if (!started && my == gen) { try { onStart(0L) } catch (e: Throwable) { } }
            }
        }.start()
    }
}
