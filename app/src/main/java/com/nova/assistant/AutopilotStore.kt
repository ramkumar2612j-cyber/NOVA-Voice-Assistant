package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * v7.158 AUTOPILOT STORAGE (Android side, private prefs only).
 *
 *  - Config: the owner's AUTOPILOT tab form (group name, website, repo, site type, targets, sent-mode, caps,
 *    best-time slots, concierge). One JSON string, migrated add-only: a new version keeps every old key
 *    (nothing the owner typed is lost on update).
 *  - Job queue: every post job (target: site / ig_feed / ig_reel / ig_story / yt) with PENDING / DONE / FAILED /
 *    SKIPPED / REVIEW states, the idempotency keys that were already published, and the daily counters for the caps.
 *  - Orders: the concierge's in-flight and confirmed orders.
 * Tokens NEVER live here - they stay in SecureStore slots (github / ap_ig / ap_yt_cid / ap_yt_csec / ap_yt_refresh).
 * Nothing in this file touches the network or the main thread's disk (callers run on the worker).
 */
object AutopilotStore {
    private const val PREFS = "nova_autopilot"
    private const val KEY_CFG = "cfg_v1"
    private const val KEY_JOBS = "jobs_v1"
    private const val KEY_IDEM = "idem_v1"
    private const val KEY_ORDERS = "orders_v1"
    private const val KEY_SEEN = "seen_hash_v1"
    private const val KEY_CAPDAY = "cap_day_v1"
    private const val MAX_JOBS = 300
    private const val MAX_ORDERS = 100

    // ---------------------------------------------------------------- config

    class Cfg(
        var on: Boolean = false,
        var group: String = "",
        var site: String = "",
        var repo: String = "",                 // "owner/repo" (github Pages gallery + feed)
        var siteType: String = "github",       // github | vercel | shopify | woocommerce | wix
        var brand: String = "",
        var niche: String = "",
        var igId: String = "",                 // Instagram user id (auto-detected / entered)
        var sentMode: String = "ask",          // off | ask | auto  (sent-by-me files)
        var targetSite: Boolean = true,
        var targetIgFeed: Boolean = true,
        var targetIgReel: Boolean = true,
        var targetIgStory: Boolean = true,
        var targetYt: Boolean = true,
        var bestTime: Boolean = true,          // post at IST slots; false = as soon as ready
        var concierge: Boolean = true,         // NOVA talks to business DMs
        var shopDomain: String = "",           // v7.158 task E: mystore.myshopify.com
        var wooUrl: String = "",               // v7.158 task E: https://mystore.in
        var caps: AutopilotRules.Caps = AutopilotRules.Caps(),
        var setupDone: Boolean = false,        // first-run wizard finished
        var igFeedToday: Int = 0, var igStoryToday: Int = 0, var ytToday: Int = 0, var capDay: String = "",
        var lastPostTs: Long = 0
    )

    fun cfg(ctx: Context): Cfg {
        val c = Cfg()
        try {
            val raw = prefs(ctx).getString(KEY_CFG, null) ?: return c
            val o = JSONObject(raw)
            c.on = o.optBoolean("on", c.on)
            c.group = o.optString("group", "").take(80)
            c.site = o.optString("site", "").take(200)
            c.repo = o.optString("repo", "").take(120)
            c.siteType = o.optString("siteType", c.siteType).take(20)
            c.brand = o.optString("brand", "").take(60)
            c.niche = o.optString("niche", "").take(60)
            c.igId = o.optString("igId", "").take(40)
            c.sentMode = o.optString("sentMode", c.sentMode).take(10)
            c.targetSite = o.optBoolean("targetSite", c.targetSite)
            c.targetIgFeed = o.optBoolean("targetIgFeed", c.targetIgFeed)
            c.targetIgReel = o.optBoolean("targetIgReel", c.targetIgReel)
            c.targetIgStory = o.optBoolean("targetIgStory", c.targetIgStory)
            c.targetYt = o.optBoolean("targetYt", c.targetYt)
            c.bestTime = o.optBoolean("bestTime", c.bestTime)
            c.concierge = o.optBoolean("concierge", c.concierge)
            c.shopDomain = o.optString("shopDomain", "").take(120)
            c.wooUrl = o.optString("wooUrl", "").take(160)
            c.setupDone = o.optBoolean("setupDone", c.setupDone)
            c.caps.igFeed = o.optInt("capIgFeed", c.caps.igFeed)
            c.caps.igStory = o.optInt("capIgStory", c.caps.igStory)
            c.caps.yt = o.optInt("capYt", c.caps.yt)
            c.caps.spacingMin = o.optInt("capSpacing", c.caps.spacingMin)
            c.igFeedToday = o.optInt("igFeedToday", 0)
            c.igStoryToday = o.optInt("igStoryToday", 0)
            c.ytToday = o.optInt("ytToday", 0)
            c.capDay = o.optString("capDay", "")
            c.lastPostTs = o.optLong("lastPostTs", 0)
        } catch (e: Exception) { ErrLog.add("AutopilotStore", "cfg: $e") }
        return c
    }

