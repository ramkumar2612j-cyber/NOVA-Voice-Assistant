#!/usr/bin/env python3
"""v7.201 BLUE NEON theme contract: static check that the theme block is present, loads last, and respects reduced motion."""
import sys
s = open("app/src/main/assets/ui.html", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)
ck('id="v201-blue-neon"' in s, "blue theme block present")
ck(s.index('<style id="v201-blue-neon">') > s.rfind('</style>', 0, s.index('<style id="v201-blue-neon">')), "theme block after every earlier style block")
ck(s.index('id="v201-blue-neon"') > s.find('</script>'), "theme loads after the first script (later add-only blocks may follow)")
ck("--c1:#1aa3ff" in s and "--bg:#02060f" in s, "blue palette variables set")
ck("prefers-reduced-motion:reduce" in s, "reduced-motion users get no animation")
ck(s.count('<style id="v201-blue-neon">') == 1 and s.count('</body>') == 1, "single block, single body close")
sys.exit(1 if fails else 0)
