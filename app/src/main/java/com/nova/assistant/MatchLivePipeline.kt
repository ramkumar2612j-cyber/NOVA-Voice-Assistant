package com.nova.assistant

/**
 * v7.176 MATCH LIVE ANALYSIS loop. Pure Kotlin: the screen comes in through [MovieScanCapture.Source], the AI / web through [AnalysisPipeline.Brain],
 * the owner-facing output through [Say]. Worker thread only. Frames stay in memory (never saved); a protected screen is never captured or sent.
 *
 * identify (screen) -> search (web: only team names + tournament leave the phone) -> first prediction -> every POLL_MS read the screen,
 * update when the score changes (or after a quiet PULSE_MS) -> stop when the owner says stop, leaves the match, a different match appears (restart) or the AI fails repeatedly.
 */
object MatchLivePipeline {
    fun interface Say { fun say(text: String, speech: String) }

    /** Returns why it ended: CANCELLED, LEFT, TIME, FAIL, GAVE_UP, NOSERVICE, PRIVATE, NOPRED. */
    fun run(
        src: MovieScanCapture.Source, screenText: () -> String, brain: AnalysisPipeline.Brain,
        say: Say, stage: (String) -> Unit, now: () -> Long = { System.currentTimeMillis() }
    ): String {
        val en = brain.en()
        val t0 = now()
        var carry: MatchLiveRules.Snapshot? = null   // a different match seen during the live loop: skip a second identification
        while (true) {
            val snap = carry ?: identify(src, screenText, brain, say, stage, en).let { r ->
                if (r.first != "OK") return r.first
                r.second ?: return "OTHER"
            }
            carry = null
            say.say(MatchLiveRules.foundText(snap, en), "")
            stage("SEARCH")
            val facts = searchFacts(snap, brain, { !src.cancelled() }, now, en)
            if (src.cancelled()) return "CANCELLED"
            stage("ANALYSE")
            val pred0 = predict(brain, MatchLiveRules.composeSystem(false), MatchLiveRules.composeFirstUser(snap, facts, en), { !src.cancelled() })
            if (src.cancelled()) return "CANCELLED"
            if (pred0 == null) { say.say(MatchLiveRules.noPredText(en), MatchLiveRules.noPredText(en)); return "NOPRED" }
            say.say(MatchLiveRules.firstText(snap, pred0, facts.size, en) + MatchLiveRules.sourcesText(facts, en).let { if (it.isEmpty()) "" else "\n$it" }, MatchLiveRules.firstSpeech(snap, pred0, en))
            // ---- live loop
            var last = snap
            var pred: MatchLiveRules.Pred = pred0
            var lastSay = now()
            var liveFacts: List<AnalysisRules.Result> = emptyList()
            var lastSearch = now()
            var misses = 0
            var fails = 0
            var noFrames = 0
            stage("LIVE")
            while (true) {
                if (!sleepSlices(src, MatchLiveRules.POLL_MS)) return "CANCELLED"
                if (now() - t0 > MatchLiveRules.MAX_SESSION_MS) { say.say(MatchLiveRules.timeUpText(en), MatchLiveRules.timeUpText(en)); return "TIME" }
                val r = MovieScanCapture.run(src, 2, 400)
                when (r.stop) {
                    MovieFrameRules.Stop.CANCELLED -> return "CANCELLED"
                    MovieFrameRules.Stop.NO_SERVICE, MovieFrameRules.Stop.OLD_ANDROID -> { val l = MovieFrameRules.statusText(r.stop, 0, en); say.say(l, l); return "NOSERVICE" }
                    MovieFrameRules.Stop.PROTECTED -> continue          // a private screen is in front: wait, send nothing
                    MovieFrameRules.Stop.OK -> noFrames = 0
                    else -> { if (++noFrames >= 5) { say.say(MatchLiveRules.failedText("OTHER", en), MatchLiveRules.failedText("OTHER", en)); return "FAIL" }; continue }
                }
                val rd = readScreen(r.frames, screenText(), brain, { !src.cancelled() }, 1)
                if (src.cancelled()) return "CANCELLED"
                if (rd.first == "REFUSED") continue
                if (rd.first != "OK") {
                    if (++fails >= MatchLiveRules.FAIL_LIMIT) { say.say(MatchLiveRules.failedText(rd.first, en), MatchLiveRules.failedText(rd.first, en)); return "FAIL" }
                    continue
                }
                fails = 0
                val cur = rd.second
                if (cur == null || !cur.isMatch) {
                    if (++misses >= MatchLiveRules.MISS_LIMIT) { say.say(MatchLiveRules.leftText(en), MatchLiveRules.leftText(en)); return "LEFT" }
                    continue
                }
                misses = 0
                if (cur.hasTeams && !MatchLiveRules.sameMatch(snap, cur)) {
                    if (MatchLiveRules.identified(cur)) { say.say(MatchLiveRules.newMatchText(cur, en), ""); carry = cur; break }
                    continue
                }
                val changed = MatchLiveRules.scoreChanged(last, cur)
                if (!MatchLiveRules.updateDue(changed, now() - lastSay)) continue
                stage("UPDATE")
                if (now() - lastSearch >= MatchLiveRules.LIVE_SEARCH_MS) {   // fresh web facts (live score / news / toss / lineups) every few minutes
                    stage("SEARCH")
                    val fresh = searchFacts(snap, brain, { !src.cancelled() }, now, en, MatchLiveRules.liveQueries(snap))
                    if (src.cancelled()) return "CANCELLED"
                    if (fresh.isNotEmpty()) liveFacts = fresh
                    lastSearch = now()
                }
                val merged = (liveFacts + facts).distinctBy { it.url }.take(10)
                val next = predict(brain, MatchLiveRules.composeSystem(true), MatchLiveRules.composeLiveUser(snap, last, cur, pred, merged, en), { !src.cancelled() })
                if (src.cancelled()) return "CANCELLED"
                stage("LIVE")
                if (next == null) {
                    if (++fails >= MatchLiveRules.FAIL_LIMIT) { say.say(MatchLiveRules.failedText("OTHER", en), MatchLiveRules.failedText("OTHER", en)); return "FAIL" }
                    continue
                }
                fails = 0
                pred = MatchLiveRules.limitSwing(pred, next)
                last = cur
                lastSay = now()
                say.say(MatchLiveRules.updateText(snap, cur, pred, en), MatchLiveRules.updateSpeech(snap, pred, en))
            }
        }
    }

