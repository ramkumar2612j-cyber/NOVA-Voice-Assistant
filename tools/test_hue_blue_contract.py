#!/usr/bin/env python3
"""v7.208 HUE BLUE contract: the page has the hue-rotate block after the blue theme block."""
import sys
s = open("app/src/main/assets/ui.html", encoding="utf-8").read()
ok = 'id="v208-hue-blue"' in s and 'hue-rotate(175deg)' in s and s.index('id="v208-hue-blue"') > s.index('id="v201-blue-neon"')
print(("PASS " if ok else "FAIL ") + "hue-rotate block present, after blue theme")
sys.exit(0 if ok else 1)
