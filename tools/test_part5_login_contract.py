import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'app/src/main/java/com/nova/assistant'
rules = (j / 'LoginRules.kt').read_text()
svc = (j / 'NovaService.kt').read_text()
agent = (j / 'AgentRules.kt').read_text()
web = (j / 'WebOperatorRules.kt').read_text()
test = (root / 'app/src/test/java/com/nova/assistant/LoginRulesTest.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
run = svc[svc.find('private fun agentRun'):svc.find('/** v6.68: when a job stops early')]
wall = run[run.find('v7.108 PART 5: login wall'):run.find('v7.108 PART 5: login wall') + 1600]
chk = agent[agent.find('fun check('):agent.find('fun check(') + 2200]
checks = {
 'pure rules: no Android imports': 'import android' not in rules and 'object LoginRules' in rules,
 'password / code / captcha walls block, chooser does not': 'fun blocks(' in rules and 'State.PASSWORD || s == State.CODE || s == State.CAPTCHA' in rules,
 'weak code words need a code box (mailbox text is not a wall)': 'CODE_WEAK' in rules and 'secretEditable && hit(CODE_WEAK)' in rules,
 'stops only after several consecutive steps': 'WALL_STEPS = 3' in rules and 'loginWallSteps >= LoginRules.WALL_STEPS' in wall and 'loginWallSteps = 0' in wall,
 'service: wall stop is NEEDS_OWNER with the exact thing, no fake success': 'agentFinish(AgentTaskRules.State.NEEDS_OWNER, LoginRules.ownerMessage(' in wall,
 'service: wall check skipped on protected screens, try/catch with ErrLog': 'if (!shielded)' in wall and 'ErrLog.add("NovaLogin"' in svc,
 'service: secret box uses the Part 4 rule': 'WebOperatorRules.isSecretField(AgentRules.label(it))' in wall and 'fun isSecretField(' in web,
 'service: login hint added to the job text only for login/mail jobs': 'LoginRules.wantsHint(task)' in run and 'if (loginHint.isNotBlank()) append' in run,
 'service: nothing is typed or stored by the login code': 'agentType' not in wall and 'PlaybookStore' not in wall and 'SharedPreferences' not in rules,
 'AgentRules.check denies account-security taps (remove/recovery/2-step/passkey)': 'LoginRules.isAccountDanger(label(node))' in chk and 'Verdict.DENY, "account security item"' in chk,
 'Part 4 password-box typing deny still present': 'WebOperatorRules.isSecretField(label(node))' in chk,
 'hard DENY list and protected screens untouched': '"pay", "payment", "buy"' in agent and 'Verdict.DENY, "money, account or security item"' in agent and 'BLOCKED_FRAGMENTS' in (j / 'Control.kt').read_text(),
 'no install call added': not re.search(r'installApp|PlayInstall|startInstall|ACTION_INSTALL', wall + rules),
 'Parts 2-4 hooks still present': 'PlaybookStore(this).record(pbKind, apps, state == AgentTaskRules.State.SUCCEEDED)' in svc and 'agentAltSwitch(AlternativesRules.Gap.STUCK' in svc and 'WebOperatorRules.targets(task)' in svc,
 'old v7.103 learning hooks still present': 'CapabilityLearningStore(this).recordFailure(message)' in svc,
 'unit test covers password, code, mailbox, captcha/chooser, whole words, danger, hint, messages': all(x in test for x in ('passwordBoxOrPrompt', 'codeAndPhoneApproval', 'mailboxLinesAboutCodes', 'captchaIsAWall', 'wholeWordsOnly', 'accountSecurityTaps', 'hintOnlyFor', 'ownerMessagesName')),
 'version 7.108 or newer (code 35+)': any(('versionName = "%s"' % v) in gradle for v in ('7.108', '7.109', '7.110', '7.113', '7.114', '7.133', '7.134', '7.135', '7.136', '7.137', '7.138')) and any(('"%s"' % c) in gradle for c in ('35', '36', '37')),
}
for n, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + n)
raise SystemExit(0 if all(checks.values()) else 1)
