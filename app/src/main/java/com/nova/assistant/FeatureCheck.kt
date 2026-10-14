package com.nova.assistant

import android.content.Context
import android.content.Intent

/**
 * v6.18c AUTO TEST of every feature, called from SelfTest after the owner has entered settings and keys.
 * Part 1 (understanding): does each voice sentence reach the right feature. Pure code, no network.
 * Part 2 (readiness): which keys / switches / apps each feature needs, and whether they are there. Key VALUES are never printed.
 * It never calls, messages, pays, installs or deletes anything.
 * Evidence level: written + statically checked. Not compiled here, not phone tested.
 */
object FeatureCheck {
    private fun chk(rec: (String, String, String) -> Unit, ok: Boolean, name: String, detail: String) =
        rec(if (ok) "PASS" else "FAIL", name, detail)

    /** Part 1. Every line says "Samajh" = only the sentence was understood, the feature itself was not run. */
    fun understanding(rec: (String, String, String) -> Unit) {
        val s1 = try { SiteIntent.parse("nova ek restaurant ki website banao") } catch (e: Throwable) { null }
        chk(rec, s1 != null && s1.subject == "restaurant", "Samajh: website banao", "subject=" + (s1?.subject ?: "none"))
        val s2 = try { SiteIntent.parse("ek website banao jahan nova app download ho sake") } catch (e: Throwable) { null }
        chk(rec, s2 != null && s2.raw.contains("nova"), "Samajh: NOVA download website (poora sentence AI tak jata hai)", "raw=" + (s2?.raw?.take(40) ?: "none"))
        chk(rec, try { SiteIntent.isPublish("nova website publish karo") } catch (e: Throwable) { false }, "Samajh: website publish karo", "")
        val im = try { ImageIntent.parse("nova ek billi ki photo banao") } catch (e: Throwable) { null }
        chk(rec, im != null && !im.blocked, "Samajh: photo banao", "subject=" + (im?.subject ?: "none"))
        val ln = try { LearnRules.parse("nova solar panel ke baare mein seekho") } catch (e: Throwable) { null }
        chk(rec, ln != null && !ln.blocked, "Samajh: seekho", "topic=" + (ln?.topic ?: "none"))
        chk(rec, try { AgentRules.isTask("chatgpt open karo aur usse ek banner banwao") } catch (e: Throwable) { false }, "Samajh: ChatGPT se banner banwao (agent)", "")
        chk(rec, try { AgentRules.isTask("chatgpt kholo aur usse ek website banwao") } catch (e: Throwable) { false }, "Samajh: ChatGPT se website banwao (agent)", "")
        chk(rec, try { !AgentRules.isTask("battery kitni hai") && !AgentRules.isTask("open youtube") } catch (e: Throwable) { false }, "Samajh: chhote command agent nahi bante", "")
        val act = try { AgentRules.parse("{\"act\":\"done\",\"say\":\"ok\"}") } catch (e: Throwable) { null }
        chk(rec, act != null && act.act == "done", "Agent: AI ka jawab padhna (JSON)", "")
        val pn = try { AgentRules.planNote("{\"goal\":\"g\",\"prompt\":\"p\"}") } catch (e: Throwable) { "" }
        chk(rec, pn.contains("text-to-type"), "Agent: plan note banana", "")
        val html = "<!DOCTYPE html><html><body>" + "x".repeat(260) + "</body></html>"
        chk(rec, try { SiteGen.cleanHtml("junk " + html + " junk") != null && SiteGen.cleanHtml("no page here") == null } catch (e: Throwable) { false }, "Website: AI ke jawab se page nikalna", "")
        chk(rec, try { SiteGen.userPrompt("x", "nova app download").contains("nova app download") } catch (e: Throwable) { false }, "Website: poora sentence AI ko jata hai", "")
        chk(rec, try { Logic.bestAppIndex("twitter", listOf("X")) == 0 && Logic.bestAppIndex("insta", listOf("Instagram")) == 0 && Logic.bestAppIndex("wa", listOf("WhatsApp")) == 0 } catch (e: Throwable) { false }, "Apps: Twitter/X, Insta, WA aliases", "")
        chk(rec, try { LeadRules.command("lead capture")?.kind == "lead_capture" && LeadRules.command("lead draft english")?.arg == "en" && LeadRules.command("lead bhejo")?.kind == "lead_send" } catch (e: Throwable) { false }, "Leads: capture/draft/batch commands", "")
        chk(rec, try { OfflineQueueRules.command("queue dikhao")?.kind == "queue_list" && OfflineQueueRules.command("queue saaf karo")?.kind == "queue_clear" && !OfflineQueueRules.mayQueue(OfflineQueueRules.Kind.WEBSITE, true) } catch (e: Throwable) { false }, "Offline queue: voice commands and screen-task block", "")
        chk(rec, try { Logic.classify("message mode on")?.kind == "message_on" && Logic.classify("message mode off")?.kind == "message_off" && Logic.classify("messages padho")?.kind == "message_read" && Logic.classify("reply likho")?.kind == "message_dictate" } catch (e: Throwable) { false }, "Message mode: on/off/read/dictate commands", "")
        chk(rec, try { Logic.classify("message apps linkedin on")?.let { it.kind == "message_app" && it.arg == "com.linkedin.android" } == true && Logic.classify("message apps x on")?.arg == "com.twitter.android" } catch (e: Throwable) { false }, "Message mode: optional X/LinkedIn app selection", "")
        val scheduled = try { ScheduledTaskRules.parse("roz subah 8 baje weather bolo") } catch (e: Throwable) { null }
        chk(rec, scheduled != null && scheduled.hour == 8 && scheduled.repeatDaily && scheduled.kind == ScheduledTaskRules.Kind.WEATHER, "Schedule: Hinglish daily weather at 8", "")
        chk(rec, try { AgentTaskRules.isHistoryQuery("task history") && AgentTaskRules.canResumeCheckpoint("open_app", true) && !AgentTaskRules.canResumeCheckpoint("tap", true) && !AgentTaskRules.canResumeCheckpoint("type", true) && AgentTaskRules.isResumeQuery("resume last task") } catch (e: Throwable) { false }, "Agent checkpoint/history + safe resume: unsafe replay blocked", "")
    }

