package com.nova.assistant

import org.json.JSONObject

/**
 * v7.203 AUTO FIX LOOP policy (add-only, pure Kotlin, unit-tested).
 *
 * Owner decision (2026-11-16): build RED ho to JARVIS dekhe kyun red hai, fix kare, phir build karke install kare jab tak sahi na ho, aur roz khud sudhre.
 * SAFETY CAPS (ye bina owner ke nahi badlenge):
 *  - ek red streak me max 3 fix koshishein; 3 ke baad ruk kar owner ko bata deta hai
 *  - din me max 3 auto-fix (existing self-patch ki 5/din cap ke andar)
 *  - ek time par ek hi patch; purana patch RED par apne aap revert (existing v7.197)
 *  - protected files (SecureStore, UpdateManager, GitHub*, PayKit...) kabhi nahi
 *  - HARD DENY (paisa / bank / password / OTP / PIN / CVV) kabhi nahi
 * Fix ka sach: AI ka fix build pass kar sakta hai aur phir bhi phone par galat chal sakta hai. Install ke baad bhi owner ko bataya jaayega.
 */
internal object AutoFixRules {
    const val MAX_STREAK = 3
    const val MAX_PER_DAY = 3

    class Decision(val go: Boolean, val why: String)

    fun decide(phase: String, streak: Int, fixesToday: Int, patchBusy: Boolean, enabled: Boolean): Decision {
        if (!enabled) return Decision(false, "auto fix band hai")
        if (phase != SelfBuildRules.RED) return Decision(false, "build red nahi hai")
        if (patchBusy) return Decision(false, "ek patch pehle se chal raha hai")
        if (streak >= MAX_STREAK) return Decision(false, "$MAX_STREAK koshishein ho chuki, ab owner ka faisla chahiye")
        if (fixesToday >= MAX_PER_DAY) return Decision(false, "aaj ke $MAX_PER_DAY fix ho chuke")
        return Decision(true, "red build, fix koshish ${streak + 1} of $MAX_STREAK")
    }

    /** Names of the failed steps from GitHub's jobs JSON, e.g. "unit tests: Run tests". Empty if unknown. */
    fun failedSteps(jobsJson: String): List<String> {
        return try {
            val out = ArrayList<String>()
            val jobs = JSONObject(jobsJson).optJSONArray("jobs")
            if (jobs == null) emptyList() else {
                for (i in 0 until jobs.length()) {
                    val j = jobs.optJSONObject(i) ?: continue
                    if (j.optString("conclusion") != "failure") continue
                    val steps = j.optJSONArray("steps")
                    var found = false
                    if (steps != null) {
                        for (k in 0 until steps.length()) {
                            val st = steps.optJSONObject(k) ?: continue
                            if (st.optString("conclusion") == "failure") { out.add(j.optString("name") + ": " + st.optString("name")); found = true; break }
                        }
                    }
                    if (!found) out.add(j.optString("name"))
                }
                out
            }
        } catch (e: Throwable) { emptyList() }
    }

    /** The wish given to the existing self-patch. Only names of failed steps, never secrets. */
    fun wishFor(steps: List<String>): String {
        val s = steps.take(3).joinToString("; ").take(200)
        return "build RED hai. Galti theek karo: " + (if (s.isBlank()) "Actions log dekh kar" else s)
    }
}
