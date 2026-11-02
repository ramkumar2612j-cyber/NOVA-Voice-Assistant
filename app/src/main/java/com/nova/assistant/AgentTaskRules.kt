package com.nova.assistant

/**
 * Durable task lifecycle rules for NOVA's agent. Pure Kotlin: no Android APIs, so transitions are testable.
 * A task reported complete by a model is deliberately NOT the same as independently verified success.
 */
object AgentTaskRules {
    const val MAX_HISTORY = 20
    const val MAX_TASK_CHARS = 400
    const val MAX_SUMMARY_CHARS = 120
    const val MAX_RETRIES = 3
    const val MAX_RUNTIME_MS = 300_000L

    enum class State {
        CREATED,
        PLANNING,
        WAITING_FOR_PERMISSION,
        WAITING_FOR_CONFIRMATION,
        RUNNING,
        VERIFYING,
        RETRYING,
        SUCCEEDED,
        REPORTED_COMPLETE,
        NEEDS_OWNER,
        FAILED,
        CANCELLED,
        INTERRUPTED
    }

    fun isTerminal(state: State): Boolean = state in setOf(
        State.SUCCEEDED, State.REPORTED_COMPLETE, State.NEEDS_OWNER,
        State.FAILED, State.CANCELLED, State.INTERRUPTED
    )

    fun isActive(state: State): Boolean = !isTerminal(state)

    fun canTransition(from: State, to: State): Boolean {
        if (from == to) return true
        if (isTerminal(from)) return false
        if (to == State.INTERRUPTED) return true
        if (to in setOf(State.SUCCEEDED, State.REPORTED_COMPLETE, State.NEEDS_OWNER, State.FAILED, State.CANCELLED)) return true
        return when (from) {
            State.CREATED -> to == State.PLANNING
            State.PLANNING -> to in setOf(State.RUNNING, State.WAITING_FOR_PERMISSION, State.WAITING_FOR_CONFIRMATION, State.RETRYING, State.VERIFYING)
            State.WAITING_FOR_PERMISSION -> to in setOf(State.PLANNING, State.RUNNING, State.WAITING_FOR_CONFIRMATION)
            State.WAITING_FOR_CONFIRMATION -> to in setOf(State.RUNNING, State.PLANNING, State.WAITING_FOR_PERMISSION)
            State.RUNNING -> to in setOf(State.PLANNING, State.WAITING_FOR_PERMISSION, State.WAITING_FOR_CONFIRMATION, State.VERIFYING, State.RETRYING)
            State.VERIFYING -> to in setOf(State.RUNNING, State.PLANNING, State.RETRYING)
            State.RETRYING -> to in setOf(State.PLANNING, State.RUNNING)
            else -> false
        }
    }

    fun canRetry(retryCount: Int, createdAtMs: Long, nowMs: Long, maxRetries: Int = MAX_RETRIES): Boolean {
        if (retryCount < 0 || maxRetries < 0 || retryCount >= maxRetries) return false
        if (nowMs < createdAtMs) return false
        return nowMs - createdAtMs < MAX_RUNTIME_MS
    }

    /** Store only a short, redacted task label; never persist screen text, audio, or full project prompts in the ledger. */
    /**
     * v6.72: a short, privacy-safe record of one finished step for a restart / resume: step number, action name, app name for open_app,
     * and the result ("ok" / "FAILED" and whether the screen changed). NEVER the typed text and never a screen label or excerpt.
     */
    fun progressLine(step: Int, act: String, app: String, ok: Boolean, changed: Boolean): String {
        val a = act.filter { it.isLetterOrDigit() || it == '_' }.take(16)
        val ap = if (act == "open_app") " " + safeSummary(app).take(24) else ""
        return "step $step: $a$ap -> " + (if (ok) "ok" else "FAILED") + (if (changed) ", screen changed" else ", screen unchanged")
    }

    const val PROGRESS_LINES = 12
    const val PROGRESS_CHARS = 900

    /** Adds one line and keeps only the newest PROGRESS_LINES lines / PROGRESS_CHARS characters (lines are separated by a newline). */
    fun appendProgress(old: String, line: String): String {
        val one = safeSummary(line).take(90)
        if (one.isBlank()) return old.take(PROGRESS_CHARS)
        val all = ArrayList<String>()
        old.split('\n').filter { it.isNotBlank() }.forEach { all.add(it) }
        all.add(one)
        var keep = all.takeLast(PROGRESS_LINES)
        while (keep.size > 1 && keep.sumOf { it.length + 1 } > PROGRESS_CHARS) keep = keep.drop(1)
        return keep.joinToString("\n")
    }

    fun safeSummary(raw: String): String {
        val oneLine = raw.replace('\n', ' ').replace('\r', ' ').replace(Regex("\\s+"), " ").trim()
        val redacted = oneLine
            .replace(Regex("(?i)(api[_ -]?key|token|password|passwd|secret)\\s*[:=]\\s*\\S+"), "$1=[hidden]")
            .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]")
            .replace(Regex("(?<![0-9])\\+?[0-9][0-9 -]{8,}[0-9](?![0-9])"), "[number]")
            .replace(Regex("(?<![A-Za-z0-9])[A-Za-z0-9_\\-]{32,}(?![A-Za-z0-9])"), "[hidden]")
        return redacted.take(MAX_SUMMARY_CHARS)
    }


    /** A repeated action is only a resume candidate when it is known to be idempotent and its previous result was saved. */
    fun isIdempotentCheckpoint(action: String): Boolean = action.trim().lowercase() in setOf("open_app", "wait")

    fun canResumeCheckpoint(action: String, checkpointCompleted: Boolean): Boolean =
        checkpointCompleted && isIdempotentCheckpoint(action)

    /** All conditions required for a manual resume; no restart path calls this automatically. */
    fun canResumeInterruptedTask(
        state: State,
        steps: Int,
        action: String,
        checkpointCompleted: Boolean,
        resumeSafeSoFar: Boolean,
        resumeAttempted: Boolean,
        deadlineAtMs: Long,
        nowMs: Long
    ): Boolean = state == State.INTERRUPTED && steps > 0 && resumeSafeSoFar &&
        !resumeAttempted && deadlineAtMs > nowMs && canResumeCheckpoint(action, checkpointCompleted)

    /** Explicit, narrow resume commands only; ordinary tasks must not be intercepted. */
    fun isResumeQuery(raw: String): Boolean {
        val n = Logic.norm(raw).removePrefix("nova ").trim()
        return n in setOf(
            "resume last task", "resume task", "task resume", "resume pichla task",
            "pichla task resume karo", "pichla kaam resume karo", "pichla kaam jari rakho",
            "pichla task continue karo", "task continue karo", "kaam continue karo",
            "resume pichla kaam", "kaam aage chalao", "task aage chalao"
        )
    }

    fun isHistoryQuery(raw: String): Boolean {
        val n = Logic.norm(raw).removePrefix("nova ").trim()
        return n in setOf("agent history", "task history", "kaam history", "kaam ki history", "pichle kaam", "pichle task")
    }

    fun isStatusQuery(raw: String): Boolean {
        val n = Logic.norm(raw).removePrefix("nova ").trim()
        return n in setOf("agent status", "task status", "kaam status", "kaam ki status", "pichla kaam", "last task", "agent history", "kaam ka status")
    }
}
