package com.nova.assistant

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * v34 PART 3: ONE silent background runner for everything NOVA can prepare by itself once the owner has pressed "allow":
 *  1. the verified offline Qwen model when the device has enough RAM/storage,
 *  2. a tiny health check of every online AI provider that has a key (Groq, Qwen via Groq, OpenRouter).
 * No pop-up, nothing spoken, no provider name in a chat reply. Results are only SELF TEST lines (Settings).
 * Retries use AutoSetupPlan's back-off. It cannot switch on Accessibility / Notification access / Overlay: Android forbids that.
 */
object AutoSetup {
    private val started = AtomicBoolean(false)
    @Volatile private var stopFlag = false
    private val lines = ArrayList<String>()

    private fun add(l: String) {
        synchronized(lines) {
            lines.add(l)
            while (lines.size > 40) lines.removeAt(0)
        }
    }

    /** Lines written by the background runner so far. */
    fun log(): List<String> = synchronized(lines) { ArrayList(lines) }

    /** Called when the mic service is running (permissions given). Safe to call again: only one runner exists. */
    fun start(ctx: Context) {
        startQwen(ctx.applicationContext)   // v6.33: the offline brain has its own runner, it no longer waits behind the other tasks
        startPack(ctx.applicationContext)   // v6.96: five more offline AI models, one after another, Wi-Fi only
        if (!started.compareAndSet(false, true)) { stopFlag = false; return }   // already running (maybe about to stop): keep it
        stopFlag = false
        val app = ctx.applicationContext
        Thread({
            try {
                loop(app)
            } catch (e: Throwable) {
                add("WARN Auto setup ruk gaya - " + e.javaClass.simpleName)
            } finally {
                started.set(false)
            }
        }, "nova-autosetup").apply { isDaemon = true }.start()
    }

    fun stop() { stopFlag = true }

    private fun loop(app: Context) {
        var tasks = AutoSetupPlan.initial()
        var hindiAttempted = false
        while (!stopFlag) {
            val now = System.currentTimeMillis()
            val t = AutoSetupPlan.due(tasks, now)
            if (t == null) {
                // The provider task repeats every 6 hours, so code after loop(app) would never run while the service stays alive.
                // Try the optional Hindi model once after the initial voice/provider setup pass, before waiting for the next task.
                if (!hindiAttempted) {
                    hindiAttempted = true
                    if (!stopFlag && ExtCfg.autoModels(app) && !HindiPack.ready(app)) {
                        val herr = HindiPack.ensure(app)
                        if (herr == null) add("READY Hindi voice pack")
                        else if (herr != "wait" && herr != "busy") add("WARN Hindi voice pack - $herr, agli baar koshish hogi")
                    }
                }
                val next = AutoSetupPlan.nextWake(tasks) ?: return
                try { Thread.sleep(minOf(maxOf(next - now, 1000L), 60_000L)) } catch (e: InterruptedException) { return }
                continue
            }
            val o = try {
                when (t.id) {
                    AutoSetupPlan.PROVIDERS -> providersTask(app)
                    else -> AutoSetupPlan.Outcome.FATAL
                }
            } catch (e: Throwable) { AutoSetupPlan.Outcome.RETRY }
            val after = AutoSetupPlan.apply(t, o, System.currentTimeMillis())
            tasks = tasks.map { if (it.id == t.id) after else it }
        }
    }

    // ---- v6.33: offline Qwen brain, downloaded automatically as soon as the app opens (own thread, retries by itself, resumes the .part file)
    private val qwenStarted = AtomicBoolean(false)

