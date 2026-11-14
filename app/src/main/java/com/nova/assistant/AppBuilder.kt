package com.nova.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger

/**
 * v7.177 APP BUILDER: idea -> deep research -> verified fact sheet -> app -> GitHub Actions build -> install.
 * The rules (grammar, prompts, fact checks, project files) are in AppBuilderRules (pure, tested); this object runs the steps and keeps the live log
 * that the APP BUILDER (terminal) screen shows.
 *
 * Network: web search = the same AnalysisSearchRules.runSearch the movie / match analysis uses (only short text queries leave the phone); pages are read
 * with plain https GETs (public pages only, no sign-in, size capped); the AI is the same provider chain the website builder uses (SiteGen.requestHtml);
 * GitHub = the owner's own token (SecureStore slot "github"), sent in a request header only, never in a URL or a log line.
 * Evidence level: written + the pure rules compiled and tested; this file is NOT compiled here and not phone tested.
 */
object AppBuilder {
    @Volatile var ui: ((String) -> Unit)? = null

    private const val API = "https://api.github.com"
    private const val PREFS = "nova_app_builder"
    private const val BUILD_WAIT_MS = 14L * 60_000L
    private val lock = Any()
    private val lines = ArrayList<Pair<String, String>>()
    private val gen = AtomicInteger(0)
    @Volatile private var state = "IDLE"          // IDLE RUNNING DONE FAIL STOPPED
    @Volatile private var stage = ""
    @Volatile private var appName = ""
    @Volatile private var startedAt = 0L
    @Volatile private var repoUrl = ""

    class Gh(val code: Int, val body: String)

    // ---------------------------------------------------------------- log (what the terminal screen shows)

    private fun alive(g: Int) = gen.get() == g

    private fun log(g: Int, kind: String, text: String) {
        if (!alive(g)) return
        synchronized(lock) {
            lines.add(Pair(kind, text.take(300)))
            while (lines.size > 400) lines.removeAt(0)
        }
        poke()
    }

    private fun poke() { try { ui?.invoke("window.onAppBuilderEvent&&window.onAppBuilderEvent()") } catch (e: Throwable) { } }

    private fun setStage(g: Int, s: String) { if (alive(g)) { stage = s; poke() } }

    fun openScreen() { try { ui?.invoke("window.__apbOpen&&window.__apbOpen()") } catch (e: Throwable) { } }

