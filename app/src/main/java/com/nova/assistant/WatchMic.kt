package com.nova.assistant

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Build

/**
 * v6.97-wip9 WATCH MIC (owner: calling watch connected over Bluetooth, its AI button should wake NOVA and NOVA should hear the watch mic).
 * A calling watch is a Bluetooth HEADSET to the phone. Its AI / voice button sends the headset "voice command" signal, which Android turns into
 * android.intent.action.VOICE_COMMAND (WatchCommandActivity). Then the watch mic is only reachable through a Bluetooth SCO call-audio link:
 * this object opens that link and points NOVA's AudioRecord at it, and closes it again after the turn so music / phone mic come back.
 * NOT TESTED on a real watch. Works only if the watch really sends the voice-command signal and exposes SCO.
 */
object WatchMic {
    @Volatile var active = false
        private set

    private fun am(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** A Bluetooth SCO headset is connected (the watch, when it acts as a headset). */
    fun headsetConnected(ctx: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 31) am(ctx).availableCommunicationDevices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        else am(ctx).isBluetoothScoAvailableOffCall && am(ctx).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    } catch (e: Exception) { false }

    private fun scoInput(ctx: Context): AudioDeviceInfo? = try {
        am(ctx).getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
    } catch (e: Exception) { null }

    /** Blocking (up to ~3 s): call from a background thread. true = the watch mic is now the recording source. */
    fun start(ctx: Context, rec: AudioRecord?): Boolean {
        return try {
            val a = am(ctx)
            if (Build.VERSION.SDK_INT >= 31) {
                val d = a.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO } ?: return false
                a.mode = AudioManager.MODE_IN_COMMUNICATION
                if (!a.setCommunicationDevice(d)) return false
            } else {
                a.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION") a.startBluetoothSco()
                @Suppress("DEPRECATION") run { a.isBluetoothScoOn = true }
            }
            var dev: AudioDeviceInfo? = null
            for (i in 0 until 15) {            // the SCO link needs a moment to come up
                dev = scoInput(ctx)
                if (dev != null) break
                try { Thread.sleep(200) } catch (e: InterruptedException) { break }
            }
            val ok = dev != null && (rec?.setPreferredDevice(dev) ?: false)
            active = ok
            if (!ok) stop(ctx, rec)
            ok
        } catch (e: Exception) {
            ErrLog.add("WatchMic", e.toString())
            stop(ctx, rec)
            false
        }
    }

    fun stop(ctx: Context, rec: AudioRecord?) {
        try {
            val a = am(ctx)
            try { rec?.setPreferredDevice(null) } catch (e: Exception) { ErrLog.addOnce("quiet:WatchMic", e.toString()) }
            if (Build.VERSION.SDK_INT >= 31) a.clearCommunicationDevice()
            else {
                @Suppress("DEPRECATION") a.stopBluetoothSco()
                @Suppress("DEPRECATION") run { a.isBluetoothScoOn = false }
            }
            a.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) { ErrLog.addOnce("quiet:WatchMic", e.toString()) }
        active = false
    }
}
