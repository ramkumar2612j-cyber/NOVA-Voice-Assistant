package com.nova.assistant

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * v7.53 PART 13B-1 - ANALYSIS PIPELINE (Android side): INTAKE -> FRAMES -> VISION -> SEARCH -> COMPOSE -> DONE / FAILED. (v7.58 13C added SEARCH + COMPOSE: rules in AnalysisSearchRules.)
 * All wording, parsing and safety decisions come from [AnalysisRules] and [AnalysisPipelineRules] (pure, JUnit). This file only moves data.
 *
 *  - ONE separate background thread (single thread executor). Never the main thread. A generation number ends an old run; its late result is thrown away.
 *  - Own watchdog 120 s ([AnalysisPipelineRules.WATCHDOG_MS]); cancel() (owner ROKO, STOP via NovaService.softStop / HUD onStop) ends it at once.
 *  - INTAKE: the owner's question is cleaned and gated BEFORE any network use (AnalysisRules.gateQuestion). Refused = FAILED + honest text, nothing is sent.
 *  - FRAMES: uses the frames FrameExtractor already kept, or starts FrameExtractor and waits for its real done / total (polled; its onStage is NOT touched).
 *  - VISION: frames go to Gemini as inlineData (image/jpeg base64) through [Brain.call] = NovaService.analysisCall (the existing key / model / quota path, no new HTTP client).
 *    Answer: AnalysisRules.parseVision (strict JSON, one retry). The model's safety flag refuses. The model can never put a URL into the report.
 *  - No key / offline / limit / vision failed: AnalysisRules.metadataReport (honest, nothing invented).
 *  - Result (report JSON + chat text) is kept in RAM only (last few). No path ever leaves Kotlin.
 *  - Progress: real [AnalysisStage] events (done / total of that stage). Part 14 (scan) and 15 (cards) use the same events.
 */
object AnalysisPipeline {
    /** What the pipeline needs from NovaService. Implemented there (NovaService.analysisBrain). */
    interface Brain {
        fun en(): Boolean
        /** "" = Gemini can be used now, else "NOKEY" or "LIMIT" (cooling down). */
        fun blocked(): String
        /** One Gemini generateContent call. (code, answer text); code 598 / 599 = offline / timeout, 0 = no key. Worker thread only. */
        fun call(json: String): Pair<Int, String>
        /** v7.54 13B-2a: a free vision provider (Groq / OpenRouter) can be asked now (switch on, key set, not cooling). Default: no. */
        fun freeVisionReady(): Boolean = false
        /** v7.54 13B-2a: the frames (at most FreeVision.FRAMES_PER_CALL, image/jpeg base64) through the free vision chain. (code, answer text) like [call]. Worker thread only. */
        fun callFree(system: String, userText: String, framesB64: List<String>, stop: () -> Boolean = { false }): Pair<Int, String> = Pair(0, "")
        /** v7.58 13C: one Gemini call that returns the RAW body (grounded search needs groundingMetadata). (code, raw body); 0 = no key, 598 / 599 offline / timeout. Own read / deadline budget. Worker thread only. */
        fun callRaw(json: String, readMs: Long, deadlineMs: Long): Pair<Int, String> = Pair(0, "")
        /** v7.58 13C: a text-only Gemini call (compose) with its own budget. (code, answer text) like [call]. Worker thread only. */
        fun callTextWith(json: String, readMs: Long, deadlineMs: Long): Pair<Int, String> = Pair(0, "")
        /** v7.58 13C: one key-less public GET (WebBrowsers.httpGet); null on any failure. Worker thread only. */
        fun webGet(url: String, ms: Int): String? = null
    }

    /** v7.59 13D: told ONCE when a run that was started by voice / chat ends: (ok, plain report text or the honest failure sentence, short speech summary). Never called for a run started by the ANALYSE button. */
    fun interface Notify { fun done(ok: Boolean, text: String, speech: String) }
    @Volatile private var notify: Notify? = null
    private fun takeNotify(): Notify? { synchronized(lock) { val n = notify; notify = null; return n } }
    /** Key of the report of a chat photo (it has no media id). Same RAM-only store, same bridge (analysisResultText). */
    const val CHAT_ID = "chatphoto"

