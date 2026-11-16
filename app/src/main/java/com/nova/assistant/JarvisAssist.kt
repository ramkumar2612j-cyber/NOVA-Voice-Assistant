package com.nova.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Lets the user pick JARVIS as the phone's default assistant (Settings > Default digital assistant app).
 * Then a long-press of Home / the power button wakes JARVIS like the wake word does.
 * JARVIS does NOT read the screen through this door: onHandleAssist / screenshots are not used.
 */
class JarvisInteractionService : VoiceInteractionService()

class JarvisSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = JarvisSession(this)
}

class JarvisSession(private val svc: JarvisSessionService) : VoiceInteractionSession(svc) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            val s = JarvisService.instance
            if (s != null && JarvisService.running) {
                s.wakeNow()                       // same as saying the wake word
            } else {
                svc.startActivity(
                    Intent(svc, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            // never crash the assistant slot
        }
        hide()
    }
}

/** Android lists an assistant only if it names a recognition service. JARVIS listens through Vosk itself, so this one just says "not available". */
class JarvisRecognitionStub : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try { listener?.error(SpeechRecognizer.ERROR_CLIENT) } catch (e: Exception) { ErrLog.add("JarvisAssist", e.toString()) }
    }
    override fun onCancel(listener: Callback?) { }
    override fun onStopListening(listener: Callback?) { }
}