    fun snapshotJson(ctx: Context): String {
        val arr = JSONArray()
        synchronized(lock) { for (l in lines.takeLast(250)) arr.put(JSONArray().put(l.first).put(l.second)) }
        val sp = prefs(ctx)
        val apk = sp.getString("apk", "") ?: ""
        return JSONObject()
            .put("state", state).put("stage", stage).put("name", appName)
            .put("secs", if (state == "RUNNING") ((System.currentTimeMillis() - startedAt) / 1000L) else 0L)
            .put("lines", arr)
            .put("canInstall", apk.isNotEmpty() && File(apk).exists())
            .put("lastName", sp.getString("name", "") ?: "")
            .put("repo", repoUrl)
            .put("hasToken", SecureStore.getSlot(ctx, GitHubPublish.SLOT).isNotEmpty())
            .toString()
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- start / stop

    /** Voice, chat and the screen all come here. Returns the one sentence NOVA says now; the work runs on its own thread. */
    fun start(ctx: Context, ideaRaw: String, brain: AnalysisPipeline.Brain?, en: Boolean): String {
        val app = ctx.applicationContext
        val idea = AppBuilderRules.ideaOf(ideaRaw)
        synchronized(lock) { if (state == "RUNNING") return AppBuilderRules.busyText(en) }
        val name = AppBuilderRules.appName(idea)
        if (idea.length < 6 || (name.isEmpty() && idea.split(" ").size < 4)) return AppBuilderRules.askIdeaText(en)
        if (SecureStore.getSlot(app, GitHubPublish.SLOT).isEmpty()) return AppBuilderRules.noTokenText(en)
        if (brain == null) return if (en) "Please tap ACTIVATE first, then ask again." else "Pehle NOVA ko ACTIVATE karo, phir dobara bolo."
        val g = gen.incrementAndGet()
        synchronized(lock) { lines.clear() }
        state = "RUNNING"; stage = "PLAN"; appName = name.ifEmpty { "My App" }; startedAt = System.currentTimeMillis(); repoUrl = ""
        openScreen()
        val t = Thread {
            try { run(app, g, idea, brain, en) }
            catch (e: Throwable) {
                ErrLog.add("AppBuilder", e.javaClass.simpleName)
                finish(app, g, false, if (en) "The app builder stopped on an unexpected error. Try again." else "App banate waqt koi gadbad ho gayi. Dobara try karo.", en)
            }
        }
        t.name = "nova-app-builder"; t.isDaemon = true; t.start()
        return AppBuilderRules.startedText(appName, en)
    }

    /** Stops ONLY the build. NOVA itself keeps running. */
    fun stop(en: Boolean): String {
        synchronized(lock) { if (state != "RUNNING") return AppBuilderRules.idleStopText(en) }
        gen.incrementAndGet()                  // the old thread sees a different generation and ends quietly
        state = "STOPPED"; stage = ""
        synchronized(lock) { lines.add(Pair("err", "STOP: build roka gaya")) }
        poke()
        return AppBuilderRules.stoppedText(en)
    }

    private fun finish(ctx: Context, g: Int, ok: Boolean, say: String, en: Boolean) {
        if (!alive(g)) return
        state = if (ok) "DONE" else "FAIL"; stage = ""
        log(g, if (ok) "ok" else "err", say)
        poke()
        try { NovaService.instance?.announce(say) } catch (e: Throwable) { ErrLog.addOnce("quiet:AppBuilder", e.toString()) }
        if (ok) offerInstall(ctx, say, en)
    }

    // ---------------------------------------------------------------- the pipeline

    private fun todayText(): String {
        val c = Calendar.getInstance()
        return AppBuilderRules.today(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** One AI text call through the website builder's provider chain (own fresh time budget). null = no AI answered. */
    private fun ai(ctx: Context, system: String, prompt: String): String? =
        SiteGen.requestHtml(ctx, system, prompt, System.currentTimeMillis()) { it.trim().takeIf { x -> x.isNotEmpty() } }

    private fun secs(since: Long): String = String.format("%.1fs", (System.currentTimeMillis() - since) / 1000.0)

    private fun run(ctx: Context, g: Int, idea: String, brain: AnalysisPipeline.Brain, en: Boolean) {
        val today = todayText()

        // 1. PLAN
        var t = System.currentTimeMillis()
        log(g, "cmd", "nova plan --idea \"" + idea.take(70) + "\"")
        val aiPlan = AppBuilderRules.parsePlan(ai(ctx, AppBuilderRules.PLAN_SYSTEM, AppBuilderRules.planPrompt(idea, today)))
        if (aiPlan == null) log(g, "info", "AI plan nahi mila: standard research plan use ho raha hai")
        val plan: AppBuilderRules.Plan = aiPlan ?: AppBuilderRules.defaultPlan(idea)
        if (!alive(g)) return
        val name = AppBuilderRules.appName(idea).ifEmpty { plan.title }.ifEmpty { "My App" }
        appName = name
        log(g, "ok", "plan taiyaar: " + plan.sections.size + " hisse (" + secs(t) + ")")
        for (s in plan.sections) log(g, "info", "  - " + s.name)

        // 2. DEEP WEB SEARCH (one query per section; only these short texts leave the phone)
        setStage(g, "RESEARCH")
        t = System.currentTimeMillis()
        val gem: ((String) -> Pair<Int, String>)? =
            if (brain.blocked().isEmpty()) ({ j: String -> brain.callRaw(j, AnalysisSearchRules.SEARCH_READ_MS, AnalysisSearchRules.SEARCH_DEADLINE_MS) }) else null
        val lists = ArrayList<List<Triple<String, String, String>>>()
        for (chunk in plan.sections.map { it.query }.chunked(AnalysisSearchRules.MAX_SEARCH_CALLS)) {
            if (!alive(g)) return
            for (q in chunk) log(g, "cmd", "web search \"" + q + "\"")
            val out = try {
                AnalysisSearchRules.runSearch(chunk, en, gem, { u -> brain.webGet(u, AnalysisSearchRules.KEYLESS_GET_MS) }, { alive(g) }, { System.currentTimeMillis() }) { _, _ -> }
            } catch (e: Throwable) { null }
            if (out != null) lists.add(out.results.map { Triple(it.title, it.url, it.snippet) })
        }
        if (!alive(g)) return
        var srcs = AppBuilderRules.mergeSources(lists)
        log(g, if (srcs.isEmpty()) "err" else "ok", srcs.size.toString() + " sources mile (" + secs(t) + ")")
        for (s in srcs) log(g, "info", "  [" + s.n + "] " + (hostOf(s.url)) + " - " + s.title.take(60))

        // 3. READ THE TOP PAGES (public pages only)
        if (srcs.isNotEmpty()) {
            t = System.currentTimeMillis()
            log(g, "cmd", "read pages (top " + minOf(AppBuilderRules.MAX_PAGES, srcs.size) + ")")
            var got = 0
            srcs = srcs.mapIndexed { i, s ->
                if (i < AppBuilderRules.MAX_PAGES && alive(g)) {
                    val p = fetchPage(s.url)
                    if (p.isNotEmpty()) { got++; AppBuilderRules.Src(s.n, s.title, s.url, s.snippet, p) } else s
                } else s
            }
            log(g, "ok", got.toString() + " page poora padha (" + secs(t) + ")")
        }
        if (!alive(g)) return

        // 4. FACT SHEET: every fact pinned to a source; a fact whose numbers no cited source contains is thrown away
        setStage(g, "FACTS")
        t = System.currentTimeMillis()
        log(g, "cmd", "nova facts --verify --sources " + srcs.size)
        var sheet: AppBuilderRules.Sheet? = null
        if (srcs.isNotEmpty()) {
            val prompt = AppBuilderRules.factsPrompt(idea, plan, srcs, today)
            for (attempt in 0 until 2) {
                if (!alive(g)) return
                sheet = AppBuilderRules.parseSheet(ai(ctx, AppBuilderRules.FACTS_SYSTEM, prompt), plan, srcs)
                if (sheet != null) break
                log(g, "info", "facts ka jawab theek nahi aaya, dobara try" + (if (attempt == 0) "" else " (aakhri)"))
            }
        }
        if (!alive(g)) return
        val sh = sheet
        if (sh != null) {
            log(g, "ok", sh.total.toString() + " verify ki hui details, " + sh.dropped + " hata di (source se match nahi) (" + secs(t) + ")")
            for (s in sh.sections) log(g, if (s.facts.isEmpty()) "err" else "info", "  - " + s.name + ": " + s.facts.size + (if (s.gap.isNotEmpty()) "  | GAP: " + s.gap.take(80) else ""))
        } else log(g, "err", "facts nikal nahi paye")
        val why = AppBuilderRules.notEnough(plan, sh, srcs.size)
        if (why != null) {
            finish(ctx, g, false, AppBuilderRules.notEnoughText(why, if (why == "sources") srcs.size else (sh?.total ?: 0), en), en)
            return
        }

        // 5. WRITE THE APP
        setStage(g, "CODE")
        t = System.currentTimeMillis()
        log(g, "cmd", "nova code app/src/main/assets/index.html")
        val prompt = AppBuilderRules.appPrompt(idea, name, today, plan, sh, srcs)
        val written = ai(ctx, AppBuilderRules.APP_SYSTEM, prompt)?.let { SiteGen.cleanHtml(it) }
        if (written == null) { finish(ctx, g, false, AppBuilderRules.noAiText(en), en); return }
        if (!alive(g)) return
        var html: String = ensureViewport(written)
        log(g, "ok", "app likhi gayi, " + (html.length / 1024) + " KB (" + secs(t) + ")")

        // 6. CHECK: years / phones / e-mails / links that are not in the fact sheet
        val allowed = (if (sh != null) AppBuilderRules.sheetText(sh, srcs) else "") + "\n" + idea + "\n" + today + "\n" + srcs.joinToString("\n") { it.title + " " + it.url }
        val needsFacts = plan.needsFacts
        var problems = AppBuilderRules.checkHtml(html, allowed, needsFacts, srcs)
        log(g, "cmd", "nova check --no-invented-details")
        for (pass in 0 until 2) {
            if (problems.isEmpty() || !alive(g)) break
            for (p in problems) log(g, "info", "  ! " + p.take(110))
            log(g, "cmd", "nova repair (" + (pass + 1) + "/2)")
            val fixed = ai(ctx, AppBuilderRules.APP_SYSTEM, AppBuilderRules.repairPrompt(problems, html, prompt))?.let { SiteGen.cleanHtml(it) }
            if (!alive(g)) return
            if (fixed == null) break
            val f2 = ensureViewport(fixed)
            val p2 = AppBuilderRules.checkHtml(f2, allowed, needsFacts, srcs)
            if (p2.size < problems.size) { html = f2; problems = p2 } else break
        }
        log(g, if (problems.isEmpty()) "ok" else "err", if (problems.isEmpty()) "check saaf: UI, poori features aur bina-source detail sab theek" else problems.size.toString() + " cheez abhi bhi theek nahi (neeche report mein)")

        // 7. PROJECT -> GitHub (the owner's own account)
        setStage(g, "PUSH")
        val token = SecureStore.getSlot(ctx, GitHubPublish.SLOT)
        if (token.isEmpty()) { finish(ctx, g, false, AppBuilderRules.noTokenText(en), en); return }
        t = System.currentTimeMillis()
        log(g, "cmd", "git init + push (GitHub)")
        val who = gh(token, "GET", "/user", null)
        if (!alive(g)) return
        if (who.code !in 200..299) { finish(ctx, g, false, ghFail(who.code, "login"), en); return }
        val login = try { JSONObject(who.body).optString("login") } catch (e: Exception) { "" }
        if (login.isEmpty() || !login.all { it.isLetterOrDigit() || it == '-' }) { finish(ctx, g, false, ghFail(599, "login"), en); return }
        val stamp = System.currentTimeMillis()
        val pkg = AppBuilderRules.packageName(name, stamp)
        val repo = "nova-app-" + AppBuilderRules.slugOf(name).replace('_', '-') + "-" + (stamp % 10000)
        val mk = gh(token, "POST", "/user/repos", JSONObject().put("name", repo).put("description", "App built by NOVA").put("private", false).put("auto_init", true).toString())
        if (!alive(g)) return
        if (mk.code !in 200..299) { finish(ctx, g, false, ghFail(mk.code, "repo"), en); return }
        val branch = try { JSONObject(mk.body).optString("default_branch", "main").ifEmpty { "main" } } catch (e: Exception) { "main" }
        repoUrl = "https://github.com/" + login + "/" + repo
        log(g, "ok", "repo bana: " + login + "/" + repo)
        val files = AppBuilderRules.projectFiles(name, pkg, html, AppBuilderRules.sourcesMd(name, today, sh, srcs)).filter { it.first != "README.md" }
        for ((path, content) in files) {
            if (!alive(g)) return
            val put = gh(token, "PUT", "/repos/" + login + "/" + repo + "/contents/" + path,
                JSONObject().put("message", "NOVA: add " + path.substringAfterLast('/')).put("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)).toString())
            if (put.code !in 200..299) {
                val why2 = if (path.startsWith(".github/") && (put.code == 403 || put.code == 404 || put.code == 422))
                    (if (en) "GitHub refused the build file. The token needs the workflow permission (classic token: repo + workflow; fine-grained: Workflows read and write)." else "GitHub ne build file mana kar di. Token mein workflow permission chahiye (classic token: repo + workflow, fine-grained: Workflows read and write).")
                else ghFail(put.code, "file")
                finish(ctx, g, false, why2, en); return
            }
            log(g, "info", "  + " + path)
        }
        log(g, "ok", files.size.toString() + " file push hui (" + secs(t) + ")")

        // 8. BUILD (GitHub Actions)
        setStage(g, "BUILD")
        t = System.currentTimeMillis()
        log(g, "cmd", "gradle assembleDebug  (GitHub Actions)")
        var dispatched = false
        for (i in 0 until 8) {
            if (!alive(g)) return
            val d = gh(token, "POST", "/repos/" + login + "/" + repo + "/actions/workflows/build.yml/dispatches", JSONObject().put("ref", branch).toString())
            if (d.code in 200..299) { dispatched = true; break }
            if (d.code != 404 && d.code != 422) { finish(ctx, g, false, ghFail(d.code, "dispatch"), en); return }
            sleep(g, 4000)
        }
        if (!dispatched) { finish(ctx, g, false, if (en) "GitHub did not start the build. Open the repo > Actions and press Run workflow." else "GitHub ne build shuru nahi kiya. Repo > Actions mein Run workflow dabao.", en); return }
        log(g, "ok", "build shuru hui")
        var conclusion = ""
        var runId = ""
        var lastTick = 0L
        val limit = System.currentTimeMillis() + BUILD_WAIT_MS
        while (System.currentTimeMillis() < limit) {
            if (!alive(g)) return
            sleep(g, 8000)
            val r = gh(token, "GET", "/repos/" + login + "/" + repo + "/actions/runs?per_page=1", null)
            val st = AppBuilderRules.parseRun(if (r.code in 200..299) r.body else null)
            if (System.currentTimeMillis() - lastTick > 20_000L) {
                lastTick = System.currentTimeMillis()
                log(g, "live", "build chal rahi hai... " + (if (st != null) st.status else "queue") + "  " + secs(t))
            }
            if (st != null && st.status == "completed") { conclusion = st.conclusion; runId = AppBuilderRules.parseRunId(r.body); break }
        }
        if (!alive(g)) return
        if (conclusion != "success") {
            var step = ""
            if (runId.isNotEmpty()) { val j = gh(token, "GET", "/repos/" + login + "/" + repo + "/actions/runs/" + runId + "/jobs", null); step = AppBuilderRules.parseFailedStep(if (j.code in 200..299) j.body else null) }
            log(g, "err", "build fail" + (if (step.isNotEmpty()) " | step: " + step else "") + (if (conclusion.isEmpty()) " | time khatam" else ""))
            finish(ctx, g, false, if (en) "The app build failed" + (if (step.isNotEmpty()) " at \"" + step + "\"" else "") + ". Open the repo's Actions tab to see why, or say the idea again." else "App ka build fail ho gaya" + (if (step.isNotEmpty()) " (\"" + step + "\" par)" else "") + ". Repo ke Actions tab mein wajah dekho ya idea dobara bolo.", en)
            return
        }
        log(g, "ok", "build poora (" + secs(t) + ")")

        // 9. DOWNLOAD + verify
        setStage(g, "DOWNLOAD")
        t = System.currentTimeMillis()
        log(g, "cmd", "download app-debug.apk")
        var apkUrl: String? = null
        for (i in 0 until 5) {
            if (!alive(g)) return
            val rel = gh(token, "GET", "/repos/" + login + "/" + repo + "/releases/latest", null)
            apkUrl = AppBuilderRules.parseApkUrl(if (rel.code in 200..299) rel.body else null)
            if (apkUrl != null) break
            sleep(g, 3000)
        }
        if (apkUrl == null) { finish(ctx, g, false, if (en) "The build finished but the APK was not found. Open the repo's Releases page." else "Build ho gaya par APK nahi mili. Repo ke Releases page par dekho.", en); return }
        val dir = File(ctx.cacheDir, "built_apps"); dir.mkdirs()
        val dest = File(dir, AppBuilderRules.slugOf(name) + ".apk")
        if (!download(apkUrl, dest)) { finish(ctx, g, false, if (en) "The APK download failed. Try again." else "APK download nahi hui. Dobara try karo.", en); return }
        val info = try { ctx.packageManager.getPackageArchiveInfo(dest.absolutePath, 0) } catch (e: Throwable) { null }
        if (info == null || info.packageName != pkg) { try { dest.delete() } catch (e: Throwable) { }; finish(ctx, g, false, if (en) "The downloaded file is not the app I built, so I did not use it." else "Download hui file wahi app nahi nikli, isliye use nahi ki.", en); return }
        if (!alive(g)) return
        prefs(ctx).edit().putString("apk", dest.absolutePath).putString("pkg", pkg).putString("name", name).putString("repo", repoUrl).apply()
        log(g, "ok", "APK taiyaar: " + (dest.length() / 1024) + " KB (" + secs(t) + ")")
        val gaps = sh?.sections?.count { it.facts.isEmpty() || it.gap.isNotEmpty() } ?: 0
        finish(ctx, g, true, AppBuilderRules.doneText(name, sh?.total ?: 0, sh?.usedSources()?.size ?: 0, sh?.dropped ?: 0, gaps, problems, en), en)
    }

    private fun sleep(g: Int, ms: Long) {
        var left = ms
        while (left > 0 && alive(g)) { try { Thread.sleep(minOf(500L, left)) } catch (e: InterruptedException) { return }; left -= 500L }
    }

    private fun hostOf(url: String): String = try { URL(url).host.removePrefix("www.") } catch (e: Exception) { "" }

    private fun ensureViewport(html: String): String {
        if (html.lowercase().contains("name=\"viewport\"") || html.lowercase().contains("name='viewport'")) return html
        val i = html.indexOf("<head", ignoreCase = true)
        if (i < 0) return html
        val close = html.indexOf('>', i)
        if (close < 0) return html
        return html.substring(0, close + 1) + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">" + html.substring(close + 1)
    }

    // ---------------------------------------------------------------- network helpers

    private fun gh(token: String, method: String, path: String, body: String?): Gh {
        try {
            val c = URL(API + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 40000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "NOVA-assistant")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            return Gh(code, text)
        } catch (e: java.net.UnknownHostException) {
            return Gh(598, "")
        } catch (e: Exception) {
            return Gh(599, "")
        }
    }

    private fun ghFail(code: Int, what: String): String = when (code) {
        401 -> "GitHub token galat ya expire ho gaya. Naya token daalo"
        403, 404 -> "GitHub ne mana kiya. Token mein repo ka permission chahiye"
        422 -> "GitHub ne ye $what accept nahi kiya. Dobara try karo"
        598 -> "Internet nahi mil raha"
        else -> "GitHub abhi jawab nahi de raha. Thodi der baad dobara try karo"
    }

    /** Public https pages only (validUrl blocks raw IPs / localhost), redirects re-checked, 400 KB cap, text only. "" = could not read. */
    private fun fetchPage(url0: String): String {
        var url = url0
        for (hop in 0..3) {
            if (AnalysisRules.validUrl(url) == null) return ""
            var c: HttpURLConnection? = null
            try {
                c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "GET"
                c.connectTimeout = 5000
                c.readTimeout = 7000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) NOVA-research")
                c.setRequestProperty("Accept", "text/html,text/plain;q=0.9")
                val code = c.responseCode
                if (code in 301..308 && code != 304) {
                    val loc = c.getHeaderField("Location") ?: return ""
                    url = URL(URL(url), loc).toString()
                    continue
                }
                if (code !in 200..299) return ""
                val type = (c.contentType ?: "").lowercase()
                if (type.isNotEmpty() && !type.contains("text/html") && !type.contains("text/plain")) return ""
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                c.inputStream.use { input ->
                    while (out.size() < 400_000) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
                return AppBuilderRules.pageText(out.toString("UTF-8"))
            } catch (e: Throwable) {
                return ""
            } finally {
                try { c?.disconnect() } catch (e: Throwable) { }
            }
        }
        return ""
    }

    /** The release asset, https from allow-listed GitHub hosts only (redirects re-checked), size capped. */
    private fun download(url0: String, dest: File): Boolean {
        var url = url0
        for (hop in 0..5) {
            if (!Updater.hostAllowed(url)) return false
            var c: HttpURLConnection? = null
            try {
                c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "GET"
                c.connectTimeout = 15000
                c.readTimeout = 60000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "NOVA-assistant")
                val code = c.responseCode
                if (code in 301..308 && code != 304) {
                    val loc = c.getHeaderField("Location") ?: return false
                    url = URL(URL(url), loc).toString()
                    continue
                }
                if (code !in 200..299) return false
                var total = 0L
                val buf = ByteArray(16384)
                dest.outputStream().use { o ->
                    c.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > Updater.MAX_APK_BYTES) return false
                            o.write(buf, 0, n)
                        }
                    }
                }
                return total > 1024L
            } catch (e: Throwable) {
                return false
            } finally {
                try { c?.disconnect() } catch (e: Throwable) { }
            }
        }
        return false
    }

    // ---------------------------------------------------------------- install (Android always asks the user; never silent)

    private fun installIntent(ctx: Context): Intent? {
        val path = prefs(ctx).getString("apk", "") ?: ""
        val f = File(path)
        if (path.isEmpty() || !f.exists()) return null
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** After a build: open the installer when NOVA's screen is on top, otherwise a tap-to-install notification. */
    private fun offerInstall(ctx: Context, say: String, en: Boolean) {
        try {
            val i = installIntent(ctx) ?: return
            if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) return   // the screen's INSTALL button explains the permission
            if (NovaService.uiVisible) { try { ctx.startActivity(i) } catch (e: Throwable) { UpdateManager.notifyUser(ctx, say.take(120), i) } }
            else UpdateManager.notifyUser(ctx, say.take(120), i)
        } catch (e: Throwable) { ErrLog.addOnce("quiet:AppBuilder", e.toString()) }
    }

    /** INSTALL button / "banayi hui app install karo". Returns what to tell the owner. */
    fun install(ctx: Context, en: Boolean): String {
        val app = ctx.applicationContext
        val i = try { installIntent(app) } catch (e: Throwable) { null } ?: return AppBuilderRules.noApkText(en)
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            try {
                app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Throwable) { ErrLog.addOnce("quiet:AppBuilder", e.toString()) }
            return if (en) "First allow NOVA to install apps (the screen I opened), then tap INSTALL again." else "Pehle NOVA ko app install karne ki ijaazat do (jo screen kholi hai), phir INSTALL dobara dabao."
        }
        return try {
            app.startActivity(i)
            if (en) "Opened the installer. Tap Install." else "Installer khol diya. Install dabao."
        } catch (e: Throwable) {
            if (en) "I could not open the installer." else "Installer nahi khul paya."
        }
    }
}
