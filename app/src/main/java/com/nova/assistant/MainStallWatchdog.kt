package com.nova.assistant

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v7.146 STEP 6 OF 10: ANR watchdog. A daemon thread posts a tiny task to the main thread every second. If the main thread has not run it
 * for [StallRules.STALL_MS] the thread copies the main thread's stack (class and method names only), keeps one short line in a ring of 10
 * and adds one line to the in-memory ErrLog (so the existing Settings error-log COPY button shows it). It records only: it never interrupts,
 * kills or restarts anything, and every step is wrapped so the watchdog itself can never crash the app.
 */
object MainStallWatchdog {
    private const val PREF = "nova_stall"
    private const val KEY = "list"
    private val started = AtomicBoolean(false)
    @Volatile private var lastAck = 0L

    fun start(app: Application) {
        if (!started.compareAndSet(false, true)) return
        try {
            val main = Handler(Looper.getMainLooper())
            val mainThread = Looper.getMainLooper().thread
            lastAck = SystemClock.uptimeMillis()
            val t = Thread({
                var reportedFor = 0L
                while (true) {
                    try {
                        main.post { lastAck = SystemClock.uptimeMillis() }
                        val s0 = SystemClock.elapsedRealtime()
                        Thread.sleep(StallRules.CHECK_MS)
                        val slept = SystemClock.elapsedRealtime() - s0
                        val ack = lastAck
                        val behind = SystemClock.uptimeMillis() - ack
                        if (!StallRules.wasFrozen(slept) && StallRules.isStall(behind) && ack != reportedFor) {
                            reportedFor = ack
                            record(app, behind, mainThread.stackTrace.map { it.toString() })
                        }
                    } catch (e: InterruptedException) {
                        return@Thread
                    } catch (e: Throwable) {
                        // the watchdog must never take the app down
                    }
                }
            }, "nova-stall-watch")
            t.isDaemon = true
            t.start()
        } catch (e: Throwable) {
            started.set(false)
        }
    }

    private fun record(ctx: Context, behindMs: Long, frames: List<String>) {
        val top = StallRules.topFrame(frames)
        val entry = StallRules.Entry(System.currentTimeMillis(), behindMs, top)
        try {
            val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val now = StallRules.append(StallRules.decodeAll(sp.getString(KEY, "") ?: ""), entry)
            sp.edit().putString(KEY, StallRules.encodeAll(now)).apply()
        } catch (e: Throwable) { }
        try { ErrLog.add("hang", "main thread stuck ~" + behindMs + "ms at " + StallRules.shortName(top)) } catch (e: Throwable) { }
    }

    /** Read-only: the saved lines. */
    fun read(ctx: Context): List<StallRules.Entry> = try {
        StallRules.decodeAll(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "") ?: "")
    } catch (e: Throwable) { emptyList() }
}
