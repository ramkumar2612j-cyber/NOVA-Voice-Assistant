#!/usr/bin/env python3
"""v7.219 TV contract (Part 3): OSCAR TV module ki wiring + honest docs + no-fake-claims checks.
Kotlin compile/JUnit yahan nahi (local_compile_check.sh / CI ka kaam)."""
import os, re, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
bad = []
P = rd('app/src/main/java/com/nova/assistant/TvProto.kt')
RM = rd('app/src/main/java/com/nova/assistant/TvRemote.kt')
C = rd('app/src/main/java/com/nova/assistant/TvCert.kt')
RU = rd('app/src/main/java/com/nova/assistant/TvRules.kt')
S = rd('app/src/main/java/com/nova/assistant/JarvisService.kt')
M = rd('app/src/main/java/com/nova/assistant/MainActivity.kt')
U = rd('app/src/main/assets/ui.html')
D = rd('docs/OSCAR_TV.md')
MF = rd('app/src/main/AndroidManifest.xml')

# --- 1) protocol constants (AOSP spec se — invent nahi kiye)
for need in ['API_PORT = 6466', 'PAIR_PORT = 6467', 'SERVICE_NAME = "atvremote"', 'STATUS_OK = 200', 'DIR_SHORT = 3']:
    if need not in RM + P: bad.append('protocol constant missing: ' + need)
# keycodes (AOSP keycodes.h)
for need in ['KEY_HOME = 3', 'KEY_BACK = 4', 'KEY_VOLUME_UP = 24', 'KEY_DPAD_CENTER = 23', 'KEY_SLEEP = 223', 'KEY_WAKEUP = 224']:
    if need not in P: bad.append('keycode missing: ' + need)

# --- 2) field numbers spec ke mutabik (polo.proto + remotemessage.proto)
for need in ['outerMessage(10', 'outerMessage(20', 'outerMessage(30', 'outerMessage(40',   # pairing_request/options/configuration/secret
             'remoteMessage(1,', 'remoteMessage(10,', 'remoteMessage(90,']:                            # configure/key_inject/app_link
    if need not in P: bad.append('proto field number wiring missing: ' + need)

# --- 3) secret algorithm comment + sha256 (polo.py wala)
if 'sha256(clientMod || clientExp || serverMod || serverExp || pin[2:]' not in P:
    bad.append('secret algorithm not implemented per spec')
if 'SHA-256' not in P: bad.append('sha256 missing in TvProto')

# --- 4) honest docs: NOT TESTED + limitations likhe hone chahiye
for need in ['NOT TESTED', 'wake word', 'Fire TV', 'webOS', 'Tizen', 'Chromecast', 'Android TV']:
    if need not in D: bad.append('docs/OSCAR_TV.md missing honest section: ' + need)
if re.search(r'100% (working|tested)', D, re.I): bad.append('docs me fake 100% claim')

# --- 5) service + bridge + ui wiring
for need in ['tvRoute(text)', 'tvRoute(text) ?: laptopHandoffRoute', 'TvRules.parse(text)']:
    if need not in S: bad.append('JarvisService missing ' + need)
for fn in ['saveTvLink', 'tvSetPin', 'tvStatus', 'clearTvLink']:
    if 'fun ' + fn not in M: bad.append('bridge missing ' + fn)
    if '"%s"' % fn not in U: bad.append('BRIDGE list missing ' + fn)
for need in ['id="tv_host"', 'id="tv_pin"', 'id="tv_save"', 'id="tv_pinbtn"', 'id="tv_del"', 'id="tv_st"', 'Android.tvSetPin', 'Android.clearTvLink']:
    if need not in U: bad.append('ui.html missing ' + need)
# bridge caps (String params)
if 'raw0.length > 100' not in M or 'raw0.length > 10' not in M: bad.append('tv bridge String params uncapped')

# --- 6) nayi permission nahi (INTERNET pehle se hai)
if '<uses-permission android:name="android.permission.INTERNET"' not in MF: bad.append('INTERNET permission missing (pehle se hona chahiye)')

# --- 7) JUnit test files (TvProtoTest me TvRulesTest bhi hai)
tvp = rd('app/src/test/java/com/nova/assistant/TvProtoTest.kt') if os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/TvProtoTest.kt')) else ''
if not tvp: bad.append('TvProtoTest missing')
if 'class TvRulesTest' not in tvp: bad.append('TvRulesTest class missing')

# --- 8) TvRules phrases quick check (python mirror)
if 'netflix' not in RU.lower() or 'hotstar' not in RU.lower(): bad.append('app deep links missing')

if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS tv contract (protocol constants + wiring + honest docs)')
