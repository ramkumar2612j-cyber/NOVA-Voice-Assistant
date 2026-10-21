package com.nova.assistant

import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject

/**
 * v7.35 AUTO VERIFY: NOVA checks BY ITSELF that every feature is understood AND wired, with no button and no popup (it starts 12 s after the app opens,
 * at most once per 10 minutes, and again when the owner taps the AUTO VERIFY box). Read-only: it never calls, messages, pays, installs, deletes,
 * speaks, flashes the torch or changes a setting.
 *  1. [FeatureCheck.understanding]: all older "Samajh" checks (website, image, learn, agent, open web, leads, queue, message mode, schedule ...).
 *  2. [probes]: Parts 1-5 (auto image/site/photo-edit/write detection, Devanagari voice text, 9 phone commands + ToolVerify list, help guide, auto trace, privacy mask).
 *  3. [stateChecks]: real phone state: FeatureStatus rows, History log, health report contains AUTO TRACE.
 *  4. page: ui.html (script p6_verify) checks that every Android.* function the page calls exists in the JS bridge, every JS function Kotlin calls exists,
 *     the key elements and the new scripts exist, then answers through Android.verifyPage.
 * Honest limit: "PASS" here means understood + wired. The real hardware work (torch, volume, mic ...) is still the job of the older SelfTest.
 * Privacy: only fixed test sentences are used; the owner's text is never read. Result is kept in memory, shown in the app, appended to the health report.
 */
object FeatureVerify {
    const val MIN_GAP_MS = 10 * 60_000L
    @Volatile var lastReport: String = ""
    @Volatile var lastSummary: String = ""
    @Volatile private var pageJson: String? = null
    @Volatile var pageDiag: String = ""
    @Volatile private var running = false
    @Volatile private var lastAt = 0L
    @Volatile private var lastFails: List<String> = emptyList()

    /** Called by the page (bridge verifyPage). Kept small. */
    fun setPage(json: String) { pageJson = json.take(8000) }

    private fun chk(rec: (String, String, String) -> Unit, ok: Boolean, name: String, detail: String = "") =
        rec(if (ok) "PASS" else "FAIL", name, if (ok) "" else detail)

    private val AUTO_TABLE: List<Pair<String, AutoIntent.Kind>> = listOf(
        "mujhe ek logo chahiye" to AutoIntent.Kind.IMAGE,
        "i need a poster for my cafe" to AutoIntent.Kind.IMAGE,
        "ek billi draw karo" to AutoIntent.Kind.IMAGE,
        "website ke liye logo banao" to AutoIntent.Kind.IMAGE,
        "gym ke liye site chahiye" to AutoIntent.Kind.SITE,
        "online store banwana hai" to AutoIntent.Kind.SITE,
        "logo wali website banao" to AutoIntent.Kind.SITE,
        "make a website for my gym" to AutoIntent.Kind.SITE,
        "website kaise banate hain" to AutoIntent.Kind.NONE,
        "photo whatsapp pe bhejo" to AutoIntent.Kind.NONE,
        "kal subah 7 baje alarm lagao" to AutoIntent.Kind.NONE,
        "ek kahani likh do" to AutoIntent.Kind.WRITE
    )

    private val CMD_TABLE: List<Pair<String, String>> = listOf(
        "Papa ko call karo" to "call", "torch off" to "torch_off", "volume up" to "volume", "what time is it" to "time",
        "battery" to "battery", "stop" to "stop", "play music" to "media", "brightness up" to "brightness_step", "aaj ki tarikh" to "date"
    )

