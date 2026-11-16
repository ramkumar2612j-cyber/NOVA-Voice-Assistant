package com.nova.assistant

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v7.197 SELF PATCH ("jarvis khud code likho: ..."): owner decision 2026-11-16 - JARVIS writes Kotlin with AI and pushes it
 * straight to `main` WITHOUT asking each time. What replaces asking (all in [SelfPatchRules] + here):
 *  - wish is refused if it touches HARD DENY (payment / order / password / OTP / PIN / CVV) or secrets / workflow files;
 *  - only app/src/main/java/com/nova/assistant/ ke andar ki .kt files, never the protected files (secrets, installer, GitHub access, this feature);
 *  - AI reply must pass validation (package line, brackets balanced, no forbidden calls, not a truncated answer);
 *  - max 5 pushes a day; ONE file per request;
 *  - GitHub Actions builds the push. If that build goes RED (or tests fail) the OLD file is put back automatically
 *    (a new file is overwritten with an empty stub). "code wapas karo" does the same by hand.
 * The installed APK is NOT changed by this: a green build just exists on GitHub; installing it is the separate, owner-tapped
 * "apk banao" / "apk install karo" flow (GitHubSelfBuild).
 * Evidence level: SelfPatchRules is compiled and run for real (tools/test_self_patch_contract.py). THIS file (network, GitHub
 * Contents API, Gemini call) is only parsed + statically checked here - never run against real GitHub: NOT RUN.
 */
object GitHubSelfPatch {
    class Out(val ok: Boolean, val msg: String = "")

    private const val PREFS = "jarvis_selfpatch"
    private val lock = Any()
    @Volatile private var watching = false

    private fun repo(): String = GitHubUpgradePublish.OWNER_REPO
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- state (kept in prefs so "code status" still works after the app / service restarts) ----
    private fun setState(ctx: Context, phase: String, msg: String) {
        prefs(ctx).edit().putString("phase", phase).putString("msg", msg).apply()
    }
    private fun phase(ctx: Context): String = prefs(ctx).getString("phase", SelfPatchRules.IDLE) ?: SelfPatchRules.IDLE
    private fun message(ctx: Context): String = prefs(ctx).getString("msg", "") ?: ""

    fun statusJson(ctx: Context): String {
        val p = phase(ctx)
        return JSONObject().put("phase", p).put("label", SelfPatchRules.phaseLabel(p)).put("msg", message(ctx))
            .put("path", prefs(ctx).getString("path", "") ?: "").toString()
    }

    private fun countToday(ctx: Context): Int {
        val p = prefs(ctx)
        val today = SelfPatchRules.dayKey(System.currentTimeMillis())
        return if (p.getLong("day", 0L) == today) p.getInt("count", 0) else 0
    }
    private fun bumpToday(ctx: Context) {
        val today = SelfPatchRules.dayKey(System.currentTimeMillis())
        prefs(ctx).edit().putLong("day", today).putInt("count", countToday(ctx) + 1).apply()
    }

    // ---- GitHub ----
    private fun call(token: String, method: String, path: String, body: String?): Pair<Int, String> {
        return try {
            val c = URL("https://api.github.com" + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15000; c.readTimeout = 40000
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "JARVIS-assistant")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            Pair(code, text)
        } catch (e: java.net.UnknownHostException) { Pair(598, "") }
        catch (e: Exception) { Pair(599, "") }
    }

    private fun failFor(code: Int): String = when (code) {
        401 -> "GitHub token galat ya expire ho gaya. Settings mein naya token daalo"
        403, 404 -> "GitHub ne mana kiya. Token mein 'repo' aur 'workflow' scope chahiye"
        409, 422 -> "GitHub par file badal chuki hai ya conflict hai. Dobara try karo"
        598 -> "Internet nahi mil raha"
        else -> "Abhi nahi ho paya. Thodi der baad dobara try karo"
    }