    private fun online(app: Context): Boolean = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Throwable) { true }

    private fun startQwen(app: Context) {
        if (!ExtCfg.autoModels(app)) return   // v7.130: the AUTO DOWNLOAD switch also stops the offline brain download/load (it used to ignore the switch and could slow the phone)
        if (!qwenStarted.compareAndSet(false, true)) return
        Thread({
            try {
                qwenLoop(app)
            } catch (e: Throwable) {
                add("WARN Offline Qwen runner ruk gaya - " + e.javaClass.simpleName)
            } finally {
                qwenStarted.set(false)
            }
        }, "nova-qwen-auto").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun qwenLoop(app: Context) {
        try { Thread.sleep(9000L) } catch (e: InterruptedException) { return }   // let the first screen and intro finish before any disk / network work
        var attempt = 0
        var refused = 0
        while (true) {
            if (UpdateManager.activeModelPath(app).isNotEmpty()) {
                add("READY Offline Qwen")
                LocalBrains.warmUp(app)
                return
            }
            if (!ModelCatalog.ramOk(totalRamMb(app))) { add("SKIP Offline Qwen - RAM too low"); return }
            if (LocalBrains.factory == null) LocalBrains.installEngine(app)
            if (online(app)) {
                val r = ModelAuto.silentOfflineQwen(app)
                when {
                    r == null -> {
                        add("READY Offline Qwen - downloaded and verified, offline ON")
                        LocalBrains.warmUp(app)
                        return
                    }
                    r == "ram-low" || r == "engine-unavailable" -> { add("SKIP Offline Qwen - $r"); return }
                    r == "busy" -> { /* the manual DOWNLOAD button is running it: just look again soon */ }
                    else -> {
                        add("WARN Offline Qwen - $r, dobara koshish hogi")
                        // A wrong file is deleted by the safety check; do not download 491 MB again and again.
                        if ((r ?: "").contains("refused") || (r ?: "").contains("checksum")) { if (++refused >= 2) { add("FAIL Offline Qwen - file verify nahi hui, band"); return } }
                        attempt++
                    }
                }
            }
            val wait = if (!online(app)) 20_000L else minOf(15_000L shl minOf(attempt, 7), 30L * 60_000L)
            try { Thread.sleep(wait) } catch (e: InterruptedException) { return }
        }
    }

    // ---- v6.96: five extra offline AI models (3 Chinese, 2 world). No key, no account. Downloaded by themselves after install.
    private val packStarted = AtomicBoolean(false)

    private fun unmetered(app: Context): Boolean = try {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } catch (e: Throwable) { false }

    private fun startPack(app: Context) {
        if (!ExtCfg.autoModels(app)) return
        if (!HeavyGate.charging(app)) return   // wip15: the five extra offline models only start by themselves while charging
        if (!packStarted.compareAndSet(false, true)) return
        Thread({
            try {
                packLoop(app)
            } catch (e: Throwable) {
                add("WARN Offline AI pack runner ruk gaya - " + e.javaClass.simpleName)
            } finally {
                packStarted.set(false)
            }
        }, "nova-pack-auto").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private fun packLoop(app: Context) {
        try { Thread.sleep(20_000L) } catch (e: InterruptedException) { return }
        if (LocalBrains.factory == null) LocalBrains.installEngine(app)
        val ram = totalRamMb(app)
        if (OfflinePack.runnable(ram).isEmpty()) { add("SKIP Offline AI pack - RAM too low"); return }
        val skipped = HashSet<String>()      // models whose file was refused or whose repo has no usable file: not tried again this run
        val failCount = HashMap<String, Int>()
        var announced = false
        while (true) {
            // the built-in 0.5B model downloads first; do not fight it for bandwidth
            if (qwenStarted.get()) { try { Thread.sleep(30_000L) } catch (e: InterruptedException) { return }; continue }
            val installed = OfflinePackStore.installed(app).keys
            val todo = OfflinePack.toDownload(installed, skipped, ram, OfflinePackStore.freeBytes(app), unmetered(app), online(app))
            val remaining = OfflinePack.runnable(ram).filter { it.id !in installed && it.id !in skipped }
            if (remaining.isEmpty()) {
                add(OfflinePack.statusLine(installed, ram))
                return
            }
            if (todo.isEmpty()) {
                if (!announced) { announced = true; add("WAIT Offline AI pack - Wi-Fi / storage / internet ka intezaar") }
                try { Thread.sleep(2 * 60_000L) } catch (e: InterruptedException) { return }
                continue
            }
            val m = todo[0]
            val err = OfflinePackStore.installOne(app, m)
            if (err == null) {
                add("READY Offline AI " + m.label + " (" + m.maker + ") - downloaded and verified")
                failCount.remove(m.id)
            } else if (err.startsWith("refused")) {
                add("FAIL Offline AI " + m.label + " - " + err + ", skip")
                skipped.add(m.id)
            } else {
                val n = (failCount[m.id] ?: 0) + 1
                failCount[m.id] = n
                add("WARN Offline AI " + m.label + " - " + err + ", dobara koshish hogi")
                if (n >= 6) { add("FAIL Offline AI " + m.label + " - 6 koshish ke baad skip"); skipped.add(m.id) }
                try { Thread.sleep(minOf(30_000L shl minOf(n, 6), 30L * 60_000L)) } catch (e: InterruptedException) { return }
            }
        }
    }

    private fun totalRamMb(ctx: Context): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return 0L
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024L * 1024L)
    }

    /** Providers that have the key they need. Pollinations (no key) is left out: it is not a setup item. */
    private fun keyOf(ctx: Context, p: FreeProvider): String = when (p.keyName) {
        "groq" -> SecureStore.getGroq(ctx)
        "openrouter" -> SecureStore.getSlot(ctx, "openrouter")
        "zai" -> SecureStore.getSlot(ctx, "zai")
        "zai2" -> SecureStore.getSlot(ctx, "zai2")
        "zai3" -> SecureStore.getSlot(ctx, "zai3")
        else -> ""
    }.trim()

    /** One tiny request per keyed provider (first model only). Returns SELF TEST lines. Network: worker thread only. */
    fun checkProviders(ctx: Context): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        for (p in FreeProviders.ordered()) {
            if (p.keyName == null) continue
            val key = keyOf(ctx, p)
            if (key.isEmpty()) continue
            val r = FreeHttp.post(p, FreeProviders.buildBody(p.models[0], "Reply with one word.", "ok", 16), key)
            out.add(Pair(p.label + " (" + p.models[0] + ")", r.code))
        }
        return out
    }

    private fun providersTask(app: Context): AutoSetupPlan.Outcome {
        val res = checkProviders(app)
        if (res.isEmpty()) return AutoSetupPlan.Outcome.SKIP
        var worst = AutoSetupPlan.Outcome.OK
        for ((label, code) in res) {
            add(AutoSetupPlan.line(label, code))
            if (AutoSetupPlan.outcomeFor(code) == AutoSetupPlan.Outcome.RETRY) worst = AutoSetupPlan.Outcome.RETRY
        }
        return worst
    }
}
