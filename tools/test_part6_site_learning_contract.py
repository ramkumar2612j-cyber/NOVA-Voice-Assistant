import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'app/src/main/java/com/nova/assistant'
rules = (j / 'SiteLearningRules.kt').read_text()
store = (j / 'SiteLearningStore.kt').read_text()
svc = (j / 'NovaService.kt').read_text()
cap = (j / 'CapabilityLearningRules.kt').read_text()
login = (j / 'LoginRules.kt').read_text()
test = (root / 'app/src/test/java/com/nova/assistant/SiteLearningRulesTest.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
fin = svc[svc.find('private fun agentFinish'):svc.find('private fun agentBegin')]
learn = svc[svc.find('private fun agentSiteLearn'):svc.find('private fun agentFinish')]
run = svc[svc.find('private fun agentRun'):svc.find('/** v6.68: when a job stops early')]
wall = run[run.find('v7.108 PART 5: login wall'):run.find('v7.108 PART 5: login wall') + 1900]
persisted = re.findall(r'\.put\("(\w+)"', store)
checks = {
 'pure rules: no Android imports': 'import android' not in rules and 'object SiteLearningRules' in rules,
 'store keeps only key / counters / time (no task, url, text, reason)': set(persisted) <= {'key', 'ok', 'sw', 'last', 'b'} and 'put("task"' not in store and 'put("url"' not in store and 'put("text"' not in store,
 'host label: <= 24 chars via cleanApp, no path / query, denied hosts never stored': 'PlaybookRules.cleanApp(h)' in rules and 'WebOperatorRules.isDenied(clean)' in rules,
 'stored keys are re-validated on read': 'SiteLearningRules.cleanKey(' in store,
 'bounded: entries, sites per job, hint chars, decay': all(x in rules for x in ('MAX_ENTRIES = 16', 'MAX_SITES_PER_JOB = 2', 'MAX_HINT_CHARS = 520', 'DECAY_AT = 40')),
 'blockers are coarse kinds only': all(x in rules for x in ('PASSWORD("password")', 'CODE("code")', 'CAPTCHA("captcha")', 'APP_MISSING("app_missing")', 'OTHER("other")')),
 'hint is advice only and never says to type a password': 'never overrides' in rules and 'do not retry the login' in rules and not re.search(r'(type|enter|fill) (the |your )?password', rules.replace('Never type', '')),
 'hint has no install instruction': 'never install automatically' in rules and not re.search(r'installApp|PlayInstall|startInstall|ACTION_INSTALL', rules + learn + wall),
 'store API: record / hintFor / summary / clear': all(('fun ' + f) in store for f in ('record(', 'hintFor(', 'summary(', 'clear(')),
 'service: hint added to the job text': 'SiteLearningStore(this).hintFor(' in run and 'if (siteHint.isNotBlank()) append' in run,
 'service: hint uses only owner-named hosts of this job + the job kind': 'hintFor(webOpenable.map { it.host }, PlaybookRules.classify(task))' in run,
 'service: recorded once per job, only when a playbook kind exists': 'agentSiteRecorded = true' in learn and 'val kind = agentPlaybookKind ?: return' in learn and 'agentSiteLearn(state)' in fin,
 'service: SUCCEEDED / FAILED / NEEDS_OWNER-with-blocker recorded, CANCELLED neutral': 'State.SUCCEEDED || state == AgentTaskRules.State.FAILED || (state == AgentTaskRules.State.NEEDS_OWNER && agentBlocker != null)' in learn,
 'service: login wall stop sets the blocker before NEEDS_OWNER': 'agentBlocker = SiteLearningRules.blockerFor(st)' in wall and wall.find('agentBlocker = ') < wall.find('agentFinish(AgentTaskRules.State.NEEDS_OWNER'),
 'service: dead-end missing app sets APP_MISSING': 'agentBlocker = SiteLearningRules.Blocker.APP_MISSING' in run,
 'service: reset per job': 'agentBlocker = null; agentSiteRecorded = false' in svc,
 'service: try/catch with ErrLog': 'ErrLog.add("NovaSiteLearning"' in learn,
 'service: nothing typed or stored with raw text by the learning code': 'agentType' not in learn and 'task' not in learn.replace('playbook kind', ''),
 'capability learning: LOGIN gap for the Part 5 stop messages': 'LOGIN("login")' in cap and 'Gap.LOGIN' in cap and '"sign in ke liye", "sign in needs", "captcha"' in cap and 'sign in' in login.lower(),
 'Part 2 neutral NEEDS_OWNER rule unchanged': 'PlaybookStore(this).record(pbKind, apps, state == AgentTaskRules.State.SUCCEEDED)' in fin and 'State.NEEDS_OWNER' not in fin[fin.find('v7.105'):],
 'Parts 1-5 hooks still present': all(x in svc for x in ('CapabilityLearningStore(this).recordFailure(message)', 'agentAltSwitch(AlternativesRules.Gap.STUCK', 'WebOperatorRules.targets(task)', 'LoginRules.WALL_STEPS', 'LoginRules.wantsHint(task)')),
 'HARD DENY list and protected screens untouched': '"pay", "payment", "buy"' in (j / 'AgentRules.kt').read_text() and 'BLOCKED_FRAGMENTS' in (j / 'Control.kt').read_text(),
 'unit tests cover site label, bank deny, counters, bounds, decay, hints, keys, summary, login gap': all(x in test for x in ('siteLabelIsHostOnly', 'bankAndPaymentHostsAreNeverStored', 'recordCountsOkAndBlockersPerKindAndSite', 'atMostTwoSitesPerJobAndBoundedEntries', 'countersDecayAndStayCapped', 'hintWarnsEarlyAboutRepeatedWall', 'hintIsQuietWhenSiteMostlyWorks', 'missingAppAndSwitchHintsComeFromTheKind', 'storedKeysMustStayClean', 'summaryHasNoRawText', 'loginStopMessagesBecomeLoginGap')),
 'version 7.109 or newer (code 36+)': any(('versionName = "%s"' % v) in gradle for v in ('7.109', '7.110', '7.113')) and any(('"%s"' % c) in gradle for c in ('36', '37')),
}
for n, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + n)
raise SystemExit(0 if all(checks.values()) else 1)
