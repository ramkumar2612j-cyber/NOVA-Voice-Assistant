package com.nova.assistant

/**
 * v7.76 - the video read run: frames (memory only) + screen text -> vision AI (Gemini, then the free chain Groq / OpenRouter) -> if a movie / series / anime
 * looks likely: short title web search -> name + confidence. Worker thread only. Same Brain, keys, cooldowns and safety as MovieGuessPipeline.
 * Frames are never written to disk. Nothing here reads a face or names a person.
 */
object VideoScanPipeline {
    /** kind: OK, REFUSED (private screen), NO_AI, OFFLINE, LIMIT, OTHER, UNREADABLE, CANCELLED. */
    class Done(val kind: String, val read: ScreenVideoRules.Read?, val outcome: MovieGuessRules.Outcome?)

    fun run(
        frames: List<ByteArray>, screenText: String, brain: AnalysisPipeline.Brain,
        alive: () -> Boolean, stage: (String, Int) -> Unit, now: () -> Long = { System.currentTimeMillis() }
    ): Done {
        val en = brain.en()
        if (frames.isEmpty()) return Done("OTHER", null, null)
        stage(MovieGuessPipeline.ST_READ, 10)
        val text = MovieGuessRules.cleanScreenText(screenText)
        if (!alive()) return Done("CANCELLED", null, null)

        stage(MovieGuessPipeline.ST_VISION, 30)
        val blocked = brain.blocked()
        if (blocked.isNotEmpty() && !brain.freeVisionReady()) return Done(if (blocked == "LIMIT") "LIMIT" else "NO_AI", null, null)
        val sys = ScreenVideoRules.systemPrompt(en)
        var useFree = blocked.isNotEmpty()
        var read: ScreenVideoRules.Read? = null
        var failKind = ""
        val enc = java.util.Base64.getEncoder()
        val b64 = ArrayList<String>()
        try {
            for (f in frames.take(AnalysisRules.VISION_FRAMES_PER_CALL)) b64.add(enc.encodeToString(f))
            val ut0 = ScreenVideoRules.userPrompt(text, b64.size, en)
            for (attempt in 0..1) {
                val ut = if (attempt == 0) ut0 else ut0 + AnalysisPipelineRules.retryNote(en)
                var res: Pair<Int, String> = Pair(0, "")
                var outcome = "OK"
                if (!useFree) {
                    res = brain.call(AnalysisPipelineRules.visionRequestJson(sys, ut, b64))
                    if (!alive()) return Done("CANCELLED", null, null)
                    outcome = AnalysisPipelineRules.callOutcome(res.first)
                    if (AnalysisPipelineRules.tryFreeAfter(outcome, brain.freeVisionReady(), false)) useFree = true
                }
                if (useFree) {
                    val pick = FreeVision.pickIndexes(b64.size).map { b64[it] }
                    res = brain.callFree(sys, ut, pick) { !alive() }
                    if (!alive()) return Done("CANCELLED", null, null)
                    outcome = AnalysisPipelineRules.callOutcome(res.first)
                }
                if (outcome != "OK") { failKind = outcome; break }
                read = ScreenVideoRules.parse(res.second)
                if (read != null) break
            }
        } finally { b64.clear() }
        if (failKind.isNotEmpty()) return Done(failKind, null, null)
        val r = read ?: return Done("UNREADABLE", null, null)
        if (r.vision.safety != "ok") return Done("REFUSED", r, null)
        val cands = r.vision.candidates
        if (!ScreenVideoRules.isWork(r.kind) || cands.isEmpty()) { stage(MovieGuessPipeline.ST_RESULT, 100); return Done("OK", r, null) }

        // only short title queries leave the phone (no picture, no screen text)
        stage(MovieGuessPipeline.ST_SEARCH, 60)
        val queries = MovieGuessRules.searchQueries(cands)
        var found: List<AnalysisRules.Result> = emptyList()
        var ran = false
        if (queries.isNotEmpty() && alive()) {
            val gem: ((String) -> Pair<Int, String>)? =
                if (brain.blocked().isEmpty()) ({ j: String -> brain.callRaw(j, AnalysisSearchRules.SEARCH_READ_MS, AnalysisSearchRules.SEARCH_DEADLINE_MS) }) else null
            val so = AnalysisSearchRules.runSearch(
                queries, en, gem, { u -> brain.webGet(u, AnalysisSearchRules.KEYLESS_GET_MS) },
                alive, now, { d, tt -> stage(MovieGuessPipeline.ST_SEARCH, 60 + (if (tt <= 0) 0 else 35 * d / tt)) }
            )
            found = so.results; ran = so.ran
        }
        if (!alive()) return Done("CANCELLED", r, null)
        stage(MovieGuessPipeline.ST_RESULT, 100)
        return Done("OK", r, MovieGuessRules.pick(cands, found, ran))
    }
}
