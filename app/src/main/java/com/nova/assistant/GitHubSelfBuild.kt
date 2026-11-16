package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * v7.196 SELF BUILD ("jarvis apk banao"): owner ki maang "ek baar install ke baad Jarvis khud GitHub se APK
 * banwaye, baar-baar Termux na chalana pade". Flow:
 *  1. trigger(): POST workflow_dispatches -> build.yml ko "main" par chalne ko bolta hai. Koi .kt file yahan se
 *     nahi badalta - sirf already-reviewed "main" branch ka wahi code dobara build hota hai.
 *  2. poll(): us run ki status padhta hai (read-only GET).
 *  3. Build GREEN hone par CI khud (is app se nahi) app-debug.apk + .sha256 file ko GitHub Release "jarvis-latest"
 *     ke roop me publish karti hai (dekho .github/workflows/build.yml) - us step ke liye GitHub ka apna built-in
 *     run token kaafi hai, koi extra secret nahi chahiye.
 *  4. install(): wahi release asset download karta hai, CI ne jo sha256 publish kiya usi se match karta hai, aur
 *     phir UpdateManager.installApkFile() ko deta hai - same package / strictly newer / same signing-key check +
 *     Android ka apna PackageInstaller + Install screen, bilkul doosre kisi update jaisa. Android khud silent
 *     install nahi deta, isliye aakhri "Install" tap hamesha owner ka hi rahega.
 * HARD DENY se koi lena-dena nahi (payment/order/password/OTP/PIN/CVV): ye sirf code build + install karta hai.
 * Evidence level: likha aur statically checked hi hai. Is sandbox me network/Android/Gradle nahi hai, isliye
 * kabhi compile ya asli GitHub call ke saath nahi chala (TEST_STATUS.md me "NOT RUN" likha jayega).
 */
object SelfBuildRules {
    const val IDLE = "idle"
    const val DISPATCHING = "dispatching"
    const val QUEUED = "queued"
    const val RUNNING = "running"
    const val GREEN = "green"
    const val RED = "red"
    const val DOWNLOADING = "downloading"
    const val READY = "ready"
    const val INSTALLED = "installed"
    const val ERROR = "error"

    const val RELEASE_TAG = "jarvis-latest"

    private val TRIGGER = listOf("apk banao", "apk banwao", "khud apk banao", "naya apk banao", "jarvis build karo", "apk build karo", "apk banwaye")
    private val STATUS = listOf("apk status", "apk ka status", "build status", "apk kaha tak", "build kaha tak")
    private val INSTALL = listOf("apk install karo", "naya apk install karo", "update install karo", "build install karo")

    fun isTrigger(norm: String): Boolean = TRIGGER.any { norm.contains(it) }
    fun isStatus(norm: String): Boolean = STATUS.any { norm.contains(it) }
    fun isInstall(norm: String): Boolean = INSTALL.any { norm.contains(it) }

    /** Picks the run WE triggered: newest workflow_dispatch run on main, created at/after [sinceMs] (60s clock-skew slack).
     *  Pure JSON parsing, no network - this is what SelfBuildRulesTest exercises without a real GitHub call. */
    fun pickRun(runsJson: String, sinceMs: Long): JSONObject? {
        val arr = try { JSONObject(runsJson).optJSONArray("workflow_runs") ?: JSONArray() } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optString("event") != "workflow_dispatch") continue
            if (r.optString("head_branch") != "main") continue
            val created = parseIso(r.optString("created_at"))
            if (created != 0L && created + 60000 < sinceMs) continue
            return r
        }
        return null
    }

    fun parseIso(s: String): Long = try { java.time.Instant.parse(s).toEpochMilli() } catch (e: Exception) { 0L }

    /** Finds the .apk asset and its matching .sha256 text asset inside a GitHub Release JSON body. */
    fun pickAssets(releaseJson: String): Pair<JSONObject?, JSONObject?> {
        val o = try { JSONObject(releaseJson) } catch (e: Exception) { return Pair(null, null) }
        val arr = o.optJSONArray("assets") ?: JSONArray()
        var apk: JSONObject? = null
        var sha: JSONObject? = null
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val name = a.optString("name")
            if (apk == null && name.endsWith(".apk")) apk = a
            if (sha == null && name.endsWith(".sha256")) sha = a
        }
        return Pair(apk, sha)
    }

    /** The GitHub bearer token must only ever go to GitHub's own API host. A redirect target (S3 etc.) never gets it -
     *  sending it there is both a leak and something those hosts reject outright. */
    fun authFor(host: String): Boolean = host.equals("api.github.com", ignoreCase = true)

    fun phaseLabel(p: String): String = when (p) {
        IDLE -> "shuru nahi hua"
        DISPATCHING -> "GitHub ko bola ja raha hai"
        QUEUED -> "build line mein hai"
        RUNNING -> "build chal raha hai"
        GREEN -> "build GREEN, naya APK taiyar"
        RED -> "build RED (fail)"
        DOWNLOADING -> "naya APK download ho raha hai"
        READY -> "verify ho gaya, Install screen khulegi"
        INSTALLED -> "install ho gaya"
        ERROR -> "error"
        else -> p
    }

    /** sha256 line published by CI may be "hash  filename" (sha256sum format) or just "hash". */
    fun firstToken(line: String): String = line.trim().split(Regex("\\s+")).firstOrNull()?.lowercase() ?: ""
}

