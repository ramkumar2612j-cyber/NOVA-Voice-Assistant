package com.nova.assistant

import android.app.Activity
import android.os.Bundle

/**
 * v6.97-wip9: target of android.intent.action.VOICE_COMMAND, which Android sends when a Bluetooth headset / calling watch presses its
 * voice-assistant (AI) button. No screen: it wakes NOVA (same as the wake word) on the watch mic and closes at once.
 * The first time Android may ask which app to use: choose NOVA and ALWAYS.
 */
class WatchCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val s = NovaService.instance
            if (s != null && NovaService.running) s.wakeFromWatch()
        } catch (e: Exception) {
            ErrLog.add("WatchCommand", e.toString())
        }
        finish()
    }
}
