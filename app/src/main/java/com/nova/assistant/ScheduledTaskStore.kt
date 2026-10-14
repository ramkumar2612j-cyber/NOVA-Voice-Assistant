package com.nova.assistant

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.util.UUID

/** Private on-device schedule store. Scheduled message content is never sent to AI or written to logs. */
object ScheduledTaskStore {
    data class Entry(val id: String, val spec: ScheduledTaskRules.Spec, val nextAtMs: Long)
    private const val PREFS = "nova_scheduled_tasks"
    private const val KEY = "items_v1"
    private const val MAX_ITEMS = 30

    @Synchronized private fun read(ctx: Context): List<Entry> {
        val raw = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw); val out = ArrayList<Entry>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val spec = ScheduledTaskRules.decode(o.optString("spec")) ?: continue
                val id = o.optString("id").take(80)
                if (id.isNotBlank()) out.add(Entry(id, spec, o.optLong("next", 0L)))
            }
            out.takeLast(MAX_ITEMS)
        } catch (ignored: Exception) { emptyList() }
    }

    @Synchronized private fun write(ctx: Context, entries: List<Entry>): Boolean {
        val a = JSONArray()
        entries.takeLast(MAX_ITEMS).forEach { e ->
            a.put(JSONObject().put("id", e.id).put("spec", ScheduledTaskRules.encode(e.spec)).put("next", e.nextAtMs))
        }
        return ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).commit()
    }

    /** Returns status exact/inexact/cap/storage/invalid. A schedule never claims exact timing without OS permission. */
    @Synchronized fun add(ctx: Context, spec: ScheduledTaskRules.Spec): String {
        if (spec.hour !in 0..23 || spec.minute !in 0..59) return "invalid"
        if (spec.kind == ScheduledTaskRules.Kind.SEND_MESSAGE &&
            (spec.recipient.isBlank() || spec.message.isBlank() || Driving.looksSensitive(spec.message))) return "message_incomplete_or_sensitive"
        val old = read(ctx)
        if (old.size >= MAX_ITEMS) return "limit"
        val entry = Entry(UUID.randomUUID().toString(), spec, ScheduledTaskRules.nextAtMillis(spec))
        if (!write(ctx, old + entry)) return "storage_failed"
        return if (scheduleAlarm(ctx, entry)) "exact" else "inexact"
    }

    /** v6.40: change only how important a job is (prio 0..2, or -1 = keep) and/or its own-words note (null = keep). Alarms are not touched. */
    @Synchronized fun update(ctx: Context, query: String, prio: Int, note: String?): Entry? {
        val old = read(ctx)
        val q = query.trim()
        if (q.isEmpty()) return null
        val m = old.firstOrNull { it.id == q || it.id.startsWith(q) || it.spec.label.equals(q, true) }
            ?: old.firstOrNull { it.spec.label.contains(q, true) } ?: return null
        val ns = m.spec.copy(prio = if (prio in 0..2) prio else m.spec.prio, note = note?.take(120) ?: m.spec.note)
        val e = m.copy(spec = ns)
        return if (write(ctx, old.map { if (it.id == m.id) e else it })) e else null
    }

    fun list(ctx: Context): List<Entry> = read(ctx)
    fun get(ctx: Context, id: String): Entry? = read(ctx).firstOrNull { it.id == id }

    @Synchronized fun cancelAll(ctx: Context): Int {
        val old = read(ctx)
        old.forEach { cancelAlarm(ctx, it.id) }
        write(ctx, emptyList())
        return old.size
    }

    @Synchronized fun cancel(ctx: Context, query: String): Boolean {
        val old = read(ctx)
        val match = old.firstOrNull { it.id == query || it.id.startsWith(query) || it.spec.label.equals(query, true) }
            ?: return false
        cancelAlarm(ctx, match.id)
        return write(ctx, old.filterNot { it.id == match.id })
    }

    /** Called by the receiver at fire time. One-shot jobs are removed before running to prevent duplicate sends. */
    @Synchronized fun consume(ctx: Context, id: String): Entry? {
        val old = read(ctx)
        val found = old.firstOrNull { it.id == id } ?: return null
        return if (found.spec.repeatDaily) {
            val next = found.copy(nextAtMs = if (found.spec.days != 0) ScheduledTaskRules.nextAtMillis(found.spec) else maxOf(found.nextAtMs + 86_400_000L, ScheduledTaskRules.nextAtMillis(found.spec)))
            if (write(ctx, old.map { if (it.id == id) next else it })) scheduleAlarm(ctx, next)
            found
        } else {
            cancelAlarm(ctx, id)
            write(ctx, old.filterNot { it.id == id })
            found
        }
    }

    @Synchronized fun rescheduleAll(ctx: Context) {
        val all = read(ctx)
        all.forEach { e ->
            val corrected = if (e.nextAtMs > System.currentTimeMillis()) e else e.copy(nextAtMs = ScheduledTaskRules.nextAtMillis(e.spec))
            scheduleAlarm(ctx, corrected)
        }
    }

    fun listText(ctx: Context): String {
        val items = list(ctx)
        if (items.isEmpty()) return "Koi scheduled task nahi hai."
        return items.take(10).joinToString(" ") { e ->
            val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(e.nextAtMs), ZoneId.systemDefault())
            val time = String.format(java.util.Locale.ROOT, "%02d:%02d", t.hour, t.minute)
            "ID ${e.id.take(8)}: ${e.spec.label.ifBlank { e.spec.kind.name }}${if (e.spec.prio > 0) " (" + ScheduledTaskRules.prioText(e.spec.prio) + ")" else ""}${if (e.spec.note.isNotBlank()) " [" + e.spec.note + "]" else ""} $time ${if (e.spec.days != 0) ScheduledTaskRules.dayText(e.spec.days) + (if (e.spec.repeatDaily) " har hafte" else "") else if (e.spec.repeatDaily) "roz" else "ek baar"}."
        }
    }

    private fun pending(ctx: Context, e: Entry): PendingIntent = PendingIntent.getBroadcast(
        ctx, e.id.hashCode(), Intent(ctx, ScheduledTaskReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, e.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** v6.40: a second alarm LEAD_MIN minutes before a reminder (the "25 minute pehle" heads-up / morning wake alarm). */
    private fun leadPending(ctx: Context, id: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, id.hashCode() + 1, Intent(ctx, ScheduledTaskReceiver::class.java).setAction(ACTION_LEAD).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleLead(ctx: Context, e: Entry) {
        if (e.spec.kind != ScheduledTaskRules.Kind.REMINDER) return
        val at = e.nextAtMs - LEAD_MIN * 60_000L
        if (at <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, leadPending(ctx, e.id))
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, leadPending(ctx, e.id))
        } catch (ignored: Exception) { ErrLog.add("ScheduledTaskStore", ignored.toString()) }
    }

    private fun scheduleAlarm(ctx: Context, e: Entry): Boolean {
        scheduleLead(ctx, e)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return try {
            val pi = pending(ctx, e)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.nextAtMs, pi)
                false
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.nextAtMs, pi)
                true
            }
        } catch (ignored: SecurityException) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.nextAtMs, pending(ctx, e)) } catch (ignored: Exception) { ErrLog.add("ScheduledTaskStore", ignored.toString()) }
            false
        } catch (ignored: Exception) { false }
    }

    private fun cancelAlarm(ctx: Context, id: String) {
        try { ctx.getSystemService(AlarmManager::class.java)?.cancel(leadPending(ctx, id)) } catch (ignored: Exception) { }
        try { ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, Entry(id, ScheduledTaskRules.Spec(0, 0, false, ScheduledTaskRules.Kind.REMINDER, ""), 0))) } catch (ignored: Exception) { }
    }

    const val LEAD_MIN = 25
    const val ACTION_LEAD = "com.nova.assistant.SCHEDULED_TASK_LEAD"
    const val ACTION_FIRE = "com.nova.assistant.SCHEDULED_TASK_FIRE"
    const val EXTRA_ID = "task_id"
}