object GitHubSelfBuild {
    class Out(val ok: Boolean, val msg: String = "")

    @Volatile private var phase = SelfBuildRules.IDLE
    @Volatile private var message = ""
    @Volatile private var dispatchedAt = 0L
    private val lock = Any()

    private fun repo(): String = GitHubUpgradePublish.OWNER_REPO

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
        598 -> "Internet nahi mil raha"
        else -> "Abhi nahi ho paya. Thodi der baad dobara try karo"
    }

    private fun setPhase(p: String, msg: String) = synchronized(lock) { phase = p; message = msg }
    private fun snapshot(): Pair<String, String> = synchronized(lock) { Pair(phase, message) }

    fun statusJson(): String {
        val (p, m) = snapshot()
        return JSONObject().put("phase", p).put("label", SelfBuildRules.phaseLabel(p)).put("msg", m).toString()
    }

    /** "jarvis apk banao" -> asks GitHub Actions to run build.yml on main. Returns right away, does not wait for the build. */
    fun trigger(ctx: Context): Out {
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        if (token.isEmpty()) { setPhase(SelfBuildRules.ERROR, "GitHub token nahi mila. Settings mein daalo"); return Out(false, snapshot().second) }
        setPhase(SelfBuildRules.DISPATCHING, "")
        val r = call(token, "POST", "/repos/${repo()}/actions/workflows/build.yml/dispatches", JSONObject().put("ref", "main").toString())
        if (r.first !in 200..299) { setPhase(SelfBuildRules.ERROR, failFor(r.first)); return Out(false, snapshot().second) }
        synchronized(lock) { dispatchedAt = System.currentTimeMillis() }
        setPhase(SelfBuildRules.QUEUED, "Build shuru karne ko bol diya. Thodi der baad \"apk status\" bolo (10-20 minute lagte hain).")
        return Out(true, snapshot().second)
    }

    /** Single, idempotent status check - safe to call repeatedly from a UI timer or by voice ("apk status"). */
    fun poll(ctx: Context): Out {
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        if (token.isEmpty()) { setPhase(SelfBuildRules.ERROR, "GitHub token nahi mila"); return Out(false, snapshot().second) }
        val since = synchronized(lock) { dispatchedAt }
        if (since == 0L) return Out(false, snapshot().second.ifEmpty { "Pehle \"apk banao\" bolo" })
        val r = call(token, "GET", "/repos/${repo()}/actions/runs?event=workflow_dispatch&branch=main&per_page=10", null)
        if (r.first !in 200..299) { setPhase(SelfBuildRules.ERROR, failFor(r.first)); return Out(false, snapshot().second) }
        val run = SelfBuildRules.pickRun(r.second, since)
            ?: return Out(false, snapshot().second.ifEmpty { "Build abhi list mein nahi aaya, thodi der ruk ke dobara poochho" })
        val st = run.optString("status")
        val conclusion = run.optString("conclusion")
        when {
            st != "completed" -> setPhase(SelfBuildRules.RUNNING, "Build chal raha hai ($st)")
            conclusion == "success" -> setPhase(SelfBuildRules.GREEN, "Build GREEN. \"apk install karo\" bolo ya Test Center mein INSTALL dabao.")
            else -> setPhase(SelfBuildRules.RED, "Build RED. GitHub Actions ki failed step ki sirf laal lines bhejo.")
        }
        return Out(snapshot().first == SelfBuildRules.GREEN, snapshot().second)
    }

    /** After GREEN: download the release asset CI published, verify its sha256, then the normal verified-install path. */
    fun install(ctx: Context, done: (Boolean, String) -> Unit) {
        val app = ctx.applicationContext
        Thread {
            val r = try { doInstall(app) } catch (e: Exception) { Pair(false, "Install failed: " + (e.message ?: e.javaClass.simpleName)) }
            setPhase(if (r.first) SelfBuildRules.INSTALLED else SelfBuildRules.ERROR, r.second)
            try { done(r.first, r.second) } catch (e: Exception) { ErrLog.add("GitHubSelfBuild", e.toString()) }
        }.start()
    }

    private fun doInstall(ctx: Context): Pair<Boolean, String> {
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        if (token.isEmpty()) return Pair(false, "GitHub token nahi mila")
        setPhase(SelfBuildRules.DOWNLOADING, "Release dhoondh raha hu...")
        val rel = call(token, "GET", "/repos/${repo()}/releases/tags/${SelfBuildRules.RELEASE_TAG}", null)
        if (rel.first !in 200..299) return Pair(false, failFor(rel.first))
        val (apkA, shaA) = SelfBuildRules.pickAssets(rel.second)
        if (apkA == null) return Pair(false, "Release mein APK nahi mila")
        setPhase(SelfBuildRules.DOWNLOADING, "APK download ho raha hai...")
        val apkBytes = downloadAsset(token, apkA.optLong("id")) ?: return Pair(false, "APK download nahi hua")
        val expectSha = if (shaA != null) {
            val b = downloadAsset(token, shaA.optLong("id"))
            SelfBuildRules.firstToken(b?.toString(Charsets.UTF_8) ?: "")
        } else ""
        val gotSha = hex(MessageDigest.getInstance("SHA-256").digest(apkBytes))
        if (expectSha.isNotEmpty() && gotSha != expectSha) return Pair(false, "Checksum match nahi hua, file discard ki")
        val dir = File(ctx.filesDir, "updates"); dir.mkdirs()
        val f = File(dir, "selfbuild_dl.apk")
        f.writeBytes(apkBytes)
        setPhase(SelfBuildRules.READY, "Verify ho gaya, Install screen khul rahi hai...")
        return UpdateManager.installApkFile(ctx, f, keepName = "selfbuild.apk")
    }

    /** GitHub answers an asset request with a 302 to a signed storage URL. That second hop must NOT carry the
     *  GitHub token (S3-style hosts reject an unexpected Authorization header; it would also leak the token off-GitHub). */
    private fun downloadAsset(token: String, id: Long): ByteArray? {
        if (id <= 0) return null
        return try {
            var url = "https://api.github.com/repos/${repo()}/releases/assets/$id"
            var sendAuth = true
            for (hop in 0..5) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15000; c.readTimeout = 60000
                if (sendAuth) c.setRequestProperty("Authorization", "Bearer " + token)
                c.setRequestProperty("Accept", "application/octet-stream")
                c.setRequestProperty("User-Agent", "JARVIS-assistant")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location")
                    c.disconnect()
                    if (loc == null) return null
                    url = loc
                    sendAuth = SelfBuildRules.authFor(URL(loc).host)
                    continue
                }
                if (code != 200) { c.disconnect(); return null }
                val bytes = c.inputStream.use { it.readBytes() }
                c.disconnect()
                return bytes
            }
            null
        } catch (e: Exception) { null }
    }

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    /** Voice / chat entry, same shape as UpgradeLedger.handle: returns JARVIS's reply, or null if this text is not
     *  about the self-build feature. No HAAN gate before trigger/poll (owner asked for this to "just work" once the
     *  GitHub token is saved) - the one unavoidable approval is Android's own Install screen inside install(). */
    fun handleVoice(ctx: Context, norm: String): String? {
        if (SelfBuildRules.isInstall(norm)) {
            if (snapshot().first != SelfBuildRules.GREEN) return "Abhi koi naya GREEN build taiyar nahi hai. Pehle \"apk banao\" aur phir \"apk status\" bolo."
            install(ctx) { _, _ -> }
            return "APK download aur verify shuru kar diya. Thodi der mein Android ki Install screen khulegi, wahan Install tap karna hoga."
        }
        if (SelfBuildRules.isStatus(norm)) { poll(ctx); return snapshot().second }
        if (SelfBuildRules.isTrigger(norm)) { trigger(ctx); return snapshot().second }
        return null
    }
}
