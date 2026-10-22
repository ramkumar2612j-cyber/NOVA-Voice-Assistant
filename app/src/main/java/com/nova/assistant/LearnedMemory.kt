package com.nova.assistant

import android.content.Context

/**
 * v7.110 PART 7: ONE place to SHOW and CLEAR everything NOVA learned from jobs: route notes (PlaybookStore), site / blocker
 * counters (SiteLearningStore) and capability-gap lessons (CapabilityLearningStore). All three hold only coarse labels,
 * counters and times (no task text, URL path, screen text, password or message). Clear removes ALL THREE, nothing else
 * (chat history, keys, settings, offline models stay).
 */
object LearnedMemory {
    /** Short Hinglish text for the card. Never throws. */
    fun summary(ctx: Context): String = try {
        val app = ctx.applicationContext
        val parts = ArrayList<String>()
        val pb = try { PlaybookStore(app).summary() } catch (e: Exception) { "" }
        val st = try { SiteLearningStore(app).summary() } catch (e: Exception) { "" }
        val cp = try { CapabilityLearningStore(app).summary() } catch (e: Exception) { "" }
        if (pb.isNotBlank()) parts.add("ROUTE: " + pb)
        if (st.isNotBlank()) parts.add("SITES: " + st)
        if (cp.isNotBlank()) parts.add("KAMIYAN: " + cp)
        parts.joinToString("\n\n").take(1800)
    } catch (e: Throwable) { "" }

    /** Clears all three stores. Each one is cleared even if another fails. Returns how many were cleared. */
    fun clearAll(ctx: Context): Int {
        val app = ctx.applicationContext
        var n = 0
        try { PlaybookStore(app).clear(); n++ } catch (e: Throwable) { ErrLog.add("LearnedMemory", "playbook " + e) }
        try { SiteLearningStore(app).clear(); n++ } catch (e: Throwable) { ErrLog.add("LearnedMemory", "site " + e) }
        try { CapabilityLearningStore(app).clear(); n++ } catch (e: Throwable) { ErrLog.add("LearnedMemory", "capability " + e) }
        return n
    }
}
