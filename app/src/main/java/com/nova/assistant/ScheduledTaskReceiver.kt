package com.nova.assistant

import android.content.BroadcastReceiver
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Alarm receiver never sends anything itself. If NOVA is not already active, it reports the missed task. */
class ScheduledTaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ScheduledTaskStore.ACTION_LEAD) {
            // v6.40: 25 minutes before. Only when NOVA is already running (it never starts the microphone by itself).
            val lid = intent.getStringExtra(ScheduledTaskStore.EXTRA_ID).orEmpty()
            val le = ScheduledTaskStore.get(context, lid) ?: return
            val sv = NovaService.instance
            if (sv != null && NovaService.running && sv.isAliveNow()) sv.onLeadTime(le)
            return
        }
        if (intent?.action != ScheduledTaskStore.ACTION_FIRE) return
        val id = intent.getStringExtra(ScheduledTaskStore.EXTRA_ID).orEmpty()
        if (id.isBlank()) return
        val entry = ScheduledTaskStore.consume(context, id) ?: return
        val service = NovaService.instance
        if (service != null && NovaService.running && service.isAliveNow()) {
            service.onScheduledTask(entry)
        } else {
            notifySkipped(context, entry)
        }
    }

    /** v7.12: one notice for one-shot jobs that were due while the phone was off / the app was updated. */
    fun notifyMissed(context: Context, labels: List<String>) {
        if (labels.isEmpty()) return
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("nova_schedule", "NOVA Scheduled Tasks", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(context, 7431, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, "nova_schedule") else Notification.Builder(context)
            n.setContentTitle("NOVA: " + labels.size + " task miss ho gaya")
                .setContentText(labels.take(3).joinToString(", ") + " - tay waqt par phone band tha / app update hua. Zaroorat ho to dobara set karo.")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(open).setAutoCancel(true)
            nm.notify(7431, n.build())
        } catch (ignored: Exception) { ErrLog.add("ScheduledTaskReceiver", ignored.toString()) }
    }

    private fun notifySkipped(context: Context, entry: ScheduledTaskStore.Entry) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
                NotificationChannel("nova_schedule", "NOVA Scheduled Tasks", NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                context, entry.id.hashCode(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, "nova_schedule") else Notification.Builder(context)
            n.setContentTitle("NOVA: scheduled task due")
                .setContentText("${entry.spec.label} — NOVA active nahi thi; automatic action skip hua. Tap karke NOVA khol sakte ho.")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(open).setAutoCancel(true)
            nm.notify(entry.id.hashCode(), n.build())
        } catch (ignored: Exception) { ErrLog.add("ScheduledTaskReceiver", ignored.toString()) }
    }
}
