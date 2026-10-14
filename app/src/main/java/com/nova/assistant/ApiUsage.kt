package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * v6.18 "LIMITS" screen data: how many calls each online service was used today, the daily limit, and what is left.
 *
 * Honest by design:
 *  - "used" is counted ON THIS PHONE (every request NOVA itself sends). It cannot see calls made by other apps/devices
 *    with the same key, so it can be lower than the provider's own number.
 *  - Only Groq documents live headers (x-ratelimit-limit-requests / x-ratelimit-remaining-requests, per day).
 *    When those arrive today they win over the local count and the row says "live".
 *  - Limits that the provider does not publish (Z.ai, Pollinations, Together, Hugging Face) are shown as "no fixed limit
 *    published" instead of an invented number. Gemini's free limits depend on the model/project, so its number is a
 *    default the owner can change (setLimit) and is labelled "estimate".
 *  - Nothing here sends a request, stores a key, or changes which provider NOVA calls. It only counts.
 */
object UsageMath {
    /** Calendar day in the provider's own reset zone, e.g. "2026-10-11". */
    fun dayKey(epochMs: Long, zoneId: String): String {
        val z = try { ZoneId.of(zoneId) } catch (e: Exception) { ZoneId.of("UTC") }
        return Instant.ofEpochMilli(epochMs).atZone(z).toLocalDate().toString()
    }

    /** -1 = unknown (no limit published / set). */
    fun remaining(limit: Int, used: Int): Int = if (limit <= 0) -1 else maxOf(0, limit - maxOf(0, used))

    /** 0..100, or -1 when there is no limit to compare with. */
    fun percent(limit: Int, used: Int): Int =
        if (limit <= 0) -1 else minOf(100, (maxOf(0, used).toLong() * 100L / limit).toInt())

    fun headerInt(s: String?): Int {
        val t = s?.trim() ?: return -1
        val n = t.toLongOrNull() ?: return -1
        return if (n < 0L || n > 100_000_000L) -1 else n.toInt()
    }

    /** A request that really reached the provider (not "offline" 598, not "timeout / IO" 599, not "no request made" 0). */
    fun countsAsCall(code: Int): Boolean = code in 100..597

    /** User override range for a daily limit: 0 clears it, otherwise 1..10,000,000. */
    fun cleanLimit(n: Int): Int = if (n <= 0) 0 else minOf(n, 10_000_000)
}

object ApiUsage {
    class Row(
        val id: String,
        val label: String,
        /** brain | voice | image */
        val group: String,
        /** 0 = the provider publishes no fixed daily request limit */
        val defLimit: Int,
        val zone: String,
        /** official = from the provider's docs, estimate = changeable guess, none = not published */
        val limitKind: String,
        val note: String
    )

    val ROWS: List<Row> = listOf(
        Row("gemini", "Gemini", "brain", 250, "America/Los_Angeles", "estimate",
            "Gemini free limit model aur project par badalti hai. Yeh andaza hai: apni asli limit AI Studio > Rate limits me dekho aur yahan badal do. Roz raat 12 baje (Pacific time) reset."),
        Row("groq", "Groq (GPT-OSS)", "brain", 1000, "UTC", "official",
            "Groq free plan: 1,000 request/din, 30 request/min. Groq har jawab ke saath asli bacha hua count bhejta hai, wahi dikhaya jaata hai."),
        Row("groqqwen", "Qwen (via Groq)", "brain", 1000, "UTC", "official",
            "Groq ki hi key, alag model ki alag limit: 1,000 request/din."),
        Row("groqstt", "Groq Whisper (sunna)", "voice", 2000, "UTC", "official",
            "Awaaz sunne ke liye Groq Whisper: 2,000 request/din, 20 request/min."),
        Row("zai", "GLM (Z.ai)", "brain", 0, "UTC", "none",
            "Z.ai Flash models ki koi fixed daily request limit publish nahi hai. Sirf aaj ki ginti dikhti hai."),
        Row("zai2", "GLM #2 (Z.ai)", "brain", 0, "UTC", "none",
            "Doosri Z.ai key: pehli key ki limit poori hone par chalti hai. Sirf aaj ki ginti dikhti hai."),
        Row("zai3", "GLM #3 (Z.ai)", "brain", 0, "UTC", "none",
            "Teesri Z.ai key: doosri ke baad chalti hai. Sirf aaj ki ginti dikhti hai."),
        Row("openrouter", "OpenRouter (free)", "brain", 50, "UTC", "official",
            "Free models: 20 request/min. 50 request/din (agar 10 credit se kam kharide), 1,000/din (10+ credit). Agar credit kharide hain to limit yahan badal do."),
        Row("pollinations", "Pollinations (text)", "brain", 0, "UTC", "none",
            "Bina key wali service, koi fixed limit publish nahi. Kabhi bhi rate-limit ho sakti hai."),
        Row("together", "Together AI (image)", "image", 0, "UTC", "none",
            "Image banane ki free limit account par depend karti hai. Sirf aaj ki ginti dikhti hai."),
        Row("hf", "Hugging Face (image)", "image", 0, "UTC", "none",
            "Credits account par depend karte hain, paise lag sakte hain. Sirf aaj ki ginti dikhti hai."),
        Row("pollimg", "Pollinations (image)", "image", 0, "UTC", "none",
            "Bina key wali image service, koi fixed limit publish nahi.")
    )

    private const val PREFS = "nova_usage"

    @Volatile private var app: Context? = null

    /** Called once from MainActivity and NovaService. Without it every call below is a silent no-op. */
    fun init(ctx: Context) {
        app = ctx.applicationContext
    }

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rowOf(id: String): Row? = ROWS.firstOrNull { it.id == id }