    fun saveCfg(ctx: Context, c: Cfg): Boolean = try {
        val o = JSONObject()
            .put("on", c.on).put("group", c.group.take(80)).put("site", c.site.take(200)).put("repo", c.repo.take(120))
            .put("siteType", c.siteType.take(20)).put("brand", c.brand.take(60)).put("niche", c.niche.take(60))
            .put("igId", c.igId.take(40)).put("sentMode", c.sentMode.take(10))
            .put("targetSite", c.targetSite).put("targetIgFeed", c.targetIgFeed).put("targetIgReel", c.targetIgReel)
            .put("targetIgStory", c.targetIgStory).put("targetYt", c.targetYt)
            .put("bestTime", c.bestTime).put("concierge", c.concierge).put("setupDone", c.setupDone)
            .put("shopDomain", c.shopDomain.take(120)).put("wooUrl", c.wooUrl.take(160))
            .put("capIgFeed", c.caps.igFeed).put("capIgStory", c.caps.igStory).put("capYt", c.caps.yt).put("capSpacing", c.caps.spacingMin)
            .put("igFeedToday", c.igFeedToday).put("igStoryToday", c.igStoryToday).put("ytToday", c.ytToday)
            .put("capDay", c.capDay).put("lastPostTs", c.lastPostTs)
        prefs(ctx).edit().putString(KEY_CFG, o.toString()).commit()
    } catch (e: Exception) { ErrLog.add("AutopilotStore", "saveCfg: $e"); false }

    /** The AUTOPILOT tab saves the form as one JSON blob (bridge caps the length first). */
    fun saveCfgJson(ctx: Context, json: String): Boolean = try {
        val patch = JSONObject(json)
        val c = cfg(ctx)
        c.on = patch.optBoolean("on", c.on)
        c.group = patch.optString("group", c.group).take(80)
        c.site = patch.optString("site", c.site).take(200)
        c.repo = patch.optString("repo", c.repo).take(120)
        c.siteType = patch.optString("siteType", c.siteType).take(20)
        c.brand = patch.optString("brand", c.brand).take(60)
        c.niche = patch.optString("niche", c.niche).take(60)
        c.igId = patch.optString("igId", c.igId).take(40)
        val sm = patch.optString("sentMode", c.sentMode).take(10)
        if (sm in setOf("off", "ask", "auto")) c.sentMode = sm
        c.targetSite = patch.optBoolean("targetSite", c.targetSite)
        c.targetIgFeed = patch.optBoolean("targetIgFeed", c.targetIgFeed)
        c.targetIgReel = patch.optBoolean("targetIgReel", c.targetIgReel)
        c.targetIgStory = patch.optBoolean("targetIgStory", c.targetIgStory)
        c.targetYt = patch.optBoolean("targetYt", c.targetYt)
        c.bestTime = patch.optBoolean("bestTime", c.bestTime)
        c.concierge = patch.optBoolean("concierge", c.concierge)
        c.shopDomain = patch.optString("shopDomain", c.shopDomain).take(120)
        c.wooUrl = patch.optString("wooUrl", c.wooUrl).take(160)
        c.caps.igFeed = patch.optInt("capIgFeed", c.caps.igFeed).coerceIn(1, 6)
        c.caps.igStory = patch.optInt("capIgStory", c.caps.igStory).coerceIn(1, 15)
        c.caps.yt = patch.optInt("capYt", c.caps.yt).coerceIn(1, 5)
        c.caps.spacingMin = patch.optInt("capSpacing", c.caps.spacingMin).coerceIn(15, 240)
        saveCfg(ctx, c)
    } catch (e: Exception) { ErrLog.add("AutopilotStore", "saveCfgJson: $e"); false }

