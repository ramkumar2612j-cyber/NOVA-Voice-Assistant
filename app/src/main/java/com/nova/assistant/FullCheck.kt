package com.nova.assistant

import android.content.Context
import org.json.JSONObject

/**
 * v7.181 FULL CHECK: ONE report that merges the older ones (SelfTest/AUTO TEST report, AUTO VERIFY, FeatureStatus, recent errors).
 * Pure text rules live in FullCheckRules (no Android, unit-tested). Nothing here runs a new test by itself: it merges what the
 * automatic checks already produced, so it is instant, read-only and never opens a popup, plays a sound or touches the torch.
 */
object FullCheckRules {
    class Item(val section: String, val state: String, val text: String)   // state: FAIL | WARN | OFF

    private fun lineState(l: String): String? {
        val t = l.trim().removePrefix("-").trim()
        return when {
            t.startsWith("FAIL") -> "FAIL"
            t.startsWith("WARN") -> "WARN"
            else -> null
        }
    }

    /** FAIL / WARN lines of a SelfTest or AUTO VERIFY report. */
    fun linesOf(section: String, report: String): List<Item> =
        report.lines().mapNotNull { l -> lineState(l)?.let { Item(section, it, l.trim().removePrefix("-").trim().take(300)) } }

    /** FeatureStatus rows: "off" = not working, "warn" = works with a problem. */
    fun statusItems(rows: List<Triple<String, String, String>>): List<Item> =   // name, state, why
        rows.mapNotNull { (n, s, w) ->
            when (s) {
                "off" -> Item("FEATURE STATUS", "OFF", (n + ": " + w).take(300))
                "warn" -> Item("FEATURE STATUS", "WARN", (n + ": " + w).take(300))
                else -> null
            }
        }

    fun counts(items: List<Item>): Triple<Int, Int, Int> =
        Triple(items.count { it.state == "FAIL" }, items.count { it.state == "OFF" }, items.count { it.state == "WARN" })

    /** The merged, copyable report. Section texts are included whole so nothing is lost; NOT WORKING comes first. */
    fun build(version: String, stamp: String, items: List<Item>, selfReport: String, verifyReport: String, errors: String): String {
        val (f, o, w) = counts(items)
        val sb = StringBuilder()
        sb.append("JARVIS FULL CHECK  v").append(version).append("  ").append(stamp).append('\n')
        sb.append("SUMMARY not_working=").append(f + o).append(" (fail=").append(f).append(" off=").append(o).append(") warn=").append(w).append('\n')
        sb.append("Evidence level: automatic read-only checks. Hardware checks (torch, mic, screen) run only from Settings > POORI JAANCH ABHI.\n\n")
        sb.append("== NOT WORKING ==\n")
        val bad = items.filter { it.state != "WARN" }
        if (bad.isEmpty()) sb.append("none found by the automatic checks\n") else bad.forEach { sb.append("- [").append(it.section).append("] ").append(it.state).append(' ').append(it.text).append('\n') }
        sb.append("\n== WARN (works with a problem / switched off) ==\n")
        val warns = items.filter { it.state == "WARN" }
        if (warns.isEmpty()) sb.append("none\n") else warns.forEach { sb.append("- [").append(it.section).append("] ").append(it.text).append('\n') }
        sb.append("\n== SELF TEST (full) ==\n").append(if (selfReport.isBlank()) "not run yet\n" else selfReport.trim() + "\n")
        sb.append("\n== AUTO VERIFY (wiring) ==\n").append(if (verifyReport.isBlank()) "not run yet\n" else verifyReport.trim() + "\n")
        sb.append("\n== ").append(errors.trim()).append('\n')
        return sb.toString()
    }

    /** Short spoken / chat answer: what is not working, at most [max] items. Honest when no check has run yet. */
    fun spoken(items: List<Item>, ran: Boolean, max: Int = 6): String {
        if (!ran) return "Abhi koi jaanch chali nahi hai. App khulne ke kuch second baad apne aap chalti hai, thodi der baad dobara pucho."
        val bad = items.filter { it.state != "WARN" }
        val warn = items.count { it.state == "WARN" }
        if (bad.isEmpty()) return "Jaanch me koi cheez band ya FAIL nahi mili." + (if (warn > 0) " $warn WARN hain (ye features abhi band hain ya limit par hain)." else "") + " Poora report Settings > TEST REPORT > POORA REPORT me copy ho sakta hai."
        val sb = StringBuilder("Ye cheezein nahi chal rahi (").append(bad.size).append("):\n")
        bad.take(max).forEach { sb.append("- ").append(it.text.take(140)).append('\n') }
        if (bad.size > max) sb.append("... aur ").append(bad.size - max).append(" aur. Poora report Settings > TEST REPORT > POORA REPORT me hai.")
        return sb.toString().trim()
    }

    private val ASK = listOf("kya nahi chal", "kya nhi chal", "full check", "poora check", "pura check", "poori jaanch", "pura report", "poora report", "full report", "sab kuch check", "app ka status", "jarvis ka status", "jarvis ka status")

    fun isAsk(norm: String): Boolean = norm.isNotBlank() && norm.length <= 60 && ASK.any { norm.contains(it) }
}

object FullCheck {
    private fun status(ctx: Context): List<Triple<String, String, String>> = try {
        val arr = JSONObject(FeatureStatus.json(ctx)).optJSONArray("items")
        val out = ArrayList<Triple<String, String, String>>()
        if (arr != null) for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); out.add(Triple(o.optString("name"), o.optString("state"), o.optString("why"))) }
        out
    } catch (e: Throwable) { emptyList() }

    private fun items(ctx: Context): List<FullCheckRules.Item> =
        FullCheckRules.linesOf("SELF TEST", AutoTest.lastReport) + FullCheckRules.linesOf("AUTO VERIFY", FeatureVerify.lastReport) + FullCheckRules.statusItems(status(ctx))

    private fun ran(): Boolean = AutoTest.lastReport.isNotBlank() || FeatureVerify.lastReport.isNotBlank()

    /** The merged report text (capped). */
    fun report(ctx: Context): String = try {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())
        FullCheckRules.build(UpdateManager.appVersion(ctx).toString(), stamp, items(ctx), AutoTest.lastReport, FeatureVerify.lastReport, ErrLog.dump()).take(60_000)
    } catch (e: Throwable) { ErrLog.add("FullCheck", e.toString()); "FULL CHECK: report nahi ban saka (" + e.javaClass.simpleName + ")" }

    /** Spoken / chat answer for "kya nahi chal raha". */
    fun answer(ctx: Context): String = try { FullCheckRules.spoken(items(ctx), ran()) } catch (e: Throwable) { "Jaanch padhne me gadbad hui." }
}
