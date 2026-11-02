package com.nova.assistant

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor

/**
 * v6.36: background noise cancellation on the ONE mic loop (NovaService.audioLoop).
 * Uses the phone's own NoiseSuppressor + AcousticEchoCanceler (the AEC also helps against NOVA's own speech).
 * AutomaticGainControl is NOT used (it lifts the background noise between words).
 * Every call is wrapped: a phone without these effects, or an error, just means no suppression, never a crash.
 * Evidence level: written + statically checked, not compiled, not phone tested. Whether the phone really has these effects
 * is shown by status() (NoiseSuppressor.isAvailable()).
 */
object MicEffects {
    private val lock = Any()
    private var ns: AudioEffect? = null
    private var aec: AudioEffect? = null

    /** Attach to the AudioRecord session. Safe to call again (old effects are released first). */
    fun attach(sessionId: Int, on: Boolean) {
        synchronized(lock) {
            releaseLocked()
            if (sessionId == 0) return
            try { if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(sessionId) } catch (t: Throwable) { ns = null }
            try { if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(sessionId) } catch (t: Throwable) { aec = null }
            setEnabledLocked(on)
        }
    }

    /** Switch on/off live, without restarting the mic. */
    fun setEnabled(on: Boolean) { synchronized(lock) { setEnabledLocked(on) } }

    private fun setEnabledLocked(on: Boolean) {
        try { ns?.enabled = on } catch (t: Throwable) { ErrLog.add("MicEffects", t.toString()) }
        try { aec?.enabled = on } catch (t: Throwable) { ErrLog.add("MicEffects", t.toString()) }
    }

    fun release() { synchronized(lock) { releaseLocked() } }

    private fun releaseLocked() {
        try { ns?.release() } catch (t: Throwable) { }
        try { aec?.release() } catch (t: Throwable) { }
        ns = null
        aec = null
    }

    /** Short Hinglish text for Settings. */
    fun status(on: Boolean): String {
        val has = synchronized(lock) { ns != null }
        val avail = try { NoiseSuppressor.isAvailable() } catch (t: Throwable) { false }
        return when {
            !on -> "band"
            has -> "ON (phone ka noise suppressor chal raha hai)"
            avail -> "ON (agli baar ACTIVATE par lagega)"
            else -> "ON, par is phone me noise suppressor nahi hai"
        }
    }
}
