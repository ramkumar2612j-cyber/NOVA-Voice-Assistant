#!/usr/bin/env python3
"""v7.116 UI COMPLETION contract (static + headless render). Checks: every new SKILLS entry exists, MADAD topics equal Kotlin, new bridge methods exist and are
used by the page, ExtCfg setters exist, the missing HOME icon is fixed, the reel start is locked, and the new Settings card really calls the bridge.
Render part skips (exit 0) when playwright / chromium is missing. NOT a phone test."""
import os, re, sys, json
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
U = rd('app/src/main/assets/ui.html'); K = rd('app/src/main/java/com/nova/assistant/HelpGuide.kt')
M = rd('app/src/main/java/com/nova/assistant/MainActivity.kt'); E = rd('app/src/main/java/com/nova/assistant/ExtCfg.kt'); N = rd('app/src/main/java/com/nova/assistant/NovaService.kt')
bad = []
reg = U[U.index('function skRegistry'):U.index('function skRun')]
for t in ['Reels / Instagram (scroll', 'Instagram dost + Friend watch', 'Family numbers (Papa', 'Koi bhi website par kaam (Website operator)', 'Pehle se login wale kaam',
          'Doosra raasta (app nahi mila', 'NOVA ne kya seekha (Playbook', 'Website bina AI key ke', 'Movie ka naam (scene se)', 'TV / horn se galat wake', '"Samajh nahi aaya" kam karna',
          'Poochna aur sunna (haan / na ke switch)']:
    if "t:'" + t not in reg: bad.append('SKILLS entry missing: ' + t)
for i in set(re.findall(r"go\('([a-z_0-9]+)'\)", reg)):
    if 'id="%s"' % i not in U: bad.append('go() target missing: ' + i)
for t in ['reels', 'familynum', 'webjobs']:
    if 'Topic("%s"' % t not in K: bad.append('Kotlin topic missing ' + t)
    if '"id": "%s"' % t not in U: bad.append('CAPS topic missing ' + t)
if '"reels", "familynum", "webjobs", "callanswer", "calendar")' not in K: bad.append('new topics must be PRIORITY (media topic also has the key "reel")')
for f in ['getAskPrefs', 'setAskPref']:
    if 'fun %s(' % f not in M: bad.append('bridge missing ' + f)
    if f not in U: bad.append('page does not use ' + f)
for f in ['setSkipRiskyConfirm', 'setSkipOrderConfirm', 'setSkipConnectorConfirm']:
    if 'fun %s(' % f not in E: bad.append('ExtCfg missing ' + f)
    if f not in M: bad.append('MainActivity does not call ' + f)
# defaults must not change (owner choice since v6.32 / v6.38 / v7.104 / v7.73)
for k in ['skip_risky_confirm", true', 'skip_order_confirm", true', 'skip_connector_confirm", true', 'busy_listen", true']:
    if k not in E: bad.append('default changed: ' + k)
if 'IC[f[0]]||IC.bolt' not in U: bad.append('HOME chip icon fallback missing')
first_ic = U[U.index('var IC={'):U.index('var IC={') + 60]
if 'globe:' not in first_ic: bad.append('HOME IC has no globe icon (PROFILE SEARCH chip)')
if 'synchronized(reelLock)' not in N: bad.append('reel start is not locked')
if not os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/UiCompletion116Test.kt')): bad.append('JUnit file missing')
# Settings card: ask = opposite of skip; unknown key refused
if '"riskyAsk" -> ExtCfg.setSkipRiskyConfirm(this@MainActivity, !on)' not in M: bad.append('riskyAsk must store the opposite of skip')
if 'else -> return "unknown"' not in M: bad.append('setAskPref must refuse unknown keys')
if bad:
    [print('FAIL', x) for x in bad]; sys.exit(1)
print('PASS static v7.116 ui completion')
# ---------------- render ----------------
try:
    from playwright.sync_api import sync_playwright
except Exception:
    print('SKIP render (no playwright)'); sys.exit(0)
BR = """(function(){var S={busy:true,riskyAsk:false,orderAsk:false,connectorAsk:false},C=[];window.__calls=C;
var R={getStatus:'{}',getSettings:'{}',getBrowserStatus:'{}',getConnectors:'[]',listChats:'[]',getTier:'high',getAutoReply:'{}',getSelfImproveCfg:'{}',getWaitSec:5,getFamilyJson:'{"papa":"9876543210","mummy":"","others":[]}'};
window.Android=new Proxy({},{get:function(t,k){if(k==='getAskPrefs')return function(){return JSON.stringify(S)};
if(k==='setAskPref')return function(a,b){C.push([a,b]);S[a]=b;return 'ok'};
return function(){return (k in R)?R[k]:''}}})})();"""
bad = []
with sync_playwright() as p:
    try: b = p.chromium.launch()
    except Exception: print('SKIP render (no chromium)'); sys.exit(0)
    pg = b.new_context(viewport={'width': 360, 'height': 700}).new_page(); pg.add_init_script(BR)
    pg.on('pageerror', lambda e: bad.append('js error: ' + str(e)[:120]))
    pg.on('console', lambda m: bad.append('console: ' + m.text[:120]) if m.type == 'error' else None)
    pg.goto('file://' + os.path.abspath(os.path.join(R, 'app/src/main/assets/ui.html')))
    try: pg.wait_for_function('document.body.classList.contains("ready")', timeout=30000)
    except Exception: bad.append('never ready')
    pg.wait_for_timeout(600)
    st = pg.evaluate('["pt116_busy","pt116_risky","pt116_order","pt116_conn"].map(function(i){return document.getElementById(i).checked})')
    if st != [True, False, False, False]: bad.append('initial state wrong: %s' % st)
    pg.evaluate('document.getElementById("pt116_risky").click();document.getElementById("pt116_busy").click()')
    calls = pg.evaluate('window.__calls')
    if calls != [['riskyAsk', True], ['busy', False]]: bad.append('bridge calls wrong: %s' % calls)
    # skills: new cards render and the search finds them
    pg.evaluate('(function(){var t=document.querySelectorAll("#tabs .tb");for(var i=0;i<t.length;i++){if(t[i].textContent.toUpperCase().indexOf("SKILLS")>=0){t[i].click();break}}})()'); pg.wait_for_timeout(500)
    for q, need in [('reel', 2), ('family', 1), ('login', 1), ('wake', 1), ('poochna', 1)]:
        pg.fill('#sk_q', q); pg.wait_for_timeout(300)
        n = pg.evaluate('document.querySelectorAll("#sk_l .vc").length')
        if n < need: bad.append('search %s found %d < %d' % (q, n, need))
    ph = pg.evaluate('document.querySelectorAll("path[d=undefined]").length')
    if ph: bad.append('%d SVG paths with d=undefined' % ph)
    b.close()
if bad:
    [print('FAIL', x) for x in bad]; sys.exit(1)
print('PASS render v7.116: Settings card calls the bridge, new skills searchable, 0 JS errors, 0 undefined icons')