    /** Pure Kotlin probes for Parts 1-5 and the phone commands. Safe in JUnit. */
    fun probes(rec: (String, String, String) -> Unit) {
        val bad = ArrayList<String>()
        for ((q, k) in AUTO_TABLE) {
            val r = try { AutoIntent.detect(q, false).kind } catch (e: Throwable) { null }
            if (r != k) bad.add(q.take(18) + "=" + (r?.name ?: "err"))
        }
        chk(rec, bad.isEmpty(), "Auto: " + AUTO_TABLE.size + " sentences image / website / likhna sahi samjhe", bad.joinToString(", ").take(110))
        val pe = try { AutoIntent.detect("background hatao", true).kind } catch (e: Throwable) { null }
        chk(rec, pe == AutoIntent.Kind.PHOTO_EDIT, "Auto: photo ke sath edit samjha (PHOTO_EDIT)", "kind=" + (pe?.name ?: "err"))
        val vt = try { VoiceText.normalize("वॉल्यूम ५० करो") } catch (e: Throwable) { "err" }
        chk(rec, vt == "volume 50 karo", "Voice: Devanagari bol Roman ban kar routing tak jata hai", "got=" + vt.take(30))
        val cb = ArrayList<String>()
        for ((q, k) in CMD_TABLE) {
            val r = try { Logic.classify(q)?.kind } catch (e: Throwable) { "err" }
            if (r != k) cb.add(q.take(14) + "=" + r)
        }
        chk(rec, cb.isEmpty(), "Commands: " + CMD_TABLE.size + " phone commands (call, torch, volume, time, battery, stop, music, brightness, date) sahi samjhe", cb.joinToString(", ").take(110))
        val tools = try { ToolVerify.KNOWN_TOOLS.containsAll(listOf("call", "torch_off", "volume", "media", "brightness_step", "open_app", "alarm", "sms", "whatsapp")) } catch (e: Throwable) { false }
        chk(rec, tools, "Commands: har tool ka verify level maujood (ToolVerify)", "KNOWN_TOOLS kam hai")
        val help = try { HelpGuide.isAsk("tum kya kar sakte ho") && HelpGuide.TOPICS.isNotEmpty() && HelpGuide.TOPICS.all { HelpGuide.answer(it.example, false).isNotBlank() } } catch (e: Throwable) { false }
        chk(rec, help, "Help: MADAD topics + 'tum kya kar sakte ho' jawab dete hain", "HelpGuide")
        val fv = try { FeatureVoice.key("auto reply ka status batao") == "autoreply" } catch (e: Throwable) { false }
        chk(rec, fv, "Status sawal: 'auto reply ka status' samjha", "FeatureVoice")
        val at = try { AutoTrace.isAsk("auto kya samjha") && !AutoTrace.isAsk("mujhe ek logo chahiye") && !AutoTrace.cleanSubject("abc 9876543210").contains("9876543210") } catch (e: Throwable) { false }
        chk(rec, at, "Log: 'auto kya samjha' samjha + number / e-mail masked", "AutoTrace")
        val er = try { !ErrLog.clean("mail abc@xyz.com 9876543210").contains("abc@xyz.com") } catch (e: Throwable) { false }
        chk(rec, er, "Privacy: error log e-mail / number chhupata hai", "ErrLog.clean")
        val me = try { isAsk("auto verify status") && !isAsk("mujhe ek logo chahiye") } catch (e: Throwable) { false }
        chk(rec, me, "Verify: 'auto verify status' sawal samjha", "FeatureVerify.isAsk")
        // v7.98: the newer skills must be reachable by voice, not only listed
        val nw = ArrayList<String>()
        if (try { TradeRules.parse("gold ka analysis karo") == null } catch (e: Throwable) { true }) nw.add("trade")
        if (try { UnreadQueryRules.parse("ye btao insta pe koi naya message aaya h") == null } catch (e: Throwable) { true }) nw.add("unread")
        if (try { !FeatureFixRules.matches("band features set karo") } catch (e: Throwable) { true }) nw.add("features")
        if (try { Logic.classify("3:30 ka schedule banao rss meeting me jana hai")?.kind != "calendar_add" } catch (e: Throwable) { true }) nw.add("meeting")
        if (try { ProfileSearchRules.parse("instagram par rahul_sharma search karo") == null } catch (e: Throwable) { true }) nw.add("profile")
        if (try { CameraEyeRules.parse("peeche wale camera se dekho ye kya hai") == null } catch (e: Throwable) { true }) nw.add("camera")
        chk(rec, nw.isEmpty(), "Naye skills: trade, naya message, band features, meeting, profile, camera bolne se pakde jate hain", nw.joinToString(", "))
    }

    /** Real phone state. Never changes anything. */
    fun stateChecks(act: Activity, rec: (String, String, String) -> Unit) {
        val fs = try { org.json.JSONObject(FeatureStatus.json(act)).optJSONArray("items") } catch (e: Throwable) { null }
        var rowsOk = fs != null && fs.length() > 0
        if (fs != null) for (i in 0 until fs.length()) { val o = fs.optJSONObject(i); if (o == null || o.optString("id").isEmpty() || o.optString("state").isEmpty()) rowsOk = false }
        chk(rec, rowsOk, "Home: KYA CHALTA HAI list (" + (fs?.length() ?: 0) + " rows) bani", "FeatureStatus.json")
        val hist = try { JSONArray(ActionLog.listJson(act)); true } catch (e: Throwable) { false }
        chk(rec, hist, "History: NOVA NE KYA KIYA list padhi ja sakti hai", "ActionLog.listJson")
        val rep = try { HealthCheck.report(act, emptyList()) } catch (e: Throwable) { "" }
        chk(rec, rep.contains("AUTO TRACE"), "Health report me AUTO TRACE section hai", "HealthCheck.report")
        rec(if (NovaService.running) "PASS" else "WARN", if (NovaService.running) "NOVA service chal rahi hai" else "Band: NOVA service abhi chalu nahi (ACTIVATE dabao)", "")
    }

    private fun arr(o: JSONObject, k: String): String {
        val a = o.optJSONArray(k) ?: return ""
        val l = ArrayList<String>()
        for (i in 0 until a.length()) l.add(a.optString(i))
        return l.take(8).joinToString(",")
    }

