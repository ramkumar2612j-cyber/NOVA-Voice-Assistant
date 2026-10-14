#!/usr/bin/env python3
"""v6.95 ROZ KA SUDHAAR contract: in-app daily self-improve is non-Gemini, 1 hour per day, proposals only (owner approves), undoable."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = "app/src/main/java/com/nova/assistant/"
rd = lambda p: open(os.path.join(R, p), encoding="utf-8").read()
rules, store, run = rd(J + "SelfImproveRules.kt"), rd(J + "SelfImproveStore.kt"), rd(J + "SelfImprove.kt")
main, svc, ui, ext = rd(J + "MainActivity.kt"), rd(J + "NovaService.kt"), rd("app/src/main/assets/ui.html"), rd(J + "ExtCfg.kt")
bad = 0
def check(n, c):
    global bad
    print(("PASS " if c else "FAIL ") + n)
    if not c: bad += 1
code = lambda s: re.sub(r"/\*.*?\*/|//[^\n]*", "", s, flags=re.S)
check("no Gemini call in the self-improve code", not re.search(r"(?i)gemini|generativelanguage", code(run) + code(store)) and "gemini" in rules.lower())
check("only owner-key providers (never anonymous)", "it.keyName != null" in rules and "SelfImproveRules.allowedProviders(FreeProviders.ordered())" in run)
check("daily budget and window are exactly 1 hour", rules.count("60L * 60L * 1000L") >= 2 and "WINDOW_MS" in rules and "DAILY_BUDGET_MS" in rules)
check("default is OFF", 'getBoolean("self_improve", false)' in ext)
check("a proposal is applied only by the bridge HAAN call", len(re.findall(r"SelfImproveStore\.apply\(", main)) == 1 and "SelfImproveStore.apply(" not in svc and "SelfImproveStore.apply(" not in run)
check("the runner only records pending proposals", "recordRun(" in run and "TeachStore" not in code(run))
check("apply remembers the old value and revert restores it", "prev" in store and "fun revert" in store and "TeachStore.delete" in store)
check("only teach-aliases can change (no settings, keys, contacts)", "TeachStore.add" in store and not re.search(r"SecureStore\.(set|save)|ContactLookup|ExtCfg\.set", code(store) + code(run)))
check("service loop does not use the network by itself", "selfImproveLoop" in svc and "SelfImprove.run(" in svc)
check("UI has HAAN / NAHI / WAPAS", all(w in ui for w in ("applySelfImprove", "rejectSelfImprove", "revertSelfImprove", "ROZ KA SUDHAAR")))
check("only app names are sent, no contacts / errors / messages", "ErrLog" not in code(rules) and "userPrompt(apps, have)" in run and "ContactLookup" not in run)
sys.exit(1 if bad else 0)
