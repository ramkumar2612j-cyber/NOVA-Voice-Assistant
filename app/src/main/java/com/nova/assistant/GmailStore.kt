package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * v6.93: what NOVA keeps for Gmail: the watch list, saved e-mail addresses (name -> address) and the last few mails seen in
 * notifications (sender + subject only, numbers masked). No password, no token, nothing is uploaded.
 */
object GmailStore {
    private const val PREF = "nova_gmail"
    private const val MAX_RECENT = 20
    private const val MAX_WATCH = 20
    const val GMAIL_PKG = "com.google.android.gm"

    class Watch(val name: String, val period: String, val lastSeen: Long)
    class Mail(val sender: String, val subject: String, val at: Long, val important: Boolean)

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun gmailInstalled(ctx: Context): Boolean = try { ctx.packageManager.getPackageInfo(GMAIL_PKG, 0); true } catch (e: Exception) { false }

    // ---- watch list
    fun watches(ctx: Context): List<Watch> = try {
        val a = JSONArray(p(ctx).getString("watch", "[]") ?: "[]")
        List(a.length()) { i -> a.getJSONObject(i).let { Watch(it.getString("n"), it.optString("p", GmailRules.WEEKLY), it.optLong("t", 0L)) } }
    } catch (e: Exception) { emptyList() }

    private fun saveWatches(ctx: Context, list: List<Watch>): Boolean {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("n", it.name).put("p", it.period).put("t", it.lastSeen)) }
        return p(ctx).edit().putString("watch", a.toString()).commit()
    }

    /** false = list full. Same name again just changes its period. */
    fun addWatch(ctx: Context, name0: String, period: String): Boolean {
        val name = name0.trim().lowercase().take(40)
        if (name.isEmpty()) return false
        val cur = watches(ctx)
        val old = cur.firstOrNull { it.name == name }
        if (old == null && cur.size >= MAX_WATCH) return false
        return saveWatches(ctx, cur.filter { it.name != name } + Watch(name, period, old?.lastSeen ?: 0L))
    }

    fun removeWatch(ctx: Context, name0: String): Boolean {
        val name = name0.trim().lowercase()
        val cur = watches(ctx)
        val hit = cur.filter { it.name == name || GmailRules.matchesWatch(name, it.name, "") }
        if (hit.isEmpty()) return false
        return saveWatches(ctx, cur.filter { w -> hit.none { it.name == w.name } })
    }

    fun touchWatch(ctx: Context, name: String, now: Long) {
        saveWatches(ctx, watches(ctx).map { if (it.name == name) Watch(it.name, it.period, now) else it })
    }

    // ---- addresses
    fun aliasOf(ctx: Context, name: String): String {
        val key = name.trim().lowercase()
        return try { JSONObject(p(ctx).getString("alias", "{}") ?: "{}").optString(key, "") } catch (e: Exception) { "" }
    }

    fun setAlias(ctx: Context, name: String, email: String): Boolean {
        val key = name.trim().lowercase().take(30)
        if (key.isEmpty() || !GmailRules.EMAIL_RE.matches(email.trim())) return false
        val o = try { JSONObject(p(ctx).getString("alias", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        if (!o.has(key) && o.length() >= 50) return false
        o.put(key, email.trim().lowercase())
        return p(ctx).edit().putString("alias", o.toString()).commit()
    }

    // ---- recent mails (from notifications)
    fun recent(ctx: Context): List<Mail> = try {
        val a = JSONArray(p(ctx).getString("recent", "[]") ?: "[]")
        List(a.length()) { i -> a.getJSONObject(i).let { Mail(it.optString("s"), it.optString("j"), it.optLong("t"), it.optBoolean("i")) } }
    } catch (e: Exception) { emptyList() }

    /** Adds a mail. Returns false when the same sender + subject was already seen in the last 10 minutes (Gmail re-posts updated notifications). */
    fun addRecent(ctx: Context, sender: String, subject: String, now: Long, important: Boolean): Boolean {
        val s = sender.take(60)
        val j = GmailRules.maskNumbers(subject).take(120)
        val cur = recent(ctx)
        if (cur.any { it.sender == s && it.subject == j && now - it.at < 10L * 60L * 1000L }) return false
        val next = (cur + Mail(s, j, now, important)).takeLast(MAX_RECENT)
        val a = JSONArray()
        next.forEach { a.put(JSONObject().put("s", it.sender).put("j", it.subject).put("t", it.at).put("i", it.important)) }
        p(ctx).edit().putString("recent", a.toString()).commit()
        return true
    }

    /** One phone notification (works even when NOVA is not talking). */
    fun notifyPhone(ctx: Context, title: String, message: String) {
        try {
            val app = ctx.applicationContext
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel("nova_gmail", "NOVA Gmail", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val intent = android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (android.os.Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0
            val pi = android.app.PendingIntent.getActivity(app, 7420, intent, flags)
            val note = androidx.core.app.NotificationCompat.Builder(app, "nova_gmail")
                .setSmallIcon(com.nova.assistant.R.mipmap.ic_launcher).setContentTitle(title.take(80))
                .setContentText(message.take(180)).setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message.take(500)))
                .setContentIntent(pi).setAutoCancel(true).build()
            nm.notify((System.currentTimeMillis() % 1000000).toInt() + 1, note)
        } catch (e: Exception) { /* notifications can be denied by the owner */ }
    }
}
