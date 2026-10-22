package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v7.110 PART 7: the "BADA BRAIN" card. Shows which bigger offline brains this phone can run and downloads ONE only when the
 * owner taps it (never by itself). Decisions live in BrainChoiceRules (unit tested); the download is the existing verified
 * OfflinePackStore.installOne (resumable, size + SHA-256 checked, wrong file deleted).
 * Evidence level: written + statically checked. NOT compiled here, NOT run on a phone.
 */
object BrainChoice {
    private val running = AtomicBoolean(false)
    private val cancel = AtomicBoolean(false)
    @Volatile private var progress = 0
    @Volatile private var activeId = ""
    @Volatile private var lastMsg = ""

    private fun totalRamMb(ctx: Context): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return 0L
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024L * 1024L)
    }

    private fun online(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Throwable) { false }

    private fun unmetered(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } catch (e: Throwable) { false }

    /** JSON for the card: {ram, headline, busy, id, pct, msg, options:[{id,label,mb,state,rec,note}]}. Never throws. */
    fun stateJson(ctx: Context): String = try {
        val app = ctx.applicationContext
        val ram = totalRamMb(app)
        val installed = OfflinePackStore.installed(app).keys
        val arr = JSONArray()
        for (o in BrainChoiceRules.options(ram, OfflinePackStore.freeBytes(app), installed)) {
            arr.put(JSONObject().put("id", o.id).put("label", o.label).put("mb", o.approxMb).put("state", o.state.name)
                .put("rec", o.recommended).put("note", o.note))
        }
        JSONObject().put("ram", ram).put("headline", BrainChoiceRules.headline(ram, installed))
            .put("busy", running.get()).put("id", activeId).put("pct", progress).put("msg", lastMsg).put("options", arr).toString()
    } catch (e: Throwable) {
        ErrLog.add("BrainChoice", e.toString())
        "{}"
    }

    /**
     * Called ONLY by the owner's tap in the app (bridge). Returns "started" or a short reason. The download runs on a worker thread.
     * [done] is called on that thread when it ends (ok, reason).
     */
    fun start(ctx: Context, id: String, done: (Boolean, String) -> Unit): String {
        val app = ctx.applicationContext
        if (!BrainChoiceRules.isOfferedId(id)) return "unknown brain"
        val m = OfflinePack.byId(id) ?: return "unknown brain"
        val reason = BrainChoiceRules.refusal(id, true, totalRamMb(app), OfflinePackStore.freeBytes(app), unmetered(app), online(app), OfflinePackStore.installed(app).keys)
        if (reason != null) return reason
        if (!running.compareAndSet(false, true)) return "another download is running"
        cancel.set(false); progress = 0; activeId = id; lastMsg = "downloading " + m.label
        Thread({
            var ok = false
            var msg = ""
            try {
                if (LocalBrains.factory == null) LocalBrains.installEngine(app)
                val err = OfflinePackStore.installOne(app, m, { cancel.get() }, { p -> progress = p.coerceIn(0, 100) })
                ok = err == null
                msg = if (ok) m.label + " installed and verified" else if (cancel.get()) "cancelled" else err ?: "failed"
                if (ok) LocalBrains.release()   // next answer loads the new brain first (tryOrder)
            } catch (t: Throwable) {
                ErrLog.add("BrainChoice", t.toString())
                msg = "failed"
            } finally {
                lastMsg = msg
                activeId = ""
                running.set(false)
                try { done(ok, msg) } catch (ignored: Throwable) { }
            }
        }, "nova-brain-choice").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        return "started"
    }

    fun cancel() { cancel.set(true) }
}
