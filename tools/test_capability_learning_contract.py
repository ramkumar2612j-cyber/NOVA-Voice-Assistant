import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
rules = (root / 'app/src/main/java/com/nova/assistant/CapabilityLearningRules.kt').read_text()
store = (root / 'app/src/main/java/com/nova/assistant/CapabilityLearningStore.kt').read_text()
service = (root / 'app/src/main/java/com/nova/assistant/JarvisService.kt').read_text()
agent = (root / 'app/src/main/java/com/nova/assistant/AgentRules.kt').read_text()
checks = {
 'failure categories are bounded': 'MAX_RECORDS = 24' in rules,
 'only coarse categories are persisted': 'put("gap", it.gap.key)' in store and 'put("reason"' not in store and 'put("task"' not in store,
 'previous lessons are added to the agent task context': 'CapabilityLearningStore(this).guidanceFor()' in service and 'learnedGuidance' in service,
 'failure outcome records a lesson once per task': 'agentFailureRecorded' in service and 'recordFailure(message)' in service,
 'agent is instructed to try a different safe route before giving up': 'RECOVERY-FIRST' in agent and 'materially different SAFE route' in agent,
 'does not claim imaginary skills or bypass Android permissions': 'Never claim you installed a skill' in agent and 'Android requires' in agent,
 'unit tests added': (root / 'app/src/test/java/com/nova/assistant/CapabilityLearningRulesTest.kt').exists(),
 'version updated': bool(re.search(r'versionName = "7\.(1[0-9][0-9]|2[0-9][0-9])"', (root / 'app/build.gradle.kts').read_text())),
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
raise SystemExit(0 if all(checks.values()) else 1)
