package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * v6.95: the list of self-improve proposals + the daily time counter. Private file self_improve.json (never uploaded, no keys, no audio).
 * A proposal is only a record until apply() is called from the owner's HAAN button. apply() remembers what the alias was before, so revert() can undo it.
 */
object SelfImproveStore {
    private const val FILE = "self_improve.json"

    class Item(val id: String, val alias: String, val value: String, val why: String, val status: String, val prev: String, val ts: Long)
    class State(val day: String, val usedMs: Long, val lastRun: Long, val runsToday: Int, val items: List<Item>)

    @Synchronized private fun load(ctx: Context): State {
        try {
            val f = File(ctx.filesDir, FILE)
            if (!f.isFile || f.length() > 60_000) return State("", 0L, 0L, 0, emptyList())
            val o = JSONObject(f.readText(Charsets.UTF_8))
            val a = o.optJSONArray("items") ?: JSONArray()
            val out = ArrayList<Item>()
            for (i in 0 until minOf(a.length(), SelfImproveRules.MAX_ITEMS)) {
                val x = a.optJSONObject(i) ?: continue
                val id = x.optString("id")
                val alias = x.optString("alias")
                val value = x.optString("value")
                if (id.isEmpty() || alias.isEmpty() || value.isEmpty()) continue
                out.add(Item(id, alias, value, x.optString("why"), x.optString("status", SelfImproveRules.PENDING), x.optString("prev"), x.optLong("ts")))
            }
            return State(o.optString("day"), o.optLong("usedMs"), o.optLong("lastRun"), o.optInt("runsToday"), out)
        } catch (e: Exception) {
            ErrLog.add("SelfImprove", e.toString())
            return State("", 0L, 0L, 0, emptyList())
        }
    }

    @Synchronized private fun save(ctx: Context, s: State) {
        try {
            val a = JSONArray()
            for (x in s.items.takeLast(SelfImproveRules.MAX_ITEMS)) {
                a.put(JSONObject().put("id", x.id).put("alias", x.alias).put("value", x.value).put("why", x.why)
                    .put("status", x.status).put("prev", x.prev).put("ts", x.ts))
            }
            val o = JSONObject().put("day", s.day).put("usedMs", s.usedMs).put("lastRun", s.lastRun).put("runsToday", s.runsToday).put("items", a)
            File(ctx.filesDir, FILE).writeText(o.toString(), Charsets.UTF_8)
        } catch (e: Exception) { ErrLog.add("SelfImprove", e.toString()) }
    }

    fun today(now: Long): String = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(now))

    /** Counters for today (a new day starts at zero). */
    @Synchronized fun usedToday(ctx: Context, now: Long): Long { val s = load(ctx); return if (s.day == today(now)) s.usedMs else 0L }
    @Synchronized fun runsToday(ctx: Context, now: Long): Int { val s = load(ctx); return if (s.day == today(now)) s.runsToday else 0 }
    @Synchronized fun lastRun(ctx: Context): Long = load(ctx).lastRun
    @Synchronized fun pendingCount(ctx: Context): Int = load(ctx).items.count { it.status == SelfImproveRules.PENDING }
    @Synchronized fun aliasesInUse(ctx: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(TeachStore.listJson(ctx))
            for (i in 0 until a.length()) a.optJSONObject(i)?.optString("alias")?.let { if (it.isNotEmpty()) out.add(it) }
        } catch (e: Exception) { ErrLog.add("SelfImprove", e.toString()) }
        // aliases that are already waiting for the owner's answer count as known too
        load(ctx).items.filter { it.status == SelfImproveRules.PENDING || it.status == SelfImproveRules.APPLIED }.forEach { out.add(it.alias) }
        return out
    }

    /** One finished run: adds the time spent, the run, and the new proposals (as pending). Returns how many were added. */
    @Synchronized fun recordRun(ctx: Context, now: Long, spentMs: Long, found: List<SelfImproveRules.Proposal>): Int {
        val s = load(ctx)
        val sameDay = s.day == today(now)
        val items = ArrayList(s.items)
        var added = 0
        var seq = 0
        for (p in found) {
            if (items.count { it.status == SelfImproveRules.PENDING } >= SelfImproveRules.MAX_PENDING) break
            items.add(Item(now.toString() + "-" + (seq++), p.alias, p.value, p.why, SelfImproveRules.PENDING, "", now))
            added++
        }
        save(ctx, State(today(now), (if (sameDay) s.usedMs else 0L) + spentMs.coerceAtLeast(0L), now, (if (sameDay) s.runsToday else 0) + 1, items))
        return added
    }

    @Synchronized fun listJson(ctx: Context): String {
        val a = JSONArray()
        for (x in load(ctx).items.asReversed()) {
            a.put(JSONObject().put("id", x.id).put("alias", x.alias).put("value", x.value).put("why", x.why).put("status", x.status))
        }
        return a.toString()
    }

    @Synchronized private fun setStatus(ctx: Context, id: String, from: String, to: String, prev: String?): Item? {
        val s = load(ctx)
        val idx = s.items.indexOfFirst { it.id == id && it.status == from }
        if (idx < 0) return null
        val old = s.items[idx]
        val items = ArrayList(s.items)
        items[idx] = Item(old.id, old.alias, old.value, old.why, to, prev ?: old.prev, old.ts)
        save(ctx, State(s.day, s.usedMs, s.lastRun, s.runsToday, items))
        return old
    }

    private fun currentValue(ctx: Context, alias: String): String {
        try {
            val a = JSONArray(TeachStore.listJson(ctx))
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("alias").equals(alias, true)) return o.optString("value")
            }
        } catch (e: Exception) { ErrLog.add("SelfImprove", e.toString()) }
        return ""
    }

    /** Owner pressed HAAN. Writes the alias with TeachStore. "ok" or "fail". */
    @Synchronized fun apply(ctx: Context, id: String): String {
        val item = load(ctx).items.firstOrNull { it.id == id && it.status == SelfImproveRules.PENDING } ?: return "fail"
        val prev = currentValue(ctx, item.alias)
        val r = TeachStore.add(ctx, item.alias, item.value)
        if (r != "ok") return "fail"
        setStatus(ctx, id, SelfImproveRules.PENDING, SelfImproveRules.APPLIED, prev)
        return "ok"
    }

    /** Owner pressed NAHI. */
    @Synchronized fun reject(ctx: Context, id: String): String =
        if (setStatus(ctx, id, SelfImproveRules.PENDING, SelfImproveRules.REJECTED, null) != null) "ok" else "fail"

    /** Owner pressed WAPAS: puts the old value back, or removes the alias when there was none. */
    @Synchronized fun revert(ctx: Context, id: String): String {
        val item = load(ctx).items.firstOrNull { it.id == id && it.status == SelfImproveRules.APPLIED } ?: return "fail"
        val ok = if (item.prev.isEmpty()) TeachStore.delete(ctx, item.alias) else TeachStore.add(ctx, item.alias, item.prev) == "ok"
        if (!ok) return "fail"
        setStatus(ctx, id, SelfImproveRules.APPLIED, SelfImproveRules.REVERTED, null)
        return "ok"
    }
}