    /**
     * One finished request. [code] is the HTTP status (598/599/0 are ignored). The three header values are only
     * passed for services that document them (Groq); anything else passes null.
     */
    @Synchronized
    fun hit(id: String, code: Int, limitHdr: String? = null, remainingHdr: String? = null, resetHdr: String? = null) {
        try {
            val p = prefs() ?: return
            val row = rowOf(id) ?: return
            if (!UsageMath.countsAsCall(code)) return
            val now = System.currentTimeMillis()
            val day = UsageMath.dayKey(now, row.zone)
            var used = p.getInt("u_$id", 0)
            var failed = p.getInt("f_$id", 0)
            var quota = p.getInt("q_$id", 0)
            var liveLimit = p.getInt("ll_$id", -1)
            var liveRem = p.getInt("lr_$id", -1)
            var liveReset = p.getString("lz_$id", "") ?: ""
            if ((p.getString("d_$id", "") ?: "") != day) {
                used = 0; failed = 0; quota = 0; liveLimit = -1; liveRem = -1; liveReset = ""
            }
            used++
            if (code !in 200..299) failed++
            if (code == 429) quota++
            val l = UsageMath.headerInt(limitHdr)
            val r = UsageMath.headerInt(remainingHdr)
            if (l > 0 && r >= 0) {
                liveLimit = l; liveRem = r
                liveReset = (resetHdr ?: "").trim().take(24)
            }
            p.edit()
                .putString("d_$id", day)
                .putInt("u_$id", used).putInt("f_$id", failed).putInt("q_$id", quota)
                .putInt("ll_$id", liveLimit).putInt("lr_$id", liveRem).putString("lz_$id", liveReset)
                .putLong("t_$id", now).putInt("c_$id", code)
                .apply()
        } catch (e: Exception) {
            // counting must never break a real request
        }
    }

    /** Owner-chosen daily limit for one service (0 clears it and goes back to the default). */
    @Synchronized
    fun setLimit(id: String, n: Int): Boolean {
        val p = prefs() ?: return false
        if (rowOf(id) == null) return false
        val v = UsageMath.cleanLimit(n)
        val e = p.edit()
        if (v == 0) e.remove("lo_$id") else e.putInt("lo_$id", v)
        e.apply()
        return true
    }

    /** Zero today's counters (the provider's own count is not touched). */
    @Synchronized
    fun resetToday() {
        val p = prefs() ?: return
        val e = p.edit()
        for (r in ROWS) {
            e.remove("u_${r.id}").remove("f_${r.id}").remove("q_${r.id}")
                .remove("ll_${r.id}").remove("lr_${r.id}").remove("lz_${r.id}")
        }
        e.apply()
    }

    private fun keySet(ctx: Context, id: String): Boolean = try {
        when (id) {
            "gemini" -> SecureStore.hasKeys(ctx)
            "groq", "groqqwen", "groqstt" -> SecureStore.hasGroq(ctx)
            "zai" -> SecureStore.hasSlot(ctx, "zai")
            "zai2" -> SecureStore.hasSlot(ctx, "zai2")
            "zai3" -> SecureStore.hasSlot(ctx, "zai3")
            "openrouter" -> SecureStore.hasSlot(ctx, "openrouter")
            "together" -> SecureStore.hasSlot(ctx, "together")
            "hf" -> SecureStore.hasSlot(ctx, "hf")
            "pollinations", "pollimg" -> ExtCfg.freeKeyless(ctx)
            else -> false
        }
    } catch (e: Exception) { false }

    /**
     * JSON for the LIMITS screen. Never contains a key. "remaining" / "limit" = -1 means unknown.
     * source: "live" (provider headers today) | "local" (counted here against a limit) | "count" (no limit known).
     */
    @Synchronized
    fun snapshotJson(ctx: Context): String {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val rows = JSONArray()
        var total = 0
        for (r in ROWS) {
            val id = r.id
            val sameDay = (p.getString("d_$id", "") ?: "") == UsageMath.dayKey(now, r.zone)
            val used = if (sameDay) p.getInt("u_$id", 0) else 0
            val failed = if (sameDay) p.getInt("f_$id", 0) else 0
            val quota = if (sameDay) p.getInt("q_$id", 0) else 0
            val liveLimit = if (sameDay) p.getInt("ll_$id", -1) else -1
            val liveRem = if (sameDay) p.getInt("lr_$id", -1) else -1
            val override = p.getInt("lo_$id", 0)
            val live = liveLimit > 0 && liveRem >= 0 && override == 0
            val limit = when {
                override > 0 -> override
                live -> liveLimit
                else -> r.defLimit
            }
            val remaining = if (live) liveRem else UsageMath.remaining(limit, used)
            val shownUsed = if (live) maxOf(0, liveLimit - liveRem) else used
            val pct = if (live) UsageMath.percent(liveLimit, shownUsed) else UsageMath.percent(limit, used)
            total += used
            rows.put(
                JSONObject()
                    .put("id", id).put("label", r.label).put("group", r.group).put("note", r.note)
                    .put("limitKind", if (override > 0) "custom" else r.limitKind)
                    .put("keySet", keySet(ctx, id))
                    .put("used", shownUsed).put("sent", used).put("failed", failed).put("quotaHits", quota)
                    .put("limit", if (limit > 0) limit else -1)
                    .put("remaining", remaining).put("percent", pct)
                    .put("source", if (live) "live" else if (limit > 0) "local" else "count")
                    .put("liveReset", if (live) (p.getString("lz_$id", "") ?: "") else "")
                    .put("lastTs", p.getLong("t_$id", 0L)).put("lastCode", p.getInt("c_$id", 0))
                    .put("defLimit", r.defLimit)
            )
        }
        return JSONObject().put("day", UsageMath.dayKey(now, "UTC")).put("totalToday", total).put("rows", rows).toString()
    }
}
