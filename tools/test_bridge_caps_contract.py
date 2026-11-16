#!/usr/bin/env python3
"""Part 9: every @JavascriptInterface method with a String parameter must cap its length
(.take(N) / .length > N / ChatSessionRules.validId). Static check, no allowlist."""
import re, sys
src = open("app/src/main/java/com/nova/assistant/MainActivity.kt", encoding="utf-8").read().split("\n")
fails, checked = [], 0
for i, l in enumerate(src):
    if "@JavascriptInterface" not in l:
        continue
    j = i + 1
    m = re.match(r"\s*fun (\w+)\(([^)]*)\)", src[j])
    if not m:
        fails.append((j + 1, "unparsed signature")); continue
    name, params = m.groups()
    k = j + 1
    while k < len(src) and "@JavascriptInterface" not in src[k]:
        k += 1
    body = "\n".join(src[j:k])
    for p in [x.strip() for x in params.split(",") if x.strip()]:
        pn, ty = [t.strip() for t in p.split(":", 1)]
        if ty != "String":
            continue
        checked += 1
        ok = re.search(r"\b" + re.escape(pn) + r"\.(trim\(\)\.)?take\(|\b" + re.escape(pn) + r"\.length\s*>|validId\(\s*" + re.escape(pn), body)
        if not ok:
            fails.append((j + 1, name + "(" + pn + ") uncapped"))
for f in fails:
    print("FAIL line %d: %s" % f)
print("bridge String params checked: %d, uncapped: %d" % (checked, len(fails)))
sys.exit(1 if fails else 0)
