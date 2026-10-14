package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * v6.30: the home-page box "KYA CHALTA HAI / KYA NAHI". Every row is read from the REAL state of the phone and the app
 * (permission granted? key saved? service running? last cloud error?). Nothing here runs a feature, so the owner is told
 * "state" and a reason - the real work test runs automatically (AutoTest, report in Settings > TEST REPORT). Evidence level: written + statically checked, not compiled here.
 * state: "ok" = ready, "off" = not working (reason says why), "warn" = works but with a problem (limit, optional missing).
 */
object FeatureStatus {
    private fun row(a: JSONArray, id: String, name: String, state: String, why: String, fix: String) {
        a.put(JSONObject().put("id", id).put("name", name).put("state", state).put("why", why).put("fix", fix))
    }

    private fun granted(ctx: Context, p: String): Boolean = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Newest error of a provider in the last 10 minutes, or null. */
    private fun recentErr(src: String): String? = ErrLog.latest(src, 10 * 60 * 1000L)

    fun json(ctx: Context): String {
        val a = JSONArray()
        val mic = granted(ctx, Manifest.permission.RECORD_AUDIO)
        val running = NovaService.running
        val svc = NovaService.instance
        // 1. wake word listening (v6.74: the real reason comes from VoiceDiagnosis: refused service start, mic busy, model missing)
        val wr = VoiceDiagnosis.wake(mic, running, NovaService.startFailure, NovaService.lastFatal, false)
        row(a, "wake", "Wake word sunna", wr.state, wr.why, wr.fix)
        // 2. spoken replies
        val ts = try { svc?.testState() ?: "" } catch (e: Throwable) { "" }
        val hv = when {
            ts.contains("hindi_voice=missing") -> "missing"
            ts.contains("hindi_voice=ok") -> "ok"
            else -> "unknown"
        }
        val tr = VoiceDiagnosis.tts(running && svc != null, ts.contains("ttsReady=true"), NovaService.ttsInitCode, hv, false)
        row(a, "tts", "Awaaz mein jawab", tr.state, tr.why, tr.fix)
        // 3. accessibility based features
        val acc = NovaAccessibilityService.isEnabled(ctx)
        row(a, "acc", "Phone Control + Screen padhna", if (acc) "ok" else "off",
            if (acc) "Accessibility ON hai" else "Accessibility ON nahi hai: tap/scroll/back aur screen padhna band hain", if (acc) "" else "acc")
        // 4. floating card
        val ov = Settings.canDrawOverlays(ctx)
        row(a, "overlay", "Wake par card (popup)", if (ov) "ok" else "off",
            if (ov) "Display over other apps ON hai" else "Display over other apps ON nahi hai: doosre app ke upar card nahi dikhega", if (ov) "" else "overlay")
        // 4b. v6.67: jobs inside other apps (chat search by name, YouTube play/pause/channel) need Accessibility + an AI key
        val aiKey = try { SecureStore.hasKeys(ctx) || SecureStore.hasGroq(ctx) } catch (e: Throwable) { false }
        row(a, "inapp", "Apps ke andar kaam (chat, YouTube)", if (acc && aiKey) "ok" else "off",
            if (acc && aiKey) "Chat khojna, naam chunna, message type karna, YouTube chalana / roakna taiyaar hai"
            else if (!acc) "Accessibility ON nahi hai: doosre app ke andar kaam nahi hoga"
            else "AI key nahi hai: screen samajhne wala dimaag chalu nahi hoga", if (!acc) "acc" else if (!aiKey) "groq_key" else "")
        // 5. driving mode
        val na = DrivingBridge.listenerEnabled(ctx)
        row(a, "driving", "Driving mode (messages sunna)", if (na) "ok" else "off",
            if (na) "Notification access ON hai" else "Notification access ON nahi hai: messages bol kar nahi sunayega", if (na) "" else "notifAccess")
        // 6. contacts
        val ct = granted(ctx, Manifest.permission.READ_CONTACTS)
        row(a, "contacts", "Naam se call / message", if (ct) "ok" else "off",
            if (ct) "Contacts permission hai" else "Contacts permission nahi hai: naam se call nahi hoga", if (ct) "" else "contacts")
        // 7. brightness
        val ws = Settings.System.canWrite(ctx)
        row(a, "bright", "Brightness control", if (ws) "ok" else "off",
            if (ws) "Write settings ON hai" else "Write settings ON nahi hai: brightness nahi badlegi", if (ws) "" else "writeSettings")
        // 8. background (v6.74: brand-aware advice, e.g. Xiaomi Autostart)
        val bat = (ctx.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        val br = VoiceDiagnosis.battery(bat, Build.MANUFACTURER ?: "", false)
        row(a, "battery", "Background mein chalte rehna", br.state, br.why, br.fix)
        // 9. Gemini
        val gem = SecureStore.hasKeys(ctx)
        val ge = recentErr("gemini")
        when {
            !gem -> row(a, "gemini", "Gemini AI (lambe sawaal, screen analyze)", "off", "Gemini key nahi hai", "gemini_key")
            ge != null && ge.contains("429") -> row(a, "gemini", "Gemini AI (lambe sawaal, screen analyze)", "warn", "Gemini ki limit poori (429). Backup AI chal rahe hain; limit baad mein khulegi", "")
            ge != null && (ge.contains("503") || ge.contains("500")) -> row(a, "gemini", "Gemini AI (lambe sawaal, screen analyze)", "warn", "Gemini ka server busy tha (5xx). Backup AI use hoga", "")
            else -> row(a, "gemini", "Gemini AI (lambe sawaal, screen analyze)", "ok", "Key hai, haal mein koi error nahi", "")
        }
        // 10. backup chain
        val names = ArrayList<String>()
        if (SecureStore.hasGroq(ctx)) { names.add("Groq"); names.add("Qwen") }
        var zn = 0
        for (s in listOf("zai", "zai2", "zai3")) if (SecureStore.hasSlot(ctx, s)) zn++
        if (zn > 0) names.add(if (zn == 1) "GLM" else "GLM x$zn")
        if (SecureStore.hasSlot(ctx, "openrouter")) names.add("OpenRouter")
        val chainOn = ExtCfg.freeChain(ctx)
        when {
            names.isEmpty() -> row(a, "chain", "Backup AI (Groq / Qwen / GLM / OpenRouter)", "off", "Koi backup key nahi hai: Gemini band ho to bhi jawab dene wala koi nahi", "groq_key")
            !chainOn -> row(a, "chain", "Backup AI (Groq / Qwen / GLM / OpenRouter)", "off", "Keys hain par Free chain OFF hai (Settings mein ON karo)", "chain")
            else -> row(a, "chain", "Backup AI (Groq / Qwen / GLM / OpenRouter)", "ok", names.joinToString(", ") + " taiyaar", "")
        }
        // 12. website publish
        val gh = try { SecureStore.hasSlot(ctx, GitHubPublish.SLOT) } catch (e: Throwable) { false }
        row(a, "publish", "Website publish (GitHub)", if (gh) "ok" else "off",
            if (gh) "GitHub token hai" else "GitHub token nahi hai: website banegi par publish nahi hogi (optional)", if (gh) "" else "github")
        // Auto-Heal and the wake diagnosis (what NOVA fixed or noticed by itself)
        val heal = try { svc?.healInfo() ?: emptyList() } catch (e: Throwable) { emptyList<String>() }
        row(a, "heal", "Auto-Heal (NOVA khud theek karta hai)", if (heal.isEmpty()) "ok" else "warn",
            if (heal.isEmpty()) (if (running) "Har 30 second apni jaanch karta hai; abhi kuch theek karna nahi pada" else "NOVA band hai, isliye jaanch nahi chal rahi")
            else heal.take(3).joinToString(" | "), "")
        val wk = try { svc?.wakeInfo() ?: "" } catch (e: Throwable) { "" }
        if (wk.isNotEmpty()) row(a, "wakediag", "Bina bulaye jaagna (diagnosis)", "ok", wk, "")
        // Last agent job is read from the private local ledger (no screen text, audio, or API keys are stored here).
        val lastJob = try { AgentTaskLedger(ctx).latest() } catch (e: Throwable) { null }
        if (lastJob == null) {
            row(a, "lastjob", "Last agent job", "warn", "Abhi koi task history nahi hai", "")
        } else {
            val (st, why) = when (lastJob.state) {
                AgentTaskRules.State.SUCCEEDED -> "ok" to "Verifier passed: " + lastJob.summary
                AgentTaskRules.State.REPORTED_COMPLETE -> "warn" to "Completion verify nahi hua: " + lastJob.summary
                AgentTaskRules.State.NEEDS_OWNER -> "warn" to "Owner ki action zaroori thi: " + lastJob.summary
                AgentTaskRules.State.RUNNING, AgentTaskRules.State.PLANNING, AgentTaskRules.State.VERIFYING, AgentTaskRules.State.RETRYING -> "warn" to "Task abhi chal raha hai: " + lastJob.summary
                AgentTaskRules.State.FAILED -> "off" to "Task fail hua: " + lastJob.summary
                AgentTaskRules.State.CANCELLED -> "warn" to "Task rok diya: " + lastJob.summary
                AgentTaskRules.State.INTERRUPTED -> "warn" to "Task interrupt hua; automatic replay nahi hoga: " + lastJob.summary
                else -> "warn" to "Task status: " + lastJob.summary
            }
            row(a, "lastjob", "Last agent job", st, why.take(360), "")
        }
        val readyNames = ArrayList<String>()
        if (running && mic) readyNames.add("wake-word listening")
        if (acc) readyNames.add("screen read/control")
        if (SecureStore.hasKeys(ctx) || (chainOn && names.isNotEmpty())) readyNames.add("AI answers/planning")
        readyNames.add("local phone commands")
        readyNames.add("website generation" + if (SecureStore.hasKeys(ctx) || SecureStore.hasGroq(ctx) || SecureStore.hasSlot(ctx, "zai") || SecureStore.hasSlot(ctx, "openrouter")) " (provider configured)" else " (needs AI key)")
        row(a, "capabilities", "Kya kar sakta hoon", "ok", readyNames.joinToString(" | ").take(360), "")

        // 13. install
        val notif = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
        row(a, "notif", "NOVA chalu hai ka notification", if (notif) "ok" else "warn",
            if (notif) "Notification permission hai" else "Notification permission nahi hai: phone service band kar sakta hai", if (notif) "" else "notif")
        return JSONObject().put("items", a).put("running", running).toString()
    }
}
