package com.nova.assistant

/**
 * v7.65 PART 14B-3 - the movie guess run: frames (memory) + screen text -> vision AI (Gemini, then the free chain) -> web search -> name + confidence + sources.
 * Pure Kotlin: every Android / network part comes in through [AnalysisPipeline.Brain] (the same one the 13B / 13C analysis uses, so keys, cooldowns and usage counters are shared). Worker thread only.
 * Frames are never written to disk; the base64 copies live only inside run(). Nothing here reads a face or names a person.
 */
object MovieGuessPipeline {
    /** OK = [outcome] may still be null (nothing found); REFUSED = private screen; NO_AI / OFFLINE / LIMIT / OTHER = the AI could not be asked; UNREADABLE = two answers were not valid JSON; CANCELLED = STOP. */
    class Done(val kind: String, val outcome: MovieGuessRules.Outcome?, val vision: MovieGuessRules.Vision?)

    /** Real stage names for the laser overlay (14B-4) and the progress text. */
    const val ST_READ = "READ TEXT"
    const val ST_VISION = "VISION"
    const val ST_SEARCH = "SEARCH"
    const val ST_RESULT = "RESULT"

    fun run(
        frames: List<ByteArray>, screenText: String, brain: AnalysisPipeline.Brain,
        alive: () -> Boolean, stage: (String, Int) -> Unit, now: () -> Long = { System.currentTimeMillis() }
    ): Done {
        val en = brain.en()
        if (frames.isEmpty()) return Done("OTHER", null, null)
        stage(ST_READ, 10)
        val text = MovieGuessRules.cleanScreenText(screenText)
        if (!alive()) return Done("CANCELLED", null, null)

        // ---- VISION
        stage(ST_VISION, 25)
        val blocked = brain.blocked()
        if (blocked.isNotEmpty() && !brain.freeVisionReady()) return Done(if (blocked == "LIMIT") "LIMIT" else "NO_AI", null, null)
        val sys = MovieGuessRules.systemPrompt(en)
        var useFree = blocked.isNotEmpty()
        var vision: MovieGuessRules.Vision? = null
        var failKind = ""
        val enc = java.util.Base64.getEncoder()
        val b64 = ArrayList<String>()
        try {
            for (f in frames.take(AnalysisRules.VISION_FRAMES_PER_CALL)) b64.add(enc.encodeToString(f))
            val ut0 = MovieGuessRules.userPrompt(text, b64.size, en)
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
                vision = MovieGuessRules.parseVision(res.second)
                if (vision != null) break
            }
        } finally { b64.clear() }
        if (failKind.isNotEmpty()) return Done(failKind, null, null)
        val v = vision ?: return Done("UNREADABLE", null, null)
        if (v.safety != "ok") return Done("REFUSED", null, v)
        if (v.candidates.isEmpty()) { stage(ST_RESULT, 100); return Done("OK", null, v) }

        // ---- SEARCH (only short title queries leave the phone; no picture, no screen text)
        stage(ST_SEARCH, 55)
        val queries = MovieGuessRules.searchQueries(v.candidates)
        var found: List<AnalysisRules.Result> = emptyList()
        var ran = false
        if (queries.isNotEmpty() && alive()) {
            val gem: ((String) -> Pair<Int, String>)? =
                if (brain.blocked().isEmpty()) ({ j: String -> brain.callRaw(j, AnalysisSearchRules.SEARCH_READ_MS, AnalysisSearchRules.SEARCH_DEADLINE_MS) }) else null
            val so = AnalysisSearchRules.runSearch(
                queries, en, gem, { u -> brain.webGet(u, AnalysisSearchRules.KEYLESS_GET_MS) },
                alive, now, { d, tt -> stage(ST_SEARCH, 55 + (if (tt <= 0) 0 else 35 * d / tt)) }
            )
            found = so.results; ran = so.ran
        }
        if (!alive()) return Done("CANCELLED", null, v)
        stage(ST_RESULT, 100)
        return Done("OK", MovieGuessRules.pick(v.candidates, found, ran), v)
    }
}
