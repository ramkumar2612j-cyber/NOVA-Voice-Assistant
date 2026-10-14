package com.nova.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/**
 * After a phone restart NOVA is OFF (Android 14 does not let a microphone service start from the boot signal).
 * v6.94: it re-arms the user's saved reminders/scheduled tasks and posts ONE notification; one tap opens NOVA and NOVA then starts by itself.
 * No notification when the owner pressed STOP on purpose, or the microphone was never allowed.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val a = i.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != "android.intent.action.QUICKBOOT_POWERON") return
        try { Cfg(c).active = false } catch (ignored: Exception) { ErrLog.add("BootReceiver", ignored.toString()) }
        try { ScheduledTaskStore.rescheduleAll(c) } catch (ignored: Exception) { ErrLog.add("BootReceiver", ignored.toString()) }
        try { askToStart(c) } catch (ignored: Exception) { ErrLog.add("BootReceiver", ignored.toString()) }
    }

    private fun askToStart(c: Context) {
        val prefs = c.getSharedPreferences("nova_perm", Context.MODE_PRIVATE)
        if (prefs.getBoolean("user_stopped", false)) return
        if (c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("nova_boot", "NOVA start", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(c, 7421, open, flags)
        val note = androidx.core.app.NotificationCompat.Builder(c, "nova_boot")
            .setSmallIcon(com.nova.assistant.R.mipmap.ic_launcher)
            .setContentTitle("NOVA band hai")
            .setContentText("Shuru karne ke liye ek baar tap karo")
            .setContentIntent(pi).setAutoCancel(true).build()
        nm.notify(7421, note)
    }
}
