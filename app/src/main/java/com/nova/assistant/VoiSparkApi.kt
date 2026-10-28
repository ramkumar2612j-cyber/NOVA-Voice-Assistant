package com.nova.assistant

import org.json.JSONArray
import org.json.JSONObject

/**
 * v7.129: VoiSpark TTS (Ultron voice) - pure rules, no network, no Android. Unit tested.
 *
 * ONLY what the official VoiSpark docs (docs pages "API V2 Reference", "Quick Start (V2)", "Fish Audio Drama 3 guide",
 * "Generate speech", "Get Benefit") show is used here:
 *   base URL            https://api.voispark.com
 *   auth                Authorization: Bearer <API token>   (token made in the voispark.com dashboard > API Tokens)
 *   list voices         GET  /api/v2/voices
 *   voice models        GET  /api/v2/voices/{voice_id}/models?operation=tts
 *   generate speech     POST /api/v2/tts/generate   body: voice_id, model_id, text, configs (optional), sync
 *   poll task           GET  /api/v2/tts/task/{task_id}      (sync=false -> data.task_id)
 *   credits             GET  /api/benefit
 *   answer envelope     {code, message, data}; code == 0 is success even when HTTP is 200; failures can be HTTP 200 + nonzero code + data=null
 *   Drama 3 model       fish-audio/drama-3 ; 83 languages, language detected automatically (do NOT send a language) ;
 *                       4 VoiSpark credits per character (direction/tag characters count) ; model limit 10,000 characters (plan may be lower)
 *                       configs keys: speed, volume, normalize, temperature, top_p (unknown keys are rejected)
 * NOT shown in the docs I was given (so NOT assumed): the exact fields of the finished task / the sync answer (where the audio is),
 * the audio format, the task status words, the voice-list filters. [audioCandidates] therefore only DISCOVERS an audio link or
 * base64 blob in the answer; the in-app VoiSpark check prints the answer's shape (keys only, no values) so the real field can be pinned.
 */
object VoiSparkApi {
    const val BASE = "https://api.voispark.com"
    const val HOST = "api.voispark.com"
    const val MODEL_ID = "fish-audio/drama-3"
    /** The Ultron voice id the owner gave. It is NOT a secret. Whether it really is "Ultron" is proven only by [findVoiceInList]. */
    const val DEFAULT_VOICE_ID = "ad2612df-ff6e-4291-bcc6-902767a6a5fd"
    const val CREDITS_PER_CHAR = 4
    const val MODEL_MAX_CHARS = 10000
    /** Every spoken character costs 4 credits, so only short replies use VoiSpark (the rest uses the existing voice). */
    const val DEFAULT_MAX_CHARS = 240
    const val MIN_MAX_CHARS = 40
    const val MAX_MAX_CHARS = 1000
    /** After a failure (no credits, bad key, no net) VoiSpark is skipped for this long so replies are not slowed by retries. */
    const val PAUSE_MS = 10 * 60 * 1000L

    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun validVoiceId(id: String): Boolean = UUID.matches(id.trim())
    fun clampMaxChars(n: Int): Int = n.coerceIn(MIN_MAX_CHARS, MAX_MAX_CHARS)
    fun creditsFor(text: String): Int = text.length * CREDITS_PER_CHAR
    fun tooLong(text: String, maxChars: Int): Boolean = text.length > maxChars || text.length > MODEL_MAX_CHARS

    fun voicesPath(): String = "/api/v2/voices"
    fun modelsPath(voiceId: String): String = "/api/v2/voices/" + voiceId.trim() + "/models?operation=tts"
    fun generatePath(): String = "/api/v2/tts/generate"
    fun taskPath(taskId: String): String = "/api/v2/tts/task/" + taskId.trim()
    fun benefitPath(): String = "/api/benefit"

    /** Body of POST /api/v2/tts/generate. No language field (auto detect), no configs (defaults), sync=false (docs: then read data.task_id). */
    fun generateBody(voiceId: String, text: String): String =
        JSONObject().put("voice_id", voiceId.trim()).put("model_id", MODEL_ID).put("text", text).put("sync", false).toString()

    class Envelope(val http: Int, val code: Int, val message: String, val data: Any?) {
        /** Success needs HTTP 2xx AND code == 0. HTTP 200 with a nonzero code is a business failure (credits, provider). */
        val ok: Boolean get() = http in 200..299 && code == 0
    }

    /** code = -1 when the body is not the documented {code,message,data} shape. Never throws. */
    fun parseEnvelope(http: Int, body: String): Envelope {
        return try {
            val o = JSONObject(body)
            if (!o.has("code")) return Envelope(http, -1, "no code field", null)
            val d = if (o.isNull("data")) null else o.get("data")
            Envelope(http, o.optInt("code", -1), o.optString("message", ""), d)
        } catch (e: Exception) { Envelope(http, -1, "not json", null) }
    }

