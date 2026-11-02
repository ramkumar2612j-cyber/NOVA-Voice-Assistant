"""Headless-Chromium UI check with a FAKE window.Android bridge. Usage: python3 ui_shots.py <outdir> [w h]...
Evidence level: renders here only (SwiftShader), nothing on a real phone."""
import sys, os, json
from playwright.sync_api import sync_playwright
HERE = os.path.dirname(os.path.abspath(__file__))
UI = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html')
FAKE = r"""
(function(){
var usage={day:'2026-10-12',totalToday:412,rows:[
{id:'gemini',label:'Gemini',group:'brain',note:'Gemini free limit estimate.',limitKind:'estimate',keySet:true,used:180,sent:180,failed:3,quotaHits:1,limit:250,remaining:70,percent:72,source:'local',liveReset:'',lastTs:Date.now()-60000,lastCode:200,defLimit:250},
{id:'groq',label:'Groq (GPT-OSS)',group:'brain',note:'Groq official 1,000/day.',limitKind:'official',keySet:true,used:220,sent:220,failed:0,quotaHits:0,limit:1000,remaining:780,percent:22,source:'live',liveReset:'8h',lastTs:Date.now()-5000,lastCode:200,defLimit:1000},
{id:'groqstt',label:'Groq Whisper (sunna)',group:'voice',note:'2,000/day.',limitKind:'official',keySet:true,used:12,sent:12,failed:0,quotaHits:0,limit:2000,remaining:1988,percent:1,source:'local',liveReset:'',lastTs:0,lastCode:0,defLimit:2000},
{id:'zai',label:'GLM (Z.ai)',group:'brain',note:'No published limit.',limitKind:'none',keySet:false,used:0,sent:0,failed:0,quotaHits:0,limit:-1,remaining:-1,percent:-1,source:'count',liveReset:'',lastTs:0,lastCode:0,defLimit:0},
{id:'openrouter',label:'OpenRouter (free)',group:'brain',note:'50/day.',limitKind:'custom',keySet:true,used:48,sent:48,failed:2,quotaHits:2,limit:50,remaining:2,percent:96,source:'local',liveReset:'',lastTs:0,lastCode:429,defLimit:50},
{id:'together',label:'Together AI (image)',group:'image',note:'-',limitKind:'none',keySet:false,used:3,sent:3,failed:0,quotaHits:0,limit:-1,remaining:-1,percent:-1,source:'count',liveReset:'',lastTs:0,lastCode:200,defLimit:0}]};
var st={versionName:'6.18',versionCode:618,mic:true,notif:true,overlay:false,acc:true,notifAccess:false,installOk:true,assistant:false,contacts:true,writeSettings:false,battery:true,pattern:false,feedUrl:''};
var se={wake:'nova',lang:'hi-IN',rate:1,pitch:1,model:'gemini-2.5-flash',models:['gemini-2.5-flash'],autostart:false,hasKey:true,keyCount:1,hasGroq:true,localMode:false,acc:true,screenMonitor:false};
var calls=[];window.__calls=calls;
window.Android=new Proxy({},{get:function(t,k){return function(){var a=[].slice.call(arguments);calls.push([k].concat(a));
 if(k==='getLockGuard')return true;
 if(k==='lockGuardStatus')return 'Notification access ON | Volume-key stop ON';
 if(k==='getBrowserStatus')return JSON.stringify({web:true,localFirst:true,localReady:false,city:'Delhi',saved:{webToday:7,localToday:12,webTotal:70,localTotal:99},browsers:[{id:'wiki',label:'Wikipedia + Wiktionary',service:'Wikimedia',state:'ok',calls:5,ok:5},{id:'instant',label:'DuckDuckGo Instant',service:'DuckDuckGo',state:'unused',calls:0,ok:0},{id:'weather',label:'Open-Meteo Weather',service:'Open-Meteo',state:'failing',calls:2,ok:0},{id:'news',label:'Google News',service:'Google News RSS',state:'paused',calls:3,ok:1},{id:'market',label:'Currency + Crypto',service:'Frankfurter / CoinGecko',state:'ok',calls:4,ok:4}]});
 if(k==='getApiUsage')return JSON.stringify(usage);
 if(k==='getProviderHealth')return JSON.stringify({active:true,providers:[{id:'groq',label:'Groq',keySet:true,cooling:true,cooldownSec:42}]});
 if(k==='getStatus')return JSON.stringify(st);
 if(k==='getSettings')return JSON.stringify(se);
 if(k==='setApiLimit'){return a[1]<0?'invalid':'ok'}
 if(k==='resetApiUsage'||k==='setApiLimit')return 'ok';
 if(/^(has|running|get(Convo|Alerts|FreeChain|FreeImg|FreeKeyless|AutoModels|MemoryInChain))/.test(k))return false;
 if(/^get(Errors|Learned|Updates|MemoryText|VoiceLimitText)$/.test(k))return k==='getUpdates'?'{"items":[]}':'';
 if(/^get(Wait|Auto|Memory|Voice)/.test(k))return 0;
 return ''}}});
})();
"""
def run(out, sizes):
    os.makedirs(out, exist_ok=True)
    res = {}
    with sync_playwright() as pw:
        b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
        for (w, h) in sizes:
            ctx = b.new_context(viewport={'width': w, 'height': h}, device_scale_factor=1)
            pg = ctx.new_page()
            errs = []
            pg.on('pageerror', lambda e: errs.append(str(e)))
            pg.on('console', lambda m: errs.append('console:' + m.text) if m.type == 'error' else None)
            pg.add_init_script(FAKE)
            pg.goto('file://' + os.path.abspath(UI))
            pg.wait_for_function("document.body.classList.contains('ready')", timeout=20000)
            pg.wait_for_timeout(2500)
            res[f'{w}x{h}'] = {'errors': errs, 'orb3d': pg.evaluate('window.__orb3d')}
            pg.screenshot(path=os.path.join(out, f'home_{w}x{h}.png'))
            for name in sys.argv[0:0]:
                pass
            hook = os.environ.get('SHOT_JS')
            if hook:
                for part in hook.split(';;'):
                    nm, js = part.split('=>', 1)
                    pg.evaluate(js); pg.wait_for_timeout(900)
                    pg.screenshot(path=os.path.join(out, f'{nm}_{w}x{h}.png'))
                res[f'{w}x{h}']['errors'] = errs
            ctx.close()
        b.close()
    print(json.dumps(res, indent=1))
if __name__ == '__main__':
    out = sys.argv[1]
    nums = [int(x) for x in sys.argv[2:]]
    sizes = [(nums[i], nums[i + 1]) for i in range(0, len(nums) - 1, 2)] or [(360, 640), (390, 844)]
    run(out, sizes)
