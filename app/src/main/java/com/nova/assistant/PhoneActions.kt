package com.nova.assistant

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification

/**
 * v6.99 Part 1: the Android side of "call uthao / call kaato".
 * [CallHub] remembers the Answer / Decline buttons of the call notification that is ringing right now
 * (normal call, WhatsApp, Instagram, Telegram ...). [PhoneActions] presses them; for a normal phone call
 * with no such button it falls back to TelecomManager (needs the ANSWER_PHONE_CALLS permission).
 * Rules (which words, which button) are pure and tested in PhoneVoiceRules.
 */
object CallHub {
    private class Ring(val key: String, val pkg: String, val actions: List<Notification.Action>, val at: Long)
    private var ring: Ring? = null
    private const val MAX_AGE_MS = 90_000L

    @Synchronized fun onPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val acts = n.actions?.toList() ?: emptyList()
        val kind = LockGuard.callKind(sbn.packageName ?: "", n.category, title, text, acts.map { it.title?.toString() ?: "" })
        if (kind == LockGuard.Kind.CALL_RINGING) ring = Ring(sbn.key ?: "", sbn.packageName ?: "", acts, System.currentTimeMillis())
    }

    @Synchronized fun onRemoved(key: String) { if (ring?.key == key) ring = null }

    @Synchronized private fun current(): Ring? = ring?.takeIf { System.currentTimeMillis() - it.at <= MAX_AGE_MS }

    @Synchronized fun ringing(): Boolean = current() != null

    /** Presses the Answer / Decline button of the ringing call. false = no ringing call, no such button, or the button is gone. */
    @Synchronized fun press(ctx: Context, answer: Boolean): Boolean {
        val r = current() ?: return false
        val i = PhoneVoiceRules.pickAction(r.actions.map { it.title?.toString() ?: "" }, answer)
        if (i < 0) return false
        val pi: PendingIntent = r.actions[i].actionIntent ?: return false
        return try { pi.send(); true } catch (e: PendingIntent.CanceledException) { false }
    }
}

object PhoneActions {
    class Res(val ok: Boolean, val code: String)

    fun answerCall(ctx: Context): Res {
        if (CallHub.press(ctx, true)) return Res(true, "ok")
        return telecom(ctx, true)
    }

    fun rejectCall(ctx: Context): Res {
        if (CallHub.press(ctx, false)) return Res(true, "ok")
        return telecom(ctx, false)
    }

    private fun telecom(ctx: Context, answer: Boolean): Res {
        if (Build.VERSION.SDK_INT < 28) return Res(false, "old_android")
        if (ctx.checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return Res(false, "no_permission")
        return try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            if (answer) { tm.acceptRingingCall(); Res(true, "ok") }
            else if (tm.endCall()) Res(true, "ok") else Res(false, "no_call")
        } catch (e: SecurityException) { Res(false, "no_permission") }
        catch (e: Exception) { ErrLog.add("PhoneActions", e.javaClass.simpleName); Res(false, "failed") }
    }

    /** mode = silent | vibrate | normal. The result is read back from AudioManager: ok only when the ringer really changed. */
    fun setRinger(ctx: Context, mode: String): Res {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val want = when (mode) { "silent" -> AudioManager.RINGER_MODE_SILENT; "vibrate" -> AudioManager.RINGER_MODE_VIBRATE; else -> AudioManager.RINGER_MODE_NORMAL }
        return try {
            am.ringerMode = want
            if (am.ringerMode == want) Res(true, "ok") else Res(false, if (want == AudioManager.RINGER_MODE_SILENT) "no_policy" else "mismatch")
        } catch (e: SecurityException) {
            // silent / some switches need Do Not Disturb access. Vibrate needs none: do that and say so.
            if (want == AudioManager.RINGER_MODE_SILENT) {
                try { am.ringerMode = AudioManager.RINGER_MODE_VIBRATE } catch (e2: Exception) { /* even vibrate refused */ }
                Res(false, if (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE) "no_policy_vibrate" else "no_policy")
            } else Res(false, "no_policy")
        } catch (e: Exception) { ErrLog.add("PhoneActions", "ringer " + e.javaClass.simpleName); Res(false, "failed") }
    }

    /** Auto-rotate on / off through the system setting (needs "Modify system settings", the same one brightness uses). Read back. */
    fun setRotation(ctx: Context, on: Boolean): Res {
        if (!Settings.System.canWrite(ctx)) return Res(false, "no_write")
        return try {
            val v = if (on) 1 else 0
            Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, v)
            if (Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, -1) == v) Res(true, "ok") else Res(false, "mismatch")
        } catch (e: Exception) { ErrLog.add("PhoneActions", "rotation " + e.javaClass.simpleName); Res(false, "failed") }
    }

    fun policyAccess(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted
    } catch (e: Exception) { false }

    fun failText(code: String, answer: Boolean, en: Boolean): String = when (code) {
        "no_permission" -> if (en) "I need the Phone (answer calls) permission. Tap Allow in the NOVA app." else "Call uthane ke liye Phone permission chahiye. NOVA app mein Allow dabao."
        "old_android" -> if (en) "This Android version does not let me do that." else "Is Android version mein ye nahi ho sakta."
        "no_policy_vibrate" -> if (en) "Silent needs Do Not Disturb access, so I set vibrate. Tap ALLOW DND in the NOVA app for silent." else "Silent ke liye Do Not Disturb access chahiye, isliye vibrate kar diya. Silent ke liye NOVA app mein ALLOW DND dabao."
        "no_policy" -> if (en) "The ringer did not change. Tap ALLOW DND in the NOVA app." else "Ringer nahi badla. NOVA app mein ALLOW DND dabao."
        "no_write" -> if (en) "Auto-rotate needs a permission. Tap ALLOW BRIGHTNESS in the NOVA app (it is the same Modify system settings switch)." else "Auto-rotate ke liye permission chahiye. NOVA app mein ALLOW BRIGHTNESS dabao (wahi Modify system settings switch hai)."
        "mismatch" -> if (en) "I changed it, but the phone did not keep it." else "Maine badla, par phone ne rakha nahi."
        "failed_shot" -> if (en) "The screenshot did not work. Check NOVA Phone Control (Accessibility)." else "Screenshot nahi ho paya. NOVA Phone Control (Accessibility) check karo."
        "no_acc" -> if (en) "Turn on NOVA Phone Control (Accessibility) first." else "Pehle NOVA Phone Control (Accessibility) ON karo."
        "no_call" -> if (en) "No call is ringing right now." else "Abhi koi call nahi aa rahi."
        else -> if (answer) (if (en) "I could not pick up the call." else "Call utha nahi paya.") else (if (en) "I could not reject the call." else "Call kaat nahi paya.")
    }
}
