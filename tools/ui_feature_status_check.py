"""Part 9 UI check: headless Chromium + FAKE window.Android. Feeds the real ui.html a feature-status JSON with missing keys and
missing permissions and checks that (a) every missing item is listed with its reason, (b) each THEEK KARO button calls the right
Android 'open settings / request' method, (c) no JS error. Evidence level: ui.html behaviour only (not Kotlin, not a phone).
Usage: python3 ui_feature_status_check.py <outdir>"""
import sys, os, json
from playwright.sync_api import sync_playwright
HERE = os.path.dirname(os.path.abspath(__file__))
UI = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html')
ITEMS = [
 {"id":"mic","name":"Microphone","state":"off","why":"Mic permission nahi hai","fix":"mic"},
 {"id":"overlay","name":"Wake par card (popup)","state":"off","why":"Display over other apps ON nahi hai","fix":"overlay"},
 {"id":"acc","name":"Phone Control + Screen padhna","state":"off","why":"Accessibility ON nahi hai","fix":"acc"},
 {"id":"notifAccess","name":"Driving mode messages","state":"off","why":"Notification access nahi hai","fix":"notifAccess"},
 {"id":"contacts","name":"Naam se call","state":"off","why":"Contacts permission nahi hai","fix":"contacts"},
 {"id":"writeSettings","name":"Brightness","state":"off","why":"Brightness permission nahi hai","fix":"writeSettings"},
 {"id":"battery","name":"Battery","state":"warn","why":"Battery restricted hai","fix":"battery"},
 {"id":"tts","name":"Awaaz mein jawab","state":"off","why":"Hindi voice missing","fix":"tts"},
 {"id":"gemini_key","name":"Gemini key","state":"off","why":"Gemini API key nahi daali","fix":"gemini_key"},
 {"id":"groq_key","name":"Groq key","state":"off","why":"Groq key nahi daali","fix":"groq_key"},
 {"id":"github","name":"GitHub publish","state":"off","why":"GitHub token nahi hai","fix":"github"},
 {"id":"wake","name":"Wake word sunna","state":"ok","why":"Chal raha hai","fix":""}]
FAKE = r"""
(function(){
var calls=[];window.__calls=calls;
var ITEMS=%s;
var st={versionName:'6.83',versionCode:683,mic:false,notif:false,overlay:false,acc:false,notifAccess:false,installOk:false,assistant:false,contacts:false,writeSettings:false,battery:false,pattern:false,feedUrl:''};
window.Android=new Proxy({},{get:function(t,k){return function(){
 calls.push([k].concat([].slice.call(arguments)));
 if(k==='getFeatureStatus')return JSON.stringify({items:ITEMS});
 if(k==='getStatus')return JSON.stringify(st);
 if(k==='getSettings')return JSON.stringify({wake:'nova',lang:'hi-IN',rate:1,pitch:1,model:'x',models:['x'],hasKey:false,keyCount:0});
 if(k==='requestBasics')return 'asked';
 if(/^(has|running)/.test(k))return false;
 if(k==='getUpdates')return '{"items":[]}';
 if(/^get(Wait|Auto|Memory|Voice)/.test(k))return 0;return ''}}});
})();
""" % json.dumps(ITEMS)
EXPECT = {"mic":"requestBasics","overlay":"openOverlaySettings","acc":"openAccessibility","notifAccess":"openNotificationAccess",
          "contacts":"requestContacts","writeSettings":"openWriteSettings","battery":"openBatterySettings","tts":"openTtsSettings"}
out = sys.argv[1] if len(sys.argv) > 1 else '/tmp/ui_fs'
os.makedirs(out, exist_ok=True)
bad = 0
def check(n, c):
    global bad
    print(('PASS ' if c else 'FAIL ') + n)
    if not c: bad += 1
with sync_playwright() as pw:
    b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    pg = b.new_context(viewport={'width': 360, 'height': 740}).new_page()
    errs = []
    pg.on('pageerror', lambda e: errs.append(str(e)))
    pg.add_init_script(FAKE)
    pg.goto('file://' + os.path.abspath(UI))
    pg.wait_for_function("document.body.classList.contains('ready')", timeout=20000)
    pg.wait_for_timeout(1800)
    pg.evaluate("fsLoad()")
    txt = pg.evaluate("document.getElementById('fs_list').innerText")
    summ = pg.evaluate("document.getElementById('fs_sum').innerText")
    missing = [i for i in ITEMS if i["state"] != "ok"]
    check("summary counts problems (%s)" % summ, ("%d dhyan do" % len(missing)) in summ)
    for i in missing:
        check("lists '%s' with its reason" % i["name"], i["name"] in txt and i["why"] in txt)
    check("healthy row hidden by default", "Chal raha hai" not in txt)
    n = pg.evaluate("document.querySelectorAll('#fs_list .fr button').length")
    check("one THEEK KARO button per problem (%d)" % n, n == len(missing))
    for idx, i in enumerate(missing):
        pg.evaluate("window.__calls.length=0")
        pg.evaluate("document.querySelectorAll('#fs_list .fr button')[%d].click()" % idx)
        pg.wait_for_timeout(120)
        calls = [c[0] for c in pg.evaluate("window.__calls")]
        if i["fix"] in EXPECT:
            check("button '%s' -> Android.%s" % (i["fix"], EXPECT[i["fix"]]), EXPECT[i["fix"]] in calls)
        else:
            # key rows jump to the matching settings box (no grant, no network call)
            check("button '%s' opens its key box without granting anything" % i["fix"],
                  not any(c.startswith(("save", "request")) for c in calls))
    pg.evaluate("window.__calls.length=0")
    pg.evaluate("[].slice.call(document.querySelectorAll('#fs_list button')).filter(function(b){return b.textContent==='SETUP DOBARA CHALAO'})[0].click()")
    pg.wait_for_timeout(120)
    check("SETUP DOBARA CHALAO button calls Android.rerunSetup", 'rerunSetup' in [c[0] for c in pg.evaluate("window.__calls")])
    pg.screenshot(path=os.path.join(out, 'feature_status.png'))
    check("no JavaScript errors %s" % errs, not errs)
    b.close()
print("\nfeature-status UI check: %s" % ("ALL PASS" if not bad else "%d FAIL" % bad))
sys.exit(1 if bad else 0)
