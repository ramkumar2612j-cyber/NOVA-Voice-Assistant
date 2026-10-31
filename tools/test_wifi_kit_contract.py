#!/usr/bin/env python3
"""v7.153 contract: WIFI KIT (legal Wi-Fi tools) is fully wired - rules + store + Android kit + voice routing +
permissions + settings switch + MADAD topic + JUnit tests - and the LEGAL LINE holds: no attack tooling anywhere.
Add-only: nothing from v7.149-152 may be missing."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, '..')
J = os.path.join(R, 'app/src/main/java/com/nova/assistant')
T = os.path.join(R, 'app/src/test/java/com/nova/assistant')
def rd(p):
    return open(p, encoding='utf8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)

rules = rd(os.path.join(J, 'WifiKitRules.kt'))
kit = rd(os.path.join(J, 'WifiKit.kt'))
store = rd(os.path.join(J, 'WifiKitStore.kt'))
test = rd(os.path.join(T, 'WifiKitTest.kt')) if os.path.exists(os.path.join(T, 'WifiKitTest.kt')) else ''
logic = rd(os.path.join(J, 'Logic.kt'))
ns = rd(os.path.join(J, 'NovaService.kt'))
ma = rd(os.path.join(J, 'MainActivity.kt'))
ext = rd(os.path.join(J, 'ExtCfg.kt'))
fs = rd(os.path.join(J, 'FeatureStatus.kt'))
hg = rd(os.path.join(J, 'HelpGuide.kt'))
ui = open(os.path.join(R, 'app/src/main/assets/ui.html'), encoding='utf8').read()
manifest = rd(os.path.join(R, 'app/src/main/AndroidManifest.xml'))
gradle = rd(os.path.join(R, 'app/build.gradle.kts'))

# 1. Files exist and the rules layer stays PURE Kotlin (no Android imports -> unit testable)
for f in ['WifiKitRules.kt', 'WifiKit.kt', 'WifiKitStore.kt']:
    need(os.path.exists(os.path.join(J, f)), f + ' missing')
need('import android' not in rules, 'WifiKitRules.kt must stay pure Kotlin (no android imports)')
need('import android.content.Context' in kit, 'WifiKit.kt is the Android layer')

# 2. Kinds + legal line: the forbidden-words scan covers ALL three Wi-Fi sources (comments included)
for k in ['wifi_scan', 'wifi_best', 'wifi_switch', 'wifi_open', 'wifi_qr_make', 'wifi_qr_read',
          'wifi_fix', 'wifi_save_pass', 'wifi_status', 'wifi_guard_on', 'wifi_guard_off', 'wifi_forget']:
    need('"%s"' % k in rules, 'WifiKitRules kind missing: ' + k)
src_all = (rules + kit + store).lower()
for w in ['crack', 'brute', 'wordlist', 'aircrack', 'reaver', 'wifite', 'pin attack', 'wps attack', 'handshake grab']:
    need(w not in src_all, 'FORBIDDEN attack word in the Wi-Fi sources: ' + w)
need('joined only when the owner supplies the password' in rules.replace('\n', ' ').replace('  ', ' ') or 'owner supplies the password' in rules,
     'WifiKitRules must state the legal line (owner-supplied password only)')

# 3. Voice routing: Logic.classify -> WifiKitRules.parse (before the old wifi on/off rule), NovaService dispatch
need('WifiKitRules.parse(text)?.let { return it }' in logic, 'Logic.classify must call WifiKitRules.parse(text)')
i_parse = logic.find('WifiKitRules.parse(text)')
i_onoff = logic.find('if (size <= 3 && has("wifi", "bluetooth")')
need(0 < i_parse < i_onoff, 'WifiKitRules.parse must run BEFORE the old wifi on/off settings rule')
need('in WifiKitRules.KINDS -> WifiKit.handle(this, c, cfg.lang == "en")' in ns, 'NovaService.runCommand must dispatch the wifi kinds')
need('WifiKit.Guard.start(this)' in ns and 'WifiKit.Guard.stop()' in ns, 'NovaService must start/stop WifiKit.Guard')

# 4. Store: passwords only via SecureStore (Keystore AES-GCM), bounded list, tmp+rename save
need('SecureStore.saveSlot' in store and 'SecureStore.getSlot' in store and 'SecureStore.clearSlot' in store,
     'WifiKitStore must keep passwords in SecureStore (Keystore-encrypted slots)')
need('private const val MAX = 12' in store, 'WifiKitStore list must stay bounded (MAX 12)')
need('.tmp' in store, 'WifiKitStore must save via tmp+rename')

# 5. Android kit: scan + honest permission handling, suggestion API (29+) + legacy path, panel, portal probe
need('ACCESS_FINE_LOCATION' in kit, 'WifiKit must check the Location permission before scanning')
need('WifiNetworkSuggestion' in kit and 'addNetworkSuggestions' in kit, 'WifiKit needs the API 29+ suggestion path')
need('WifiConfiguration' in kit, 'WifiKit needs the legacy (API 28-) path')
need('Settings.Panel.ACTION_WIFI' in kit, 'WifiKit must open the system Wi-Fi panel (the owner taps, NOVA never forces)')
need('connectivitycheck.gstatic.com/generate_204' in kit, 'WifiKit needs the captive-portal 204 probe')
need('QRCodeWriter' in kit and 'QRCodeReader' in kit, 'WifiKit needs zxing QR make+read')
need('WIFI:T:WPA' not in kit, 'QR payload strings live in WifiKitRules only (one place)')
need('tryQrShare' in kit and 'WifiKitStore.save' in kit, 'QR share flow must store via WifiKitStore')
need('ExtCfg.setWifiGuard' in kit and 'ExtCfg.wifiGuard(ctx)' in kit, 'guard tick must read the ExtCfg switch')
need('guardDecide' in rules and 'guardDecide' in kit, 'guard must decide through the pure rules (cooldown tested)')

# 6. Permissions in the manifest (with the neverForLocation flag) + the setup chain round
for p in ['android.permission.ACCESS_WIFI_STATE', 'android.permission.CHANGE_WIFI_STATE',
          'android.permission.ACCESS_FINE_LOCATION', 'android.permission.NEARBY_WIFI_DEVICES',
          'android.permission.CHANGE_NETWORK_STATE']:
    need(p in manifest, 'manifest missing ' + p)
need('usesPermissionFlags="neverForLocation"' in manifest, 'NEARBY_WIFI_DEVICES must be declared neverForLocation')
need('Manifest.permission.ACCESS_FINE_LOCATION' in ma, 'MainActivity must ask the Location permission in the first-run dialog')
chain = rd(os.path.join(J, 'SetupChainRules.kt'))
need('const val VERSION = 3' in chain, 'SetupChainRules.VERSION must be 3 (new permission round for the Wi-Fi scan location)')

# 7. Settings: ui checkbox <-> get/saveSettings <-> ExtCfg (opt-in, default OFF, like v7.152 did for lightPopup)
need('id="s_wguard"' in ui, 'ui.html: s_wguard checkbox missing')
need('wifiGuard:$(\'s_wguard\').checked' in ui, 'ui.html: collect() must send wifiGuard')
need("$('s_wguard').checked=!!S.wifiGuard" in ui, 'ui.html: fillPanel must read S.wifiGuard')
need('default OFF' in ui.split('s_wguard')[1][:200], 'ui.html: the WIFI GUARD row must say default OFF')
need('.put("wifiGuard", ExtCfg.wifiGuard(this@MainActivity))' in ma, 'MainActivity.getSettings: wifiGuard missing')
need('ExtCfg.setWifiGuard(this@MainActivity, o.optBoolean("wifiGuard", false))' in ma, 'MainActivity.saveSettings: wifiGuard missing (default false = opt-in kept)')
need('fun setWifiGuard' in ext and 'getBoolean(WIFI_GUARD, false)' in ext, 'ExtCfg.wifiGuard get/set missing (default OFF)')

# 8. Feature status + help: the wifi row, the topic in HelpGuide AND the MADAD CAPS list (equal lists)
need('row(a, "wifi", "WiFi Kit (scan, switch, QR, doctor)"' in fs, 'FeatureStatus wifi row missing')
need('Topic("wifi", "WiFi Kit (scan, switch, QR, doctor)"' in hg, 'HelpGuide wifi topic missing')
need('"id": "wifi"' in ui and '"ex": "nova wifi scan karo"' in ui, 'ui.html CAPS wifi entry missing')
import re, json
kt = re.findall(r'Topic\("([a-z]+)", "([^"]*)", "[^"]*", listOf\([^)]*\), "([^"]*)"', hg)
m = re.search(r'var CAPS=(\[.*?\]);\n', ui, re.S)
caps = json.loads(m.group(1)) if m else []
need([(a, c) for a, b, c in kt] == [(c['id'], c['ex']) for c in caps], 'HelpGuide topics and ui.html CAPS differ (ids/examples)')
need([(a, b) for a, b, c in kt] == [(c['id'], c['t']) for c in caps], 'HelpGuide titles and ui.html CAPS titles differ')

# 9. Share/pick hook: the QR photo can arrive via Share -> NOVA or the MEDIA picker
need(ma.count('WifiKit.tryQrShare') >= 2, 'MainActivity must call WifiKit.tryQrShare for shares AND picks')
need('shareImageUris' in ma, 'MainActivity.shareImageUris helper missing')

# 10. Gradle: zxing core for the QR work + the version bump
need('com.google.zxing:core:3.5.3' in gradle, 'app/build.gradle.kts: zxing core missing')
need(any(('versionName = "%s"' % v) in gradle for v in ('7.153', '7.154')), 'versionName must be 7.153 or 7.154')

# 11. JUnit tests cover the rules (names pinned so future edits keep the guarantees)
for tname in ['securityTypesAreClassified', 'scanReplySortsDedupsAndLimits', 'bestSwitchPicksStrongestCandidateWithGain',
              'bestSwitchRefusesSmallGain', 'guardDecideRespectsTheCooldown', 'qrPayloadRoundTripWithEscapes',
              'parseQrRejectsNonWifiText', 'doctorExplainsEveryHonestState', 'passwordSaveParsesAndKeepsCase',
              'passwordQuestionsNeverSave', 'plainWifiOnOffAndSettingsNeverMatchTheKit',
              'negatedAndUnrelatedSentencesNeverMatch', 'rulesSourceStaysFreeOfAttackWords']:
    need(tname in test, 'WifiKitTest missing ' + tname)

if bad:
    print('FAIL v7153 wifi kit contract'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS v7153 wifi kit contract (legal tools, fully wired)')
