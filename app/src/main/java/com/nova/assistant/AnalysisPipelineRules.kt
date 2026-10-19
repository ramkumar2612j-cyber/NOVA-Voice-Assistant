package com.nova.assistant

/**
 * v7.53 PART 13B-1 - pure rules for the analysis pipeline (no Android classes, JUnit-able; uses only MiniJson + AnalysisRules + AnalysisStage).
 * The Android side is AnalysisPipeline.kt. Nothing here touches the network; it only builds the request text, the status text and the decisions.
 */
object AnalysisPipelineRules {
    /** Whole pipeline watchdog (owner prompt: 120 s). A late result of an old run is thrown away. */
    const val WATCHDOG_MS = 120_000L
    /** How long the pipeline waits for FrameExtractor (its own watchdog is 60 s). */
    const val FRAME_WAIT_MS = 65_000L
    /** One vision call may take at most this long in total (all keys / models together). */
    const val CALL_DEADLINE_MS = 35_000L
    const val CALL_READ_MS = 30_000L
    const val MAX_RESULTS_KEPT = 5
    const val MAX_OUT_TOKENS = 2048

    const val ST_IDLE = "idle"
    const val ST_RUNNING = "running"
    const val ST_DONE = "done"
    const val ST_ERROR = "error"
    const val ST_CANCELLED = "cancelled"

    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    /** One Gemini generateContent body: system instruction, the frames as inlineData (image/jpeg base64) then the user text, strict JSON answer, temperature 0.1. */
    fun visionRequestJson(system: String, userText: String, framesB64: List<String>): String {
        val parts = StringBuilder()
        for (b in framesB64) {
            if (parts.isNotEmpty()) parts.append(',')
            parts.append("{\"inlineData\":{\"mimeType\":\"image/jpeg\",\"data\":").append(MiniJson.quote(b)).append("}}")
        }
        if (parts.isNotEmpty()) parts.append(',')
        parts.append("{\"text\":").append(MiniJson.quote(userText)).append('}')
        return "{\"systemInstruction\":{\"parts\":[{\"text\":" + MiniJson.quote(system) + "}]}," +
            "\"contents\":[{\"role\":\"user\",\"parts\":[" + parts + "]}]," +
            "\"generationConfig\":{\"temperature\":0.1,\"responseMimeType\":\"application/json\",\"maxOutputTokens\":" + MAX_OUT_TOKENS + "}}"
    }

    /** Added to the user text for the single retry when the first answer was not a valid JSON object. */
    fun retryNote(en: Boolean): String = t(en,
        "\nPichhla jawab valid nahi tha. Ab sirf ek hi valid JSON object do, aur kuch nahi (na markdown, na text).",
        "\nThe previous answer was not valid. Reply with exactly one valid JSON object and nothing else (no markdown, no text).")

    /** Seconds of a frame batch: [secs] are the time points of all kept frames, [from] / [count] choose the batch. A photo has no secs. */
    fun batchSecs(secs: List<Int>, from: Int, count: Int): List<Int> =
        if (secs.isEmpty() || from < 0 || count <= 0) emptyList() else secs.drop(from).take(count)

