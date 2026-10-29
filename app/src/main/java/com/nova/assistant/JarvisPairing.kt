package com.nova.assistant

import org.json.JSONObject

/**
 * v7.137: one-tap pairing code for the Jarvis server. The Colab notebook prints (and copies) ONE line:
 *   NOVA-JARVIS:<base64url of {"a":"https://....trycloudflare.com","t":"<token>"}>
 * NOVA reads it from the clipboard, so the owner never types the address or the token. Pure rules, no Android, no network. Unit tested.
 * The code holds a secret (the token): it is never logged, never stored as a code, only split into the saved address and the encrypted token.
 */
object JarvisPairing {
    const val PREFIX = "NOVA-JARVIS:"
    private const val MAX_RAW = 2000

    class Paired(val address: String, val token: String)

    /** Builds a code (used by JUnit and the same format as the notebook). */
    fun encode(address: String, token: String): String {
        val j = JSONObject().put("a", address).put("t", token).toString()
        return PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(j.toByteArray(Charsets.UTF_8))
    }

    /** Finds a code inside any pasted text. Returns null when there is none or it is not safe to use. */
    fun parse(raw: String?): Paired? {
        if (raw == null || raw.length > MAX_RAW) return null
        val at = raw.indexOf(PREFIX)
        if (at < 0) return null
        val body = raw.substring(at + PREFIX.length).takeWhile { !it.isWhitespace() }
        if (body.isEmpty()) return null
        return try {
            val json = JSONObject(String(java.util.Base64.getUrlDecoder().decode(body), Charsets.UTF_8))
            val address = JarvisVoiceApi.cleanBase(json.optString("a", ""))
            val token = json.optString("t", "").trim()
            if (address.isEmpty()) null
            else if (token.isNotEmpty() && (token.length < 8 || token.length > 300 || token.any { it.isWhitespace() })) null
            else Paired(address, token)
        } catch (e: Exception) { null }
    }
}
