#!/usr/bin/env python3
"""Part 10 guard (add-only): every @JavascriptInterface bridge fun is used by ui.html, every Logic.classify Cmd kind has a handler in NovaService,
run_all_checks.sh cannot be blocked by known-stale guards, and the owner setup guide exists."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
J = R/"app/src/main/java/com/nova/assistant"
ui = (R/"app/src/main/assets/ui.html").read_text(encoding="utf-8")
ma = (J/"MainActivity.kt").read_text(encoding="utf-8")
lg = (J/"Logic.kt").read_text(encoding="utf-8"); sv = (J/"NovaService.kt").read_text(encoding="utf-8")
bad = []
for f in re.findall(r'@JavascriptInterface\s+fun\s+(\w+)', ma):
    if not re.search(r'\b'+f+r'\b', ui): bad.append("bridge fun not in ui.html: "+f)
for k in sorted(set(re.findall(r'Cmd\("([a-z_]+)"', lg))):
    if ('"%s" ->' % k) not in sv and ('"%s",' % k) not in sv: bad.append("voice kind without handler: "+k)
if '[ "$fail" -le "$stale" ]' not in (R/"tools/run_all_checks.sh").read_text(): bad.append("run_all_checks.sh can block CI on stale guards")
if not (R/"OWNER_SETUP_AND_APK_STEPS.md").exists(): bad.append("OWNER_SETUP_AND_APK_STEPS.md missing")
print("FAIL: " + "; ".join(bad) if bad else "PASS part10 feature map"); sys.exit(1 if bad else 0)
