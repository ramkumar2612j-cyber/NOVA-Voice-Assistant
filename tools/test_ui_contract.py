#!/usr/bin/env python3
"""UI guards: wrapper class preserved; setup/update center never auto-opens; permissions require a tap; mode badge/provider copy stay correct."""
import re, sys, os
here = os.path.dirname(os.path.abspath(__file__))
ui = open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
svc = open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/NovaService.kt'), encoding='utf-8').read()
bad = []
if len(re.findall(r"app\.className='wrap '", ui)) != 3: bad.append("app.className must keep 'wrap ' in all 3 places")
if re.search(r"app\.className=(o|on|\(t=='on'\))\?", ui): bad.append("a bare app.className assignment is back (it wipes the wrap class)")
m = re.search(r"function ucAuto\(\)\{.*?\n\}", ui, re.S)
if m and ('ucOpen()' in m.group(0) or 'showSetup' in m.group(0) or 'missingSetup' in m.group(0)): bad.append("ucAuto must never open guided setup automatically")
if not m and ("function ucAuto(" in ui or "setTimeout(ucAuto" in ui): bad.append("automatic setup hook must be removed")
if m and any(x in m.group(0) for x in ('Android.requestBasics()', 'Android.requestContacts()', 'Android.openAccessibility()')): bad.append("ucAuto must not request permissions before an explicit tap")
if "var SETUP_ORDER=['mic','notif','acc','overlay','notifAccess','contacts','writeSettings','battery']" not in ui: bad.append("guided permission sequence missing or changed unexpectedly")
if 'SETUP_ORDER.length' not in ui: bad.append("Update Center Next button must guide remaining permissions")
if re.search(r"function ucMaybe\(\)\{[^\n]*ucOpen\(\)", ui): bad.append("ucMaybe must not call ucOpen()")
if 'id="fix-scroll"' not in ui or '#mode{display:none!important}' not in ui: bad.append("fix-scroll block / hidden mode badge missing")
for pat in ('"Gemini ne suna', '"Groq: ', 'Groq se sunna nahi ho paya', 'Gemini ka jawab samajh nahi aaya'):
    if pat in svc: bad.append("provider name in a user-visible line: " + pat)
if bad:
    print('\n'.join('FAIL ' + b for b in bad)); sys.exit(1)
print('ALL PASSED')
