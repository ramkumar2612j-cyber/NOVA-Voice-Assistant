package com.nova.assistant

import android.content.Context

/** Extra settings that live in their own small store: how long NOVA waits after you stop speaking. */
object ExtCfg {
    private const val PREF = "nova_ext"
    private const val WAIT = "wait_sec"
    const val DEFAULT_WAIT_SEC = 5

    fun waitSec(ctx: Context): Int = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(WAIT, DEFAULT_WAIT_SEC).coerceIn(2, 10)
    } catch (e: Exception) { DEFAULT_WAIT_SEC }

    fun setWaitSec(ctx: Context, sec: Int) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(WAIT, sec.coerceIn(2, 10)).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    fun waitMs(ctx: Context): Int = waitSec(ctx) * 1000

    private const val ALERTS = "alerts_on"

    /** v16 proactive alerts (low battery spoken once per 30 minutes). Default OFF since v6.30 (it made NOVA listen without being called). */
    fun alerts(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(ALERTS, true)
    } catch (e: Exception) { true }

    fun setAlerts(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(ALERTS, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    private const val CONVO = "convo_mode_v2"   // v6.30: new key so the old default-ON value of existing installs is dropped

    /** v16 conversation mode: after a spoken reply NOVA listens again (up to 3 times in a row) without the wake word. Default OFF since v6.30 (it made NOVA listen without being called). */
    fun convo(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(CONVO, false)
    } catch (e: Exception) { false }

    fun setConvo(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(CONVO, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    private const val AUTO_MODELS = "auto_models"

    /** v17: download optional models (Hindi pack, offline brain) by itself. Default ON. */
    fun autoModels(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(AUTO_MODELS, true)
    } catch (e: Exception) { true }

    fun setAutoModels(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(AUTO_MODELS, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    private const val AUTO_LEARN = "auto_learn"

    /** v6.38 owner request: RAKHO is required again. Auto learn (v6.37) is now OFF by default; the old pending note + RAKHO flow is used. */
    fun autoLearn(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(AUTO_LEARN, false)
    } catch (e: Exception) { false }

    fun setAutoLearn(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(AUTO_LEARN, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    private const val NOISE_CANCEL = "noise_cancel"

    /** v6.36: background noise cancellation (phone's NoiseSuppressor + echo canceller) on the mic. Default ON. */
    fun noiseCancel(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(NOISE_CANCEL, true)
    } catch (e: Exception) { true }

    fun setNoiseCancel(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(NOISE_CANCEL, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    private const val LOCK_GUARD = "lock_guard"

    /** v6.98 LOCK GUARD: while the phone is locked NOVA takes no commands, reads no normal messages, and only alerts for calls (phone silent) and Papa / Mummy. Default ON. */
    fun lockGuard(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(LOCK_GUARD, true)
    } catch (e: Exception) { true }

    fun setLockGuard(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(LOCK_GUARD, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    /** v6.38 owner request: no spoken yes/no for risky taps, deletes, bulk sends. Default ON = never ask. Hard-blocked screens (bank, payment, password, Settings, installer) stay blocked. */
    fun skipRiskyConfirm(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("skip_risky_confirm", true)
    } catch (e: Exception) { true }

    /** v6.32 owner request: an ordinary order (WhatsApp message, call) runs at once, no yes/no question. Deletes, payments and risky taps still ask. */
    fun skipOrderConfirm(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("skip_order_confirm", true)
    } catch (e: Exception) { true }

    private const val FREE_CHAIN = "free_chain"
    private const val FREE_KEYLESS = "free_keyless"

    /** Public topic-only Wikipedia lookup plus the free online AI chain (Groq / OpenRouter / legacy Pollinations). Default OFF since v6.30 (it made NOVA listen without being called). */
    fun freeChain(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(FREE_CHAIN, true)
    } catch (e: Exception) { true }

    /** v6.90: which AI answers chat ("auto" | "gemini" | a free provider id) and whether the other AIs may take over when it fails. */
    fun chatAi(ctx: Context): String = try {
        AiChoice.clean(ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("chat_ai", AiChoice.AUTO), FreeProviders.ordered().map { it.id })
    } catch (e: Exception) { AiChoice.AUTO }

    fun chatFallback(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("chat_ai_fallback", true)
    } catch (e: Exception) { true }

    /** For the chat picker: current choice + which AIs have a key (never the key itself). */
    fun chatAiJson(ctx: Context): String {
        val a = org.json.JSONArray()
        for (p in FreeProviders.ordered()) {
            val has = when (p.keyName) {
                null -> p.id == "pollinations" && freeKeyless(ctx)
                "groq" -> SecureStore.hasGroq(ctx)
                else -> SecureStore.hasSlot(ctx, p.keyName)
            }
            a.put(org.json.JSONObject().put("id", p.id).put("label", p.label.substringBefore(" (").trim()).put("keySet", has))
        }
        return org.json.JSONObject().put("choice", chatAi(ctx)).put("fallback", chatFallback(ctx))
            .put("gemini", SecureStore.hasKeys(ctx)).put("providers", a).toString()
    }

    fun setChatAi(ctx: Context, choice: String, fallback: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("chat_ai", AiChoice.clean(choice, FreeProviders.ordered().map { it.id }))
                .putBoolean("chat_ai_fallback", fallback).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    fun setFreeChain(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(FREE_CHAIN, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    private const val MEMORY_IN_CHAIN = "memory_in_chain"

    /** Default ON: at most 3 RELEVANT personal facts may be added to the online free chain's prompt (never to Gemini). */
    fun memoryInChain(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(MEMORY_IN_CHAIN, true)
    } catch (e: Exception) { true }

    fun setMemoryInChain(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(MEMORY_IN_CHAIN, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    /** Legacy no-key Pollinations attempt. Current provider docs may require a key; keep failures best-effort only. */
    fun freeKeyless(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(FREE_KEYLESS, true)
    } catch (e: Exception) { true }

    /** v6.64: typed chat replies are SILENT by default; ON = they are also spoken. Tapping the speaker icon on a message always speaks it. */
    fun chatSpeak(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("chat_speak", false)
    } catch (e: Exception) { false }

    fun setChatSpeak(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("chat_speak", on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    fun setFreeKeyless(ctx: Context, on: Boolean) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(FREE_KEYLESS, on).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    // v6.95 ROZ KA SUDHAAR (daily self-improve, with approval). Default OFF. One 1-hour window per day that starts at this hour.
    fun selfImprove(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("self_improve", false)
    } catch (e: Exception) { false }

    fun selfImproveHour(ctx: Context): Int = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("self_improve_hour", SelfImproveRules.DEFAULT_START_HOUR).coerceIn(0, 23)
    } catch (e: Exception) { SelfImproveRules.DEFAULT_START_HOUR }

    fun setSelfImprove(ctx: Context, on: Boolean, hour: Int) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("self_improve", on).putInt("self_improve_hour", hour.coerceIn(0, 23)).apply()
        } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    // v6.97: the five key-less browsers, local-first on-device AI, and the owner's home city (for "mausam kaisa hai" without a city).
    fun webBrowsers(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("web_browsers", true)
    } catch (e: Exception) { true }

    fun setWebBrowsers(ctx: Context, on: Boolean) {
        try { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("web_browsers", on).apply() } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    fun localFirst(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("local_first", true)
    } catch (e: Exception) { true }

    fun setLocalFirst(ctx: Context, on: Boolean) {
        try { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("local_first", on).apply() } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
    }

    fun homeCity(ctx: Context): String = try {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("home_city", "") ?: ""
    } catch (e: Exception) { "" }

    /** Letters, spaces and a few marks only, max 40 chars: it is sent to a public weather service. Returns the saved value. */
    fun setHomeCity(ctx: Context, city: String): String {
        val clean = city.replace(Regex("[^A-Za-z .'-]"), " ").replace(Regex("\\s+"), " ").trim().take(40)
        try { ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("home_city", clean).apply() } catch (e: Exception) { ErrLog.add("ExtCfg", e.toString()) }
        return clean
    }
}
