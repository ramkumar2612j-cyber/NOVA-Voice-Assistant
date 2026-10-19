package com.nova.assistant

import android.app.Application
import android.content.Context
import java.io.File

/**
 * v6.24: a tiny on-device black box, so a report can SHOW why Phone Control (Accessibility) is "ON but not connected".
 *  - last_crash.txt: the last uncaught exception (thread, class, a masked short message, top 10 frames). Written by the crash handler, then
 *    the normal crash handling continues unchanged.
 *  - acc_events.txt: the last 8 Accessibility service events (connected / unbound / destroyed) with times.
 * Nothing is uploaded or sent anywhere. Messages go through ErrLog.clean (key-like tokens become ***). Audio, screen text and chats are never stored.
 */
object CrashNote {
    private const val CRASH = "last_crash.txt"
    private const val EVENTS = "acc_events.txt"
    const val MAX_EVENTS = 8

    fun install(app: Application) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { File(app.filesDir, CRASH).writeText(format(t.name, e, System.currentTimeMillis())) } catch (x: Throwable) { ErrLog.add("CrashNote", x.toString()) }
            try { CrashGuardStore.recordCrash(app) } catch (x: Throwable) { }   // v7.69 PART 18: safe mode after repeated crashes
            if (prev != null) prev.uncaughtException(t, e)
        }
    }

    /** Pure and testable: "at=<ms> thread=<name>" then "<Class>: <masked message>" then up to 10 "at ..." frames. */
    fun format(thread: String, e: Throwable, now: Long): String {
        val sb = StringBuilder()
        sb.append("at=").append(now).append(" thread=").append(ErrLog.clean(thread).take(30)).append('\n')
        sb.append(e.javaClass.name).append(": ").append(ErrLog.clean(e.message ?: "")).append('\n')
        for (f in e.stackTrace.take(10)) sb.append("  at ").append(f.className).append('.').append(f.methodName)
            .append('(').append(f.fileName ?: "?").append(':').append(f.lineNumber).append(")\n")
        return sb.toString()
    }

    /** Keeps the newest [max] lines. Pure. */
    fun trimLines(old: String, add: String, max: Int = MAX_EVENTS): String {
        val lines = ArrayList<String>()
        for (l in old.split('\n')) if (l.isNotBlank()) lines.add(l)
        lines.add(add)
        while (lines.size > max) lines.removeAt(0)
        return lines.joinToString("\n") + "\n"
    }

    fun event(ctx: Context, what: String, now: Long = System.currentTimeMillis()) {
        try {
            val f = File(ctx.applicationContext.filesDir, EVENTS)
            val old = if (f.exists()) f.readText() else ""
            f.writeText(trimLines(old, now.toString() + " " + ErrLog.clean(what).take(40)))
        } catch (e: Throwable) {
            // best effort only
        }
    }

    /** Text block for the self-test report. */
    fun report(ctx: Context, now: Long = System.currentTimeMillis()): String {
        val sb = StringBuilder()
        try {
            val c = File(ctx.applicationContext.filesDir, CRASH)
            if (c.exists()) {
                val txt = c.readText()
                val at = Regex("^at=(\\d+)").find(txt)?.groupValues?.get(1)?.toLongOrNull()
                sb.append("LAST CRASH (saved on the phone, ").append(if (at != null) ErrLog.ago(now, at) else "time unknown").append("):\n").append(txt.take(1200))
            } else sb.append("LAST CRASH: none saved\n")
            val ev = File(ctx.applicationContext.filesDir, EVENTS)
            if (ev.exists()) {
                sb.append("ACCESSIBILITY EVENTS (oldest first):\n")
                for (l in ev.readText().split('\n')) {
                    if (l.isBlank()) continue
                    val ms = l.substringBefore(' ').toLongOrNull()
                    sb.append("- ").append(if (ms != null) ErrLog.ago(now, ms) else "?").append(" | ").append(l.substringAfter(' ')).append('\n')
                }
            } else sb.append("ACCESSIBILITY EVENTS: none yet (Android never connected the service since this install)\n")
        } catch (e: Throwable) {
            sb.append("CRASH NOTE: could not be read\n")
        }
        return sb.toString()
    }
}

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashNote.install(this)
    }
}
