package com.nova.assistant

/**
 * v7.206 TERMUX ROUTE rules (add-only, pure Kotlin, unit-tested).
 *
 * Owner ki maang (2026-11-16): JARVIS kaam phone ke Termux terminal se kare.
 * Safety:
 *  - ALLOW (bina poochhe): sirf read-only: pwd, ls, date, uptime, df -h, git status
 *  - NEEDS CONFIRM: baaki sab. JARVIS command dikhata hai, aap "haan run karo" bolo tabhi chalti hai (5 minute me expire)
 *  - DENY (kabhi nahi, haan se bhi nahi): rm, sudo, su, chmod, chown, dd, mkfs, curl, wget, ssh, nc, eval,
 *    pipes / chaining (| ; && || ` $( > <), system ya secret paths, token / key / password / otp / pin / cvv / paisa / bank
 */
internal object TermuxRules {
    const val PACKAGE = "com.termux"
    const val CONFIRM_MS = 5L * 60_000L

    private val ALLOW = setOf("pwd", "ls", "date", "uptime", "df -h", "git status")
    private val DENY_WORDS = setOf("rm", "sudo", "su", "chmod", "chown", "dd", "mkfs", "curl", "wget", "ssh", "nc", "eval")
    private val DENY_SNIPPETS = listOf("|", ";", "&&", "||", "`", "\$(", ">", "<", "/etc", "/system", "keystore", "token", "password", "otp", "cvv", "pin ", "paisa", "bank", "secret")

    sealed class Verdict {
        object Allowed : Verdict()
        class NeedsConfirm(val cmd: String) : Verdict()
        class Denied(val why: String) : Verdict()
    }

    fun classify(raw: String): Verdict {
        val cmd = raw.trim().replace(Regex("\\s+"), " ")
        if (cmd.isBlank() || cmd.length > 200) return Verdict.Denied("command khaali ya bahut lamba hai")
        val lower = cmd.lowercase()
        if (DENY_SNIPPETS.any { lower.contains(it) } && !ALLOW.contains(cmd)) {
            return Verdict.Denied("is command me khatarnak chihn ya secret shabd hai")
        }
        val first = lower.split(" ").first()
        if (first in DENY_WORDS) return Verdict.Denied("'$first' kabhi nahi chalega")
        if (ALLOW.contains(cmd)) return Verdict.Allowed
        return Verdict.NeedsConfirm(cmd)
    }

    /** Words after "jarvis terminal me" / "termux me". */
    private val TRIGGER = Regex("^\\s*(jarvis\\s+)?(terminal\\s+(me|mein|par|pe)|termux\\s+(me|mein|par|pe))\\s*", RegexOption.IGNORE_CASE)
    fun isRoute(said: String): Boolean = TRIGGER.containsMatchIn(said.trim())
    fun commandOf(said: String): String {
        val m = TRIGGER.find(said.trim()) ?: return ""
        return said.trim().substring(m.range.last + 1).trim().take(200)
    }
    private val CONFIRM_RE = Regex("(haan\\s*run\\s*karo|haan\\s*chalao|run\\s*karo|haan\\s*chalo)", RegexOption.IGNORE_CASE)
    fun isConfirm(said: String): Boolean = said.trim().split(Regex("\\s+")).size <= 5 && CONFIRM_RE.containsMatchIn(said)
    fun pendingLive(savedAt: Long, now: Long): Boolean = savedAt > 0 && now - savedAt <= CONFIRM_MS
}
