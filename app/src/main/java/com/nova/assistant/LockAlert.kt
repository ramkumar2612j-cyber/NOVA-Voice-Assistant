package com.nova.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * v6.97-wip9 loud lock-screen alert: 3 beeps on the ALARM stream (a silent / vibrate ringer does not mute it), then the sentence
 * from LockGuard.text spoken on the alarm stream, repeated up to LockGuard.ROUNDS times.
 * Own TextToSpeech instance, so it works even when NOVA is not ACTIVATED (the notification listener is kept alive by Android).
 * Stops by: one volume key press (NovaAccessibilityService.onKeyEvent -> [stop]), unlocking the phone, or the last round.
 * The alarm volume is raised to at least 60 % only while the alert plays and put back afterwards.
 * Limits (honest): Do Not Disturb can still block the alarm stream; the volume-key stop needs the Accessibility service to be ON.
 */
object LockAlert {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var active = false
        private set

    private var app: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var tone: ToneGenerator? = null
    private var text = ""
    private var english = false
    private var round = 0
    private var savedVol = -1
    private var unlockRx: BroadcastReceiver? = null
    private var generation = 0

    /** Starts (or restarts with the new text) the alert. Safe to call from any thread. */
    fun start(ctx: Context, sentence: String, en: Boolean) {
        main.post { startMain(ctx.applicationContext, sentence, en) }
    }

    private fun startMain(ctx: Context, sentence: String, en: Boolean) {
        try {
            stopMain(restoreOnly = false)
            app = ctx
            text = sentence
            english = en
            round = 0
            active = true
            generation++
            raiseVolume(ctx)
            registerUnlock(ctx)
            ensureTts(ctx)
            playRound(generation)
        } catch (e: Exception) {
            ErrLog.add("LockAlert", e.toString())
            active = false
        }
    }

    /** One press of a volume key (or unlock) calls this. */
    fun stop() {
        main.post { stopMain(restoreOnly = false) }
    }

    private fun stopMain(restoreOnly: Boolean) {
        val wasActive = active
        active = false
        pendingSpeak = false
        generation++
        main.removeCallbacksAndMessages(null)
        try { tts?.stop() } catch (e: Exception) { }
        try { tone?.stopTone() } catch (e: Exception) { }
        try { tone?.release() } catch (e: Exception) { }
        tone = null
        val c = app
        if (c != null) {
            unregisterUnlock(c)
            if (wasActive || restoreOnly) restoreVolume(c)
        }
    }

    private fun ensureTts(ctx: Context) {
        if (tts != null) return
        ttsReady = false
        tts = TextToSpeech(ctx) { status ->
            main.post {
                val t = tts
                if (status == TextToSpeech.SUCCESS && t != null) {
                    ttsReady = true
                    applyLang(t)
                    // if the beeps already finished, speak now
                    if (active && pendingSpeak) { pendingSpeak = false; speakNow(generation) }
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) { onSpoken(utteranceId) }
            override fun onDone(utteranceId: String?) { onSpoken(utteranceId) }
        })
    }

    @Volatile private var pendingSpeak = false

    private fun applyLang(t: TextToSpeech) {
        try {
            val want = if (english) Locale("en", "IN") else Locale("hi", "IN")
            val r = t.setLanguage(want)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.getDefault())
        } catch (e: Exception) { }
    }

    private fun playRound(gen: Int) {
        if (!active || gen != generation) return
        round++
        val tg = try { tone ?: ToneGenerator(AudioManager.STREAM_ALARM, 100).also { tone = it } } catch (e: Exception) { null }
        for (i in 0 until 3) {
            main.postDelayed({
                if (active && gen == generation) try { tg?.startTone(ToneGenerator.TONE_PROP_BEEP2, 280) } catch (e: Exception) { }
            }, i * 450L)
        }
        main.postDelayed({ if (active && gen == generation) speakNow(gen) }, 1500L)
    }

    private fun speakNow(gen: Int) {
        if (!active || gen != generation) return
        val t = tts
        if (t == null || !ttsReady) { pendingSpeak = true; return }   // spoken as soon as the engine is ready
        try {
            applyLang(t)
            val params = Bundle()
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            val r = t.speak(text, TextToSpeech.QUEUE_FLUSH, params, "lock$gen")
            if (r != TextToSpeech.SUCCESS) onSpoken("lock$gen")
        } catch (e: Exception) {
            onSpoken("lock$gen")
        }
    }

    private fun onSpoken(id: String?) {
        main.post {
            if (!active || id != "lock$generation") return@post
            if (round >= LockGuard.ROUNDS) { stopMain(restoreOnly = false); return@post }
            val g = generation
            main.postDelayed({ playRound(g) }, LockGuard.ROUND_GAP_MS)
        }
    }

    private fun raiseVolume(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val target = LockGuard.targetAlarmVolume(cur, max)
            if (target != cur) {
                savedVol = cur
                am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
            }
        } catch (e: Exception) {
            // some phones refuse (Do Not Disturb policy): the alert still plays at the current alarm volume
        }
    }

    private fun restoreVolume(ctx: Context) {
        if (savedVol < 0) return
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0)
        } catch (e: Exception) { }
        savedVol = -1
    }

    private fun registerUnlock(ctx: Context) {
        try {
            val rx = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) { stop() }
            }
            unlockRx = rx
            ctx.registerReceiver(rx, IntentFilter(Intent.ACTION_USER_PRESENT))
        } catch (e: Exception) { unlockRx = null }
    }

    private fun unregisterUnlock(ctx: Context) {
        val rx = unlockRx ?: return
        unlockRx = null
        try { ctx.unregisterReceiver(rx) } catch (e: Exception) { }
    }

    /** Is the phone silent right now? ringer silent / vibrate, or ring volume 0. */
    fun phoneSilent(ctx: Context): Boolean = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        LockGuard.isSilent(am.ringerMode == AudioManager.RINGER_MODE_NORMAL, am.getStreamVolume(AudioManager.STREAM_RING))
    } catch (e: Exception) { false }

    /** Screen locked or off (the same test the voice loop uses). */
    fun phoneLocked(ctx: Context): Boolean = try {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        km.isKeyguardLocked || !pm.isInteractive
    } catch (e: Exception) { false }
}