    private fun enc(s: String): String = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    private fun dec(b64: String): String = String(Base64.decode(b64.replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)

    /** Existing file on main: Pair(blob sha, text) or null when it does not exist. Throws nothing; code 0 = error. */
    private class Remote(val code: Int, val sha: String, val text: String)
    private fun getFile(token: String, path: String): Remote {
        val r = call(token, "GET", "/repos/${repo()}/contents/$path?ref=main", null)
        if (r.first == 404) return Remote(404, "", "")
        if (r.first !in 200..299) return Remote(r.first, "", "")
        return try {
            val o = JSONObject(r.second)
            Remote(200, o.optString("sha"), dec(o.optString("content")))
        } catch (e: Exception) { Remote(599, "", "") }
    }

    /** PUT one file to main. Returns Pair(http code, commit sha). */
    private fun putFile(token: String, path: String, text: String, sha: String?, message: String): Pair<Int, String> {
        val b = JSONObject().put("message", message).put("content", enc(text)).put("branch", "main")
        if (!sha.isNullOrEmpty()) b.put("sha", sha)
        val r = call(token, "PUT", "/repos/${repo()}/contents/$path", b.toString())
        if (r.first !in 200..299) return Pair(r.first, "")
        val commit = try { JSONObject(r.second).optJSONObject("commit")?.optString("sha") ?: "" } catch (e: Exception) { "" }
        return Pair(r.first, commit)
    }

    // ---- AI (own call: a whole file needs far more output room than CloudTaskText allows) ----
    private fun askAi(ctx: Context, system: String, user: String): String? {
        val keys = SecureStore.getKeys(ctx).ifEmpty { listOf(SecureStore.getKey(ctx)).filter { it.isNotBlank() } }.distinct()
        for (key in keys) {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
                .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 16000))
                .toString()
            try {
                val c = URL("https://generativelanguage.googleapis.com/v1beta/models/${Logic.DEFAULT_MODEL}:generateContent").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 150000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("x-goog-api-key", key)
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                ApiUsage.hit("gemini", code)
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                c.disconnect()
                if (code in 200..299) {
                    val parts = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) {
                        val out = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }.joinToString("\n")
                        if (out.isNotBlank()) return out
                    }
                }
            } catch (e: Exception) { /* try next key */ }
        }
        return null
    }

    // ---- main flow ----
    /** "jarvis khud code likho: ..." -> returns at once; the work runs on a background thread. */
    fun start(ctx: Context, wish: String): Out {
        val app = ctx.applicationContext
        synchronized(lock) {
            if (phase(app) == SelfPatchRules.WRITING) return Out(false, "Pichhla code abhi likha ja raha hai, thodi der ruko.")
            if (phase(app) == SelfPatchRules.PUSHED) return Out(false, "Pichhle code ka build abhi chal raha hai. \"code status\" bolo, khatam hone par naya kaam do.")
            if (SelfPatchRules.wishBlocked(wish)) return Out(false, "Ye kaam main khud nahi likhunga: payment / password / OTP / PIN / CVV / secrets / build files HARD DENY hain.")
            if (!SelfPatchRules.underCap(countToday(app))) return Out(false, "Aaj ki limit (${SelfPatchRules.MAX_PER_DAY} code push) poori ho gayi. Kal dobara bolo.")
            if (SecureStore.getSlot(app, GitHubPublish.SLOT).isEmpty()) return Out(false, "GitHub token nahi mila. Settings mein daalo.")
            setState(app, SelfPatchRules.WRITING, "AI code likh raha hai...")
        }
        Thread {
            try { doPatch(app, wish) }
            catch (e: Exception) { setState(app, SelfPatchRules.ERROR, "Kuch gadbad hui, kuch push nahi hua.") }
        }.start()
        return Out(true, "Theek hai, code likh raha hu. Pura hone par seedha main par daal dunga aur build chalega. Build RED hui to code khud wapas le lunga. \"code status\" bolo.")
    }

    private fun doPatch(ctx: Context, wish: String) {
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        val name = SelfPatchRules.targetName(wish)
        val expected = name?.let { SelfPatchRules.pathFor(it) }
        if (expected != null && !SelfPatchRules.isAllowedPath(expected)) { setState(ctx, SelfPatchRules.ERROR, "Ye file protected hai, main ise nahi badalunga."); return }
        var oldText: String? = null
        var oldSha: String? = null
        if (expected != null) {
            val g = getFile(token, expected)
            when {
                g.code == 200 -> {
                    if (g.text.toByteArray(Charsets.UTF_8).size > SelfPatchRules.MAX_EDIT_BYTES) { setState(ctx, SelfPatchRules.ERROR, "Ye file bahut badi hai (limit ${SelfPatchRules.MAX_EDIT_BYTES} bytes). Chhoti file ya naya file maango."); return }
                    oldText = g.text; oldSha = g.sha
                }
                g.code == 404 -> { /* named file does not exist yet = new file */ }
                else -> { setState(ctx, SelfPatchRules.ERROR, failFor(g.code)); return }
            }
        }
        var patch: SelfPatchRules.Patch? = null
        var why = ""
        for (attempt in 1..2) {
            val extra = if (attempt == 2) "\n\nPichhla jawab reject hua: $why. Is baar sahi format me poori file do." else ""
            val reply = askAi(ctx, SelfPatchRules.systemPrompt(), SelfPatchRules.userPrompt(wish, expected, oldText) + extra)
            val p = SelfPatchRules.parseReply(reply)
            if (p == null) { why = "format galat tha (PATH / SUMMARY / <<<CODE ... CODE>>>)"; continue }
            val err = SelfPatchRules.validate(p, expected, oldText?.length ?: 0)
            if (err != null) { why = err; continue }
            patch = p; break
        }
        if (patch == null) { setState(ctx, SelfPatchRules.ERROR, "AI ka code theek nahi aaya ($why). Kuch push nahi hua."); return }
        // AI chose its own new file name: it must not overwrite an existing file.
        if (expected == null) {
            val g = getFile(token, patch.path)
            if (g.code == 200) { setState(ctx, SelfPatchRules.ERROR, "AI ne mojooda file ka naam chuna. Kuch push nahi hua, dobara bolo."); return }
            if (g.code != 404) { setState(ctx, SelfPatchRules.ERROR, failFor(g.code)); return }
        }
        val put = putFile(token, patch.path, patch.code, oldSha, SelfPatchRules.commitMessage(patch.summary))
        if (put.first !in 200..299 || put.second.isEmpty()) { setState(ctx, SelfPatchRules.ERROR, failFor(put.first)); return }
        bumpToday(ctx)
        prefs(ctx).edit()
            .putString("path", patch.path).putString("commit", put.second)
            .putBoolean("existed", oldText != null).putString("old", if (oldText != null) enc(oldText) else "")
            .putLong("pushedAt", System.currentTimeMillis()).apply()
        setState(ctx, SelfPatchRules.PUSHED, "Code push ho gaya (" + patch.summary + "). Build chal raha hai, RED hua to khud wapas lunga.")
        watch(ctx)
    }

    /** Checks the build of OUR commit; on RED restores the old file. Safe to call again and again. */
    fun poll(ctx: Context): Out {
        val app = ctx.applicationContext
        if (phase(app) != SelfPatchRules.PUSHED) return Out(true, message(app).ifEmpty { SelfPatchRules.phaseLabel(phase(app)) })
        val token = SecureStore.getSlot(app, GitHubPublish.SLOT)
        val sha = prefs(app).getString("commit", "") ?: ""
        if (token.isEmpty() || sha.isEmpty()) return Out(false, "Token ya commit nahi mila")
        val r = call(token, "GET", "/repos/${repo()}/actions/runs?head_sha=$sha&per_page=10", null)
        if (r.first !in 200..299) return Out(false, failFor(r.first))
        var status = ""; var conclusion = ""
        try {
            val arr = JSONObject(r.second).optJSONArray("workflow_runs") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("path").endsWith("build.yml")) { status = o.optString("status"); conclusion = o.optString("conclusion"); break }
            }
        } catch (e: Exception) { }
        if (status.isEmpty()) return Out(true, "Build abhi list me nahi aaya, thodi der ruko.")
        if (status != "completed") return Out(true, "Build chal raha hai.")
        if (conclusion == "success") { setState(app, SelfPatchRules.OK, "Build GREEN. Naya code main me rakh liya. APK chahiye to \"apk banao\" bolo."); return Out(true, message(app)) }
        if (conclusion == "failure" || conclusion == "timed_out") {
            val rv = revert(app, "build RED, auto-revert")
            if (rv.ok) setState(app, SelfPatchRules.REVERTED, "Build RED aaya, isliye naya code khud wapas le liya. App pehle jaisa theek hai.")
            return Out(rv.ok, message(app).ifEmpty { rv.msg })
        }
        setState(app, SelfPatchRules.ERROR, "Build ka result ($conclusion) samajh nahi aaya. Code wapas nahi liya; \"code wapas karo\" bolo agar chahiye.")
        return Out(false, message(app))
    }

    /** "code wapas karo": puts the old file (or the empty stub for a new file) back on main. */
    fun undo(ctx: Context): Out {
        val app = ctx.applicationContext
        val ph = phase(app)
        if (ph != SelfPatchRules.PUSHED && ph != SelfPatchRules.OK) return Out(false, "Wapas lene layak koi code nahi hai.")
        val rv = revert(app, "undo by owner")
        if (rv.ok) setState(app, SelfPatchRules.UNDONE, "Code wapas le liya.")
        return Out(rv.ok, if (rv.ok) message(app) else rv.msg)
    }

    private fun revert(ctx: Context, why: String): Out {
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        val p = prefs(ctx)
        val path = p.getString("path", "") ?: ""
        if (token.isEmpty() || !SelfPatchRules.isAllowedPath(path)) return Out(false, "Wapas lene ke liye token / path theek nahi")
        val existed = p.getBoolean("existed", false)
        val old = p.getString("old", "") ?: ""
        val text = if (existed && old.isNotEmpty()) dec(old) else SelfPatchRules.STUB
        val cur = getFile(token, path)
        if (cur.code != 200) return Out(false, failFor(cur.code))
        val put = putFile(token, path, text, cur.sha, SelfPatchRules.commitMessage("auto-revert: $why"))
        if (put.first !in 200..299) return Out(false, failFor(put.first))
        return Out(true, "wapas ho gaya")
    }

    /** Background watcher: checks every 30 s for up to ~40 min. Not required for correctness ("code status" polls too). */
    private fun watch(ctx: Context) {
        if (watching) return
        watching = true
        Thread {
            try {
                for (i in 0 until 80) {
                    Thread.sleep(30000)
                    poll(ctx)
                    if (phase(ctx) != SelfPatchRules.PUSHED) break
                }
            } catch (e: Exception) { }
            finally { watching = false }
        }.start()
    }

    /** Voice / chat hook. Returns the spoken answer, or null when the text is not about self-code. */
    fun handleVoice(ctx: Context, norm: String, raw: String): String? {
        if (SelfPatchRules.isUndo(norm)) return undo(ctx).msg
        if (SelfPatchRules.isStatus(norm)) { poll(ctx); return message(ctx).ifEmpty { SelfPatchRules.phaseLabel(phase(ctx)) } }
        if (SelfPatchRules.isTrigger(norm)) {
            val wish = SelfPatchRules.parseWish(norm, raw)
                ?: return "Kya likhna hai? Aise bolo: jarvis khud code likho: <kya chahiye>. Kisi chhoti file ko badalna ho to uska naam likho (jaise Foo.kt); naam na do to main naya file banaunga."
            return start(ctx, wish).msg
        }
        return null
    }
}