    /** Waits [ms] in 1 s slices so STOP is felt at once. false = cancelled. */
    private fun sleepSlices(src: MovieScanCapture.Source, ms: Long): Boolean {
        var left = ms
        while (left > 0) {
            if (src.cancelled()) return false
            val s = minOf(1000L, left); src.sleep(s); left -= s
        }
        return !src.cancelled()
    }

    private fun identify(
        src: MovieScanCapture.Source, screenText: () -> String, brain: AnalysisPipeline.Brain, say: Say, stage: (String) -> Unit, en: Boolean
    ): Pair<String, MatchLiveRules.Snapshot?> {
        var tries = 0
        while (true) {
            if (src.cancelled()) return Pair("CANCELLED", null)
            stage("CAPTURE")
            val r = MovieScanCapture.run(src, 2, 700)
            if (r.stop == MovieFrameRules.Stop.CANCELLED) return Pair("CANCELLED", null)
            if (r.stop != MovieFrameRules.Stop.OK) {
                val l = MovieFrameRules.statusText(r.stop, r.frames.size, en)
                say.say(l, l)
                return Pair(if (r.stop == MovieFrameRules.Stop.PROTECTED) "PRIVATE" else "NOSERVICE", null)
            }
            stage("VISION")
            val rd = readScreen(r.frames, screenText(), brain, { !src.cancelled() }, 2)
            if (src.cancelled()) return Pair("CANCELLED", null)
            when (rd.first) {
                "OK" -> if (MatchLiveRules.identified(rd.second)) return Pair("OK", rd.second)
                "REFUSED" -> { val l = MatchLiveRules.privateText(en); say.say(l, l); return Pair("PRIVATE", null) }
                else -> { val l = MatchLiveRules.failedText(rd.first, en); say.say(l, l); return Pair("FAIL", null) }
            }
            tries++
            if (tries >= MatchLiveRules.IDENT_TRIES) { val l = MatchLiveRules.gaveUpText(en); say.say(l, l); return Pair("GAVE_UP", null) }
            if (tries == 1) { val l = MatchLiveRules.notOnScreenText(en); say.say(l, l) }
            if (!sleepSlices(src, MatchLiveRules.IDENT_RETRY_MS)) return Pair("CANCELLED", null)
        }
    }

