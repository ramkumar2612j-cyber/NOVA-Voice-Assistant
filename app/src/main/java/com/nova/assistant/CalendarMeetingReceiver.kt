package com.nova.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CalendarMeetingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CalendarRead.ACTION_MEETING) return
        val id = intent.getLongExtra(CalendarRead.EXTRA_EVENT_ID, -1L)
        val begin = intent.getLongExtra(CalendarRead.EXTRA_BEGIN, -1L)
        if (id < 0 || begin < 0) return
        val event = CalendarRead.eventForReminder(context, id, begin) ?: return // event was deleted/changed
        val service = NovaService.instance
        if (service != null && NovaService.running) service.onMeetingReminder(event.title, event.begin)
        else notify(context, event.title, event.begin)
    }

    private fun notify(ctx: Context, title: String, start: Long) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("nova_calendar", "NOVA Meeting Reminders", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, 611, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val time = DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(start))
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, "nova_calendar") else Notification.Builder(ctx)
            nm.notify(("meeting:$start".hashCode()), b.setContentTitle("NOVA meeting reminder").setContentText("$title — $time, 10 minute reminder").setSmallIcon(android.R.drawable.ic_dialog_info).setContentIntent(pi).setAutoCancel(true).build())
        } catch (ignored: Exception) { ErrLog.add("CalendarMeetingReceiver", ignored.toString()) }
    }
}
