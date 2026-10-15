package com.nova.assistant

import android.content.Context
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connects the Layer-2 slot to the model file the updater installed (UpdateManager.activeModelPath).
 * The engine is LlamaBrain (llama.cpp). [installEngine] sets [factory]; the model is only loaded when a model file
 * is installed AND a request needs it. If the native library or the file fails, [active] returns null and NOVA
 * behaves exactly as without a model.
 *
 * Only ONE request runs at a time ([busy]): a timed-out native call may still be running, so a second request is
 * refused instead of queued (no pile-up, no double RAM use).
 */
object LocalBrains {
    /** An engine integration sets this: model file path -> a LocalBrain, or null if the file cannot be loaded. */
    @Volatile var factory: ((String) -> LocalBrain?)? = null

    /** Install the real GGUF engine used by the offline Qwen model. */
    fun installEngine(ctx: Context) {
        factory = { path ->
            try { LlamaBrain(ctx.applicationContext, path) } catch (ignored: Throwable) { null }
        }
    }

    private val lock = Any()
    private var cached: LocalBrain? = null
    private var cachedPath = ""
    private val busy = AtomicBoolean(false)
    private val pool = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-localbrain").also { it.isDaemon = true } }

    /** true when an engine exists AND a model file is installed (cheap check, loads nothing). */
    fun available(ctx: Context): Boolean = factory != null && (UpdateManager.activeModelPath(ctx).isNotEmpty() || OfflinePackStore.any(ctx))

    fun active(ctx: Context): LocalBrain? = synchronized(lock) {
        val path = UpdateManager.activeModelPath(ctx)
        if (path.isEmpty()) { dropLocked(); return@synchronized null }
        brainForLocked(path)
    }

    /** v6.96: the brain for ONE model file. Only one model is ever in RAM: asking for another file unloads the previous one. */
    private fun brainForLocked(path: String): LocalBrain? {
        val f = factory ?: return null
        if (path != cachedPath || cached == null) {
            dropLocked()
            cached = try { f(path) } catch (t: Throwable) { null }
            cachedPath = path
        }
        val c = cached
        return if (c != null && c.ready) c else null
    }

    private fun brainFor(path: String): LocalBrain? = synchronized(lock) { brainForLocked(path) }

    /** Try-order for answers: the built-in Qwen 0.5B slot first, then the pack models (template per model). */
    private class Candidate(val path: String, val template: String)

    private fun candidates(ctx: Context): List<Candidate> {
        val out = ArrayList<Candidate>()
        val primary = UpdateManager.activeModelPath(ctx)
        if (primary.isNotEmpty()) out.add(Candidate(primary, OfflinePack.T_CHATML))
        for ((m, p) in OfflinePack.tryOrder(OfflinePackStore.installed(ctx))) out.add(Candidate(p, m.template))
        return out
    }

    /** At most this many models are tried for one answer, so a failing phone is not stuck loading models for minutes. */
    private const val MAX_TRIES = 3

    private fun dropLocked() {
        val old = cached
        cached = null
        cachedPath = ""
        if (old != null) try { old.close() } catch (t: Throwable) { }
    }

    private val warming = AtomicBoolean(false)

    /**
     * v6.33: loads the offline model in the background (low priority) right after it is installed / at app start, so the first
     * offline answer is instant instead of waiting up to 45 s for the model to load. Needs >= 4 GB RAM; below that it loads on first use.
     */
    fun warmUp(ctx: Context) {
        val app = ctx.applicationContext
        if (factory == null || !available(app)) return
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        if (mi.totalMem / (1024L * 1024L) < 4000L) return
        if (!warming.compareAndSet(false, true)) return
        Thread({
            try { active(app) } catch (ignored: Throwable) { ErrLog.add("LocalBrains", ignored.toString()) } finally { warming.set(false) }
        }, "nova-localbrain-warm").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    /** true when a model is already in RAM: answering with it costs no load time. */
    fun isLoaded(): Boolean = synchronized(lock) { cached != null }

    /** wip11: true when at least one offline model file is installed (the AI picker shows the PHONE AI option as ready). */
    fun hasInstalled(ctx: Context): Boolean = try { candidates(ctx).isNotEmpty() } catch (e: Throwable) { false }

    /** Unloads the model (low memory / service stopping). The next request loads it again. */
    fun release() = synchronized(lock) { dropLocked() }

    /** Runs [prompt] with a time limit. null = refused (busy), failed or too slow. */
    private fun run(brain: LocalBrain, prompt: String, timeoutMs: Long): String? {
        if (!busy.compareAndSet(false, true)) return null
        val job = pool.submit(Callable<String?> {
            try { brain.complete(prompt) } finally { busy.set(false) }
        })
        return try {
            job.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            job.cancel(true)
            null
        }
    }

    /**
     * One safe command sentence for [text], or null (no model, too slow, failed, or the answer was refused).
     * Only ONE model is asked (the first installed one): this runs inside a voice command, so no cascade of model loads.
     */
    fun ask(ctx: Context, text: String): String? {
        val c = candidates(ctx).firstOrNull() ?: return null
        val brain = brainFor(c.path) ?: return null
        val raw = run(brain, LocalBrainRules.buildPrompt(text), LocalBrainRules.TIMEOUT_MS)
        return LocalBrainRules.parseOutput(PackText.clean(raw))
    }

    /**
     * A short TEXT answer (never an action) for [text], or null. [en] = answer language for the safety line.
     * v6.96: if the first model fails (cannot load, timeout, empty or looping text) the next installed pack model is tried,
     * at most [MAX_TRIES]. A busy engine or a safety refusal never starts a cascade.
     */
    fun chat(ctx: Context, text: String, en: Boolean, facts: List<String> = emptyList(), earlier: List<String> = emptyList(), timeoutMs: Long = LocalChat.TIMEOUT_MS): String? {
        var tries = 0
        for (c in candidates(ctx)) {
            if (tries >= MAX_TRIES) break
            if (busy.get()) return null                 // a previous (maybe timed-out) native call is still running
            tries++
            val brain = brainFor(c.path) ?: continue
            val prompt = PackPrompt.convert(LocalChat.buildPrompt(text, facts, earlier), c.template)
            val raw = run(brain, prompt, timeoutMs)
            val clean = LocalChat.clean(PackText.clean(raw)) ?: continue
            return LocalChat.guard(text, clean, en)
        }
        return null
    }
}
