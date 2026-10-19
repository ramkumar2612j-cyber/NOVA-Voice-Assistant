package com.nova.assistant

import android.content.Context

/**
 * v7.69 PART 18 HANG PROTECTION: SAFE MODE after repeated crashes. Pure rules in [CrashGuard] (JUnit can run them), tiny prefs store in [CrashGuardStore].
 * 2 crashes inside 10 minutes => safe mode for 30 minutes: the page is forced to light effects, the cosmetic full-screen laser overlay is skipped and a banner
 * with a "SAFE MODE BAND KARO" button is shown. Safe mode never switches a feature off for good and never touches calls, messages or the owner's data.
 */
object CrashGuard {
    const val WINDOW_MS = 10 * 60_000L
    const val THRESHOLD = 2
    const val SAFE_HOLD_MS = 30 * 60_000L
    const val MAX_KEEP = 6

    /** Adds one crash time; keeps only the newest [MAX_KEEP]; ignores times from the future (clock change). */
    fun add(old: List<Long>, now: Long): List<Long> = (old.filter { it in 1..now } + now).takeLast(MAX_KEEP)

    fun recent(crashes: List<Long>, now: Long): Int = crashes.count { it in 1..now && now - it <= WINDOW_MS }

    /** New "safe until" time after a crash at [now]: now + 30 min if the window holds THRESHOLD crashes, else the old value. */
    fun safeUntil(crashes: List<Long>, now: Long, oldUntil: Long): Long =
        if (recent(crashes, now) >= THRESHOLD) now + SAFE_HOLD_MS else oldUntil

    /** Safe while the clock is before the end time, and the end time is not absurdly far away (clock moved back). */
    fun isSafe(until: Long, now: Long): Boolean = until > now && until - now <= SAFE_HOLD_MS

    fun encode(l: List<Long>): String = l.joinToString(",")
    fun decode(s: String?): List<Long> = (s ?: "").split(',').mapNotNull { it.trim().toLongOrNull() }

    /** Page script: light effects at once + the banner hook (the page script pt18_safe draws the banner itself). */
    const val SAFE_JS = "window.__forceLow=true;document.documentElement.classList.add('lowfx')"
}

object CrashGuardStore {
    private const val PREF = "nova_crashguard"
    private const val K_LIST = "crashes"
    private const val K_UNTIL = "safe_until"

    /** Called from the crash handler: must be fast and must not throw. commit() because the process is about to die. */
    fun recordCrash(ctx: Context, now: Long = System.currentTimeMillis()) {
        try {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val list = CrashGuard.add(CrashGuard.decode(p.getString(K_LIST, "")), now)
            val until = CrashGuard.safeUntil(list, now, p.getLong(K_UNTIL, 0L))
            p.edit().putString(K_LIST, CrashGuard.encode(list)).putLong(K_UNTIL, until).commit()
        } catch (e: Throwable) { }
    }

    fun isSafe(ctx: Context, now: Long = System.currentTimeMillis()): Boolean = try {
        CrashGuard.isSafe(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(K_UNTIL, 0L), now)
    } catch (e: Throwable) { false }

    /** Owner pressed "SAFE MODE BAND KARO": leave safe mode and forget the old crash times, so one new crash does not bring it straight back. */
    fun exit(ctx: Context) {
        try { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong(K_UNTIL, 0L).putString(K_LIST, "").apply() }
        catch (e: Throwable) { ErrLog.add("CrashGuard", e.toString()) }
    }
}
