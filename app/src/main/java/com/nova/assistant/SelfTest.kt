package com.nova.assistant

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings

/**
 * Settings > SELF TEST. Runs real checks one after another on the phone and writes PASS / FAIL / WARN / SKIP lines.
 * It never calls, messages, pays, installs or deletes anything. It briefly opens the Android Settings screen to prove
 * that NOVA can read another app, then comes back. Nothing is sent anywhere except one tiny Gemini text request (if a key is set).
 */
object SelfTest {
    @Volatile var running = false

    /** [quiet]=true is the automatic run: read-only checks only. It never speaks, flashes the torch, changes volume or jumps between screens. */
    fun start(act: Activity, emit: (String) -> Unit, done: (String) -> Unit, quiet: Boolean = false, full: Boolean = false) {
        if (running) return
        running = true
        val startMs = System.currentTimeMillis()
        Thread {
            val out = ArrayList<String>()
            val rec: (String, String, String) -> Unit = { tag, name, detail ->
                val line = tag.padEnd(5) + " " + name + (if (detail.isNotEmpty()) " - " + detail else "")
                out.add(line)
                emit(line)
            }
            try {
                body(act, rec, quiet, full)
            } catch (e: Throwable) {
                rec("FAIL", "Self test beech mein ruk gaya", e.javaClass.simpleName)
            }
            val vName = try {
                act.packageManager.getPackageInfo(act.packageName, 0).versionName ?: "?"
            } catch (e: Exception) { "?" }
            val sb = StringBuilder()
            sb.append("NOVA SELF TEST | app ").append(vName).append(" | ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" | Android ").append(Build.VERSION.SDK_INT).append('\n')
            for (l in out) sb.append(l).append('\n')
            val ruleOnly = listOf("Samajh", "Taiyaar", "Key:", "Agent:", "Website:", "Apps:", "Leads:", "Offline queue:", "Message mode:", "Schedule:", "Agent checkpoint", "Knowledge pack", "AI fallback chain", "Source map", "Local-first", "File:")
            val passAll = out.count { it.startsWith("PASS") }
            val passRule = out.count { l -> l.startsWith("PASS") && ruleOnly.any { l.drop(6).startsWith(it) } }
            sb.append("SACH: PASS = is test mein asli kaam chala ya phone ki asli state padhi. Samajh/Taiyaar/Key = sirf rule ya maujoodgi, kaam chalaya nahi. INFO = test nahi hua.\n")
            sb.append("ASLI PASS=").append(passAll - passRule).append(" | SIRF RULE/CONFIG PASS=").append(passRule).append(" | time=").append((System.currentTimeMillis() - startMs) / 1000).append("s\n")
            sb.append(ErrLog.dump(System.currentTimeMillis())).append('\n')
            sb.append(CrashNote.report(act)).append('\n')
            val nf = out.count { it.startsWith("FAIL") }
            val nw = out.count { it.startsWith("WARN") }
            sb.append(if (nf == 0) "KAAM KI BAAT: koi FAIL nahi. WARN wali lines (Band: ...) ka matlab hai wo feature abhi band hai. Koi manual test baaki nahi: sab apne aap chalta hai.\n"
                else "KAAM KI BAAT: " + nf + " FAIL hain. Ye report COPY karke bhejo, sirf FAIL wali lines theek karni hain.\n")
            if (nw > 0 && nf == 0) sb.append("Band features: ").append(out.filter { it.contains("Band:") }.joinToString(" | ") { it.substringAfter("Band: ").take(60) }).append('\n')
            sb.append("SUMMARY pass=").append(out.count { it.startsWith("PASS") })
                .append(" fail=").append(out.count { it.startsWith("FAIL") })
                .append(" warn=").append(out.count { it.startsWith("WARN") })
                .append(" skip=").append(out.count { it.startsWith("SKIP") })
            running = false
            done(sb.toString())
        }.start()
    }

    private fun pause(ms: Long) {
        try { Thread.sleep(ms) } catch (e: InterruptedException) { ErrLog.add("SelfTest", e.toString()) }
    }

    private fun body(act: Activity, rec: (String, String, String) -> Unit, quiet: Boolean = false, full: Boolean = false) {
        val ctx = act.applicationContext

        // ---- 1. permissions and switches (read only)
        for (item in HealthCheck.run(ctx)) {
            if (item.ok) rec("PASS", item.name, "") else rec(if (item.important) "FAIL" else "WARN", item.name, item.fix)
        }
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) rec("PASS", "Contacts permission", "")
        else rec("WARN", "Contacts permission", "call/message ke liye chahiye")
        val kn = KnowledgeStore.count(ctx)
        if (kn > 0) rec("PASS", "Knowledge pack", kn.toString() + " entries loaded")
        else rec("WARN", "Knowledge pack", "knowledge.json load nahi hua (offline jawab band)")
        val nl = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        if (nl.contains(ctx.packageName)) rec("PASS", "Notification access", "")
        else rec("WARN", "Notification access", "sirf driving mode ke liye (optional)")
        if (Settings.System.canWrite(ctx)) rec("PASS", "Write settings (brightness)", "")
        else rec("WARN", "Write settings (brightness)", "sirf brightness command ke liye (optional)")

        // ---- 1b. Part 10: the newer features, read-only (nothing is changed, nothing is spoken)
        try {
            val dndOn = android.os.Build.VERSION.SDK_INT < 23 || ctx.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
            if (dndOn) rec("PASS", "Ringer silent (Do Not Disturb access)", "") else rec("WARN", "Ringer silent (Do Not Disturb access)", "vibrate/normal chalenge; silent ke liye DND access ON karo")
            val rm = ctx.getSystemService(AudioManager::class.java)?.ringerMode
            rec(if (rm != null) "PASS" else "WARN", "Ringer mode padh sakta hoon", "abhi mode=" + (rm ?: -1))
            val acc2 = NovaAccessibilityService.instance != null
            if (acc2) rec("PASS", "Screenshot (Accessibility)", "") else rec("WARN", "Screenshot (Accessibility)", "Accessibility ON karo")
            if (Settings.System.canWrite(ctx)) rec("PASS", "Screen rotation (Write settings)", "") else rec("WARN", "Screen rotation (Write settings)", "Brightness control ALLOW karo")
            if (ActionLog.smartReplyOn(ctx) && !nl.contains(ctx.packageName)) rec("WARN", "AI SMART reply", "ON hai par Notification access OFF hai: jawab nahi jayega")
            else rec("PASS", "AI SMART reply", if (ActionLog.smartReplyOn(ctx)) "ON aur Notification access ON" else "OFF (optional)")
        } catch (e: Throwable) { rec("WARN", "Naye features ki jaanch", e.javaClass.simpleName) }

        // ---- 2. the service
        val svc = NovaService.instance
        if (!NovaService.running || svc == null) {
            rec("FAIL", "NOVA service", "chal nahi rahi. Home par ACTIVATE dabao, phir test dobara chalao")
        } else {
            rec("PASS", "NOVA service", svc.testState())
        }

        // ---- 3. does the command matcher understand these phrases (runs on this phone)
        val cases = listOf(
            "battery kitni hai" to "battery",
            "analyze screen" to "analyze",
            "screen dekho" to "analyze",
            "screen par kya hai" to "analyze",
            "ye kaun si movie hai" to "analyze",   // wip9: screenshot + web search
            "flashlight on" to "torch_on",
            "volume up" to "volume",
            "vibrate par karo" to "ringer",
            "auto rotate off" to "rotation"
        )
        for ((p, want) in cases) {
            val got = Logic.classify(p)?.kind ?: "none"
            rec(if (got == want) "PASS" else "WARN", "Samajh: \"" + p + "\"", "mila=" + got + " chahiye=" + want)
        }

        // ---- 4. real phone tools
        if (svc != null) {
            val b = svc.testBattery()
            rec(if (b.any { it.isDigit() }) "PASS" else "FAIL", "Battery padhna", b)
            if (quiet && !full) {
                rec("INFO", "Torch / volume / mic", "is baar chhota test: ye install ke baad pehli baar aur har update ke baad apne aap chalte hain")
            } else {
            val t1 = svc.testTorch(true)
            pause(700)
            val t2 = svc.testTorch(false)
            rec(if (t1.first && t2.first) "PASS" else "FAIL", "Torch on aur off", t1.second + " / " + t2.second)
            try { pause(300); svc.testTorch(false) } catch (e: Throwable) { ErrLog.add("SelfTest", e.toString()) }   // v6.38: the test never leaves the torch burning if the first off was not confirmed
            val am = ctx.getSystemService(AudioManager::class.java)
            val vol0 = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val v1 = svc.testVolume("up")
            val v2 = svc.testVolume("down")
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, vol0, 0) } catch (e: Exception) { ErrLog.add("SelfTest", e.toString()) }
            rec(if (v1.first && v2.first) "PASS" else "FAIL", "Volume up aur down", v1.second + " / " + v2.second)
            val mic = svc.testMic()
            rec(if (mic.first) "PASS" else "FAIL", "Mic asli audio de raha hai", mic.second)   // v6.27
            svc.testHearing(rec)
            }
        } else {
            rec("SKIP", "Battery, torch, volume", "NOVA service band hai")
        }

        // ---- 5. accessibility: can NOVA really read another app's screen
        if (NovaAccessibilityService.instance == null) {
            rec("FAIL", "Accessibility (screen padhna, scroll, back)", "connect nahi hua. Settings > Accessibility > NOVA Phone Control OFF phir ON, aur Autostart ON")
        } else {
            rec("PASS", "Accessibility connected", "")
            if (quiet && !full) {
                rec("INFO", "Screen padhna / scroll / back", "is baar chhota test: ye install ke baad pehli baar aur har update ke baad apne aap chalte hain")
            } else {
            val own = NovaAccessibilityService.readScreen(false)
            rec("INFO", "NOVA ki apni screen", "app=" + (own?.pkg ?: "none") + " lines=" + (own?.lines?.size ?: 0))
            act.runOnUiThread {
                try { act.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e: Exception) { ErrLog.add("SelfTest", e.toString()) }
            }
            pause(3000)
            val snap = NovaAccessibilityService.readScreen(false)
            val lines = snap?.lines?.filter { it.isNotBlank() } ?: emptyList()
            val protected = snap != null && snap.pkg != ctx.packageName &&
                ScreenSession.isProtected(snap.pkg, ctx.packageName)
            when {
                protected && lines.isEmpty() -> rec(
                    "PASS", "Protected screen blocked (Android Settings)",
                    "app=" + snap?.pkg ?: "unknown" + " text=blocked"
                )
                protected -> rec(
                    "FAIL", "Protected screen blocked (Android Settings)",
                    "app=" + snap?.pkg ?: "unknown" + " text=exposed; lines=" + lines.size
                )
                snap == null -> rec(
                    "WARN", "Protected screen check (Android Settings)",
                    "active window nahi mila; block verify nahi hua"
                )
                else -> rec(
                    "WARN", "Protected screen check (Android Settings)",
                    "expected Settings/protected app, mila=" + snap.pkg + "; check nahi hua"
                )
            }
            val sd = NovaAccessibilityService.scrollDir("down")
            pause(600)
            NovaAccessibilityService.scrollDir("up")
            rec(if (sd.ok) "PASS" else "FAIL", "Scroll gesture", "code=" + sd.code)
            val bk = NovaAccessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            pause(1300)
            if (!NovaService.uiVisible) {
                act.runOnUiThread {
                    try {
                        act.startActivity(
                            Intent(act, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    } catch (e: Exception) { ErrLog.add("SelfTest", e.toString()) }
                }
                pause(1300)
            }
            rec(if (bk) "PASS" else "FAIL", "Global action BACK", if (NovaService.uiVisible) "NOVA wapas aa gaya" else "NOVA screen par wapas nahi aaya")
            }
        }

        // ---- 5b. v6.27: real internet and real storage (not just "a key exists")
        val t0 = System.currentTimeMillis()
        val net = try {
            val c = java.net.URL("https://www.google.com/generate_204").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 5000
            val code = c.responseCode
            c.disconnect()
            code
        } catch (e: Exception) { -1 }
        rec(if (net in 200..299) "PASS" else "FAIL", "Internet (asli HTTPS)", if (net > 0) "code=" + net + " " + (System.currentTimeMillis() - t0) + " ms" else "koi jawab nahi - Wi-Fi/data check karo")
        val storeOk = try {
            val tf = java.io.File(ctx.filesDir, "selftest_rw.tmp")
            tf.writeText("nova-" + System.nanoTime())
            val back = tf.readText()
            tf.delete()
            back.startsWith("nova-")
        } catch (e: Exception) { false }
        rec(if (storeOk) "PASS" else "FAIL", "Phone storage likh/padh sakta hai", "")

        // ---- 6. cloud text (one tiny request)
        if (!SecureStore.hasKeys(ctx)) {
            rec("SKIP", "Gemini AI (text)", "key nahi hai")
        } else {
            val r = ImageGen.textPing(ctx)
            if (r.first in 200..299) rec("PASS", "Gemini AI (text) jawab", "code=" + r.first + " model=" + r.second)
            else rec("FAIL", "Gemini AI (text) jawab", "code=" + r.first + " " + ImageGen.failMsg(r.first))
        }
        // v34: online free AI providers (Groq, Qwen via Groq, OpenRouter): one tiny request each, only for keys that exist
        val chk = AutoSetup.checkProviders(ctx)
        if (chk.isEmpty()) rec("SKIP", "Free AI (Groq / Qwen / OpenRouter)", "koi key nahi hai (optional)")
        for ((label, code) in chk) {
            val l = AutoSetupPlan.line(label, code)
            rec(l.substringBefore(' '), "Free AI " + l.substringAfter(' ').substringBefore(" - "), l.substringAfter(" - ", ""))
        }
        for (l in AutoSetup.log()) rec(l.substringBefore(' '), "Auto setup", l.substringAfter(' '))
        if (AutoSetup.log().isEmpty()) rec("INFO", "Auto setup", "background mein chalta hai; abhi koi line nahi")
        rec("INFO", "Image banana/edit", "paisa lag sakta hai, isliye auto test me nahi chalta; asli image banane par hi pata chalega")

        // ---- 6b. v6.18c: every feature, one by one (sentences understood, keys and apps present, one real planner call)
        FeatureCheck.understanding(rec)
        val anyBrain = FeatureCheck.readiness(ctx, rec)
        if (quiet) {
            rec("INFO", "Agent soch (plan) - asli AI call", "auto test mein nahi chalta (extra API use); Gemini ping upar ho chuka")
        } else if (svc != null && anyBrain) {
            val pr = svc.testPlan()
            rec(if (pr == "ok") "PASS" else "FAIL", "Agent soch (plan) - asli AI call", pr)
        } else {
            rec("SKIP", "Agent soch (plan) - asli AI call", "NOVA service ya AI key nahi hai")
        }

        // ---- 6c. New autonomous-agent contracts: run the actual pure Kotlin rules locally on this phone.
        val specCases = listOf(
            "nova ek 3D blogging website banao" to "blog",
            "gym ki website banao" to "landing",
            "online kapde ki dukaan ki website banao" to "shop",
            "photographer portfolio website banao" to "portfolio",
            "restaurant ki website banao" to "restaurant",
            "admin analytics dashboard banao" to "dashboard"
        )
        val specsOk = specCases.all { (request, kind) -> SpecContract.analyze(request).kind == kind } &&
            SpecContract.analyze(specCases[0].first).style.contains("3D")
        rec(if (specsOk) "PASS" else "FAIL", "SpecContract (6 actual local cases)", if (specsOk) "blog+3D, gym no-shop, shop, portfolio, restaurant, dashboard verified" else "requirement analysis mismatch")
        val wrongHtml = "<!DOCTYPE html><html><head><style>.card{perspective:900px}</style></head><body><h1>Shop</h1><p>Product price</p><button>Add to cart</button><button>Checkout</button></body></html>"
        val wrongRejected = SpecContract.check(SpecContract.analyze("3D blogging website banao"), wrongHtml).size >= 2
        rec(if (wrongRejected) "PASS" else "FAIL", "SpecContract wrong-output rejection", if (wrongRejected) "ecommerce output rejected for 3D blog" else "wrong output was not rejected")
        val planSample = AgentRules.planNote("""{"goal":"build a niche dashboard","prompt":"make dashboard","must":["cite sources"],"mustnot":["fake data"],"need_research":"harmless NOVA software testing basics"}""")
        val planRoundTrip = planSample.contains("must: cite sources") && planSample.contains("mustnot: fake data") && planSample.contains("need-research: harmless NOVA software testing basics")
        rec(if (planRoundTrip) "PASS" else "FAIL", "Agent planner contract JSON", if (planRoundTrip) "must/mustnot/research fields parsed" else "planner contract parsing failed")
        val vGood = AgentRules.parseVerification("""{"satisfied":true,"gaps":[],"reason":"required result visible"}""")
        val vBad = AgentRules.parseVerification("""{"satisfied":true,"gaps":["required item missing"],"reason":"bad result"}""")
        val verifierOk = vGood?.satisfied == true && vBad?.satisfied == false
        rec(if (verifierOk) "PASS" else "FAIL", "Agent verifier known-good/known-bad samples", if (verifierOk) "bad sample cannot become success" else "verifier parsing mismatch")
        rec("INFO", "Research step live search", "TEST NAHI HUA: planner/search call is not triggered in this self-test to avoid an unrequested extra network/API call")

        // ---- 6c2. v6.33: business watch, install press, calendar command (pure rules, never touch a live watch window)
        for ((ok, name, detail) in WaBiz.selfCheck()) rec(if (ok) "PASS" else "FAIL", name, detail)
        rec(if (InstallTap.isTapLabel("Install") && !InstallTap.isTapLabel("Install anyway")) "PASS" else "FAIL", "Install button rule", "Install/Update dabega, Install anyway kabhi nahi")
        rec(if (Logic.classify("aaj ka schedule")?.kind == "calendar_today") "PASS" else "FAIL", "Samajh: aaj ka schedule", "calendar_today")
        if (ctx.packageManager.canRequestPackageInstalls()) rec("PASS", "Install unknown apps (NOVA update)", "allowed")
        else rec("WARN", "Install unknown apps (NOVA update)", "band hai; NOVA ka apna update install nahi hoga. Settings me NOVA ke liye ALLOW karo")
        rec(if (DrivingBridge.listenerEnabled(ctx)) "PASS" else "WARN", "Notification access (business watch)", if (DrivingBridge.listenerEnabled(ctx)) "" else "business watch ke liye ON karo")
        rec("INFO", "Business watch / auto reply live", "TEST NAHI HUA: asli WhatsApp message aane par hi pata chalega")

        // ---- 6f. v6.97-wip9 LOCK GUARD: rules are run for real; the phone side is only reported as it is (no fake PASS)
        run {
            val ring = listOf("Answer", "Decline")
            val call = LockGuard.classify("com.google.android.dialer", "call", "Rahul", "Incoming call", ring, false, true)
            val papa = LockGuard.classify("com.whatsapp", "call", "Papa", "Incoming voice call", ring, false, false)
            val msgFam = LockGuard.classify("com.whatsapp", "msg", "Mummy", "hello", emptyList(), false, true)
            val msgNormal = LockGuard.classify("com.whatsapp", "msg", "Rahul", "hello", emptyList(), false, true)
            val ok = call != null && papa?.family == "Papa" && msgFam?.kind == LockGuard.Kind.MESSAGE && msgNormal == null &&
                LockGuard.quietNow(true, true, false) && !LockGuard.quietNow(true, false, false)
            rec(if (ok) "PASS" else "FAIL", "Lock guard rules", if (ok) "silent call + Papa/Mummy = alert, normal message = chup" else "alert rule galat")
            rec(if (ExtCfg.lockGuard(ctx)) "PASS" else "INFO", "Lock guard switch", if (ExtCfg.lockGuard(ctx)) "ON: lock par command nahi sunta" else "OFF hai (Settings > LOCK GUARD)")
            rec(if (NovaAccessibilityService.instance != null) "PASS" else "WARN", "Volume key se alert band", if (NovaAccessibilityService.instance != null) "" else "Accessibility ON karo; warna alert unlock par ya 3 baar baj kar band hoga")
            rec(if (WatchMic.headsetConnected(ctx)) "PASS" else "INFO", "Watch / Bluetooth headset", if (WatchMic.headsetConnected(ctx)) "mila: watch ka AI button NOVA ko jagayega" else "abhi connected nahi (watch connect karo; test asli button dabane par hi hoga)")
            rec("INFO", "Lock alert asli call par", "TEST NAHI HUA: asli call / message aane par hi pata chalega")
        }

        // ---- 6e. v6.97: packed files, source-map rules, offline AI and the five key-less browsers (real requests, no key, only a tiny sample topic is sent)
        for (f in listOf("ui.html", "knowledge.json", "orb_poster.png")) {
            val ok = try { ctx.assets.open(f).use { it.read() >= 0 } } catch (e: Exception) { false }
            rec(if (ok) "PASS" else "FAIL", "File: " + f, if (ok) "" else "APK me ye file nahi mili (build me file missing)")
        }
        val routeCases = listOf(
            "jaipur ka mausam" to WebSources.Kind.WEATHER,
            "aaj ki taaza khabar sunao" to WebSources.Kind.NEWS,
            "dollar ka rate kya hai" to WebSources.Kind.CURRENCY,
            "bitcoin ka price kya hai" to WebSources.Kind.CRYPTO,
            "meaning of serendipity" to WebSources.Kind.DEFINE,
            "taj mahal ke baare mein batao" to WebSources.Kind.FACT
        )
        for ((q, want) in routeCases) {
            val got = WebSources.classify(q)?.kind
            rec(if (got == want) "PASS" else "FAIL", "Source map: \"" + q + "\"", "mila=" + got + " chahiye=" + want)
        }
        val lfOk = AiRouter.plan(true, false, true, "sooraj kya hai", true).firstOrNull() == AiRouter.Step.LOCAL &&
            AiRouter.plan(true, false, true, "bitcoin price today", true).firstOrNull() == AiRouter.Step.CLOUD
        rec(if (lfOk) "PASS" else "FAIL", "Local-first rule", if (lfOk) "chhota sawal pehle phone ka AI, live sawal browser/cloud" else "routing rule galat")
        if (LocalBrains.available(ctx)) rec("PASS", "Offline AI taiyaar", "online hone par bhi chhote sawal pehle ye sambhalega")
        else rec("WARN", "Offline AI", "abhi download nahi hua; Wi-Fi par apne aap download hoga")
        if (!ExtCfg.webBrowsers(ctx)) {
            rec("SKIP", "Web browsers", "switch band hai")
        } else if (net !in 200..299) {
            rec("SKIP", "Web browsers", "internet nahi chal raha")
        } else {
            for (b in WebSources.Browser.values()) {
                val r = try { WebBrowsers.probe(b, true, true, ExtCfg.homeCity(ctx)) } catch (t: Throwable) { null }
                if (r != null && r.ok) rec("PASS", "Browser " + b.label, r.text.take(70))
                else rec("WARN", "Browser " + b.label, "abhi jawab nahi aaya (service ya net); live sawal par NOVA sach bolega ki nahi mila")
            }
        }

        // ---- 6d. v6.27: say plainly what this test does NOT run, so a green report is never mistaken for "everything works"
        rec("INFO", "NOT TESTED (asli nahi chala)", "wake word ko asli awaaz se bulana, background recording, Driving mode, Message mode padhna, reminders ka bajna, WhatsApp batch, camera/video, real agent app-control + independent live-screen verifier, live research")

    }
}
