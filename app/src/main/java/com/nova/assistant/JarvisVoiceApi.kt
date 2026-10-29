package com.nova.assistant

import org.json.JSONObject

/**
 * v7.136: JARVIS voice (model FuturePresentLabs/tts-jarvis = VoxCPM2 + LoRA adapter, English assistant voice). Pure rules, no network, no Android. Unit tested.
 * The model cannot run on a phone (Python + PyTorch + GPU, and it needs the owner's own reference.wav), so NOVA talks to a small server the owner hosts
 * (tools/jarvis_server). The contract between phone and server is ours and tiny:
 *   POST {base}/tts     header  Authorization: Bearer <token>  (only when a token is saved)   body {"text": "..."}   ->  200 audio/wav bytes
 *   GET  {base}/health  ->  200 {"ok":true,...}
 * Replaces the old online voice service (removed in v7.136).
 */
object JarvisVoiceApi {
    const val DEFAULT_MAX_CHARS = 300      // the model card: longer synthesis can develop noise, so only short replies use it
    const val MIN_MAX_CHARS = 40
    const val MAX_MAX_CHARS = 800
    /** After a failure (server off, bad token, no net) Jarvis is skipped for this long so replies are not slowed by retries. */
    const val PAUSE_MS = 10 * 60 * 1000L
    const val MAX_AUDIO = 8_000_000

    fun clampMaxChars(n: Int): Int = n.coerceIn(MIN_MAX_CHARS, MAX_MAX_CHARS)
    fun tooLong(text: String, maxChars: Int): Boolean = text.length > maxChars

    /**
     * The server address the owner typed. Returns "" when it is not usable. https only (the token travels with every request);
     * http is accepted ONLY for 127.0.0.1 / localhost (JUnit and a PC test). No user:pass@, no query, no fragment. A trailing "/" or "/tts" is dropped.
     */
    fun cleanBase(raw: String?): String {
        var s = (raw ?: "").trim()
        if (s.isEmpty() || s.length > 300 || s.any { it.isWhitespace() }) return ""
        val lower = s.lowercase()
        val scheme = when { lower.startsWith("https://") -> "https"; lower.startsWith("http://") -> "http"; else -> return "" }
        var rest = s.substring(scheme.length + 3)
        if (rest.contains('?') || rest.contains('#') || rest.contains('@') || rest.contains('\\')) return ""
        while (rest.endsWith("/")) rest = rest.dropLast(1)
        if (rest.lowercase().endsWith("/tts")) rest = rest.dropLast(4)
        while (rest.endsWith("/")) rest = rest.dropLast(1)
        if (rest.isEmpty()) return ""
        val host = rest.substringBefore('/').substringBefore(':').lowercase()
        if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return ""
        val port = rest.substringBefore('/').substringAfter(':', "")
        if (port.isNotEmpty() && (port.length > 5 || !port.all { it.isDigit() })) return ""
        if (scheme == "http" && host != "127.0.0.1" && host != "localhost") return ""
        if (scheme == "https" && !host.contains('.')) return ""
        return scheme + "://" + rest
    }

    fun host(base: String): String = base.substringAfter("://").substringBefore('/').substringBefore(':').lowercase()
    fun ttsUrl(base: String): String = base + "/tts"
    fun healthUrl(base: String): String = base + "/health"
    fun body(text: String): String = JSONObject().put("text", text).toString()

    /** RIFF....WAVE header and some payload. The server sends a wav file; anything else (an HTML error page) is rejected. */
    fun isWav(b: ByteArray?): Boolean =
        b != null && b.size > 200 && b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte() &&
            b[8] == 'W'.code.toByte() && b[9] == 'A'.code.toByte() && b[10] == 'V'.code.toByte() && b[11] == 'E'.code.toByte()

    /** The model is English-only ("multilingual performance has not been validated"): no Devanagari, and at least 90% of the letters are plain A-Z. */
    fun englishOk(text: String): Boolean {
        var letters = 0; var ascii = 0
        for (c in text) {
            if (c in 'ऀ'..'ॿ') return false
            if (c.isLetter()) { letters++; if (c in 'A'..'Z' || c in 'a'..'z') ascii++ }
        }
        return letters > 0 && ascii * 10 >= letters * 9
    }

    /** Short owner-readable reason for a failed call. 598 = no internet, 599 = other network error / timeout / not a wav. */
    fun reason(http: Int): String = when {
        http == 401 || http == 403 -> "token galat ya missing (HTTP $http)"
        http == 404 -> "URL galat (HTTP 404): address ke aage /tts hona chahiye server pe"
        http == 413 || http == 422 -> "text server ko manzoor nahi (HTTP $http)"
        http == 429 -> "server abhi busy hai (HTTP 429)"
        http in 500..599 && http < 598 -> "server error (HTTP $http)"
        http == 598 -> "internet / server tak nahi pahunch paya"
        http == 599 -> "time out ya galat jawab"
        http == 0 -> "address sahi nahi"
        else -> "HTTP $http"
    }
}
