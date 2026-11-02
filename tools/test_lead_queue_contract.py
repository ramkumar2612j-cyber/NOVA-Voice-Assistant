#!/usr/bin/env python3
"""Static contract checks for public lead review/batch workflow, offline queue and provider failover.
These are source/wiring checks; they do NOT prove Kotlin compilation or physical phone behavior."""
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/nova/assistant"
read = lambda n: (JAVA / n).read_text(encoding="utf-8")
lead, store, rules, queue, worker = map(read, ["LeadRules.kt", "LeadStore.kt", "OfflineQueueRules.kt", "OfflineJobQueue.kt", "NovaCloudQueueWorker.kt"])
logic, service, site, cloud, msg, feature = map(read, ["Logic.kt", "NovaService.kt", "SiteGen.kt", "CloudTaskText.kt", "MessageMode.kt", "FeatureCheck.kt"])
unit_test = (ROOT / "app/src/test/java/com/nova/assistant/LeadQueueRulesTest.kt").read_text(encoding="utf-8")
checks = {
    "public lead capture allowlist only": "isAllowedPublicSource(pkg)" in lead and "parseVisible(pkg" in lead,
    "lead parser excludes private chat/notification package sources": "com.whatsapp" not in lead and "com.telegram" not in lead,
    "local lead storage bounded to 100": "MAX_LEADS = 100" in store,
    "AI drafts default to non-deceptive personal message": "Could you share the details?" in lead and "Namaste, mujhe" in lead,
    "draft preview includes exact saved text": "append(l.draft)" in store and "sys(preview)" in service,
    "one spoken batch approval before sends": "BATCH APPROVAL" in service and "askFirst(" in service and "approveBatch(this, candidates" in service,
    "batch uses existing exact-message WhatsApp-only send helper": "NovaAccessibilityService.armSend(lead.draft)" in service and "com.whatsapp.w4b" in service,
    "batch stops on first send failure": "batch stopped" in service and "if (!finished || !sendClicked.get())" in service,
    "daily cap defaults 20 and only lower": "DEFAULT_CAP = 20" in store and "coerceAtMost(old)" in store,
    "clear lead list is confirmed": '"lead_clear" -> askFirst' in service,
    "workmanager queue has max 20 and 3 attempts": "MAX_PENDING = 20" in rules and "MAX_ATTEMPTS = 3" in rules and "markAttempt" in queue,
    "queue only supports cloud jobs, not screen UI tasks": "containsScreenInstructions" in rules and "!containsScreenInstructions" in rules and "Screen/UI agent commands" in queue,
    "queue persists jobs and waits for connectivity": "SharedPreferences" in queue and "NetworkType.CONNECTED" in queue and "enqueueUniqueWork" in queue,
    "queue output can be saved into Downloads/NOVA": "Environment.DIRECTORY_DOWNLOADS" in queue and '"/NOVA"' in queue,
    "worker queues learn notes as pending for owner approval": "LearnStore.addPending" in worker and "press RAKHO" in worker,
    "screen agent tasks are never auto replayed": "containsScreenInstructions" in rules and "!containsScreenInstructions" in rules and "recoverInterrupted()" in service and "not auto-resumed" in service,
    "Gemini primary then all configured text provider fallbacks": "SecureStore.getKeys(ctx)" in cloud and "FreeProviders.ordered()" in cloud and "FreeProviders.ordered()" in site,
    "API keys are not logged by the new helper": "ApiUsage.hit(\"gemini\", code)" in cloud and "println(" not in cloud and "Log." not in cloud,
    "Twitter/X aliases are handled with exact match prioritized": "val exact = lc.indexOfFirst" in logic and '"twitter", "tweet", "x app"' in logic,
    "X and LinkedIn message notifications opt-in only": '"com.twitter.android" to "X / Twitter"' in msg and '"com.linkedin.android" to "LinkedIn"' in msg and '"com.twitter.android"' not in msg.split("private val DEFAULT_APPS")[1].split("}")[0],
    "FeatureCheck includes message mode, schedule, lead queue and checkpoints": all(x in feature for x in ("Message mode: on/off/read/dictate commands", "Schedule: Hinglish daily weather at 8", "Leads: capture/draft/batch commands", "Offline queue: voice commands", "Agent checkpoint/history")),
    "JVM unit tests cover lead, queue, scheduled-task and checkpoint pure rules": all(x in unit_test for x in ("leadCaptureOnlyAcceptsVisiblePublicBusinessSources", "offlineQueueOnlyAllowsCloudJobsAndRejectsScreenTasks", "recurringHinglishWeatherScheduleParsesCorrectly", "checkpointEligibilityDoesNotAllowUnsafeUiReplay")),
}
for name, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + name)
print(f"SUMMARY pass={sum(checks.values())} fail={len(checks)-sum(checks.values())}")
sys.exit(0 if all(checks.values()) else 1)
