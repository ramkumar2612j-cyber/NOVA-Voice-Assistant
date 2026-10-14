package com.nova.assistant

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * v16 WEBSITE BY VOICE, step 2; v6.22 provider coverage: write ONE self-contained index.html from configured text providers.
 * Order: the owner's Gemini key pool, dedicated Groq models, then enabled Groq/Qwen, Z.ai, OpenRouter and optional Pollinations
 * fallbacks. Nothing is uploaded or published here; only the site topic text goes to the provider. Keys go in request headers
 * only, never in a URL or a log. Image-only keys are not incorrectly treated as chat-completion keys.
 * Failure text never names a provider (owner asked for silent fallback).
 * Evidence level: written + statically checked. Not compiled here, not phone tested.
 */
object SiteGen {
    class Out(val ok: Boolean, val html: String = "", val msg: String = "", val problems: List<String> = emptyList())

    const val MAX_HTML = 400_000
    private const val BUDGET_MS = 170_000L

    const val SYSTEM = "You are a careful senior web engineer. Reply with ONE complete, self-contained HTML5 file and nothing else: " +
        "start with <!DOCTYPE html>, end with </html>. Put CSS in <style> and JavaScript in an inline <script>. " +
        "No external files, no external images, no CDN. Inline CSS transforms, perspective, preserve-3d, translateZ, SVG and canvas/WebGL are allowed when the request calls for them. " +
        "Do not force a generic business template: choose a real information architecture that matches the exact requested deliverable. " +
        "Preserve every requirement, named item, quantity, colour, and explicit exclusion in the REQUIREMENTS CONTRACT. " +
        "Mobile-first responsive layout and readable contrast unless contradicted by the request. " +
        "Never include login forms that send data anywhere, trackers, or code that copies a real brand."

    /** Pure: takes the model's raw answer, returns clean HTML or null when it is not a usable full page. */
    fun cleanHtml(raw: String): String? {
        var t = raw.trim()
        val lower = t.lowercase()
        var start = lower.indexOf("<!doctype")
        if (start < 0) start = lower.indexOf("<html")
        if (start < 0) return null
        val endTag = lower.lastIndexOf("</html>")
        if (endTag < start) return null
        t = t.substring(start, endTag + 7)
        if (t.length < 200 || t.length > MAX_HTML) return null
        return t
    }

    /** Pure: a safe ASCII file name part from the spoken topic. */
    fun slug(subject: String): String {
        val sb = StringBuilder()
        for (ch in subject.lowercase()) {
            if ((ch in 'a'..'z') || (ch in '0'..'9')) sb.append(ch) else if (sb.isNotEmpty() && sb.last() != '_') sb.append('_')
        }
        var s = sb.toString().trim('_')
        if (s.length > 24) s = s.substring(0, 24).trim('_')
        return if (s.isEmpty()) "site" else s
    }

    /**
     * v16b: the model gets the owner's WHOLE spoken sentence (the short subject loses words like "nova app download")
     * plus a short note on what NOVA is, so "ek website banao jahan nova app download ho sake" builds a NOVA download page.
     */
    /** v6.90: improve the website NOVA already built. The model gets the current page (capped) and the owner's change request. */
    fun improvePrompt(subject: String, request: String, baseHtml: String): String = buildString {
        append("Improve the existing website below as the owner asks. Keep all current content, names and working features; change only what the request needs ")
        append("(more professional look, clean typography, dark mode, animations, layout, extra sections). Return ONE complete corrected HTML5 file.\n")
        append("OWNER REQUEST: ").append(request.take(1500)).append("\nTOPIC: ").append(subject.take(300))
        append("\nCURRENT HTML (may be truncated at the end):\n").append(baseHtml.take(24000))
    }

    fun userPrompt(subject: String, raw: String = ""): String {
        val request = raw.trim().take(1500).ifBlank { subject.trim().take(1500) }
        val spec = SpecContract.analyze(request)
        return buildString {
            append("Build the exact website requested. Topic summary: ").append(subject.take(300))
            append("\nContext: NOVA is the owner's own Android voice assistant app (Hindi/English voice commands, wake word Nova). ")
            append("If the owner talks about the NOVA app or downloading it, include install steps and a placeholder APK link only if no real URL was supplied. ")
            append("Never invent a working download URL.\n")
            append(SpecContract.promptBlock(spec, request))
        }
    }

