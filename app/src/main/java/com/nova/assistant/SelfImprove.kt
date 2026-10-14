package com.nova.assistant

import android.content.Context

/**
 * v6.95 runner of ROZ KA SUDHAAR. One run = one question to a NON-Gemini AI (the same free chain: Groq, Qwen, GLM, OpenRouter, with the owner's own keys;
 * no anonymous provider, never Gemini). The answer becomes PENDING proposals only. The owner must press HAAN in Settings. Worker thread only.
 * Only app names and nickname lists leave the phone: no contacts, no messages, no keys, no error log.
 */
object SelfImprove {
    @Volatile private var busy = false

    class Outcome(val kind: String, val added: Int)   // kind: ok | none | busy | budget | off | nokey | fail

    private fun installedAppLabels(ctx: Context): List<String> = try {
        val pm = ctx.packageManager
        val launch = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launch, 0).map { it.loadLabel(pm).toString() }
    } catch (e: Exception) { ErrLog.add("SelfImprove", e.toString()); emptyList() }

    /** The providers this feature may use: the free chain without Gemini and without the anonymous (keyless) one. */
    fun providers(): List<FreeProvider> = SelfImproveRules.allowedProviders(FreeProviders.ordered())

    fun run(ctx: Context, en: Boolean, manual: Boolean, now: Long = System.currentTimeMillis()): Outcome {
        if (busy) return Outcome("busy", 0)
        val remaining = SelfImproveRules.remainingMs(SelfImproveStore.usedToday(ctx, now), SelfImproveStore.today(now), SelfImproveStore.today(now))
        val runs = SelfImproveStore.runsToday(ctx, now)
        val pending = SelfImproveStore.pendingCount(ctx)
        if (!manual && !ExtCfg.selfImprove(ctx)) return Outcome("off", 0)
        val allowed = if (manual) SelfImproveRules.canManualRun(remaining, runs, pending)
        else SelfImproveRules.canAutoRun(true, java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY),
            ExtCfg.selfImproveHour(ctx), remaining, now - SelfImproveStore.lastRun(ctx), runs, pending)
        if (!allowed) return Outcome("budget", 0)
        val apps = installedAppLabels(ctx)
        if (apps.isEmpty()) return Outcome("none", 0)
        val have = SelfImproveStore.aliasesInUse(ctx)
        busy = true
        val t0 = System.currentTimeMillis()
        try {
            val r = ProviderChain.run(
                providers(),
                { name ->
                    when (name) {
                        "groq" -> SecureStore.getGroq(ctx)
                        "openrouter" -> SecureStore.getSlot(ctx, "openrouter")
                        "zai" -> SecureStore.getSlot(ctx, "zai")
                        "zai2" -> SecureStore.getSlot(ctx, "zai2")
                        "zai3" -> SecureStore.getSlot(ctx, "zai3")
                        else -> ""
                    }
                },
                { true },
                SelfImproveRules.systemPrompt(),
                SelfImproveRules.userPrompt(apps, have),
                Cooldowns(),
                { System.currentTimeMillis() },
                budgetMs = minOf(30_000L, remaining)
            ) { p, _, body, key -> FreeHttp.post(p, body, key) }
            val text = r.text
            if (text == null) {
                SelfImproveStore.recordRun(ctx, now, System.currentTimeMillis() - t0, emptyList())
                return Outcome(if (r.tried.isEmpty()) "nokey" else "fail", 0)
            }
            val found = SelfImproveRules.parse(text, apps, have)
            val added = SelfImproveStore.recordRun(ctx, now, System.currentTimeMillis() - t0, found)
            return Outcome(if (added > 0) "ok" else "none", added)
        } catch (t: Throwable) {
            ErrLog.add("SelfImprove", t.toString())
            return Outcome("fail", 0)
        } finally {
            busy = false
        }
    }

    fun message(o: Outcome, en: Boolean): String = when (o.kind) {
        "ok" -> if (en) "${o.added} improvement idea(s) are waiting for your yes in Settings." else "${o.added} sudhaar tumhari haan ka intezaar kar rahe hain (Settings me)."
        "none" -> if (en) "Nothing new to suggest right now." else "Abhi koi naya sudhaar nahi mila."
        "busy" -> if (en) "Already running." else "Pehle se chal raha hai."
        "budget" -> if (en) "Today's 1 hour limit (or the run limit) is used up." else "Aaj ka 1 ghante ka limit (ya run limit) khatam."
        "off" -> if (en) "Daily improve is switched off." else "Roz ka sudhaar band hai."
        "nokey" -> if (en) "No free AI key found. Add a Groq key first." else "Koi free AI key nahi mili. Pehle Groq key daalo."
        else -> if (en) "The AI did not answer. Try later." else "AI ne jawab nahi diya. Baad me try karo."
    }
}