    fun taskId(env: Envelope): String? {
        val d = env.data as? JSONObject ?: return null
        val id = d.optString("task_id", "")
        return if (id.isNotBlank() && id.length <= 200 && id.none { it.isWhitespace() || it == '/' || it == '?' || it == '#' }) id else null
    }

    /** What an "audio found" answer is: an https link (downloaded without the key) or base64 bytes. */
    class Audio(val url: String?, val base64: String?)

    private val URL_KEY = Regex("(?i)(url|link|audio|file|result|output)")
    private val B64_KEY = Regex("(?i)(base64|b64|audio|data)")

    /** Looks through [data] for an audio link or a long base64 string. Discovery only - see the object comment. Only https links on the VoiSpark / CDN side are kept. */
    fun findAudio(data: Any?): Audio? {
        var url: String? = null
        var b64: String? = null
        fun walk(key: String, v: Any?, depth: Int) {
            if (depth > 6 || (url != null && b64 != null)) return
            when (v) {
                is JSONObject -> v.keys().forEach { k -> walk(k, v.opt(k), depth + 1) }
                is JSONArray -> for (i in 0 until minOf(v.length(), 20)) walk(key, v.opt(i), depth + 1)
                is String -> {
                    val s = v.trim()
                    if (url == null && s.startsWith("https://") && s.length < 2000 && URL_KEY.containsMatchIn(key) && s.none { it.isWhitespace() }) url = s
                    else if (b64 == null && s.length > 2000 && B64_KEY.containsMatchIn(key) && Regex("^[A-Za-z0-9+/=\\r\\n_-]+$").matches(s.take(200))) b64 = s
                }
            }
        }
        walk("", data, 0)
        return if (url == null && b64 == null) null else Audio(url, b64)
    }

    /** Words that mean "this task will never finish". The real status words are not in the docs given, so this is a safety net only; the poll also has a time limit. */
    fun looksFailed(data: Any?): Boolean {
        val o = data as? JSONObject ?: return false
        val st = o.optString("status", o.optString("state", "")).lowercase()
        return st in setOf("failed", "failure", "error", "cancelled", "canceled", "timeout")
    }

    /** Safe description of an answer: key names + value kinds only (never values, never the key, never links/base64). For the owner's copy-paste report. */
    fun shape(v: Any?, depth: Int = 0): String {
        if (depth > 4) return "..."
        return when (v) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> v.keys().asSequence().toList().sorted().take(30).joinToString(",", "{", "}") { k -> k + ":" + shape(v.opt(k), depth + 1) }
            is JSONArray -> "[" + (if (v.length() == 0) "" else shape(v.opt(0), depth + 1)) + "]x" + v.length()
            is String -> if (v.startsWith("https://")) "url" else if (v.length > 2000) "longtext" else "str"
            is Boolean -> "bool"
            is Number -> "num"
            else -> "?"
        }
    }

    /** Result of looking for the owner's voice id inside a voice-list answer. */
    class VoiceHit(val found: Boolean, val names: List<String>, val isUltron: Boolean)

    /** Finds the object that holds [voiceId] anywhere in [data] and collects its short text fields (name, title...). isUltron only when one of them says "ultron". */
    fun findVoiceInList(data: Any?, voiceId: String): VoiceHit {
        val id = voiceId.trim().lowercase()
        val names = ArrayList<String>()
        var found = false
        fun hasId(o: JSONObject): Boolean = o.keys().asSequence().any { k -> (o.opt(k) as? String)?.trim()?.lowercase() == id }
        fun walk(v: Any?, depth: Int) {
            if (depth > 6) return
            when (v) {
                is JSONObject -> {
                    if (hasId(v)) {
                        found = true
                        v.keys().forEach { k ->
                            val s = v.opt(k) as? String
                            if (s != null && s.length in 2..80 && s.lowercase() != id && !s.startsWith("http")) names.add(s)
                        }
                    }
                    v.keys().forEach { k -> walk(v.opt(k), depth + 1) }
                }
                is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), depth + 1)
            }
        }
        walk(data, 0)
        val nm = names.distinct().take(8)
        return VoiceHit(found, nm, nm.any { it.lowercase().contains("ultron") })
    }

    /** Short, owner-readable reason for a failed call. Never contains the key or the text that was spoken. */
    fun reason(http: Int, env: Envelope?): String = when {
        http == 598 -> "internet nahi"
        http == 599 -> "network error / time out"
        http == 401 || http == 403 -> "API key galat ya band (401/403)"
        http == 422 -> "request galat (422)"
        http == 429 -> "limit poori (429)"
        env != null && env.code != 0 && env.code != -1 -> "VoiSpark code=" + env.code + " " + env.message.take(60)
        env != null && env.code == -1 -> "VoiSpark ka jawab samajh nahi aaya"
        http !in 200..299 -> "HTTP " + http
        else -> "ok"
    }
}