    /** Splits [total] frames by AnalysisRules.batchSizes into (from, count) pairs; frames beyond the cap are not sent. */
    fun batches(total: Int, per: Int = AnalysisRules.VISION_FRAMES_PER_CALL): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        var from = 0
        for (n in AnalysisRules.batchSizes(total, per)) { out.add(Pair(from, n)); from += n }
        return out
    }

    /** What to do with an HTTP-ish code of a vision call: OK = parse the answer, OFFLINE / NOKEY / LIMIT / OTHER = honest metadata-only report. */
    fun callOutcome(code: Int): String = when {
        code in 200..299 -> "OK"
        code == 0 -> "NOKEY"
        code == 598 || code == 599 -> "OFFLINE"
        code == 429 -> "LIMIT"
        else -> "OTHER"
    }

    /** The honest reason line (added to limits of a metadata-only report). Never an invented description. */
    fun fallbackReason(outcome: String, en: Boolean): String = when (outcome) {
        "NOKEY" -> t(en, "AI key nahi hai, isliye tasveer dekhi nahi gayi.", "There is no AI key, so the picture was not looked at.")
        "OFFLINE" -> t(en, "Internet nahi hai ya bahut slow hai, isliye tasveer dekhi nahi gayi.", "No internet or it is too slow, so the picture was not looked at.")
        "LIMIT" -> t(en, "AI ki limit poori ho gayi, isliye tasveer dekhi nahi gayi.", "The AI limit is reached, so the picture was not looked at.")
        "NOFRAMES" -> t(en, "Frames nahi ban paye, isliye tasveer dekhi nahi gayi.", "No frames could be made, so the picture was not looked at.")
        else -> t(en, "AI se jawab nahi mila, isliye tasveer dekhi nahi gayi.", "The AI did not answer, so the picture was not looked at.")
    }

    /**
     * v7.54 13B-2a: should the free vision chain be asked after Gemini failed with [outcome]? Not when there is no internet (every provider would fail too)
     * and not when the run is already on the free chain.
     */
    fun tryFreeAfter(outcome: String, freeReady: Boolean, alreadyFree: Boolean): Boolean =
        freeReady && !alreadyFree && outcome != "OK" && outcome != "OFFLINE"

    /** Honest limit line of a report whose picture was looked at by a free provider (Groq / OpenRouter) and not by Gemini. */
    fun freeUsedNote(en: Boolean): String = t(en,
        "Tasveer Gemini ne nahi, free AI (Groq / OpenRouter) ne dekhi; ek baar me sirf 3 tak frames bheje gaye, isliye kuch frames nahi dekhe gaye ho sakte hain.",
        "The picture was looked at by a free AI (Groq / OpenRouter), not Gemini; at most 3 frames are sent per call, so some frames may not have been looked at.")

    /** Message for a model answer that could not be parsed twice. */
    fun unreadableText(en: Boolean): String = t(en, "AI ka jawab samajh nahi aaya. Dobara try karo.", "I could not understand the AI's answer. Try again.")
    fun notMediaText(en: Boolean): String = t(en, "Ye link hai, photo / video file nahi. Analysis ke liye gallery se file share karo.", "This is a link, not a photo / video file. Share the file from your gallery to analyse it.")
    fun noFileText(en: Boolean): String = t(en, "Ye file ab nahi mili. Dobara share karo.", "That file is gone. Share it again.")
    fun busyText(en: Boolean): String = t(en, "Ek analysis pehle se chal raha hai. Ruko ya ROKO dabao.", "An analysis is already running. Wait or press ROKO.")
    fun noServiceText(en: Boolean): String = t(en, "NOVA abhi band hai. Pehle NOVA chalu karo, phir ANALYSE dabao.", "NOVA is off. Start NOVA first, then press ANALYSE.")
    fun chatPhotoLaterText(en: Boolean): String = t(en, "Chat ki photo ka analysis agle hisse (13D) me aayega. Abhi media card se chalao.", "Chat photo analysis comes in the next step (13D). Use the media card for now.")
    fun timeoutText(en: Boolean): String = t(en, "Analysis bahut der laga, roka gaya.", "The analysis took too long and was stopped.")
    fun cancelledText(en: Boolean): String = t(en, "Roka gaya.", "Stopped.")

    /** Status JSON for the page. No path, no key. Percent only from the real done / total. */
    fun statusJson(state: String, stage: String, done: Int, total: Int, message: String, error: String, id: String): String =
        "{\"state\":" + MiniJson.quote(state) + ",\"stage\":" + MiniJson.quote(stage) +
            ",\"done\":" + done.coerceAtLeast(0) + ",\"total\":" + total.coerceAtLeast(0) +
            ",\"percent\":" + AnalysisStage(stage, done, total, "").percent() +
            ",\"message\":" + MiniJson.quote(message) + ",\"error\":" + MiniJson.quote(error) + ",\"id\":" + MiniJson.quote(id) + "}"

    /** A report whose mode says metadata_only gets the honest reason as one more limit. Returns a new Report; the old one is unchanged. */
    fun withReason(r: AnalysisRules.Report, reason: String): AnalysisRules.Report =
        if (reason.isBlank() || r.limits.contains(reason)) r else r.copy(limits = listOf(reason) + r.limits)

    /** The question is cleaned and gated BEFORE any network use. Pair(cleanQuestion, refusalText or "" when OK). */
    fun intake(question: String?, en: Boolean): Pair<String, String> {
        val q = AnalysisRules.cleanQuestion(question)
        val g = AnalysisRules.gateQuestion(q)
        return Pair(q, AnalysisRules.refusalText(g, en))
    }

    /** What a finished vision answer means: "" = go on, otherwise the refusal text of the model's own safety flag. */
    fun safetyRefusal(v: AnalysisRules.Vision, en: Boolean): String =
        AnalysisRules.refusalText(AnalysisRules.gateFromSafety(v.safety), en)
}
