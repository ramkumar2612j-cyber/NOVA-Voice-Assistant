package com.nova.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * v7.154 JARVIS UPGRADE: audio focus around NOVA's speech. Until now NOVA never requested audio focus, so
 * music kept playing at full volume over the reply. Standard assistant behaviour (and the owner's JARVIS
 * wish-list) is: other audio DUCKS (gets quieter) while the assistant speaks and comes back afterwards.
 *
 * Rules (add-only):
 *  - one focus request per utterance, made in NovaService.speak() right before the voice starts;
 *    released when the utterance ends (utteranceEnded / stopSpeaking / speak failure);
 *  - GAIN_TRANSIENT_MAY_DUCK: music is lowered, not paused - nothing the user was hearing is ever killed;
 *  - if another app takes permanent focus while NOVA speaks, NOVA stops talking (someone else won the phone);
 *  - the whole thing is wrapped so a failure can never break speech, and it can be turned OFF
 *    (ExtCfg.afocus, default ON; "nova audio focus off karo").
 */
object SpeakFocus {
    @Volatile private var request: AudioFocusRequest? = null
    @Volatile private var am: AudioManager? = null
    @Volatile private var active = false

    /** true when a focus request is currently held. */
    fun held(): Boolean = active

    private val listener = android.media.AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // another app won the phone: stop NOVA's speech (SafeStop semantics, service stays alive)
                active = false
                try { NovaService.instance?.stopSpeaking() } catch (e: Throwable) { }
            }
            // MAY_DUCK means we never get pause/duck callbacks for others - ignore the rest
        }
    }

    /** Request may-duck focus for one utterance. Safe to call on any thread; never throws. */
    fun acquire(ctx: Context): Boolean {
        val app = ctx.applicationContext
        return try {
            val m = am ?: (app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager).also { am = it } ?: return false
            abandon()
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attr)
                .setOnAudioFocusChangeListener(listener)
                .setWillPauseWhenDucked(false)
                .build()
            val r = m.requestAudioFocus(req)
            if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                request = req
                active = true
                true
            } else false
        } catch (e: Throwable) {
            ErrLog.addOnce("SpeakFocus", "acquire " + e.javaClass.simpleName)
            false
        }
    }

    /** Release the focus held for the utterance. Safe when nothing is held. */
    fun abandon() {
        try {
            val m = am
            val req = request
            if (m != null && req != null) m.abandonAudioFocusRequest(req)
        } catch (e: Throwable) {
            ErrLog.addOnce("SpeakFocus", "abandon " + e.javaClass.simpleName)
        } finally {
            request = null
            active = false
        }
    }
}