    /** The answer of the page (script p6_verify). */
    fun pageChecks(json: String?, rec: (String, String, String) -> Unit) {
        if (json == null) { rec("FAIL", "Page: ui.html ne jawab nahi diya (p6_verify)", "page script nahi chala" + (if (pageDiag.isNotEmpty()) " [" + pageDiag + "]" else "")); return }
        val o = try { JSONObject(json) } catch (e: Throwable) { null }
        if (o == null) { rec("FAIL", "Page: jawab padha nahi gaya", ""); return }
        val mb = arr(o, "missingBridge"); val mc = arr(o, "missingCb"); val mi = arr(o, "missingIds"); val ms = arr(o, "missingScripts")
        chk(rec, mb.isEmpty(), "Wiring: ui.html ke " + o.optInt("bridge") + " Android.* kaam Kotlin bridge me maujood", "missing: $mb")
        chk(rec, mc.isEmpty(), "Wiring: Kotlin ke " + o.optInt("cb") + " JS callback ui.html me maujood", "missing: $mc")
        chk(rec, mi.isEmpty(), "UI: chat, home core, stars, chip ke elements maujood", "missing: $mi")
        chk(rec, ms.isEmpty(), "UI: auto detect, chat effects, home, log scripts load hue", "missing: $ms")
    }

    fun summaryOf(lines: List<String>): String {
        val p = lines.count { it.startsWith("PASS") }; val f = lines.count { it.startsWith("FAIL") }; val w = lines.count { it.startsWith("WARN") }
        return if (f == 0) "AUTO VERIFY: $p pass, koi FAIL nahi, sab features jude hain" + (if (w > 0) " ($w WARN)" else "")
        else "AUTO VERIFY: $f FAIL, $p pass. Box dabao ya health report dekho"
    }

    /** Runs everything once. [askPage] must make the page call Android.verifyPage; [show] gets (one-line summary, full report). */
    fun start(act: Activity, delayMs: Long, askPage: () -> Unit, show: (String, String) -> Unit): String {
        if (running) return "busy"
        val now = System.currentTimeMillis()
        if (delayMs > 0 && lastAt != 0L && now - lastAt < MIN_GAP_MS) return "recent"
        running = true
        Thread {
            try {
                if (delayMs > 0) Thread.sleep(delayMs)
                if (act.isDestroyed || act.isFinishing) return@Thread
                val out = ArrayList<String>()
                val rec: (String, String, String) -> Unit = { tag, name, detail -> out.add(tag.padEnd(5) + " " + name + (if (detail.isNotEmpty()) " - " + detail else "")) }
                try { FeatureCheck.understanding(rec) } catch (e: Throwable) { rec("FAIL", "Samajh checks beech me ruk gaye", e.javaClass.simpleName) }
                try { probes(rec) } catch (e: Throwable) { rec("FAIL", "Parts 1-5 probes ruk gaye", e.javaClass.simpleName) }
                try { stateChecks(act, rec) } catch (e: Throwable) { rec("FAIL", "State checks ruk gaye", e.javaClass.simpleName) }
                // v7.100: the page may answer late (heavy orb, screen just closed). Ask again every 4 s for up to 24 s; an earlier answer of the same page is also valid (page is a static asset).
                var g = 0
                while (pageJson == null && g < 120) {
                    if (g % 20 == 0) { try { askPage() } catch (e: Throwable) { ErrLog.addOnce("quiet:FeatureVerify", e.toString()) } }
                    Thread.sleep(200); g++
                }
                if (pageJson == null && !NovaService.uiVisible) {
                    // screen closed: the WebView is paused, that is not a bug. Do not show a false FAIL; check again at the next open.
                    rec("WARN", "Page: screen band tha, app khulne par dobara check hoga", "")
                    pageJson = null
                    lastAt = 0L
                } else {
                    pageChecks(pageJson, rec)
                    lastAt = System.currentTimeMillis()
                }
                val sum = summaryOf(out)
                val fails = out.filter { it.startsWith("FAIL") }
                lastFails = fails.map { it.drop(6).take(100) }
                lastSummary = sum
                lastReport = "NOVA AUTO VERIFY\n" + out.joinToString("\n") + "\n" + sum
                for (f in lastFails.take(5)) ErrLog.add("Verify", f)
                try { ActionLog.add(act, "NOVA VERIFY", "NOVA", "", if (fails.isEmpty()) "sent" else "failed", sum.take(120)) } catch (e: Throwable) { ErrLog.addOnce("quiet:FeatureVerify", e.toString()) }
                show(sum, lastReport)
            } catch (e: Throwable) {
                ErrLog.add("Verify", "verify run failed: " + e.javaClass.simpleName)
            } finally {
                running = false
            }
        }.start()
        return "started"
    }

    /** For the health report and the spoken answer. */
    fun healthLines(): String {
        if (lastSummary.isEmpty()) return "AUTO VERIFY: abhi chala nahi (app khulne ke 12 sec baad apne aap chalta hai)"
        return lastSummary + (if (lastFails.isEmpty()) "" else "\n" + lastFails.joinToString("\n") { "- FAIL " + it })
    }

    /** "auto verify status", "sab features check", "features jude hain kya". Read-only: tells the last result. */
    fun isAsk(text: String): Boolean {
        val n = Logic.norm(text)
        if (n.isBlank() || n.length > 60) return false
        return listOf("auto verify", "verify status", "feature verify", "sab feature check", "saare feature check", "sab features check", "saare features check", "features jude", "feature jude", "wiring check").any { n.contains(it) }
    }
}