    fun failMsg(): String = "Abhi website nahi ban payi. Thodi der baad dobara bolo"

    /** Build -> validate -> one targeted repair. Keeps the provider order and key-in-header behaviour. */
    fun generate(ctx: Context, subject: String, raw: String = "", baseHtml: String? = null): Out {
        val start = System.currentTimeMillis()
        val fullRequest = raw.trim().take(1500).ifBlank { subject.trim().take(1500) }
        val spec = SpecContract.analyze(fullRequest)
        val prompt = if (baseHtml.isNullOrBlank()) userPrompt(subject, fullRequest) else improvePrompt(subject, fullRequest, baseHtml)
        val candidate = requestHtml(ctx, SYSTEM, prompt, start)
            ?: return Out(false, "", failMsg())
        val firstProblems = SpecContract.check(spec, candidate)
        if (firstProblems.isEmpty()) return Out(true, candidate)

        var bestHtml = candidate
        var bestProblems = firstProblems
        // One repair call only, and only while the initial generation was reasonably quick.
        if (System.currentTimeMillis() - start < 90_000L) {
            val repairPrompt = buildString {
                append("Repair this website to match the contract. Return ONE complete corrected HTML5 document only. ")
                append("Do not remove requested features or add excluded features.\n")
                append("EXACT DETECTED GAPS:\n")
                firstProblems.forEach { append("- ").append(it).append('\n') }
                append(SpecContract.promptBlock(spec, fullRequest))
                append("\nCURRENT HTML TO REPAIR (may be truncated):\n")
                append(candidate.take(18000))
            }
            val repaired = requestHtml(ctx, SYSTEM, repairPrompt, start)
            if (repaired != null) {
                val repairedProblems = SpecContract.check(spec, repaired)
                if (repairedProblems.size < bestProblems.size) {
                    bestHtml = repaired
                    bestProblems = repairedProblems
                }
            }
        }
        val msg = if (bestProblems.isEmpty()) "" else "Requirements not fully verified: " + bestProblems.joinToString("; ").take(700)
        return Out(true, bestHtml, msg, bestProblems)
    }

    /** Same configured provider sequence as before; first complete HTML response wins. */
    private fun requestHtml(ctx: Context, system: String, prompt: String, start: Long): String? {
        val gKeys = SecureStore.getKeys(ctx)
            .ifEmpty { listOf(SecureStore.getKey(ctx)).filter { it.isNotBlank() } }
            .distinct()
        val groqKey = SecureStore.getGroq(ctx)
        val otherProviderReady = (SecureStore.hasSlot(ctx, "zai") || SecureStore.hasSlot(ctx, "zai2") || SecureStore.hasSlot(ctx, "zai3") || SecureStore.hasSlot(ctx, "openrouter") || ExtCfg.freeKeyless(ctx)) && ExtCfg.freeChain(ctx)
        if (gKeys.isEmpty() && groqKey.isEmpty() && !otherProviderReady) return null
        for (key in gKeys) {
            for (model in (listOf(Logic.DEFAULT_MODEL) + Logic.MODELS).distinct()) {
                if (System.currentTimeMillis() - start > BUDGET_MS) return null
                val r = gemini(model, key, system, prompt)
                if (r.first in 200..299) cleanHtml(geminiText(r.second))?.let { return it }
                if (r.first == 401 || r.first == 403 || r.first == 598) break
            }
        }
        if (groqKey.isNotEmpty()) {
            for (model in FreeProviders.GROQ.models) {
                if (System.currentTimeMillis() - start > BUDGET_MS) return null
                val r = groq(model, groqKey, system, prompt)
                if (r.first in 200..299) cleanHtml(groqText(r.second))?.let { return it }
                if (r.first == 401 || r.first == 403 || r.first == 598) break
            }
        }
        if (ExtCfg.freeChain(ctx)) {
            for (provider in FreeProviders.ordered()) {
                val key = when (provider.keyName) {
                    "groq" -> SecureStore.getGroq(ctx)
                    "openrouter" -> SecureStore.getSlot(ctx, "openrouter")
                    "zai" -> SecureStore.getSlot(ctx, "zai")
                    "zai2" -> SecureStore.getSlot(ctx, "zai2")
                    "zai3" -> SecureStore.getSlot(ctx, "zai3")
                    else -> ""
                }
                if (provider.keyName != null && key.isBlank()) continue
                if (provider.id == "pollinations" && !ExtCfg.freeKeyless(ctx)) continue
                for (model in provider.models) {
                    if (System.currentTimeMillis() - start > BUDGET_MS) return null
                    val body = FreeProviders.buildBody(model, system, prompt, 14000, provider.extra)
                    val r = FreeHttp.post(provider, body, key)
                    if (r.code in 200..299) cleanHtml(longProviderText(r.body) ?: "")?.let { return it }
                    if (r.code == 401 || r.code == 403 || r.code == 598) break
                }
            }
        }
        return null
    }

