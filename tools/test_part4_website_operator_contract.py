import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'app/src/main/java/com/nova/assistant'
rules = (j / 'WebOperatorRules.kt').read_text()
svc = (j / 'NovaService.kt').read_text()
agent = (j / 'AgentRules.kt').read_text()
test = (root / 'app/src/test/java/com/nova/assistant/WebOperatorRulesTest.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
hard = set(re.findall(r'"(\w+)"', agent[agent.find('private val HARD'):agent.find('private val ACTS')]))
deny = set(re.findall(r'"(\w+)"', rules[rules.find('DENY_WORDS = setOf'):rules.find('/** Bank / wallet')]))
run = svc[svc.find('private fun agentRun'):svc.find('/** v6.68: when a job stops early')]
do = svc[svc.find('private fun agentDo'):svc.find('private fun agentRun')]
chk = agent[agent.find('fun check('):agent.find('fun check(') + 1600]
checks = {
 'pure rules: no Android imports': 'import android' not in rules and 'object WebOperatorRules' in rules,
 'DENY words mirror AgentRules.HARD': bool(hard) and hard == deny,
 'only http/https accepted, always upgraded to https': 'hasScheme -> return null' in rules and 'lower.contains(\':\') -> return null' in rules and '"https://" + authority + tail' in rules,
 'rejects credentials, ports, IP numbers, localhost/.local': "authority.contains('@') || authority.contains(':')" in rules and 'isDigit()' in rules and 'BAD_TLDS' in rules,
 'bare name needs a common TLD': 'tld !in TLDS' in rules,
 'bank/payment hosts and HARD words in host or path are denied': 'fun isDenied(' in rules and 'DENY_PARTS' in rules and 'pt.any { it in DENY_WORDS }' in rules,
 'secret boxes (password/OTP/card/UPI PIN) never typed into': 'fun isSecretField(' in rules and 'WebOperatorRules.isSecretField(label(node))' in chk and 'Verdict.DENY, "password or code box"' in chk,
 'prompt-injection page text flagged as untrusted data': 'fun injectionHits(' in rules and 'WebOperatorRules.injectionHits(' in run and 'WebOperatorRules.injectionLine()' in run and 'MAX_INJECTION_WARNINGS' in rules,
 'service: hosts come only from the owner job text, memory only': 'WebOperatorRules.targets(task)' in run and 'agentOwnerHosts.clear()' in svc and 'private val agentOwnerHosts' in svc,
 'service: per-job reset': svc.count('agentOwnerHosts.clear()') >= 2,
 'service: pre-open only on a fresh job, via existing openChromeTab': 'openChromeTab(first.url, first.host)' in run and '(before the interruption)' in run[run.find('openChromeTab(first.url') - 600:run.find('openChromeTab(first.url')],
 'service: denied site is not opened and is said honestly': 'WebOperatorRules.deniedLine()' in run and 'WebOperatorRules.deniedNote(' in run and 'val webOpenable = webTargets.filter { !it.denied }' in run,
 'service: open_app fallback only for fixed or owner-named hosts, never denied, existing openAppOrSite': 'WebOperatorRules.isOwnerHost(a.app' in do and '!AlternativesRules.isDenied(a.app)' in do and 'openAppOrSite(a.app)' in do,
 'service: website hint added to the job text': 'if (webHint.isNotBlank()) append' in run,
 'service: all new hooks are try/catch with ErrLog': svc.count('ErrLog.add("NovaWebOperator"') >= 3,
 'service: no install call added': not re.search(r'installApp|PlayInstall|startInstall|ACTION_INSTALL', run[run.find('v7.107 PART 4'):] + do),
 'AgentRules.check still the final vet': 'AgentRules.check(a, node, pkg, packageName)' in run and 'Verdict.DENY, "money, account or security item"' in agent,
 'hard DENY list and protected screens untouched': '"pay", "payment", "buy"' in agent and 'BLOCKED_FRAGMENTS' in (j / 'Control.kt').read_text(),
 'Part 2 + Part 3 hooks still present': 'PlaybookStore(this).record(pbKind, apps, state == AgentTaskRules.State.SUCCEEDED)' in svc and 'agentAltSwitch(AlternativesRules.Gap.STUCK' in svc and 'AlternativesRules.progressSuffix(agentRouteSwitches)' in svc,
 'old v7.103 learning hooks still present': 'CapabilityLearningStore(this).recordFailure(message)' in svc,
 'unit test covers parse, reject, tld, targets, deny, owner host, secret, injection, hint': all(x in test for x in ('parsesPlainDomains', 'rejectsDangerous', 'bareNameNeeds', 'targetsFindAtMostTwo', 'bankAndPayment', 'ownerHostOnly', 'secretBoxes', 'flagsPageText', 'hintMentions')),
 'version 7.107 or newer (code 34+)': any(('versionName = "%s"' % v) in gradle for v in ('7.107', '7.108', '7.109', '7.110')) and any(('"%s"' % c) in gradle for c in ('34', '35', '36', '37')),
}
for n, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + n)
raise SystemExit(0 if all(checks.values()) else 1)
