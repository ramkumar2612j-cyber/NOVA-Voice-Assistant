package com.nova.assistant

/**
 * v7.200 DAILY CHECK (add-only, pure Kotlin, no Android classes, unit-tested).
 *
 * Roz ki report: aaj kya bana, kaun sa feature theek hai, kaun sa kharab hai aur uska fix kya hai.
 * SAFETY (owner decision): report khud se roz chalti hai, par KOI fix bina owner ki haan ke main par nahi jaata.
 * Fix sirf tab chalta hai jab owner bole "haan fix karo" (SelfPatch wala raasta, 5/din ki cap, RED par auto-revert).
 * HARD DENY (paisa / bank / password / OTP / PIN / CVV) kabhi nahi badalta.
 */
internal object DailyCheckRules {

    class Probe(val id: String, val name: String, val ok: Boolean, val detail: String, val fix: String)

    /** Words that ask for the daily report. */
    private val REPORT_RE = Regex("(roz\\s*ki\\s*report|daily\\s*report|aaj\\s*kya\\s*bana|aaj\\s*ka\\s*(check|report)|feature\\s*check|kya\\s*kharab|self\\s*check)", RegexOption.IGNORE_CASE)
    fun isReportQuestion(said: String): Boolean = said.trim().split(Regex("\\s+")).size <= 10 && REPORT_RE.containsMatchIn(said)

    /** Owner ne fix ki haan di? (only a clear yes after a report) */
    private val FIX_RE = Regex("(haan\\s*fix|fix\\s*karo|theek\\s*karo|haan\\s*theek|yes\\s*fix|fix\\s*kar\\s*do)", RegexOption.IGNORE_CASE)
    fun isFixApproval(said: String): Boolean = said.trim().split(Regex("\\s+")).size <= 6 && FIX_RE.containsMatchIn(said)

    /** Sirf ye files self-patch se badal sakte hain (SelfPatchRules ka DIR rule bhi wahi rehta hai). */
    const val MAX_FIXES_PER_DAY = 5

    /** Builds the report text. built = aaj ke commits / changelog lines; errors = ErrLog lines (already clean). */
    fun report(probes: List<Probe>, built: List<String>, errors: List<String>, en: Boolean): String {
        val sb = StringBuilder()
        val bad = probes.filter { !it.ok }
        val okN = probes.size - bad.size
        sb.append(if (en) "Daily check: $okN of ${probes.size} features are working.\n" else "Daily check: ${probes.size} me se $okN feature theek chal rahe hain.\n")
        if (built.isNotEmpty()) {
            sb.append(if (en) "Built / changed today:\n" else "Aaj kya bana / badla:\n")
            for (b in built.take(5)) sb.append("- ").append(b.take(140)).append('\n')
        } else {
            sb.append(if (en) "Nothing new was built today.\n" else "Aaj naya kuch nahi bana.\n")
        }
        if (bad.isEmpty()) {
            sb.append(if (en) "No broken feature found.\n" else "Koi feature kharab nahi mila.\n")
        } else {
            sb.append(if (en) "Not working:\n" else "Kaam nahi kar rahe:\n")
            for (p in bad) sb.append("- ").append(p.name).append(": ").append(p.detail.take(120)).append(" -> ").append(p.fix.take(140)).append('\n')
            sb.append(if (en) "Should I fix these? Say \"haan fix karo\" and I will patch them one by one, and tell you when each is done.\n"
                      else "Inhe theek karun? \"haan fix karo\" bolo, main ek-ek karke theek karunga aur har ek ke baad bataunga.\n")
        }
        if (errors.isNotEmpty()) {
            sb.append(if (en) "Recent errors: " else "Haal ki galtiyan: ")
            sb.append(errors.take(3).joinToString(" | ") { it.take(100) })
        }
        return sb.toString().trimEnd()
    }

    /** Plan for the fix step: one line per broken feature, capped. Nothing runs here. */
    fun fixPlan(probes: List<Probe>, en: Boolean): List<String> =
        probes.filter { !it.ok }.take(MAX_FIXES_PER_DAY).map { (if (en) "Fix: " else "Theek karna: ") + it.name + " -> " + it.fix }

    /** Probes the app can answer for itself without network. */
    fun probesFrom(ttsReady: Boolean, accessibilityOn: Boolean, aiKeyReady: Boolean, micReady: Boolean, en: Boolean): List<Probe> = listOf(
        Probe("tts", if (en) "Voice (speaking)" else "Awaaz (bolna)", ttsReady,
            if (ttsReady) "ok" else (if (en) "speech engine not ready" else "speech engine taiyar nahi"),
            if (en) "Settings me Text-to-speech engine check karo" else "Settings me Text-to-speech engine check karo"),
        Probe("access", "Accessibility", accessibilityOn,
            if (accessibilityOn) "ok" else (if (en) "switch off" else "switch OFF hai"),
            if (en) "Accessibility page me JARVIS ko ON karo" else "Accessibility page me JARVIS ko ON karo"),
        Probe("ai", if (en) "AI brain" else "AI brain", aiKeyReady,
            if (aiKeyReady) "ok" else (if (en) "no key" else "key nahi"),
            if (en) "Settings me AI key daalo" else "Settings me AI key daalo"),
        Probe("mic", if (en) "Microphone" else "Mic", micReady,
            if (micReady) "ok" else (if (en) "permission missing" else "permission nahi"),
            if (en) "Mic permission do" else "Mic permission do")
    )
}
