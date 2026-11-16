package com.nova.assistant

import android.content.Context
import android.media.AudioManager

/**
 * v7.204 MEDIA WAKE GUARD (add-only, pure decision + tiny cached read of AudioManager).
 *
 * Problem (owner, 2026-11-16): reels / video ki awaaz se JARVIS bina bulaye jaag jaata tha.
 * Cause: wake word sirf loudness dekhta tha; media ki awaaz aksar loud hoti hai aur "jarvis" sunai de jaata hai.
 * Fix: jab phone par media baj raha ho, to wake ke liye zyada loud awaaz (x2.5) aur double cooldown.
 * Owner ki asli awaaz (paas se, saaf) ab bhi chalegi. Media chal raha ho aur owner ko JARVIS bulana ho to thoda zor se bolo.
 */
internal object MediaWakeGuard {
    const val LOUD_MULTIPLIER = 2.5
    const val COOLDOWN_MULTIPLIER = 2.0
    private const val CACHE_MS = 2000L

    @Volatile private var cachedAt = 0L
    @Volatile private var cachedOn = false

    /** Media baj raha hai ya nahi, 2 second tak cache (AudioManager har chunk par nahi padhna). */
    fun mediaActive(ctx: Context, now: Long): Boolean {
        if (now - cachedAt < CACHE_MS) return cachedOn
        val on = try {
            val am = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.isMusicActive
        } catch (e: Throwable) { false }
        cachedAt = now
        cachedOn = on
        return on
    }

    fun loudMultiplier(mediaOn: Boolean): Double = if (mediaOn) LOUD_MULTIPLIER else 1.0
    fun cooldownMultiplier(mediaOn: Boolean): Double = if (mediaOn) COOLDOWN_MULTIPLIER else 1.0

    /** Decision used by the wake loop: true = wake allowed. */
    fun allowWake(peak: Double, needLoud: Double, mediaOn: Boolean): Boolean = peak >= needLoud * loudMultiplier(mediaOn)
}
