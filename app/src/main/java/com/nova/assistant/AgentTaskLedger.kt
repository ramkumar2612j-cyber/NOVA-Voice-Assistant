package com.nova.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Private on-device task history. No microphone audio or screen content is stored.
 * An active task found after service/process restart is marked INTERRUPTED, never auto-replayed (avoids duplicate taps/sends).
 */
class AgentTaskLedger(context: Context) {
    data class Task(
        val id: String,
        val summary: String,
        val state: AgentTaskRules.State,
        val createdAtMs: Long,
        val updatedAtMs: Long,
        val deadlineAtMs: Long,
        val retryCount: Int = 0,
        val steps: Int = 0,
        val message: String = "",
        val checkpointAction: String = "",
        val checkpointCompleted: Boolean = false,
        val checkpointResumeSafe: Boolean = false,
        /** Sticky false once ANY non-idempotent action is about to run; protects crash windows. */
        val resumeSafeSoFar: Boolean = true,
        val resumeAttempted: Boolean = false,
        val resumedFromId: String = "",
        /** v6.72: privacy-safe step lines (AgentTaskRules.progressLine), newest last, for a resume after a restart. */
        val progress: String = ""
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun start(task: String, nowMs: Long = System.currentTimeMillis()): Task? = synchronized(LOCK) {
        val old = readAll()
        if (old.any { AgentTaskRules.isActive(it.state) }) return@synchronized null
        val clean = AgentTaskRules.safeSummary(task.take(AgentTaskRules.MAX_TASK_CHARS))
        val resumeNormalized = task.replace('\n', ' ').replace('\r', ' ').replace(Regex("\\s+"), " ").trim()
        // Resume uses only the redacted local summary; if it differs from the original command, fail closed.
        val resumeTextSafe = task.length <= AgentTaskRules.MAX_SUMMARY_CHARS && clean == resumeNormalized
        val created = Task(
            id = UUID.randomUUID().toString(),
            summary = clean,
            state = AgentTaskRules.State.CREATED,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            deadlineAtMs = nowMs + AgentTaskRules.MAX_RUNTIME_MS,
            message = "Task accepted; not yet verified",
            resumeSafeSoFar = resumeTextSafe
        )
        if (!writeAll((old + created).takeLast(AgentTaskRules.MAX_HISTORY))) return@synchronized null
        created
    }

    fun transition(id: String?, target: AgentTaskRules.State, message: String = "", nowMs: Long = System.currentTimeMillis()): Task? = synchronized(LOCK) {
        if (id.isNullOrBlank()) return@synchronized null
        val all = readAll()
        val index = all.indexOfLast { it.id == id }
        if (index < 0) return@synchronized null
        val old = all[index]
        if (!AgentTaskRules.canTransition(old.state, target)) return@synchronized old
        val next = old.copy(
            state = target,
            updatedAtMs = nowMs.coerceAtLeast(old.updatedAtMs),
            retryCount = old.retryCount + if (target == AgentTaskRules.State.RETRYING && old.state != target) 1 else 0,
            message = safeMessage(message).ifBlank { old.message }
        )
        val updated = all.toMutableList().also { it[index] = next }
        if (!writeAll(updated.takeLast(AgentTaskRules.MAX_HISTORY))) return@synchronized old
        next
    }

    /** Persist action intent BEFORE touching the UI. Non-idempotent actions permanently disable resume for this task. */
    fun beginStep(id: String?, action: String, nowMs: Long = System.currentTimeMillis()): Task? = synchronized(LOCK) {
        if (id.isNullOrBlank()) return@synchronized null
        val all = readAll()
        val index = all.indexOfLast { it.id == id }
        if (index < 0) return@synchronized null
        val old = all[index]
        if (!AgentTaskRules.isActive(old.state)) return@synchronized old
        val idempotent = AgentTaskRules.isIdempotentCheckpoint(action)
        val next = old.copy(
            updatedAtMs = nowMs.coerceAtLeast(old.updatedAtMs),
            checkpointAction = action.take(24),
            checkpointCompleted = false,
            checkpointResumeSafe = false,
            resumeSafeSoFar = old.resumeSafeSoFar && idempotent,
            message = if (idempotent) "Safe action intent saved" else "Non-repeatable action intent saved; resume disabled"
        )
        val updated = all.toMutableList().also { it[index] = next }
        if (!writeAll(updated.takeLast(AgentTaskRules.MAX_HISTORY))) return@synchronized null
        next
    }

    /** Resume only by owner request, only from the latest interrupted task, and only if every recorded action was idempotent. */
    fun resumeLastInterruptedIfSafe(nowMs: Long = System.currentTimeMillis()): Task? = synchronized(LOCK) {
        val all = readAll()
        if (all.any { AgentTaskRules.isActive(it.state) }) return@synchronized null
        val index = all.lastIndex
        if (index < 0) return@synchronized null
        val old = all[index]
        if (!isResumable(old, nowMs)) return@synchronized null
        val sourceUpdated = old.copy(
            updatedAtMs = nowMs.coerceAtLeast(old.updatedAtMs),
            resumeAttempted = true,
            message = "Owner requested one safe continuation; original task remains interrupted"
        )
        val resumed = Task(
            id = UUID.randomUUID().toString(),
            summary = old.summary,
            state = AgentTaskRules.State.CREATED,
            createdAtMs = old.createdAtMs,
            updatedAtMs = nowMs.coerceAtLeast(old.updatedAtMs),
            deadlineAtMs = old.deadlineAtMs,
            retryCount = old.retryCount,
            steps = old.steps,
            message = "Owner-requested safe continuation from interrupted task",
            checkpointAction = old.checkpointAction,
            checkpointCompleted = old.checkpointCompleted,
            checkpointResumeSafe = old.checkpointResumeSafe,
            resumeSafeSoFar = true,
            resumedFromId = old.id,
            progress = old.progress
        )
        val updated = all.toMutableList().also {
            it[index] = sourceUpdated
            it.add(resumed)
        }.takeLast(AgentTaskRules.MAX_HISTORY)
        if (!writeAll(updated)) return@synchronized null
        resumed
    }

    fun resumeStatusText(nowMs: Long = System.currentTimeMillis()): String = synchronized(LOCK) {
        val task = readAll().lastOrNull() ?: return@synchronized "Abhi resume karne ke liye koi purana task nahi hai."
        when {
            AgentTaskRules.isActive(task.state) -> "Ek task abhi active hai; pehle uska result aane do ya 'ruko' bolo."
            task.state != AgentTaskRules.State.INTERRUPTED -> "Aakhri task interrupted nahi hai, isliye resume nahi kiya. Naya task bol sakte ho."
            task.resumeAttempted -> "Is interrupted task ka safe resume pehle try ho chuka hai. Duplicate kaam rokne ke liye dobara resume nahi karunga."
            task.deadlineAtMs <= nowMs -> "Task ki original 5-minute deadline nikal gayi; naya task dobara bolo."
            task.steps <= 0 || !task.checkpointCompleted || !task.checkpointResumeSafe || !task.resumeSafeSoFar -> "Task ka safe summary ya saare steps resume-safe prove nahi hue; duplicate/galat action se bachne ke liye resume block hai. Naya task fresh shuru karo."
            else -> "Pichhla task safe resume ke liye tayyar hai. 'resume last task' bolo to current screen se dobara plan karunga."
        }
    }

    private fun isResumable(task: Task, nowMs: Long): Boolean =
        AgentTaskRules.canResumeInterruptedTask(
            state = task.state,
            steps = task.steps,
            action = task.checkpointAction,
            checkpointCompleted = task.checkpointCompleted && task.checkpointResumeSafe,
            resumeSafeSoFar = task.resumeSafeSoFar,
            resumeAttempted = task.resumeAttempted,
            deadlineAtMs = task.deadlineAtMs,
            nowMs = nowMs
        )

    fun recordStep(id: String?, message: String = "", nowMs: Long = System.currentTimeMillis(), action: String = "", checkpointCompleted: Boolean = false, progressLine: String = ""): Task? = synchronized(LOCK) {
        if (id.isNullOrBlank()) return@synchronized null
        val all = readAll()
        val index = all.indexOfLast { it.id == id }
        if (index < 0) return@synchronized null
        val old = all[index]
        if (!AgentTaskRules.isActive(old.state)) return@synchronized old
        val safe = AgentTaskRules.canResumeCheckpoint(action, checkpointCompleted)
        val next = old.copy(
            steps = (old.steps + 1).coerceAtMost(1000),
            updatedAtMs = nowMs.coerceAtLeast(old.updatedAtMs),
            message = safeMessage(message).ifBlank { old.message },
            checkpointAction = action.take(24),
            checkpointCompleted = checkpointCompleted,
            checkpointResumeSafe = safe,
            resumeSafeSoFar = old.resumeSafeSoFar && safe,
            progress = if (progressLine.isBlank()) old.progress else AgentTaskRules.appendProgress(old.progress, progressLine)
        )
        val updated = all.toMutableList().also { it[index] = next }
        if (!writeAll(updated.takeLast(AgentTaskRules.MAX_HISTORY))) return@synchronized old
        next
    }

    /** Call when NOVA's service starts. This marks stale work interrupted rather than replaying unsafe UI actions. */
    fun recoverInterrupted(nowMs: Long = System.currentTimeMillis()): Int = synchronized(LOCK) {
        val all = readAll()
        var count = 0
        val changed = all.map { task ->
            if (AgentTaskRules.isActive(task.state)) {
                count++
                task.copy(state = AgentTaskRules.State.INTERRUPTED, updatedAtMs = nowMs.coerceAtLeast(task.updatedAtMs), message = "NOVA restart/service stop detected; not resumed to avoid repeating actions")
            } else task
        }
        if (count > 0 && !writeAll(changed.takeLast(AgentTaskRules.MAX_HISTORY))) return@synchronized 0
        count
    }

    fun latest(): Task? = synchronized(LOCK) { readAll().lastOrNull() }
    fun history(): List<Task> = synchronized(LOCK) { readAll().toList() }

    fun statusText(): String {
        val task = latest() ?: return "NOVA agent history: abhi tak koi task record nahi hai."
        val state = when (task.state) {
            AgentTaskRules.State.CREATED -> "shuru hone ke liye bana"
            AgentTaskRules.State.PLANNING -> "planning"
            AgentTaskRules.State.WAITING_FOR_PERMISSION -> "permission ka intezar"
            AgentTaskRules.State.WAITING_FOR_CONFIRMATION -> "aapki confirmation ka intezar"
            AgentTaskRules.State.RUNNING -> "chal raha hai"
            AgentTaskRules.State.VERIFYING -> "verify kar raha hai"
            AgentTaskRules.State.RETRYING -> "bounded retry chal raha hai"
            AgentTaskRules.State.SUCCEEDED -> "verified success"
            AgentTaskRules.State.REPORTED_COMPLETE -> "agent ne complete report kiya; result independently verify nahi hua"
            AgentTaskRules.State.NEEDS_OWNER -> "aapki madad/step chahiye"
            AgentTaskRules.State.FAILED -> "fail hua"
            AgentTaskRules.State.CANCELLED -> "aapne rok diya"
            AgentTaskRules.State.INTERRUPTED -> "beech mein interrupt hua; automatically dobara nahi chalayega"
        }
        val label = task.summary.ifBlank { "task" }
        return "Aakhri task: $label. Status: $state. Steps: ${task.steps}. ${task.message}".take(420)
    }

    fun historyText(): String {
        val all = history().takeLast(6).reversed()
        if (all.isEmpty()) return "NOVA task history khaali hai."
        return all.joinToString("\n") { t ->
            val checkpoint = when {
                isResumable(t, System.currentTimeMillis()) -> "manual resume available (say: resume last task)"
                t.checkpointAction.isNotBlank() && (!t.resumeSafeSoFar || !t.checkpointResumeSafe) -> "checkpoint=${t.checkpointAction}; resume blocked to prevent duplicate actions"
                t.checkpointAction.isNotBlank() -> "checkpoint=${t.checkpointAction}; no automatic resume"
                else -> "checkpoint nahi"
            }
            "${t.summary} — ${t.state.name}, steps=${t.steps}; $checkpoint. ${t.message}"
        }.take(2000)
    }

    fun notifyResult(context: Context, title: String, message: String) {
        try {
            val app = context.applicationContext
            val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(android.app.NotificationChannel("nova_agent_result", "NOVA task results", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            val intent = android.content.Intent(app, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or if (android.os.Build.VERSION.SDK_INT >= 23) android.app.PendingIntent.FLAG_IMMUTABLE else 0
            val pi = android.app.PendingIntent.getActivity(app, 7419, intent, flags)
            val note = androidx.core.app.NotificationCompat.Builder(app, "nova_agent_result")
                .setSmallIcon(com.nova.assistant.R.mipmap.ic_launcher).setContentTitle(title.take(80))
                .setContentText(message.take(180)).setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message.take(500)))
                .setContentIntent(pi).setAutoCancel(true).build()
            manager.notify((System.currentTimeMillis() % 1000000).toInt(), note)
        } catch (e: Exception) { /* notifications can be denied by the owner */ }
    }

    private fun readAll(): List<Task> {
        val primary = prefs.getString(KEY_TASKS, null)
        if (!primary.isNullOrBlank()) parse(primary)?.let { return it }
        val backup = prefs.getString(KEY_BACKUP, null)
        return parse(backup) ?: emptyList()
    }

    private fun parse(raw: String?): List<Task>? {
        if (raw.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Task>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val state = try { AgentTaskRules.State.valueOf(o.optString("state")) } catch (ignored: Exception) { AgentTaskRules.State.INTERRUPTED }
                out.add(Task(
                    id = o.optString("id"),
                    summary = o.optString("summary").take(AgentTaskRules.MAX_SUMMARY_CHARS),
                    state = state,
                    createdAtMs = o.optLong("created", 0L),
                    updatedAtMs = o.optLong("updated", 0L),
                    deadlineAtMs = o.optLong("deadline", 0L),
                    retryCount = o.optInt("retries", 0).coerceIn(0, AgentTaskRules.MAX_RETRIES),
                    steps = o.optInt("steps", 0).coerceIn(0, 1000),
                    message = safeMessage(o.optString("message")),
                    checkpointAction = o.optString("checkpointAction").take(24),
                    checkpointCompleted = o.optBoolean("checkpointCompleted", false),
                    checkpointResumeSafe = o.optBoolean("checkpointResumeSafe", false) && AgentTaskRules.canResumeCheckpoint(o.optString("checkpointAction"), o.optBoolean("checkpointCompleted", false)),
                    // Missing field means an older version did not track every action's side effects: fail closed.
                    resumeSafeSoFar = o.optBoolean("resumeSafeSoFar", false),
                    resumeAttempted = o.optBoolean("resumeAttempted", false),
                    resumedFromId = o.optString("resumedFromId").take(64),
                    progress = o.optString("progress").take(AgentTaskRules.PROGRESS_CHARS)
                ))
            }
            out.takeLast(AgentTaskRules.MAX_HISTORY)
        } catch (ignored: Exception) { null }
    }

    private fun writeAll(tasks: List<Task>): Boolean {
        val current = prefs.getString(KEY_TASKS, null)
        val arr = JSONArray()
        tasks.takeLast(AgentTaskRules.MAX_HISTORY).forEach { t ->
            arr.put(JSONObject()
                .put("id", t.id)
                .put("summary", t.summary)
                .put("state", t.state.name)
                .put("created", t.createdAtMs)
                .put("updated", t.updatedAtMs)
                .put("deadline", t.deadlineAtMs)
                .put("retries", t.retryCount)
                .put("steps", t.steps)
                .put("message", safeMessage(t.message))
                .put("checkpointAction", t.checkpointAction)
                .put("checkpointCompleted", t.checkpointCompleted)
                .put("checkpointResumeSafe", t.checkpointResumeSafe)
                .put("resumeSafeSoFar", t.resumeSafeSoFar)
                .put("resumeAttempted", t.resumeAttempted)
                .put("resumedFromId", t.resumedFromId)
                .put("progress", t.progress))
        }
        // Commit synchronously: this is a small status ledger, and an async write could be lost on process death.
        return prefs.edit().putString(KEY_BACKUP, current).putString(KEY_TASKS, arr.toString()).commit()
    }

    private fun safeMessage(raw: String): String = AgentTaskRules.safeSummary(raw).take(160)

    companion object {
        private const val PREFS = "nova_agent_task_ledger"
        private const val KEY_TASKS = "tasks_v1"
        private const val KEY_BACKUP = "tasks_backup_v1"
        private val LOCK = AgentTaskLedger::class.java
    }
}
