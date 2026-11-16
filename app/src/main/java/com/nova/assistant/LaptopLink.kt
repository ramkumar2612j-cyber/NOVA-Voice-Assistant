package com.nova.assistant

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * v7.177 LAPTOP LINK, phone side (Part 2b). Wire format MUST match desktop/link.js (tools/test_laptop_link_contract.py checks both):
 *  key   = base64url(32 bytes) from the laptop code "JARVIS-LINK:<key>"
 *  topic = "jarvis-" + hex(sha256("<l2p|p2l>:" + key)).take(40)   (laptop->phone = l2p, phone->laptop = p2l)
 *  env   = base64url( iv(12) || AES-256-GCM ciphertext || tag(16) ), AAD = topic bytes, plaintext = JSON {id, ts, type, text, re}
 *  types: "cmd" (run this on the receiver), "ask" (laptop asks the phone's AI; phone replies "reply", re = id), "reply"
 *  relay: POST https://ntfy.sh/<topic> (body = env); stream GET https://ntfy.sh/<topic>/json?since=<unix>
 * The pairing key lives only in SecureStore (slot "laptoplink"). API keys (Gemini / Groq ...) never leave the phone.
 */
object LaptopLink {
    const val SLOT = "laptoplink"
    const val RELAY = "https://ntfy.sh"
    private const val SKEW_MS = 120_000L
    private const val MAX_TEXT = 1500
    private val enc = Base64.getUrlEncoder().withoutPadding()
    private val dec = Base64.getUrlDecoder()
    private fun b64u(b: ByteArray): String = enc.encodeToString(b)
    private fun unb64u(s: String): ByteArray = dec.decode(s)

    fun topic(key: String, dir: String): String =
        "jarvis-" + MessageDigest.getInstance("SHA-256").digest("$dir:$key".toByteArray()).joinToString("") { "%02x".format(it) }.take(40)

    fun seal(key: String, topic: String, type: String, text: String, re: String = ""): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(unb64u(key), "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(topic.toByteArray())
        val id = b64u(ByteArray(9).also { SecureRandom().nextBytes(it) })
        val body = JSONObject().put("id", id).put("ts", System.currentTimeMillis()).put("type", type).put("text", text.take(MAX_TEXT)).put("re", re).toString()
        return b64u(iv + c.doFinal(body.toByteArray()))   // GCM output already ends with the 16-byte tag
    }

    /** null = tampered / wrong key / stale / not a message. */
    fun open(key: String, topic: String, env: String, now: Long = System.currentTimeMillis()): JSONObject? = try {
        val raw = unb64u(env)
        if (raw.size < 29) null else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(unb64u(key), "AES"), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            c.updateAAD(topic.toByteArray())
            val m = JSONObject(String(c.doFinal(raw.copyOfRange(12, raw.size))))
            if (m.optString("id").isEmpty() || m.optString("type").isEmpty()) null
            else if (Math.abs(now - m.getLong("ts")) > SKEW_MS) null else m
        }
    } catch (e: Exception) { null }

    /** true only when the relay answered 2xx (that is "handed to the relay", not "the other side ran it"). */
    fun publish(base: String, topic: String, env: String): Boolean = try {
        val h = URL("$base/$topic").openConnection() as HttpURLConnection
        h.requestMethod = "POST"; h.doOutput = true; h.connectTimeout = 8000; h.readTimeout = 8000
        h.outputStream.use { it.write(env.toByteArray()) }
        val ok = h.responseCode in 200..299
        h.disconnect(); ok
    } catch (e: Exception) { false }

    /** Blocking stream loop: run on a background thread. onEnv gets each raw envelope. Returns when stop() is true. */
    fun listen(base: String, topic: String, stop: () -> Boolean, onEnv: (String) -> Unit) {
        while (!stop()) {
            var h: HttpURLConnection? = null
            try {
                h = URL("$base/$topic/json?since=${System.currentTimeMillis() / 1000 - 90}").openConnection() as HttpURLConnection
                h.connectTimeout = 10000; h.readTimeout = 0
                h.inputStream.bufferedReader().useLines { ls ->
                    for (l in ls) {
                        if (stop()) break
                        try { val j = JSONObject(l); if (j.optString("event") == "message") onEnv(j.getString("message")) } catch (e: Exception) { }
                    }
                }
            } catch (e: Exception) { }
            try { h?.disconnect() } catch (e: Exception) { }
            try { Thread.sleep(4000) } catch (e: InterruptedException) { return }
        }
    }
}

/** Pure rules (no Android classes): pairing code parse, the "laptop par <kaam>" phrase, replay dedupe. Unit tested. */
object LaptopLinkRules {
    private val KEY = Regex("^[A-Za-z0-9_-]{43}$")   // base64url of 32 bytes, no padding
    private val PHRASE = Regex("^(?:jarvis\\s+)?laptop\\s+(?:par|pe|me|mein)\\s+(.+)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** "JARVIS-LINK:<key>" (also with spaces around) -> the key, or null when it is not a valid pairing code. */
    fun parseCode(raw: String?): String? {
        if (raw == null || raw.length > 200) return null
        val t = raw.trim()
        val i = t.indexOf(':')
        if (i < 0 || !t.substring(0, i).trim().equals("JARVIS-LINK", ignoreCase = true)) return null
        val k = t.substring(i + 1).trim()
        return if (KEY.matches(k)) k else null
    }

    /** "laptop par chrome kholo" / "jarvis laptop pe screenshot lo" -> "chrome kholo"; anything else -> null. */
    fun laptopCommand(text: String?): String? {
        if (text == null) return null
        val m = PHRASE.find(text.trim()) ?: return null
        val c = m.groupValues[1].trim()
        return if (c.isEmpty()) null else c.take(1500)
    }

    /** Replay guard: the same message id is accepted once per 5 minutes. */
    class Dedupe(private val ttlMs: Long = 300_000L) {
        private val seen = HashMap<String, Long>()
        @Synchronized fun firstTime(id: String, now: Long = System.currentTimeMillis()): Boolean {
            val it = seen.entries.iterator()
            while (it.hasNext()) if (now - it.next().value > ttlMs) it.remove()
            if (seen.containsKey(id)) return false
            seen[id] = now
            return true
        }
    }
}