    private fun longProviderText(body: String): String? = try {
        val root = MiniJson.parse(body)
        val msg = MiniJson.field(MiniJson.at(MiniJson.field(root, "choices"), 0), "message")
        (MiniJson.field(msg, "content") as? String)?.trim()?.takeIf { it.isNotBlank() && it.length <= MAX_HTML * 2 }
    } catch (e: Exception) { null }

    private fun gemini(model: String, key: String, system: String, prompt: String): Pair<Int, String> {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 16000).put("temperature", 0.7))
            .toString()
        val r = http("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body) { c ->
            c.setRequestProperty("x-goog-api-key", key)
        }
        ApiUsage.hit("gemini", r.first)
        return r
    }

    private fun groq(model: String, key: String, system: String, prompt: String): Pair<Int, String> {
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", prompt)))
            .put("max_tokens", 7000)
            .put("temperature", 0.7)
            .toString()
        val r = http(FreeProviders.GROQ.url, body) { c ->
            c.setRequestProperty("Authorization", "Bearer " + key)
        }
        ApiUsage.hit("groq", r.first)
        return r
    }

    private fun http(url: String, body: String, headers: (HttpURLConnection) -> Unit): Pair<Int, String> {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 120000
            c.instanceFollowRedirects = false
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers(c)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            return Pair(code, text)
        } catch (e: java.net.UnknownHostException) {
            return Pair(598, "")
        } catch (e: Exception) {
            return Pair(599, "")
        }
    }

    private fun geminiText(resp: String): String {
        try {
            val parts = JSONObject(resp).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                if (p.has("text")) sb.append(p.optString("text"))
            }
            return sb.toString()
        } catch (e: Exception) {
            return ""
        }
    }

    private fun groqText(resp: String): String {
        try {
            return JSONObject(resp).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: ""
        } catch (e: Exception) {
            return ""
        }
    }

    /** Saves into Downloads > NOVA (Android 10+), else the app's own Documents folder. Returns a short place name or null. */
    fun save(ctx: Context, subject: String, html: String): String? {
        try {
            val name = slug(subject) + "_" + System.currentTimeMillis() + ".html"
            val bytes = html.toByteArray(Charsets.UTF_8)
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues()
                cv.put(MediaStore.Downloads.DISPLAY_NAME, name)
                cv.put(MediaStore.Downloads.MIME_TYPE, "text/html")
                cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/NOVA")
                cv.put(MediaStore.Downloads.IS_PENDING, 1)
                val res = ctx.contentResolver
                val uri = res.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                val os = res.openOutputStream(uri) ?: return null
                os.use { it.write(bytes) }
                val done = ContentValues()
                done.put(MediaStore.Downloads.IS_PENDING, 0)
                res.update(uri, done, null, null)
                return "Downloads > NOVA > " + name
            }
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: return null
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
            return "App folder (Android/data/com.nova.assistant/files/Documents) > " + name
        } catch (e: Throwable) {
            return null
        }
    }
}
