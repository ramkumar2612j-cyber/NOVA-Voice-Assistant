package com.nova.assistant

/**
 * v7.54 PART 13B-2a - FREE-PROVIDER VISION FALLBACK (pure Kotlin, no Android classes, JUnit-able).
 *
 * Gemini stays the first choice for ANALYSE. Only when Gemini cannot be used (no key / limit / failed) the frames may go to a free provider that
 * REALLY takes images (OpenAI-style `image_url` data URL). Looked up 2026-10-18 (not tested on a phone):
 *  - Groq: image input only for `qwen/qwen3.8-27b` (docs: console.groq.com/docs/vision): max 3 images per request, JSON mode works with images.
 *    Same key, same provider id ("groqqwen") as the text Qwen row, so quota, cooldown and ApiUsage are the same counter.
 *  - OpenRouter: the free router `openrouter/free` "accepts text and images" and itself picks a free model that can see images. Same key / id ("openrouter").
 *    Its free models change at random, so NO response_format is sent (a model that does not know it would fail with 400).
 *  - NOT used for pictures: Z.ai (the server is in China) and Pollinations (anonymous): the owner's own photos must not go there.
 *  - Groq GPT-OSS and OpenRouter's Llama text models cannot see images: they are never asked for a picture.
 * The owner's free key is needed (no key = the provider is skipped). Nothing is invented: no answer = the honest metadata-only report.
 */
object FreeVision {
    /** Groq allows 3 images per request; the same number is used for every free provider (one rule, no guessing). */
    const val FRAMES_PER_CALL = 3

    val GROQ = FreeProvider(
        "groqqwen", "Qwen vision (via Groq)", "https://api.groq.com/openai/v1/chat/completions",
        listOf("qwen/qwen3.8-27b"), "groq"
    )
    val OPENROUTER = FreeProvider(
        "openrouter", "OpenRouter free (vision)", "https://openrouter.ai/api/v1/chat/completions",
        listOf("openrouter/free"), "openrouter"
    )

    /** Order: Groq first (fast, a model that is known to see images), then OpenRouter's free router. */
    fun providers(): List<FreeProvider> = listOf(GROQ, OPENROUTER)

    /** Only Groq documents JSON mode for images. */
    fun jsonModeFor(p: FreeProvider): Boolean = p.keyName == "groq"

    /** Which frames of a batch of [n] are sent when only [max] fit: evenly spread, the first and the last always included. n <= max = all. */
    fun pickIndexes(n: Int, max: Int = FRAMES_PER_CALL): List<Int> {
        if (n <= 0 || max <= 0) return emptyList()
        if (n <= max) return (0 until n).toList()
        if (max == 1) return listOf(0)
        val out = ArrayList<Int>()
        for (i in 0 until max) {
            val idx = Math.round(i.toDouble() * (n - 1) / (max - 1)).toInt()
            if (out.isEmpty() || out.last() != idx) out.add(idx)
        }
        return out
    }

    /** One OpenAI-style chat body: system text, then ONE user message = the text and the frames as `image_url` data URLs (image/jpeg base64). */
    fun requestJson(model: String, system: String, userText: String, framesB64: List<String>, jsonMode: Boolean, maxTokens: Int = AnalysisPipelineRules.MAX_OUT_TOKENS): String {
        val content = StringBuilder()
        content.append("{\"type\":\"text\",\"text\":").append(MiniJson.quote(userText)).append('}')
        for (b in framesB64) {
            content.append(",{\"type\":\"image_url\",\"image_url\":{\"url\":")
                .append(MiniJson.quote("data:image/jpeg;base64,$b")).append("}}")
        }
        return "{\"model\":" + MiniJson.quote(model) +
            ",\"messages\":[{\"role\":\"system\",\"content\":" + MiniJson.quote(system) + "}," +
            "{\"role\":\"user\",\"content\":[" + content + "]}]," +
            "\"max_tokens\":" + maxTokens + ",\"temperature\":0.1" +
            (if (jsonMode) ",\"response_format\":{\"type\":\"json_object\"}" else "") + "}"
    }