    private fun newPool(): java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "nova-analysis").apply { isDaemon = true } }
    /** v7.54 13B-2b: still ONE worker at a time, but after cancel / watchdog the stuck worker is abandoned (interrupted, its late result is thrown away by the generation check) and a NEW worker is made, so a new analysis never waits behind an old network call. */
    @Volatile private var pool: java.util.concurrent.ExecutorService = newPool()
    private fun replaceWorker() {
        val old = pool
        pool = newPool()
        try { old.shutdownNow() } catch (x: Throwable) { }
    }
    private val gen = AtomicInteger(0)
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var watchdog: Runnable? = null

    @Volatile var state = AnalysisPipelineRules.ST_IDLE; private set
    @Volatile var stage = AnalysisStage.INTAKE; private set
    @Volatile var done = 0; private set
    @Volatile var total = 0; private set
    @Volatile var message = ""; private set
    @Volatile var error = ""; private set
    @Volatile var mediaId = ""; private set
    /** Called with every real stage event (worker thread, or main thread for cancel / watchdog). */
    @Volatile var onStage: ((AnalysisStage) -> Unit)? = null

    private class Stored(val json: String, val text: String)
    private val results = object : LinkedHashMap<String, Stored>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Stored>?): Boolean = size > AnalysisPipelineRules.MAX_RESULTS_KEPT
    }

    private fun alive(g: Int): Boolean = gen.get() == g

    private fun emit(g: Int, st: String, d: Int, t: Int, en: Boolean) {
        if (!alive(g)) return
        val m = AnalysisRules.stageMessage(st, d, t, en)
        stage = st; done = d; total = t; message = m
        try { onStage?.invoke(AnalysisStage(st, d, t, m)) } catch (x: Throwable) { }
    }

    /** Ends run [g] with an error line. A newer / cancelled generation is ignored. */
    private fun fail(g: Int, text: String) {
        synchronized(lock) {
            if (!alive(g)) return
            state = AnalysisPipelineRules.ST_ERROR; stage = AnalysisStage.FAILED; error = text; message = text
            watchdog?.let { main.removeCallbacks(it) }; watchdog = null
        }
        try { onStage?.invoke(AnalysisStage(AnalysisStage.FAILED, done, total, text)) } catch (x: Throwable) { }
        try { takeNotify()?.done(false, text, "") } catch (x: Throwable) { }
    }

    private fun succeed(g: Int, id: String, r: AnalysisRules.Report, en: Boolean) {
        synchronized(lock) {
            if (!alive(g)) return
            synchronized(results) { results[id] = Stored(AnalysisRules.toJson(r), AnalysisRules.reportText(r, en)) }
            state = AnalysisPipelineRules.ST_DONE; stage = AnalysisStage.DONE; done = 1; total = 1; error = ""
            message = AnalysisRules.stageMessage(AnalysisStage.DONE, 1, 1, en)
            watchdog?.let { main.removeCallbacks(it) }; watchdog = null
        }
        try { onStage?.invoke(AnalysisStage(AnalysisStage.DONE, 1, 1, message)) } catch (x: Throwable) { }
        try { lastCard = Pair(ResultCardRules.card(r, en), ResultCardRules.historyLine(r)) } catch (x: Throwable) { lastCard = null }   // v7.67 PART 15: card + History line of this report (text only)
        try { takeNotify()?.done(true, AnalysisRules.reportText(r, en), AnalysisRules.speechSummary(r, en)) } catch (x: Throwable) { }
    }

    /** v7.67 PART 15: (card text, History line) of the report that was just finished; the service takes it once in deliverAnalysis. */
    @Volatile var lastCard: Pair<String, String>? = null
    fun takeCard(): Pair<String, String>? { val c = lastCard; lastCard = null; return c }

    /**
     * Starts the analysis of media [id] with the owner's [question] (may be blank). Returns "ok" or a short honest reason (nothing started / refused).
     * [notify] (v7.59 13D) is told once when a voice-started run ends. [id] "" is not a media file: a chat photo uses [startChat].
     */
    fun start(c: Context, id: String?, question: String?, brain: Brain, notify: Notify? = null): String {
        val en = brain.en()
        if (id.isNullOrEmpty()) return AnalysisVoiceRules.noPhotoText(en)
        if (!MediaIntake.isValidId(id)) return AnalysisPipelineRules.noFileText(en)
        val (q, refusal) = AnalysisPipelineRules.intake(question, en)
        val e = MediaIntakeStore.list(c).firstOrNull { it.id == id } ?: return AnalysisPipelineRules.noFileText(en)
        if (e.kind != MediaIntake.Kind.IMAGE && e.kind != MediaIntake.Kind.VIDEO) return AnalysisPipelineRules.notMediaText(en)
        return launch(c, id, q, refusal, e.kind == MediaIntake.Kind.VIDEO, null, brain, notify)
    }

    /**
     * v7.59 13D: a photo attached in the CHAT + the owner's question (the old bug: the photo was ignored). [jpeg] is the already shrunk photo, kept only in RAM here.
     * Same gate, same vision / search / compose path, report stored under [CHAT_ID]. "ok" or an honest reason.
     */
    fun startChat(c: Context, jpeg: ByteArray?, question: String?, brain: Brain, notify: Notify? = null): String {
        val en = brain.en()
        if (jpeg == null || jpeg.isEmpty()) return AnalysisVoiceRules.noPhotoText(en)
        val (q, refusal) = AnalysisPipelineRules.intake(question, en)
        return launch(c, CHAT_ID, q, refusal, false, jpeg, brain, notify)
    }

    private fun launch(c: Context, id: String, q: String, refusal: String, isVideo: Boolean, chat: ByteArray?, brain: Brain, notifyTo: Notify?): String {
        val en = brain.en()
        val g: Int
        synchronized(lock) {
            if (state == AnalysisPipelineRules.ST_RUNNING) return AnalysisPipelineRules.busyText(en)
            g = gen.incrementAndGet()
            mediaId = id; done = 0; total = 0; error = ""; message = ""; stage = AnalysisStage.INTAKE
            if (refusal.isNotEmpty()) {
                // v7.53: refused BEFORE any network use or frame work: FAILED with the honest sentence, nothing is sent anywhere.
                state = AnalysisPipelineRules.ST_ERROR; stage = AnalysisStage.FAILED; error = refusal; message = refusal
            } else {
                state = AnalysisPipelineRules.ST_RUNNING
                notify = notifyTo
                val wd = Runnable {
                    var hit = false
                    synchronized(lock) {
                        if (alive(g) && state == AnalysisPipelineRules.ST_RUNNING) {
                            gen.incrementAndGet(); state = AnalysisPipelineRules.ST_ERROR; stage = AnalysisStage.FAILED
                            error = AnalysisPipelineRules.timeoutText(en); message = error; hit = true
                        }
                    }
                    if (hit) {
                        replaceWorker()
                        try { FrameExtractor.cancel() } catch (x: Throwable) { }
                        try { onStage?.invoke(AnalysisStage(AnalysisStage.FAILED, done, total, error)) } catch (x: Throwable) { }
                        try { takeNotify()?.done(false, error, "") } catch (x: Throwable) { }
                    }
                }
                watchdog = wd
                main.postDelayed(wd, AnalysisPipelineRules.WATCHDOG_MS)
            }
        }
        if (refusal.isNotEmpty()) {
            try { onStage?.invoke(AnalysisStage(AnalysisStage.FAILED, 0, 0, refusal)) } catch (x: Throwable) { }
            return refusal
        }
        pool.execute {
            try {
                run(c, g, id, q, isVideo, brain, chat)
            } catch (x: OutOfMemoryError) {
                fail(g, FrameRules.errorText(FrameRules.ERR_MEMORY))
            } catch (x: Throwable) {
                ErrLog.add("analysis", x.javaClass.simpleName)
                fail(g, AnalysisPipelineRules.unreadableText(en))
            }
        }
        return "ok"
    }

    /** Owner ROKO or STOP: ends a running analysis at once (and a frame extraction it started). Safe on any thread, any time. */
    fun cancel() {
        var was = false
        synchronized(lock) {
            if (state == AnalysisPipelineRules.ST_RUNNING) {
                gen.incrementAndGet(); state = AnalysisPipelineRules.ST_CANCELLED; stage = AnalysisStage.FAILED
                message = AnalysisPipelineRules.cancelledText(false); error = ""; was = true
                watchdog?.let { main.removeCallbacks(it) }; watchdog = null
            }
        }
        if (was) {
            replaceWorker()
            try { FrameExtractor.cancel() } catch (x: Throwable) { }
            try { onStage?.invoke(AnalysisStage(AnalysisStage.FAILED, done, total, message)) } catch (x: Throwable) { }
            try { takeNotify()?.done(false, message, "") } catch (x: Throwable) { }
        }
    }

    private fun durationOf(f: File): Long {
        val r = MediaMetadataRetriever()
        return try { r.setDataSource(f.absolutePath); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
        catch (x: Throwable) { 0L } finally { try { r.release() } catch (x: Throwable) { } }
    }

    /** Honest metadata-only report (nothing was looked at) + the reason as one more limit. */
    private fun finishMetadata(g: Int, id: String, file: File?, chat: ByteArray?, isVideo: Boolean, reasonCode: String, en: Boolean) {
        val dur = if (isVideo && file != null) { val k = FrameExtractor.lastDurationMs; if (k > 0L && FrameExtractor.mediaId == id) k else durationOf(file) } else 0L
        val size = if (chat != null) chat.size.toLong() else (file?.length() ?: 0L)
        val r = AnalysisPipelineRules.withReason(AnalysisRules.metadataReport(isVideo, size, dur, en), AnalysisPipelineRules.fallbackReason(reasonCode, en))
        succeed(g, id, r, en)
    }

    private fun run(c: Context, g: Int, id: String, q: String, isVideo: Boolean, brain: Brain, chat: ByteArray? = null) {
        val en = brain.en()
        val t0 = System.currentTimeMillis()
        emit(g, AnalysisStage.INTAKE, 0, 1, en)
        val file = if (chat != null) null else MediaIntakeStore.fileOf(c, id)   // v7.59 13D: a chat photo is in RAM (no file, no frames to extract)
        if (chat == null && file == null) { fail(g, AnalysisPipelineRules.noFileText(en)); return }
        emit(g, AnalysisStage.INTAKE, 1, 1, en)
        val blocked = brain.blocked()
        // v7.54 13B-2a: Gemini unusable (no key / limit) is not the end when a free vision provider with the owner's key is ready.
        if (blocked.isNotEmpty() && !brain.freeVisionReady()) { finishMetadata(g, id, file, chat, isVideo, blocked, en); return }
        var useFree = blocked.isNotEmpty()
        var freeUsed = false

        // ---- FRAMES
        var frames: List<File> = if (chat != null) emptyList() else FrameExtractor.keptFiles(c, id)
        if (chat == null && frames.isEmpty()) {
            val r = FrameExtractor.start(c, id, null)
            if (r != "ok") { fail(g, r); return }
            val t0 = System.currentTimeMillis()
            var lastD = -1; var lastT = -1
            while (alive(g)) {
                val st = FrameExtractor.state
                val d = FrameExtractor.done; val t = FrameExtractor.total
                if (d != lastD || t != lastT) { lastD = d; lastT = t; emit(g, AnalysisStage.FRAMES, d, t, en) }
                if (st == "done") break
                if (st != "running") { fail(g, FrameExtractor.error.ifEmpty { FrameRules.errorText(FrameRules.ERR_NONE) }); return }
                if (System.currentTimeMillis() - t0 > AnalysisPipelineRules.FRAME_WAIT_MS) {
                    try { FrameExtractor.cancel() } catch (x: Throwable) { }
                    fail(g, FrameRules.errorText(FrameRules.ERR_TIMEOUT)); return
                }
                try { Thread.sleep(150) } catch (x: InterruptedException) { return }
            }
            if (!alive(g)) return
            frames = FrameExtractor.keptFiles(c, id)
        }
        if (chat == null && frames.isEmpty()) { fail(g, FrameRules.errorText(FrameRules.ERR_NONE)); return }
        if (!alive(g)) return

        // ---- VISION
        val secs = if (chat != null) emptyList() else FrameExtractor.keptSecs()
        val nFrames = if (chat != null) 1 else frames.size
        val plan = if (useFree) AnalysisPipelineRules.batches(nFrames, FreeVision.FRAMES_PER_CALL) else AnalysisPipelineRules.batches(nFrames)
        val parts = ArrayList<AnalysisRules.Vision>()
        var sent = 0
        var firstFail = ""
        for ((k, b) in plan.withIndex()) {
            if (!alive(g)) return
            emit(g, AnalysisStage.VISION, k, plan.size, en)
            val b64 = ArrayList<String>()
            if (chat != null) b64.add(Base64.encodeToString(chat, Base64.NO_WRAP))
            else for (f in frames.subList(b.first, b.first + b.second)) {
                b64.add(Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
            val userText = AnalysisRules.visionUserPrompt(q, isVideo, b.second, AnalysisPipelineRules.batchSecs(secs, b.first, b.second), en)
            val sys = AnalysisRules.visionSystemPrompt(en)
            var v: AnalysisRules.Vision? = null
            var outcome = "OK"
            var sentNow = b.second
            for (attempt in 0..1) {
                val ut = if (attempt == 0) userText else userText + AnalysisPipelineRules.retryNote(en)
                var res: Pair<Int, String> = Pair(0, "")
                if (!useFree) {
                    res = brain.call(AnalysisPipelineRules.visionRequestJson(sys, ut, b64))
                    if (!alive(g)) return
                    outcome = AnalysisPipelineRules.callOutcome(res.first)
                    // v7.54 13B-2a: Gemini failed -> the free vision chain (own frame cap, same cooldown / usage counters as every free call)
                    if (AnalysisPipelineRules.tryFreeAfter(outcome, brain.freeVisionReady(), false)) useFree = true
                }
                if (useFree) {
                    val pick = FreeVision.pickIndexes(b64.size).map { b64[it] }
                    res = brain.callFree(sys, ut, pick) { !alive(g) }
                    if (!alive(g)) return
                    outcome = AnalysisPipelineRules.callOutcome(res.first)
                    sentNow = pick.size
                    if (outcome == "OK") freeUsed = true
                }
                if (outcome != "OK") break
                v = AnalysisRules.parseVision(res.second)
                if (v != null) break
            }
            b64.clear()
            if (outcome != "OK") { firstFail = outcome; break }
            if (v == null) { fail(g, AnalysisPipelineRules.unreadableText(en)); return }
            val refuse = AnalysisPipelineRules.safetyRefusal(v, en)
            if (refuse.isNotEmpty()) { fail(g, refuse); return }   // the model's own safety flag: refused, nothing is stored
            parts.add(v); sent += sentNow
            emit(g, AnalysisStage.VISION, k + 1, plan.size, en)
        }
        if (!alive(g)) return
        if (parts.isEmpty()) { finishMetadata(g, id, file, chat, isVideo, firstFail.ifEmpty { "OTHER" }, en); return }
        val merged = AnalysisRules.mergeVision(parts)
        if (merged == null) { fail(g, AnalysisPipelineRules.unreadableText(en)); return }
        // ---- SEARCH (v7.58 13C): one short text query per thing to check (<= 4). Only the query leaves the phone, never the picture.
        val queries = AnalysisRules.buildQueries(merged, q)
        var searchRan = false
        var found: List<AnalysisRules.Result> = emptyList()
        var searchFail = ""
        if (queries.isNotEmpty()) {
            if (!AnalysisSearchRules.searchAllowed(System.currentTimeMillis() - t0)) searchFail = "TIME"
            else {
                val gem: ((String) -> Pair<Int, String>)? =
                    if (brain.blocked().isEmpty()) ({ j: String -> brain.callRaw(j, AnalysisSearchRules.SEARCH_READ_MS, AnalysisSearchRules.SEARCH_DEADLINE_MS) }) else null
                val so = AnalysisSearchRules.runSearch(
                    queries, en, gem, { u -> brain.webGet(u, AnalysisSearchRules.KEYLESS_GET_MS) },
                    { alive(g) }, { System.currentTimeMillis() }, { d, tt -> emit(g, AnalysisStage.SEARCH, d, tt, en) }
                )
                if (!alive(g)) return
                found = so.results; searchRan = so.ran; searchFail = so.failKind
                if (!searchRan && searchFail.isEmpty()) searchFail = "OFFLINE"
            }
        }
        if (!alive(g)) return

        // ---- COMPOSE: the model only gets the claims and the numbered, sanitized results (no address) and answers with result NUMBERS. Failure = every claim stays unverified.
        var composed: AnalysisRules.Composed? = null
        var composeFail = ""
        if (AnalysisSearchRules.composeNeeded(merged, found)) {
            val bl = brain.blocked()
            if (bl.isNotEmpty()) composeFail = bl
            else if (!AnalysisSearchRules.composeAllowed(System.currentTimeMillis() - t0)) composeFail = "TIME"
            else {
                emit(g, AnalysisStage.COMPOSE, 0, 1, en)
                val co = AnalysisSearchRules.runCompose(q, merged, found, en,
                    { j -> brain.callTextWith(j, AnalysisSearchRules.COMPOSE_READ_MS, AnalysisSearchRules.COMPOSE_DEADLINE_MS) }, { alive(g) })
                if (!alive(g)) return
                composed = co.composed; composeFail = co.failKind
                emit(g, AnalysisStage.COMPOSE, 1, 1, en)
            }
        }
        var rep = AnalysisRules.buildReport(merged, composed, found, sent, isVideo, searchRan, en)
        if (queries.isNotEmpty() && !searchRan) rep = AnalysisPipelineRules.withReason(rep, AnalysisSearchRules.searchNotRunLine(searchFail, en))
        if (found.isNotEmpty() && composed == null) rep = AnalysisPipelineRules.withReason(rep, AnalysisSearchRules.composeFailedLine(composeFail, en))
        if (firstFail.isNotEmpty()) rep = AnalysisPipelineRules.withReason(rep, AnalysisPipelineRules.fallbackReason(firstFail, en))
        if (freeUsed) rep = AnalysisPipelineRules.withReason(rep, AnalysisPipelineRules.freeUsedNote(en))
        succeed(g, id, rep, en)
    }

    /** JSON for the page: state, stage, done / total, percent, message, error, id. No path. */
    fun statusJson(): String = AnalysisPipelineRules.statusJson(state, stage, done, total, message, error, mediaId)

    /** Report JSON (AnalysisRules.toJson) of media [id], "" if there is none (never analysed, refused, failed, or forgotten). */
    fun resultJson(id: String?): String = synchronized(results) { results[id ?: ""]?.json ?: "" }

    /** Plain chat text of the report of media [id], "" if none. */
    fun resultText(id: String?): String = synchronized(results) { results[id ?: ""]?.text ?: "" }

    /** Forgets reports of media that is gone (owner HATAO / SAAF KARO / 24 h). Safe on any thread. */
    fun forgetMissing(c: Context) {
        try {
            val ids = MediaIntakeStore.list(c).map { it.id }.toSet()
            synchronized(results) { results.keys.retainAll(ids) }
        } catch (x: Throwable) { }
    }
}
