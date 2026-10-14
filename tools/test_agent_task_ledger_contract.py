#!/usr/bin/env python3
"""Source-contract guards for persistent task status and safe restart behavior (not an Android build)."""
from pathlib import Path
import re
import sys
ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "app/src/main/java/com/nova/assistant"
rules = (JAVA / "AgentTaskRules.kt").read_text(encoding="utf-8")
ledger = (JAVA / "AgentTaskLedger.kt").read_text(encoding="utf-8")
service = (JAVA / "NovaService.kt").read_text(encoding="utf-8")
tests = (ROOT / "app/src/test/java/com/nova/assistant/AgentTaskRulesTest.kt").read_text(encoding="utf-8")
fails = []
def check(name, cond):
    print(("PASS " if cond else "FAIL ") + name)
    if not cond: fails.append(name)

for state in ("CREATED", "PLANNING", "WAITING_FOR_PERMISSION", "WAITING_FOR_CONFIRMATION", "RUNNING", "VERIFYING", "RETRYING", "SUCCEEDED", "REPORTED_COMPLETE", "NEEDS_OWNER", "FAILED", "CANCELLED", "INTERRUPTED"):
    check(f"task state exists: {state}", f"{state}," in rules or f"{state}\n" in rules)
check("history is capped", "MAX_HISTORY = 20" in rules and "takeLast(AgentTaskRules.MAX_HISTORY)" in ledger)
check("retry count and runtime have fixed caps", "MAX_RETRIES = 3" in rules and "MAX_RUNTIME_MS = 300_000L" in rules and "fun canRetry" in rules)
check("task summaries redact key-like text, email and phone numbers", "[hidden]" in rules and "[email]" in rules and "[number]" in rules and "MAX_SUMMARY_CHARS" in rules)
check("task ledger is private app storage", 'getSharedPreferences(PREFS, Context.MODE_PRIVATE)' in ledger)
check("task ledger persists synchronously with a backup", ".commit()" in ledger and "KEY_BACKUP" in ledger)
check("active task is not duplicated", "old.any { AgentTaskRules.isActive(it.state) }" in ledger)
check("in-flight task is marked interrupted on recovery instead of replayed", "fun recoverInterrupted" in ledger and "State.INTERRUPTED" in ledger and "not resumed to avoid repeating actions" in ledger)
check("task status summary is exposed from local ledger", "fun statusText()" in ledger and "isStatusQuery(text)" in service)
check("agent start persists task before its worker begins", service.index("ledger.start(task)") < service.index('Thread({', service.index("private fun agentStart")))
check("spoken stop cancels and persists agent state", '"stop" -> {' in service and 'agentCancel = true' in service and 'State.CANCELLED, "Owner said stop"' in service)
check("service restart cancels/marks interrupted rather than resuming automatically", ("AgentTaskLedger(this).recoverInterrupted()" in service or "AgentTaskLedger(appCtx).recoverInterrupted()" in service) and "NOVA service stopped" in service)
check("model completion requires a separate visible-screen verifier", "agentVerify(fullTask, pkg, nodes, a.say, hist)" in service and "State.SUCCEEDED" in service and "Task completion was not verified" in service and "independently verify" in ledger)
check("ledger persists a neutral terminal status, not the model's answer", "val persistedStatus = when (agentOutcome)" in service and "ledger.transition(record.id, agentOutcome, persistedStatus)" in service and "ledger.transition(record.id, agentOutcome, result)" not in service)
check("step intent is saved BEFORE action so a crash cannot replay a non-idempotent action", "beginStep(agentTaskId, a.act)" in service and service.index("beginStep(agentTaskId, a.act)") < service.index("val res = agentDo(a)") and "resumeSafeSoFar = old.resumeSafeSoFar && idempotent" in ledger)
check("manual resume is bounded to the last interrupted task and original deadline", "resumeLastInterruptedIfSafe" in ledger and "canResumeInterruptedTask(" in ledger and "manual resume available (say: resume last task)" in ledger)
check("only completed open_app/wait steps are idempotent resume candidates", 'in setOf("open_app", "wait")' in rules and '"done"' not in rules.split("fun isIdempotentCheckpoint",1)[1].split("fun canResumeCheckpoint",1)[0])
check("resume safety is sticky across the whole task and older records fail closed", "resumeSafeSoFar = old.resumeSafeSoFar && safe" in ledger and 'o.optBoolean("resumeSafeSoFar", false)' in ledger)
check("resume is disabled when the saved summary would change/truncate the owner task", "val resumeTextSafe = task.length <= AgentTaskRules.MAX_SUMMARY_CHARS && clean == resumeNormalized" in ledger and "resumeSafeSoFar = resumeTextSafe" in ledger)
check("task resume is explicit local command and not an auto-replay", "AgentTaskRules.isResumeQuery(text)" in service and "return agentResumeLastSafe()" in service and "recoverInterrupted()" in service)
check("step history stores only result summary plus bounded action checkpoint, never typed content or raw screen excerpt (v6.72: plus a privacy-safe progress line built from the action name only)", 'recordStep(agentTaskId, "Step $step: action=${a.act}; result=$res", action = a.act, checkpointCompleted = !res.startsWith("FAILED"),' in service and "progressLine = AgentTaskRules.progressLine(step, a.act, a.app," in service and "recordStep(agentTaskId, line)" not in service)
check("Kotlin unit tests cover lifecycle, retries, status and privacy", all(x in tests for x in ("normalLifecycleAndTerminalStates", "retryBudgetAndDeadlineAreBounded", "sensitiveStringsAreNotKeptInSummary", "statusQueriesAreNarrowAndDoNotStealNormalLanguage")))
print("\nALL PASSED" if not fails else f"\n{len(fails)} FAILED")
sys.exit(1 if fails else 0)
