package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Provider-aware text helper for background jobs. Tries owner's Gemini key pool, then configured free providers. Keys are header-only and never logged. */
object CloudTaskText {
    fun generate(ctx: Context, system: String, user: String, maxChars: Int = 1200): String? {
        val cleanUser = OfflineQueueRules.cleanPayload(user)
        if (cleanUser.isBlank()) return null
        val keys = SecureStore.getKeys(ctx).ifEmpty { listOf(SecureStore.getKey(ctx)).filter { it.isNotBlank() } }.distinct()
        for (key in keys) {
            for (model in (listOf(Logic.DEFAULT_MODEL) + Logic.MODELS).distinct()) {
                val pair = gemini(model, key, system, cleanUser)
                if (pair.first in 200..299) {
                    val answer = try {
                        JSONObject(pair.second).optJSONArray("candidates")?.optJSONObject(0)
                            ?.optJSONObject("content")?.optJSONArray("parts")?.let { a ->
                                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("\n")
                            }
                    } catch (e: Exception) { "" }.orEmpty().trim()
                    if (answer.isNotBlank()) return answer.take(maxChars)
                }
                if (pair.first == 401 || pair.first == 403 || pair.first == 598) break
            }
        }
        if (!ExtCfg.freeChain(ctx)) return null
        val cool = Cooldowns()
        val response = ProviderChain.run(
            FreeProviders.ordered(),
            { name -> when (name) {
                "groq" -> SecureStore.getGroq(ctx)
                "openrouter" -> SecureStore.getSlot(ctx, "openrouter")
                "zai" -> SecureStore.getSlot(ctx, "zai")
                "zai2" -> SecureStore.getSlot(ctx, "zai2")
                "zai3" -> SecureStore.getSlot(ctx, "zai3")
                else -> ""
            } },
            { id -> id != "pollinations" || ExtCfg.freeKeyless(ctx) },
            system.take(1600), cleanUser.take(FreeProviders.MAX_USER_CHARS), cool, { System.currentTimeMillis() }, budgetMs = 90_000L, brain = true   // v7.151
        ) { p, _, body, key -> FreeHttp.post(p, body, key) }
        return response.text?.take(maxChars)?.takeIf(String::isNotBlank)
    }

    private fun gemini(model: String, key: String, system: String, user: String): Pair<Int, String> = try {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.take(4000)))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user.take(1200))))))
            .put("generationConfig", JSONObject().put("temperature", 0.25).put("maxOutputTokens", 900))
            .toString()
        // v7.78 speed: same fast-thinking field as the chat call (2.5 Flash budget 0, 3.x Flash level minimal); a 400 retries the old body once.
        val fast = GeminiSpeed.withFastThinking(model, body)
        val first = geminiPost(model, key, fast)
        if (fast != body && first.first == 400) geminiPost(model, key, body) else first
    } catch (e: Exception) { Pair(599, "") }

    private fun geminiPost(model: String, key: String, body: String): Pair<Int, String> = try {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 8000; c.readTimeout = 25000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        ApiUsage.hit("gemini", code)
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText().take(80_000) }.orEmpty()
        c.disconnect(); Pair(code, text)
    } catch (e: Exception) { Pair(599, "") }
}