    private fun appInstalled(ctx: Context, name: String): Boolean = try {
        val pm = ctx.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        Logic.bestAppIndex(name, list.map { it.loadLabel(pm).toString() }) >= 0
    } catch (e: Throwable) { false }

    private fun feat(rec: (String, String, String) -> Unit, ok: Boolean, name: String, missing: String) {
        if (ok) rec("PASS", "Taiyaar: " + name, "") else rec("WARN", "Band: " + name, missing)
    }

    /** Part 2. Returns true when at least one AI key exists (the caller then runs the one real planner call). */
    fun readiness(ctx: Context, rec: (String, String, String) -> Unit): Boolean {
        val gem = try { SecureStore.getKeys(ctx).size } catch (e: Throwable) { 0 }
        val gemOk = gem > 0 || (try { SecureStore.hasKeys(ctx) } catch (e: Throwable) { false })
        val groq = try { SecureStore.hasGroq(ctx) } catch (e: Throwable) { false }
        val orr = try { SecureStore.hasSlot(ctx, "openrouter") } catch (e: Throwable) { false }
        val zai = try { SecureStore.hasSlot(ctx, "zai") || SecureStore.hasSlot(ctx, "zai2") || SecureStore.hasSlot(ctx, "zai3") } catch (e: Throwable) { false }
        val gh = try { SecureStore.hasSlot(ctx, GitHubPublish.SLOT) } catch (e: Throwable) { false }
        rec(if (gemOk) "PASS" else "SKIP", "Key: Gemini", if (gemOk) "hai (asli check upar Gemini test mein)" else "nahi hai")
        rec(if (groq) "PASS" else "SKIP", "Key: Groq", if (groq) "hai" else "nahi hai (optional)")
        rec(if (orr) "PASS" else "SKIP", "Key: OpenRouter", if (orr) "hai" else "nahi hai (optional)")
        rec(if (zai) "PASS" else "SKIP", "Key: Z.ai", if (zai) "hai" else "nahi hai (optional)")
        rec(if (gh) "PASS" else "SKIP", "Key: GitHub token (publish)", if (gh) "hai" else "nahi hai (optional)")
        val brain = gemOk || groq || orr || zai
        val acc = NovaAccessibilityService.instance != null
        feat(rec, gemOk || groq, "Website banana (voice)", "Gemini ya Groq key daalo")
        feat(rec, gh, "Website publish (GitHub)", "GitHub token daalo: Settings > GITHUB PUBLISH")
        feat(rec, brain, "Seekho (internet se seekhna)", "koi AI key daalo")
        feat(rec, brain && acc, "Task agent (app khol kar kaam)", if (!acc) "Accessibility ON karo" else "koi AI key daalo")
        feat(rec, appInstalled(ctx, "ChatGPT"), "ChatGPT app phone mein", "ChatGPT app install nahi mila; ChatGPT wale kaam nahi honge")
        feat(rec, appInstalled(ctx, "Gemini"), "Gemini app phone mein", "Gemini app install nahi mila (optional)")
        val keyless = try { ExtCfg.freeKeyless(ctx) } catch (e: Throwable) { false }
        val chain = try { ExtCfg.freeChain(ctx) } catch (e: Throwable) { false }
        rec(if (chain && (groq || orr || zai || keyless)) "PASS" else "WARN", "AI fallback chain", if (chain) "Gemini ke baad configured Groq/Qwen, Z.ai, OpenRouter aur optional Pollinations text fallback" else "Free chain OFF; Settings mein enable karo")
        val access = try { NovaAccessibilityService.isEnabled(ctx) } catch (e: Throwable) { false }
        val wa = try { ctx.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b") != null || ctx.packageManager.getLaunchIntentForPackage("com.whatsapp") != null } catch (e: Throwable) { false }
        feat(rec, access, "Public lead capture / app control", "NOVA Phone Control (Accessibility) ON karo")
        feat(rec, wa, "WhatsApp batch (owner approval required)", "WhatsApp ya WhatsApp Business install karo")
        feat(rec, brain || keyless, "Offline cloud queue worker", "Gemini ya koi configured text provider chahiye; internet wapas aane par jobs process hongi")
        rec("INFO", "Agent task history/checkpoints (TEST NAHI HUA)", "Local redacted history; resume is manual and only allowed after completed open_app/wait-only steps, within original deadline; all other steps fail closed")
        rec("INFO", "Calendar/meeting rules (TEST NAHI HUA)", "READ_CALENDAR and Contacts access are still requested only from an owner tap")
        return brain
    }
}
