package com.nova.assistant

/**
 * v7.202 AUTO RELEASE POLICY (add-only, pure Kotlin, unit-tested).
 *
 * Owner decision (2026-11-16): JARVIS khud build kare aur green build par install shuru kare, owner se poochhe nahi.
 * Android ka install confirmation screen hamesha aata hai (silent install nahi). Phone locked / call / kaam chal raha ho
 * to kuch nahi hota. Ek 6 ghante me 1 aur din me max 3 auto-installs. HARD DENY se koi lena-dena nahi.
 */
internal object AutoReleaseRules {
    const val MIN_GAP_MS = 6L * 3600_000L
    const val MAX_PER_DAY = 3

    class Decision(val go: Boolean, val why: String)

    fun shouldAutoInstall(phase: String, lastAutoAt: Long, todayCount: Int, now: Long, locked: Boolean, inCall: Boolean, busy: Boolean, enabled: Boolean): Decision {
        if (!enabled) return Decision(false, "auto install band hai")
        if (phase != SelfBuildRules.GREEN) return Decision(false, "build green nahi hai")
        if (locked) return Decision(false, "phone lock hai")
        if (inCall) return Decision(false, "call chal rahi hai")
        if (busy) return Decision(false, "ek kaam chal raha hai")
        if (lastAutoAt > 0 && now - lastAutoAt < MIN_GAP_MS) return Decision(false, "6 ghante ka gap abhi poora nahi")
        if (todayCount >= MAX_PER_DAY) return Decision(false, "aaj ke $MAX_PER_DAY installs ho chuke")
        return Decision(true, "green build, install shuru")
    }

    fun countToday(stamps: List<Long>, now: Long): Int {
        val dayStart = now - 24L * 3600_000L
        return stamps.count { it >= dayStart && it <= now }
    }
}
