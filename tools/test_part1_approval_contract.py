from pathlib import Path
root = Path(__file__).resolve().parents[1]
j = root / 'app/src/main/java/com/nova/assistant'
ext = (j / 'ExtCfg.kt').read_text()
svc = (j / 'NovaService.kt').read_text()
agent = (j / 'AgentRules.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
i = svc.find('private fun confirmStrict'); k = svc.find('private fun connectorRun')
checks = {
 'connector-write switch exists and defaults ON': 'fun skipConnectorConfirm' in ext and '"skip_connector_confirm", true)' in ext and 'catch (e: Exception) { true }' in ext[ext.find('fun skipConnectorConfirm'):],
 'connector write runs at once when switch is ON': 'if (ExtCfg.skipConnectorConfirm(this)) return connectorRun(d, act.label, ci)' in svc,
 'strict confirm path is still there when switch is OFF': 'return confirmStrict(tr("${d.name} par: $what' in svc,
 'confirmStrict itself is unchanged (no ExtCfg inside)': i > 0 and k > i and 'ExtCfg' not in svc[i:k],
 'one-time WRITE permission, connect and disconnect still ask': all(x in svc for x in ('"perm_write" -> {', '"disconnect" -> {')) and svc.count('confirmStrict(') >= 4,
 'perm_write wording is true in both modes': 'bina baar baar puchhe' in svc and 'without asking each time' in svc,
 'planner no longer demands approval for messages/forms/sign-ins': 'required local spoken approval' not in agent and 'plan them without asking for extra approval' in agent,
 'hard DENY list is untouched': all(w in agent for w in ('"pay", "payment", "buy"', '"password", "otp", "passcode"')) and 'Verdict.DENY, "money, account or security item"' in agent,
 'protected screens still blocked': 'Control.blocked(pkg, ownPkg, "tap")' in agent,
 'agent tap confirm still has its own switch': 'ExtCfg.skipRiskyConfirm(this)' in svc,
 'version is 7.104 or newer': any(('versionName = "%s"' % v) in gradle for v in ('7.104', '7.105', '7.106', '7.107', '7.108', '7.109', '7.110', '7.113', '7.114')),
}
for n, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + n)
raise SystemExit(0 if all(checks.values()) else 1)
