package com.nova.assistant

import android.content.Context

/**
 * Message Mode is independent from Driving. Its ON flag and app choices persist across restart;
 * notification text never does. A mode may read only the app allow-list and only when the owner
 * has granted Android Notification Access. Banks, payments, OTP and mail are never in this list.
 */
object MessageMode {
    private const val PREFS = "nova_message_mode"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_APPS = "selected_apps"
    @Volatile var enabled: Boolean = false
        private set

    /** Memory-only: cleared when mode is turned off and when the service stops. */
    val inbox = Driving.Inbox()

    // Only apps with message-notification support. Mail, banks and payment apps are intentionally absent.
    val APP_LABELS: Map<String, String> = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.apps.messaging" to "Google Messages",
        "com.samsung.android.messaging" to "Samsung Messages",
        "org.thoughtcrime.securesms" to "Signal",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
        "com.twitter.android" to "X / Twitter",
        "com.linkedin.android" to "LinkedIn"
    )
    private val DEFAULT_APPS = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "com.instagram.android", "org.telegram.messenger",
        "com.google.android.apps.messaging", "com.samsung.android.messaging"
    )
    private const val KEY_V652 = "defaults_v652"

    fun load(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // v6.52 (owner request): Message Mode is ON by default and Instagram is a default app. Done once; an explicit OFF later is respected.
        if (!p.getBoolean(KEY_V652, false)) {
            val e = p.edit().putBoolean(KEY_V652, true)
            if (!p.contains(KEY_ENABLED)) e.putBoolean(KEY_ENABLED, true)
            val saved = p.getStringSet(KEY_APPS, null)
            e.putStringSet(KEY_APPS, if (saved == null) DEFAULT_APPS else saved.toSet() + "com.instagram.android")
            e.apply()
        }
        enabled = p.getBoolean(KEY_ENABLED, true)
        if (!p.contains(KEY_APPS)) p.edit().putStringSet(KEY_APPS, DEFAULT_APPS).apply()
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, on).commit()
        enabled = on
        if (!on) inbox.clear()
    }

    fun selectedApps(ctx: Context): Set<String> {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = p.getStringSet(KEY_APPS, null)
        return (saved ?: DEFAULT_APPS).filter { it in APP_LABELS }.toSet()
    }

    fun isSelected(ctx: Context, pkg: String): Boolean = enabled && pkg in selectedApps(ctx) && pkg in APP_LABELS

    fun setAppSelected(ctx: Context, pkg: String, selected: Boolean): Boolean {
        if (pkg !in APP_LABELS) return false
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = selectedApps(ctx).toMutableSet()
        if (selected) now.add(pkg) else now.remove(pkg)
        return prefs.edit().putStringSet(KEY_APPS, now).commit()
    }

    fun command(n: String, core: String): Logic.Cmd? {
        val words = n.split(" ").filter { it.isNotBlank() }
        if ("message" in words && "mode" in words && words.size <= 6) {
            return Logic.Cmd(if (words.any { it in setOf("off", "stop", "band", "bandh", "disable") }) "message_off" else "message_on")
        }
        val appMatch = Regex("^(?:message|messages) apps? (.+) (on|off)$").find(n)
        if (appMatch != null) {
            val alias = appMatch.groupValues[1].trim()
            val pkg = APP_LABELS.entries.firstOrNull { it.value.lowercase() == alias || it.key.substringAfterLast('.').replace("_", " ") == alias }?.key
                ?: when (alias) {
                    "whatsapp" -> "com.whatsapp"
                    "telegram" -> "org.telegram.messenger"
                    "sms", "messages", "google messages" -> "com.google.android.apps.messaging"
                    "signal" -> "org.thoughtcrime.securesms"
                    "messenger", "facebook messenger" -> "com.facebook.orca"
                    "instagram" -> "com.instagram.android"
                    "x", "twitter" -> "com.twitter.android"
                    "linkedin", "linked in" -> "com.linkedin.android"
                    "whatsapp business" -> "com.whatsapp.w4b"
                    else -> null
                }
            if (pkg != null) return Logic.Cmd("message_app", pkg, if (appMatch.groupValues[2] == "on") 1 else 0)
        }
        if (core in setOf("messages padho", "message padho", "read messages", "read message", "read new messages", "new messages")) return Logic.Cmd("message_read")
        if (core in setOf("reply delete karo", "last reply delete karo", "reply delete", "delete last reply", "jawab delete karo", "reply hatao", "message delete karo", "bheja hua message delete karo", "delete sent reply")) return Logic.Cmd("message_delete")
        if (core in setOf("messages hatao", "clear messages", "clear message", "message clear karo")) return Logic.Cmd("message_clear")
        if (core in setOf("reply likho", "reply write", "jawab likho", "write reply", "custom reply", "reply dictate", "dictate reply")) return Logic.Cmd("message_dictate")
        val m = Regex("^reply (.+)$").find(core)
        if (m != null) Driving.quickId(m.groupValues[1].split(" "))?.let { return Logic.Cmd("message_reply", it) }
        return null
    }
}
