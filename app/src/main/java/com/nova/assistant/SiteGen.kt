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
    class Out(val ok: Boolean, val html: String = "", val msg: String = "", val problems: List<String> = emptyList(), val template: String = "", val quality: List<String> = emptyList())

    const val MAX_HTML = 400_000          // the page the AI writes (media are still short tokens at this point)
    const val MAX_FINAL = 14_000_000      // the finished page with photos/videos embedded as data: URIs
    const val MAX_REQ = 8000              // owner's whole request; long detailed briefs are no longer cut at 1500
    private const val BUDGET_MS = 170_000L
    private const val POLISH_BEFORE_MS = 100_000L   // pass 2 only starts while there is still time for a whole page

    const val SYSTEM = "You are an award-winning senior web designer and front-end engineer. Reply with ONE complete, self-contained HTML5 file and nothing else: " +
        "start with <!DOCTYPE html>, end with </html>. Put CSS in <style> and JavaScript in an inline <script>. " +
        "No external images; the only external stylesheet allowed is Google Fonts (with a system-font fallback). Inline CSS transforms, perspective, preserve-3d, translateZ, SVG, canvas/WebGL and CSS/JS animation are encouraged when the request calls for them; " +
        "the only external scripts allowed are the optional three.js and GSAP builds named in the QUALITY BAR, always with a working fallback. " +
        "When the owner supplied no photos, never leave empty grey image boxes: draw rich inline SVG illustrations, layered gradients, patterns and icons that fit the business, so every section still looks finished and professional. " +
        "Every site must look different: follow the DESIGN DIRECTION, never reuse one generic template or one colour scheme. " +
        "Do not force a generic business template: choose a real information architecture that matches the exact requested deliverable. " +
        "Preserve every requirement, named item, quantity, colour, and explicit exclusion in the REQUIREMENTS CONTRACT. " +
        "Do what the owner literally asks; when the owner names colours, sections, effects or media, use exactly those. " +
        "Mobile-first responsive layout and readable contrast unless contradicted by the request. " +
        "Never include login forms that send data anywhere, trackers, or code that copies a real brand."

    const val PATCH_SYSTEM = "You are a careful web engineer editing an EXISTING website file. Output ONLY edit blocks, no explanations, no code fences. " +
        "Each block has exactly this shape:\n<<<FIND\n(exact text copied from the CURRENT HTML, 1 to 8 lines, unique)\n===REPLACE\n(new text)\n>>>END\n" +
        "Change only what the owner asked; keep every other part, feature, text and script exactly as it is."

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

    /** v6.90: improve the website NOVA already built. v7.0: the WHOLE page goes to the model (it was cut at 24000 chars before, which dropped content). */
    fun improvePrompt(subject: String, request: String, baseHtml: String): String = buildString {
        append("Improve the existing website below as the owner asks. Keep all current content, names, photos and working features; change only what the request needs ")
        append("(colours/background, more professional look, clean typography, dark/light mode, animations, layout, extra sections, new media). Return ONE complete corrected HTML5 file. ")
        append("Tokens like __NOVA_IMG_1__ and __NOVA_VID_1__ are the owner's real photos/videos: keep them exactly as written.\n")
        append("OWNER REQUEST: ").append(request.take(MAX_REQ)).append("\nTOPIC: ").append(subject.take(300))
        append("\nCURRENT HTML:\n").append(baseHtml.take(MAX_HTML))
    }

    /** v7.0: small, safe edits (background colour, a word, one section) come back as find/replace blocks; everything else in the page stays byte for byte. */
    fun patchPrompt(subject: String, request: String, baseHtml: String): String = buildString {
        append("OWNER REQUEST (do exactly this): ").append(request.take(MAX_REQ)).append("\nTOPIC: ").append(subject.take(300)).append('\n')
        append("If the change touches many places (for example a whole-page background/theme colour), add ONE override block before </head> such as <style id=\"nova-override\">...</style> ")
        append("using CSS variables and !important, and fix text/button contrast so everything stays readable. To add a new section, insert it before the footer or </body>. ")
        append("Tokens like __NOVA_IMG_1__ / __NOVA_VID_1__ are real uploaded media: keep them exactly.\n")
        append("CURRENT HTML:\n").append(baseHtml.take(MAX_HTML))
    }

    /** Pure: applies <<<FIND ... ===REPLACE ... >>>END blocks. Null when any block does not match (caller then rewrites the full page). */
    fun applyPatch(base: String, raw: String): String? {
        val rx = Regex("<<<FIND\\r?\\n(.*?)\\r?\\n===REPLACE\\r?\\n(.*?)\\r?\\n?>>>END", RegexOption.DOT_MATCHES_ALL)
        var out = base
        var n = 0
        for (m in rx.findAll(raw)) {
            val find = m.groupValues[1]
            val rep = m.groupValues[2]
            if (find.isBlank()) return null
            val last = find.trim().equals("</body>", true) || find.trim().equals("</html>", true)
            var idx = if (last) out.lastIndexOf(find) else out.indexOf(find)
            var len = find.length
            var put = rep
            if (idx < 0) {
                val tf = find.trim()
                idx = if (last) out.lastIndexOf(tf) else out.indexOf(tf)
                if (idx < 0) return null
                len = tf.length
                put = rep.trim('\n')
            }
            out = out.substring(0, idx) + put + out.substring(idx + len)
            n++
        }
        if (n == 0 || out == base) return null
        if (out.length > MAX_HTML || !out.lowercase().contains("</html>")) return null
        return out
    }

    fun userPrompt(subject: String, raw: String = "", mediaText: String = "", seed: Long = System.nanoTime()): String {
        val request = raw.trim().take(MAX_REQ).ifBlank { subject.trim().take(MAX_REQ) }
        val spec = SpecContract.analyze(request)
        return buildString {
            append("Build the exact website requested. Topic summary: ").append(subject.take(300))
            append("\nContext: NOVA is the owner's own Android voice assistant app (Hindi/English voice commands, wake word Nova). ")
            append("If the owner talks about the NOVA app or downloading it, include install steps and a placeholder APK link only if no real URL was supplied. ")
            append("Never invent a working download URL.\n")
            append(SpecContract.promptBlock(spec, request))
            append(SiteDesign.brief(request, seed))
            append("\n\n").append(SiteDesign.CRAFT).append("\n\n").append(SiteDesign.LUXURY)
            append(mediaText)
        }
    }

    fun failMsg(): String = "Abhi website nahi ban payi. Thodi der baad dobara bolo"

    /** Build -> validate -> one targeted repair. Keeps the provider order and key-in-header behaviour.
     *  v7.0: edits try a small find/replace patch first; owner's photos/videos are embedded after writing; long requests are kept whole. */
    fun generate(ctx: Context, subject: String, raw: String = "", baseHtml: String? = null): Out {
        val start = System.currentTimeMillis()
        val fullRequest = raw.trim().take(MAX_REQ).ifBlank { subject.trim().take(MAX_REQ) }
        val spec = SpecContract.analyze(fullRequest)
        val media = try { SiteMedia.list(ctx) } catch (e: Throwable) { emptyList() }
        val isEdit = !baseHtml.isNullOrBlank()
        val userItems = try { SiteMediaStore.items() } catch (e: Throwable) { emptyList() }   // v7.12: pictures / videos the owner picked from the gallery
        val wantAi = !isEdit && userItems.isEmpty() && SiteMedia.wantsAiPictures(fullRequest)
        val mediaText = SiteMedia.describe(media) + SiteMedia.userBlock(userItems) +
            (if (wantAi) SiteMedia.promptBlock(true, MediaIntent.wantsVideo(fullRequest)) else "") +
            (if (isEdit) "Keep every NOVA_IMG:<text> and NOVA_USER_MEDIA:<id> value exactly as written.\n" else "")

        var template: String? = null
        if (isEdit) {
            val rawPatch = requestHtml(ctx, PATCH_SYSTEM, patchPrompt(subject, fullRequest, baseHtml!!) + mediaText, start) { if (it.contains("<<<FIND")) it else null }
            if (rawPatch != null) template = applyPatch(baseHtml, rawPatch)
        }
        var problems: List<String> = emptyList()
        var quality: List<String> = emptyList()
        if (template == null) {
            val prompt = if (isEdit) improvePrompt(subject, fullRequest, baseHtml!!) + mediaText else userPrompt(subject, fullRequest, mediaText)
            val candidate = requestHtml(ctx, SYSTEM, prompt, start) ?: return Out(false, "", failMsg())
            val first = (if (isEdit) emptyList() else SpecContract.check(spec, candidate)) + mediaProblems(candidate, media)
            var bestHtml = candidate
            var bestProblems = first
            // One repair call only, and only while the initial generation was reasonably quick.
            if (first.isNotEmpty() && System.currentTimeMillis() - start < 90_000L) {
                val repairPrompt = buildString {
                    append("Repair this website to match the contract. Return ONE complete corrected HTML5 document only. ")
                    append("Do not remove requested features or add excluded features. Keep the design and all existing content.\n")
                    append("EXACT DETECTED GAPS:\n")
                    first.forEach { append("- ").append(it).append('\n') }
                    append(SpecContract.promptBlock(spec, fullRequest))
                    append(mediaText)
                    append("\nCURRENT HTML TO REPAIR:\n")
                    append(candidate.take(MAX_HTML))
                }
                val repaired = requestHtml(ctx, SYSTEM, repairPrompt, start)
                if (repaired != null) {
                    val rp = (if (isEdit) emptyList() else SpecContract.check(spec, repaired)) + mediaProblems(repaired, media)
                    if (rp.size < bestProblems.size) {
                        bestHtml = repaired
                        bestProblems = rp
                    }
                }
            }
            // Part 8 PASS 2 (new sites only): the premium gate reads the page; if it finds real gaps ONE polish call fixes them.
            // The polished page is kept only if it is better; otherwise pass 1 stays (nothing is lost).
            if (!isEdit) {
                quality = SiteQuality.check(bestHtml)
                if (quality.isNotEmpty() && System.currentTimeMillis() - start < POLISH_BEFORE_MS) {
                    val polished = requestHtml(ctx, SYSTEM, SiteQuality.polishPrompt(subject, fullRequest, bestHtml, quality, bestProblems, mediaText), start)
                    if (polished != null) {
                        val pq = SiteQuality.check(polished)
                        val pp = SpecContract.check(spec, polished) + mediaProblems(polished, media)
                        if (SiteQuality.better(quality.size, bestProblems.size, pq.size, pp.size)) {
                            bestHtml = polished
                            bestProblems = pp
                            quality = pq
                        }
                    }
                }
            }
            template = bestHtml
            problems = bestProblems
        }
        var finalHtml = SiteMedia.substitute(template, media)
        finalHtml = SiteMedia.fillImages(finalHtml, aiPictures(ctx, template, start, wantAi || aiCache.isNotEmpty()))   // v7.12
        val applied = SiteMedia.applyUser(finalHtml, userItems)
        finalHtml = applied.html
        userPlaced = applied.placed
        if (finalHtml.length > MAX_FINAL) return Out(false, "", "Website bahut bhaari ho gayi (photo/video zyada hain). Kuch media hatao aur dobara bolo")
        val msg = if (problems.isEmpty()) "" else "Requirements not fully verified: " + problems.joinToString("; ").take(700)
        return Out(true, finalHtml, msg, problems, template, quality)
    }

    /** v7.12: ids of the owner's gallery items that ended up in the last page (read by NovaService for an honest sentence). */
    @Volatile var userPlaced: List<String> = emptyList()

    /** description -> data URI of the AI pictures already made (kept in memory so an edit does not pay for them again). */
    private val aiCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Makes the AI pictures a page asks for (max 4, only while the build is young); failures simply stay pixels. */
    private fun aiPictures(ctx: Context, template: String, start: Long, allowNew: Boolean): Map<String, String> {
        val wanted = SiteMedia.placeholders(template).take(4)
        if (wanted.isEmpty()) return emptyMap()
        for (d in wanted) {
            if (aiCache.containsKey(d) || !allowNew) continue
            if (System.currentTimeMillis() - start > 170_000L) break
            try {
                val r = ImageGen.generate(ctx, d, null)
                val raw = r.img
                if (r.ok && raw != null) {
                    val small = ImageGen.shrink(raw, 1024) ?: raw
                    aiCache[d] = "data:image/jpeg;base64," + ImageGen.b64(small)
                }
            } catch (e: Throwable) { ErrLog.addOnce("quiet:SiteGen", e.toString()) }
        }
        val out = HashMap<String, String>()
        for (d in wanted) aiCache[d]?.let { out[d] = it }
        return out
    }

    /** Pure-ish: uploaded media the page forgot to place. */
    private fun mediaProblems(html: String, media: List<SiteMedia.Item>): List<String> {
        val unused = SiteMedia.unusedTokens(html, media)
        return if (unused.isEmpty()) emptyList() else listOf("uploaded media not placed in the page: " + unused.joinToString(", "))
    }

    /** Same configured provider sequence as before; first complete HTML response wins. */
    private fun requestHtml(ctx: Context, system: String, prompt: String, start: Long, clean: (String) -> String? = { cleanHtml(it) }): String? {
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
                if (r.first in 200..299) clean(geminiText(r.second))?.let { return it }
                if (r.first == 401 || r.first == 403 || r.first == 598) break
            }
        }
        if (groqKey.isNotEmpty()) {
            for (model in FreeProviders.GROQ.models) {
                if (System.currentTimeMillis() - start > BUDGET_MS) return null
                val r = groq(model, groqKey, system, prompt)
                if (r.first in 200..299) clean(groqText(r.second))?.let { return it }
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
                    if (r.code in 200..299) clean(longProviderText(r.body) ?: "")?.let { return it }
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
            .put("generationConfig", JSONObject().put("maxOutputTokens", 32000).put("temperature", 0.7))
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
