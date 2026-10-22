import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'app/src/main/java/com/nova/assistant'
rules = (j / 'PlaybookRules.kt').read_text()
store = (j / 'PlaybookStore.kt').read_text()
svc = (j / 'NovaService.kt').read_text()
agent = (j / 'AgentRules.kt').read_text()
test = (root / 'app/src/test/java/com/nova/assistant/PlaybookRulesTest.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
fin = svc[svc.find('private fun agentFinish'):svc.find('private fun agentBegin')]
store_api = re.findall(r'fun (\w+)\(([^)]*)\)', store)
checks = {
 'pure rules: no Android imports': 'import android' not in rules and 'object PlaybookRules' in rules,
 'rules bounded (routes, apps, hint, decay)': all(x in rules for x in ('MAX_ROUTES_PER_KIND = 5', 'MAX_APPS_PER_TASK = 3', 'MAX_HINT_CHARS', 'DECAY_AT')),
 'app label sanitiser rejects urls/emails/long numbers/sentences': 'fun cleanApp' in rules and 'length > 24' in rules and "count { it.isDigit() } > 4" in rules,
 'hint says it never overrides owner or safety': 'never overrides the owner' in rules and 'safety' in rules,
 'store keeps only kind + app label + counters (no free text param)': 'nova_playbook_v1' in store and not any(('task' in a.lower() or 'text' in a.lower() or 'message' in a.lower()) for _, a in store_api),
 'store re-sanitises on read': 'PlaybookRules.cleanApp(r.optString("app"))' in store,
 'service: kind set at job start from classify only': 'agentPlaybookKind = PlaybookRules.classify(task)' in svc,
 'service: only apps that really opened are noted': 'if (x.ok) agentRouteApps.add(a.app)' in svc,
 'service: per-job reset': 'agentPlaybookKind = null; agentPlaybookRecorded = false; agentRouteApps.clear()' in svc,
 'service: success and FAILED recorded once, NEEDS_OWNER/CANCELLED neutral': 'PlaybookStore(this).record(pbKind, apps, state == AgentTaskRules.State.SUCCEEDED)' in fin and 'State.NEEDS_OWNER' not in fin[fin.find('v7.105'):] and 'agentPlaybookRecorded = true' in fin,
 'service: store call wrapped in try/catch with ErrLog': 'ErrLog.add("NovaPlaybook"' in svc,
 'service: hint appended to the agent task next to capability guidance': 'if (routeHint.isNotBlank()) append("\\n").append(routeHint)' in svc,
 'old v7.103 learning hooks still present': 'CapabilityLearningStore(this).recordFailure(message)' in svc and 'CapabilityLearningStore(this).guidanceFor()' in svc,
 'hard DENY list untouched': '"pay", "payment", "buy"' in agent and 'Verdict.DENY, "money, account or security item"' in agent,
 'unit test file covers classify/clean/record/prune/hint/decay': all(x in test for x in ('classifiesCommonJobs', 'cleanAppRejects', 'successCreditsAll', 'HopelessRoutesArePruned', 'hintPrefersWorkingRoute', 'oldHistoryDecays')),
 'version 7.105 or newer (code 32+)': any(('versionName = "%s"' % v) in gradle for v in ('7.105', '7.106', '7.107', '7.108', '7.109', '7.110')) and any(('"%s"' % c) in gradle for c in ('32', '33', '34', '35', '36', '37')),
}
for n, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + n)
raise SystemExit(0 if all(checks.values()) else 1)