    // ---------------------------------------------------------------- job queue

    /** One post job. mediaPath = the prepared file in the app cache; publicUrl filled after the site upload. */
    class Job(
        val id: String, val batchId: String, val target: String, val kind: String,
        val mediaPath: String, val mediaName: String, val createdAt: Long,
        var state: String = "PENDING", var runAt: Long = 0, var message: String = "",
        var publicUrl: String = "", var thumbUrl: String = "", var packJson: String = "",
        var platformId: String = "", var insightsAt: Long = 0, var reach: Long = -1L
    )

    fun addJob(ctx: Context, target: String, kind: String, mediaPath: String, mediaName: String, batchId: String, runAt: Long): Job? {
        val valid = setOf("site", "ig_feed", "ig_reel", "ig_story", "yt")
        if (target !in valid) return null
        val j = Job(UUID.randomUUID().toString().take(18), batchId.take(24), target, kind.take(10), mediaPath.take(300), mediaName.take(120), System.currentTimeMillis(), runAt = runAt)
        val all = jobs(ctx).toMutableList()
        all.add(j)
        saveJobs(ctx, all) ?: return null
        return j
    }

    fun jobs(ctx: Context): List<Job> {
        val out = ArrayList<Job>()
        try {
            val raw = prefs(ctx).getString(KEY_JOBS, null) ?: return out
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Job(
                    o.optString("id"), o.optString("batchId"), o.optString("target"), o.optString("kind"),
                    o.optString("mediaPath"), o.optString("mediaName"), o.optLong("createdAt"),
                    o.optString("state", "PENDING"), o.optLong("runAt"), o.optString("message", "").take(200),
                    o.optString("publicUrl", "").take(300), o.optString("thumbUrl", "").take(300), o.optString("pack", "").take(4000),
                    o.optString("platformId", "").take(60), o.optLong("insightsAt", 0), o.optLong("reach", -1L)
                ))
            }
        } catch (e: Exception) { ErrLog.add("AutopilotStore", "jobs: $e") }
        return out
    }

    private fun saveJobs(ctx: Context, all: List<Job>): Boolean? {
        val keep = all.takeLast(MAX_JOBS)
        return try {
            val a = JSONArray()
            for (j in keep) a.put(JSONObject()
                .put("id", j.id).put("batchId", j.batchId).put("target", j.target).put("kind", j.kind)
                .put("mediaPath", j.mediaPath).put("mediaName", j.mediaName).put("createdAt", j.createdAt)
                .put("state", j.state).put("runAt", j.runAt).put("message", j.message)
                .put("publicUrl", j.publicUrl).put("thumbUrl", j.thumbUrl).put("pack", j.packJson)
                .put("platformId", j.platformId).put("insightsAt", j.insightsAt).put("reach", j.reach))
            prefs(ctx).edit().putString(KEY_JOBS, a.toString()).commit()
        } catch (e: Exception) { ErrLog.add("AutopilotStore", "saveJobs: $e"); null }
    }

    fun updateJob(ctx: Context, id: String, mutate: (Job) -> Unit): Boolean {
        val all = jobs(ctx).toMutableList()
        val j = all.firstOrNull { it.id == id } ?: return false
        mutate(j)
        saveJobs(ctx, all) ?: return false
        return true
    }

    /** The queue action buttons: retry / skip / approve (REVIEW -> PENDING). */
    fun jobAction(ctx: Context, id: String, action: String): Boolean {
        val a = when (action) { "retry" -> "retry"; "skip" -> "skip"; "approve" -> "approve"; else -> return false }
        return updateJob(ctx, id) { j ->
            when (a) {
                "retry" -> { j.state = "PENDING"; j.runAt = 0; j.message = "" }
                "skip" -> { j.state = "SKIPPED"; j.message = "owner ne skip kiya" }
                "approve" -> { if (j.state == "REVIEW") { j.state = "PENDING"; j.runAt = 0; j.message = "owner ne haan ki" } }
            }
        }
    }

    /** Idempotency: true when this (job, target) was already published - a retry then never double-posts. */
    fun idemClaim(ctx: Context, key: String): Boolean {
        val seen = HashSet<String>(readSet(prefs(ctx).getString(KEY_IDEM, null)))
        if (key in seen) return false
        seen.add(key.take(90))
        while (seen.size > 500) seen.remove(seen.first())
        val ok = prefs(ctx).edit().putString(KEY_IDEM, seen.joinToString("\n")).commit()
        return ok
    }

    fun idemHas(ctx: Context, key: String): Boolean = key in HashSet<String>(readSet(prefs(ctx).getString(KEY_IDEM, null)))

    // ---------------------------------------------------------------- de-dupe (file hashes)

    /** true when this media hash was never seen before (and remembers it). A retry of the same file does not repost. */
    fun hashNew(ctx: Context, hash: String): Boolean {
        val h = hash.take(64)
        if (h.length < 16) return false
        val seen = HashSet<String>(readSet(prefs(ctx).getString(KEY_SEEN, null)))
        if (h in seen) return false
        seen.add(h)
        while (seen.size > 800) seen.remove(seen.first())
        prefs(ctx).edit().putString(KEY_SEEN, seen.joinToString("\n")).commit()
        return true
    }

    // ---------------------------------------------------------------- daily counters (caps)

    /** Bumps the counter for one target and resets everything when the IST day changed. */
    fun countPost(ctx: Context, target: String): Int {
        val c = cfg(ctx)
        val day = java.time.LocalDate.now(java.time.ZoneId.of(AutopilotRules.IST_ZONE)).toString()
        if (c.capDay != day) { c.capDay = day; c.igFeedToday = 0; c.igStoryToday = 0; c.ytToday = 0 }
        when (target) {
            "ig_feed", "ig_reel" -> c.igFeedToday++
            "ig_story" -> c.igStoryToday++
            "yt" -> c.ytToday++
        }
        c.lastPostTs = System.currentTimeMillis()
        saveCfg(ctx, c)
        return when (target) { "ig_feed", "ig_reel" -> c.igFeedToday; "ig_story" -> c.igStoryToday; "yt" -> c.ytToday; else -> 0 }
    }

    fun postsToday(ctx: Context, target: String): Int {
        val c = cfg(ctx)
        val day = java.time.LocalDate.now(java.time.ZoneId.of(AutopilotRules.IST_ZONE)).toString()
        if (c.capDay != day) return 0
        return when (target) { "ig_feed", "ig_reel" -> c.igFeedToday; "ig_story" -> c.igStoryToday; "yt" -> c.ytToday; else -> 0 }
    }

    fun lastPostTs(ctx: Context): Long = cfg(ctx).lastPostTs

    // ---------------------------------------------------------------- orders

    fun orders(ctx: Context): MutableList<AutopilotRules.Order> {
        val out = ArrayList<AutopilotRules.Order>()
        try {
            val raw = prefs(ctx).getString(KEY_ORDERS, null) ?: return out
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ord = AutopilotRules.Order(
                    o.optString("id").take(24), o.optString("chat").take(80), o.optString("app").take(20),
                    o.optString("item", "").take(80), o.optString("size", "").take(40), o.optInt("qty", 0),
                    o.optString("name", "").take(60), o.optString("phone", "").take(16), o.optString("address", "").take(300),
                    AutopilotRules.Step.valueOf(o.optString("step", "ITEM")), o.optLong("updated"),
                    o.optString("statusNote", "").take(200)
                )
                out.add(ord)
            }
        } catch (e: Exception) { ErrLog.add("AutopilotStore", "orders: $e") }
        return out
    }

    fun saveOrder(ctx: Context, o: AutopilotRules.Order) {
        val all = orders(ctx).toMutableList()
        val i = all.indexOfFirst { it.id == o.id }
        if (i >= 0) all[i] = o else all.add(o)
        val keep = all.filter { it.step != AutopilotRules.Step.DONE }.take(MAX_ORDERS / 2) +
            all.filter { it.step == AutopilotRules.Step.DONE }.takeLast(MAX_ORDERS / 2)
        try {
            val a = JSONArray()
            for (x in keep) a.put(JSONObject()
                .put("id", x.id).put("chat", x.chat).put("app", x.app).put("item", x.item).put("size", x.size)
                .put("qty", x.qty).put("name", x.name).put("phone", x.phone).put("address", x.address)
                .put("step", x.step.name).put("updated", x.updated).put("statusNote", x.statusNote))
            prefs(ctx).edit().putString(KEY_ORDERS, a.toString()).commit()
        } catch (e: Exception) { ErrLog.add("AutopilotStore", "saveOrder: $e") }
    }

    fun orderFor(ctx: Context, chat: String): AutopilotRules.Order? =
        orders(ctx).firstOrNull { it.chat.equals(chat, true) && it.step != AutopilotRules.Step.DONE }

    // ---------------------------------------------------------------- insights (v7.158 task D4)

    /**
     * Small learning store: per-slot average reach + sample count (self-tuning slots) and the best hooks /
     * hashtag lines of the last posts. Everything comes from the platforms' own 24 h numbers - nothing invented.
     */
    fun slotStats(ctx: Context): Pair<List<Long>, List<Int>> {
        val p = prefs(ctx)
        val avg = ArrayList<Long>()
        val n = ArrayList<Int>()
        for (i in AutopilotRules.SLOT_TIMES_IST.indices) {
            avg.add(p.getLong("slot_avg_$i", 0L))
            n.add(p.getInt("slot_n_$i", 0))
        }
        return Pair(avg, n)
    }

    /** Running average update for one slot (weighted so one lucky post cannot hijack the order). */
    fun recordSlotReach(ctx: Context, slotIndex: Int, reach: Long) {
        if (slotIndex < 0 || slotIndex >= AutopilotRules.SLOT_TIMES_IST.size || reach < 0) return
        val p = prefs(ctx)
        val n = p.getInt("slot_n_$slotIndex", 0)
        val avg = p.getLong("slot_avg_$slotIndex", 0L)
        val next = if (n == 0) reach else ((avg * n) + reach) / (n + 1)
        p.edit().putLong("slot_avg_$slotIndex", next).putInt("slot_n_$slotIndex", (n + 1).coerceAtMost(50)).commit()
    }

    fun avgReach(ctx: Context): Long {
        val (avg, n) = slotStats(ctx)
        val known = avg.indices.filter { n[it] > 0 }
        if (known.isEmpty()) return 0L
        return (known.map { avg[it] * n[it] }.sum() / known.map { n[it] }.sum().coerceAtLeast(1))
    }

    /** The last DONE pack's hook + hashtags (the never-repeat rule reads exactly these). */
    fun lastPackFootprint(ctx: Context): Pair<String, List<String>> {
        val p = prefs(ctx)
        val hook = p.getString("last_hook", "") ?: ""
        val tags = (p.getString("last_tags", "") ?: "").split("\n").filter { it.isNotBlank() }
        return Pair(hook, tags)
    }

    fun recordPackFootprint(ctx: Context, pack: AutopilotRules.Pack) {
        prefs(ctx).edit()
            .putString("last_hook", AutopilotRules.hookLine(pack).take(120))
            .putString("last_tags", pack.hashtags.joinToString("\n").take(400))
            .commit()
    }

    // ---------------------------------------------------------------- helpers

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readSet(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.take(800)
    }
}
