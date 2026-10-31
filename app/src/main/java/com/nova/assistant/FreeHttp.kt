package com.nova.assistant

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

/**
 * Part 1B: the real network call for ProviderChain. HTTPS only, no redirects, small timeouts, body capped.
 * Never throws: no internet -> 598, any other IO / timeout error -> 599 (same codes ProviderChain expects).
 * Never logs the key, the question or the answer.
 */
object FreeHttp {
    private const val MAX_BODY = 200_000
    private const val CONNECT_MS = 6000
    private const val READ_MS = 10000
    private const val BRAIN_READ_MS = 45000

    fun post(p: FreeProvider, jsonBody: String, key: String, connectMs: Int = CONNECT_MS, readMs: Int = READ_MS): NetAnswer {
        var conn: HttpURLConnection? = null
        try {
            val u = URL(p.url)
            if (u.protocol != "https") return NetAnswer(0, "")
            val c = u.openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.connectTimeout = connectMs
            c.readTimeout = if (p.brainOnly && readMs == READ_MS) BRAIN_READ_MS else readMs   // v7.151: a reasoning model thinks first, 10 s is too short
            c.instanceFollowRedirects = false
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            if (key.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer " + key)
            if (p.keyName == "openrouter") {
                c.setRequestProperty("HTTP-Referer", "https://github.com/nova-assistant")
                c.setRequestProperty("X-Title", "NOVA")
            }
            c.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val groqLike = p.keyName == "groq"
            ApiUsage.hit(
                p.id, code,
                if (groqLike) c.getHeaderField("x-ratelimit-limit-requests") else null,
                if (groqLike) c.getHeaderField("x-ratelimit-remaining-requests") else null,
                if (groqLike) c.getHeaderField("x-ratelimit-reset-requests") else null
            )
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = if (stream == null) "" else readCapped(stream)
            return NetAnswer(code, body)
        } catch (e: UnknownHostException) {
            return NetAnswer(598, "")
        } catch (e: ConnectException) {
            return NetAnswer(598, "")
        } catch (e: Exception) {
            return NetAnswer(599, "")
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
            }
        }
    }

    private fun readCapped(s: InputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        s.use { input ->
            while (out.size() < MAX_BODY) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        }
        return out.toString("UTF-8")
    }
}
