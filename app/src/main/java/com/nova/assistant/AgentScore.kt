package com.nova.assistant

/**
 * v7.141 STEP 1 OF 10: the agent's score card. The task ledger already keeps every agent job (state, steps); this only COUNTS it and turns
 * the numbers into one honest sentence ("nova kaam ka hisaab"). Pure Kotlin (no Android), unit-testable. Read-only: it never starts,
 * stops, taps or sends anything. "Verified" means the independent screen verifier said so; REPORTED_COMPLETE is NOT counted as verified.
 */
object AgentScore {
    /** state is AgentTaskRules.State.name; steps = steps the agent used. */
    class Row(val summary: String, val state: String, val steps: Int)

    class Tally(val total: Int, val verified: Int, val unverified: Int, val needsOwner: Int, val failed: Int, val stepsAvg: Int)

    private val ACTIVE = setOf("CREATED", "PLANNING", "WAITING_FOR_PERMISSION", "WAITING_FOR_CONFIRMATION", "RUNNING", "VERIFYING", "RETRYING")

    /** Counts the newest [last] finished jobs (jobs still running are left out). */
    fun tally(rows: List<Row>, last: Int = 20): Tally {
        val done = rows.filter { it.state !in ACTIVE }.takeLast(last)
        val ok = done.count { it.state == "SUCCEEDED" }
        val unv = done.count { it.state == "REPORTED_COMPLETE" }
        val own = done.count { it.state == "NEEDS_OWNER" }
        val bad = done.size - ok - unv - own
        val avg = if (done.isEmpty()) 0 else done.sumOf { it.steps } / done.size
        return Tally(done.size, ok, unv, own, bad, avg)
    }

    private fun word(state: String, en: Boolean): String = when (state) {
        "SUCCEEDED" -> if (en) "verified" else "verify hua"
        "REPORTED_COMPLETE" -> if (en) "done, not verified" else "hua par jaanch baaki"
        "NEEDS_OWNER" -> if (en) "needs you" else "aapki zaroorat"
        "CANCELLED" -> if (en) "cancelled" else "roka gaya"
        else -> if (en) "failed" else "fail"
    }

    /** One short spoken answer. Honest when there is no data. */
    fun reply(rows: List<Row>, en: Boolean): String {
        val t = tally(rows)
        if (t.total == 0) return if (en) "No finished agent jobs are recorded yet." else "Abhi tak koi poora hua agent kaam record nahi hai."
        val head = if (en)
            "Last ${t.total} jobs: ${t.verified} verified, ${t.unverified} done but not verified, ${t.needsOwner} needed you, ${t.failed} failed or stopped. About ${t.stepsAvg} steps each."
        else
            "Pichhle ${t.total} kaam: ${t.verified} verify hue, ${t.unverified} hue par jaanch baaki, ${t.needsOwner} me aapki zaroorat padi, ${t.failed} fail ya ruk gaye. Ek kaam me lagbhag ${t.stepsAvg} step."
        val lastTwo = rows.filter { it.state !in ACTIVE }.takeLast(2).reversed()
            .joinToString("; ") { it.summary.take(40) + " (" + word(it.state, en) + ")" }
        return if (lastTwo.isBlank()) head else head + (if (en) " Latest: " else " Taaza: ") + lastTwo + "."
    }
}
