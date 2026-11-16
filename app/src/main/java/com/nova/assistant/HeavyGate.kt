package com.nova.assistant

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * ADD-ONLY (wip15): the big background jobs (loading the 491 MB offline model into RAM, downloading five extra offline models)
 * used to start by themselves a few seconds after every ACTIVATE, right while the phone is already busy with the mic, Vosk and the page.
 * Now they only start by themselves while the phone is CHARGING. Nothing is removed: the offline model still loads the first time it is needed,
 * and everything still starts on its own at the next ACTIVATE while charging. Never throws.
 */
object HeavyGate {
    fun charging(ctx: Context): Boolean = try {
        val i = ctx.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val s = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        s == BatteryManager.BATTERY_STATUS_CHARGING || s == BatteryManager.BATTERY_STATUS_FULL
    } catch (e: Throwable) { false }
}
