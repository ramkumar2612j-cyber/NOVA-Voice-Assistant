import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'app/src/main/java/com/nova/assistant'
rules = (j / 'AlternativesRules.kt').read_text()
svc = (j / 'NovaService.kt').read_text()
agent = (j / 'AgentRules.kt').read_text()
store = (j / 'PlaybookStore.kt').read_text()
test = (root / 'app/src/test/java/com/nova/assistant/AlternativesRulesTest.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
hard = set(re.findall(r'"(\w+)"', agent[agent.find('private val HARD'):agent.find('private val ACTS')]))
deny = set(re.findall(r'"(\w+)"', rules[rules.find('DENY_WORDS = setOf'):rules.find('DENY_FRAGMENTS')]))
tables = rules[rules.find('private val ROUTES'):rules.find('/** Same words as AgentRules.HARD')]
labels = set(re.findall(r'(?:app|web)\("([^"]+)"\)', tables))
bad = [l for l in labels if any(w in re.split(r'[^a-z0-9]+', l) for w in hard) or any(f in l for f in ('bank', 'wallet', 'upi', 'payment', 'paytm', 'phonepe', 'settings', 'nova', 'vending'))]
sw = svc[svc.find('private fun agentAltSwitch'):svc.find('private fun agentMark')]
run = svc[svc.find('private fun agentRun'):svc.find('/** v6.68: when a job stops early')]
do = svc[svc.find('private fun agentDo'):svc.find('private fun agentRun')]
checks = {
 'pure rules: no Android imports': 'import android' not in rules and 'object AlternativesRules' in rules,
 'max 4 switches per job': 'MAX_SWITCHES = 4' in rules and 'if (switchesDone >= MAX_SWITCHES) return null' in rules,
 'next() signature has kind, tried, playbook entries, gap': 'fun next(' in rules and 'triedThisJob' in rules and 'playbookEntries' in rules and 'failureGap' in rules,
 'next() skips tried and denied, orders by PlaybookRules.score': 'it.value.label !in tried' in rules and '!isDenied(it.value.label)' in rules and 'PlaybookRules.score' in rules,
 'every Kind has a table entry (GENERIC empty)': all(('PlaybookRules.Kind.%s to' % k) in rules for k in ('VIDEO','MUSIC','MAIL','MESSAGE','MAPS','PHOTO_AI','SHOP_LOOKUP','FORM','SEARCH','GENERIC')),
 'VIDEO youtube app -> chrome youtube.com -> google search': 'app("youtube"), web("youtube.com"), web("google.com")' in rules,
 'MAIL gmail app -> mail.google.com': 'app("gmail"), web("mail.google.com")' in rules,
 'MESSAGE whatsapp -> sms app': 'app("whatsapp")' in rules and 'app("messages")' in rules,
 'SEARCH chrome -> installed browser -> google app': 'app("chrome"), app("samsung internet"), app("firefox"), app("google")' in rules,
 'DENY words mirror AgentRules.HARD': hard and hard == deny,
 'no candidate label is denied/protected': not bad,
 'labels are plain (no scheme, slash, query)': all(re.fullmatch(r'[a-z0-9 .\-]+', l) for l in labels),
 'service: per-job reset of tried/switches/dead ends': 'agentTriedRoutes.clear(); agentRouteSwitches = 0; agentRouteDeadEnds = 0' in svc,
 'service: switch helper is try/catch and hint-only': 'catch (e: Exception) { ErrLog.add("NovaAlternatives"' in sw and 'hist.add(AlternativesRules.hintLine' in sw and 'agentNote(AlternativesRules.noteLine' in sw,
 'service: trigger 1 open_app FAILED': 'a.act == "open_app" && res.startsWith("FAILED")' in run and 'Gap.APP_MISSING' in run,
 'service: trigger 2 repeated FAILED taps': 'res.startsWith("FAILED (")' in run and 'tapGapReached(failedTaps)' in run,
 'service: trigger 3 existing stuck path': 'agentAltSwitch(AlternativesRules.Gap.STUCK' in run and 'stuckHints < 4' in run,
 'service: dead end stops honestly (FAILED, no fake success)': 'agentRouteDeadEnds >= 2' in run and 'agentFinish(AgentTaskRules.State.FAILED' in run[run.find('agentRouteDeadEnds >= 2'):run.find('agentRouteDeadEnds >= 2') + 200],
 'service: GENERIC jobs keep old behaviour (no routes -> 0)': 'if (!AlternativesRules.hasRoutes(kind)) return 0' in sw,
 'service: sites go through existing openAppOrSite, only fixed hosts, never denied': '!AlternativesRules.isDenied(a.app)' in do and 'AlternativesRules.isKnownSite(a.app)' in do and 'x = openAppOrSite(a.app)' in do,
 'service: no install call added': not re.search(r'installApp|PlayInstall|startInstall|ACTION_INSTALL', sw + do[do.find('"open_app"'):do.find('"tap", "long_tap"')]),
 'service: ledger progress line gets only a switch count': 'AlternativesRules.progressSuffix(agentRouteSwitches)' in run and 'fun progressSuffix(switches: Int)' in rules,
 'service: model still emits action, AgentRules.check untouched': 'AgentRules.check(' in run and 'Verdict.DENY, "money, account or security item"' in agent,
 'store: read-only entries() added, still no free-text API': 'fun entries(): List<PlaybookRules.Entry> = read()' in store and 'nova_playbook_v1' in store,
 'Part 2 hooks still present': 'PlaybookStore(this).record(pbKind, apps, state == AgentTaskRules.State.SUCCEEDED)' in svc and 'if (x.ok) agentRouteApps.add(a.app)' in svc,
 'old v7.103 learning hooks still present': 'CapabilityLearningStore(this).recordFailure(message)' in svc,
 'hard DENY list and protected screens untouched': '"pay", "payment", "buy"' in agent and 'BLOCKED_FRAGMENTS' in (j / 'Control.kt').read_text() and 'fun blocked(' in (j / 'Control.kt').read_text(),
 'unit test covers order, skip, max 4, deny, determinism': all(x in test for x in ('videoOrder', 'alreadyTriedRoutesAreSkipped', 'neverMoreThanFourSwitches', 'deniedAndProtected', 'deterministic', 'playbookScoreOrders')),
 'version 7.106 or newer (code 33+)': any(('versionName = "%s"' % v) in gradle for v in ('7.106', '7.107', '7.108', '7.109', '7.110')) and any(('"%s"' % c) in gradle for c in ('33', '34', '35', '36', '37')),
}
for n, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + n)
raise SystemExit(0 if all(checks.values()) else 1)
