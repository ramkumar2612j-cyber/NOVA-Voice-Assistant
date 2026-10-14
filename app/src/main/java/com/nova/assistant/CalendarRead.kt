package com.nova.assistant

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Calendar text is queried and spoken locally; it is not included in cloud prompts or logs. */
object CalendarRead {
    data class Event(val id: Long, val title: String, val begin: Long, val end: Long)
    fun permitted(ctx: Context): Boolean = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun query(ctx: Context, begin: Long, end: Long): List<Event> {
        if (!permitted(ctx)) return emptyList()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, begin); ContentUris.appendId(b, end)
        val out = ArrayList<Event>()
        try {
            ctx.contentResolver.query(b.build(), arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END), null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext() && out.size < 40) {
                    val id = c.getLong(0); val title = (c.getString(1) ?: "Meeting").replace(Regex("[\\r\\n\\u0000-\\u001f]"), " ").take(100)
                    val start = c.getLong(2); val finish = c.getLong(3)
                    if (title.isNotBlank() && start in begin..end) out.add(Event(id, title, start, finish))
                }
            }
        } catch (ignored: SecurityException) { return emptyList() } catch (ignored: Exception) { return emptyList() }
        return out
    }

    fun todayText(ctx: Context): String {
        if (!permitted(ctx)) return "Calendar permission OFF hai. NOVA Settings mein ALLOW CALENDAR dabao; voice command permission khud nahi maangega."
        val zone = ZoneId.systemDefault(); val date = LocalDate.now(zone)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val events = query(ctx, start, end)
        if (events.isEmpty()) return "Aaj calendar mein koi event nahi mila."
        val fmt = DateTimeFormatter.ofPattern("h:mm a").withZone(zone)
        return "Aaj ke ${events.size} event: " + events.take(8).joinToString("; ") { "${it.title} ${fmt.format(Instant.ofEpochMilli(it.begin))}" } + "."
    }

    /** Schedules reminders 10 minutes before the next week of events. Requires owner-granted READ_CALENDAR. */
    fun scheduleUpcoming(ctx: Context): Int {
        if (!permitted(ctx)) return 0
        val now = System.currentTimeMillis()
        val events = query(ctx, now + 60_000L, now + 7L * 24 * 60 * 60 * 1000)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return 0
        var count = 0
        for (e in events) {
            val trigger = maxOf(now + 5_000L, e.begin - 10 * 60_000L)
            try {
                val pi = PendingIntent.getBroadcast(ctx, ("${e.id}:${e.begin}".hashCode()),
                    Intent(ctx, CalendarMeetingReceiver::class.java).setAction(ACTION_MEETING).putExtra(EXTRA_EVENT_ID, e.id).putExtra(EXTRA_BEGIN, e.begin),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                count++
            } catch (ignored: Exception) { ErrLog.add("CalendarRead", ignored.toString()) }
        }
        return count
    }

    fun eventForReminder(ctx: Context, id: Long, begin: Long): Event? = query(ctx, begin - 1000, begin + 1000).firstOrNull { it.id == id && it.begin == begin }

    const val ACTION_MEETING = "com.nova.assistant.CALENDAR_MEETING_REMINDER"
    const val EXTRA_EVENT_ID = "event_id"
    const val EXTRA_BEGIN = "event_begin"
}
