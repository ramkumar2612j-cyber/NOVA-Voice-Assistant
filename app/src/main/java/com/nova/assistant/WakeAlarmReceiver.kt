package com.nova.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Buttons on the morning wake-alarm notification: OFF (stops it for today) and DISMISS (rings again after 1 minute). */
class WakeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val s = NovaService.instance ?: return
        when (intent?.action) {
            WakeRules.ACTION_OFF -> s.wakeOff()
            WakeRules.ACTION_SNOOZE -> s.wakeSnooze()
        }
    }
}