    /**
     * choices[0].message.content as the model wrote it. NOT FreeProviders.parseReply: that one shortens and strips markdown (it is for speech),
     * the report needs the whole JSON. Only a hidden <think> block and a ``` fence around the JSON are removed. null = no usable text.
     */
    fun replyText(body: String): String? {
        val root = MiniJson.parse(body) ?: return null
        val msg = MiniJson.field(MiniJson.at(MiniJson.field(root, "choices"), 0), "message")
        var t = (MiniJson.field(msg, "content") as? String) ?: return null
        t = t.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        }
        return if (t.isBlank()) null else t
    }

    /** failKind of the chain -> the HTTP-ish code AnalysisPipelineRules.callOutcome understands (0 NOKEY, 429 LIMIT, 598 OFFLINE, else OTHER). */
    fun codeFor(failKind: String): Int = when (failKind) {
        "nokey" -> 0
        "quota", "cooling" -> 429
        "network" -> 598
        "slow" -> 599
        else -> 500
    }

    /** True when at least one free vision provider could be asked right now: the free chain is on, it has a key, and it is not cooling down. */
    fun ready(enabled: Boolean, keyOf: (String) -> String, cd: Cooldowns, now: Long): Boolean =
        enabled && providers().any { p -> p.keyName != null && keyOf(p.keyName).trim().isNotEmpty() && !cd.cooling(p.id, now) }

    /**
     * Tries the vision providers in order, the same rules as [ProviderChain.run]: a provider with no key / cooling is skipped without a call;
     * 429 / 400 / 404 / 422 -> next model of the same provider, 401 / 403 / 5xx / timeout -> next provider, 598 (no internet) ends everything;
     * a provider that failed is put on the shared cooldown; a 200 without usable text is a failure ("other"). [stop] is checked before every call
     * (owner ROKO / watchdog). [call] = (provider, model, jsonBody, key) -> NetAnswer and must never throw.
     */
    fun run(
        system: String,
        userText: String,
        framesB64: List<String>,
        keyOf: (String) -> String,
        enabled: Boolean,
        cd: Cooldowns,
        now: () -> Long,
        budgetMs: Long,
        stop: () -> Boolean,
        call: (FreeProvider, String, String, String) -> NetAnswer
    ): ChainResult {
        if (!enabled) return ChainResult(null, null, null, "nokey", emptyList())
        val start = now()
        val tried = ArrayList<String>()
        var worst = "nokey"
        for (p in providers()) {
            if (stop()) return ChainResult(null, null, null, "other", tried)
            if (now() - start > budgetMs) { worst = FreeProviders.worse(worst, "slow"); break }
            if (cd.cooling(p.id, now())) { worst = FreeProviders.worse(worst, "cooling"); continue }
            val key = if (p.keyName == null) "" else keyOf(p.keyName).trim()
            if (p.keyName != null && key.isEmpty()) continue
            tried.add(p.id)
            var kind = "other"
            var cool = true
            for (m in p.models) {
                if (stop()) return ChainResult(null, null, null, "other", tried)
                if (now() - start > budgetMs) { kind = "slow"; cool = false; break }
                val r = call(p, m, requestJson(m, system, userText, framesB64, jsonModeFor(p)), key)
                if (r.code in 200..299) {
                    val t = replyText(r.body)
                    if (t != null) return ChainResult(t, p.id, m, "", tried)
                    kind = FreeProviders.worse(kind, "other")
                    continue
                }
                if (r.code == 598) return ChainResult(null, null, null, "network", tried)
                kind = FreeProviders.worse(kind, FreeProviders.kindOf(r.code))
                if (!(r.code == 429 || r.code == 400 || r.code == 404 || r.code == 422)) break
            }
            if (cool) cd.set(p.id, FreeProviders.cooldownFor(kind), now())
            worst = FreeProviders.worse(worst, kind)
        }
        return ChainResult(null, null, null, worst, tried)
    }
}