    /** (kind, snapshot): kind OK / REFUSED / NO_AI / LIMIT / OFFLINE / OTHER / UNREADABLE / CANCELLED. Same Gemini-then-free-vision order as the movie scan. */
    fun readScreen(frames: List<ByteArray>, screenText: String, brain: AnalysisPipeline.Brain, alive: () -> Boolean, attempts: Int): Pair<String, MatchLiveRules.Snapshot?> {
        val en = brain.en()
        val blocked = brain.blocked()
        if (blocked.isNotEmpty() && !brain.freeVisionReady()) return Pair(if (blocked == "LIMIT") "LIMIT" else "NO_AI", null)
        val sys = MatchLiveRules.visionSystem()
        var useFree = blocked.isNotEmpty()
        val enc = java.util.Base64.getEncoder()
        val b64 = ArrayList<String>()
        try {
            for (f in frames.take(AnalysisRules.VISION_FRAMES_PER_CALL)) b64.add(enc.encodeToString(f))
            val ut0 = MatchLiveRules.visionUser(screenText, b64.size, en)
            for (attempt in 0 until attempts) {
                val ut = if (attempt == 0) ut0 else ut0 + AnalysisPipelineRules.retryNote(en)
                var res: Pair<Int, String> = Pair(0, "")
                var outcome = "OK"
                if (!useFree) {
                    res = brain.call(AnalysisPipelineRules.visionRequestJson(sys, ut, b64))
                    if (!alive()) return Pair("CANCELLED", null)
                    outcome = AnalysisPipelineRules.callOutcome(res.first)
                    if (AnalysisPipelineRules.tryFreeAfter(outcome, brain.freeVisionReady(), false)) useFree = true
                }
                if (useFree) {
                    val pick = FreeVision.pickIndexes(b64.size).map { b64[it] }
                    res = brain.callFree(sys, ut, pick) { !alive() }
                    if (!alive()) return Pair("CANCELLED", null)
                    outcome = AnalysisPipelineRules.callOutcome(res.first)
                }
                if (outcome != "OK") return Pair(outcome, null)
                val s = MatchLiveRules.parseSnapshot(res.second)
                if (s != null) return Pair(if (s.safety != "ok") "REFUSED" else "OK", s)
            }
        } finally { b64.clear() }
        return Pair("UNREADABLE", null)
    }

    /** Web facts for the two teams. Only the team names + tournament leave the phone. Empty list = nothing found (the compose step then says so). */
    private fun searchFacts(snap: MatchLiveRules.Snapshot, brain: AnalysisPipeline.Brain, alive: () -> Boolean, now: () -> Long, en: Boolean, queries: List<String> = MatchLiveRules.searchQueries(snap)): List<AnalysisRules.Result> = try {
        val gem: ((String) -> Pair<Int, String>)? =
            if (brain.blocked().isEmpty()) ({ j: String -> brain.callRaw(j, AnalysisSearchRules.SEARCH_READ_MS, AnalysisSearchRules.SEARCH_DEADLINE_MS) }) else null
        AnalysisSearchRules.runSearch(queries, en, gem, { u -> brain.webGet(u, AnalysisSearchRules.KEYLESS_GET_MS) }, alive, now) { _, _ -> }.results
    } catch (e: Throwable) { emptyList() }

    /** One compose answer: Gemini first, the free text chain when Gemini is blocked or failed. null = nothing usable. */
    private fun predict(brain: AnalysisPipeline.Brain, system: String, user: String, alive: () -> Boolean): MatchLiveRules.Pred? {
        if (brain.blocked().isEmpty()) {
            for (attempt in 0 until 2) {
                val (code, text) = brain.callTextWith(AnalysisSearchRules.composeRequestJson(system, user), AnalysisSearchRules.COMPOSE_READ_MS, AnalysisSearchRules.COMPOSE_DEADLINE_MS)
                if (!alive()) return null
                if (code in 200..299) { MatchLiveRules.parsePred(text)?.let { return it } } else break
            }
        }
        if (!alive()) return null
        val free = try { brain.callFreeText(system, user) } catch (e: Throwable) { null }
        return MatchLiveRules.parsePred(free)
    }
}
