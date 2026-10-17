"""Builds ONE self-contained preview file (real ui.html + fake Android bridge + phone frame + shortcut buttons).
Usage: python3 tools/build_ui_preview.py OUT.html   Evidence: renders in a browser only, NOT a real phone."""
import re, base64, json, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
ui = open(os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html'), encoding='utf8').read()
src = open(os.path.join(HERE, 'ui_shots.py'), encoding='utf8').read()
fake = re.search(r'FAKE = r"""(.*?)"""', src, re.S).group(1)
for a, b in [("versionName:'6.18',versionCode:618", "versionName:'6.99',versionCode:99"), ("overlay:false", "overlay:true"), ("notifAccess:false", "notifAccess:true"), ("assistant:false", "assistant:true"), ("writeSettings:false", "writeSettings:true"), ("pattern:false", "pattern:true"), ("feedUrl:''", "feedUrl:'https://raw.githubusercontent.com/devboutique0-wq/NOVA/main/feed/feed.json'")]:
    fake = fake.replace(a, b)
def A(i, l, w=False, h=''): return dict(id=i, label=l, write=w, hint=h)
def C(id, name, short, conn, perm, acct, acts, rc, wc, kind='api'): return dict(id=id, name=name, short=short, connected=conn, perm=perm, account=acct, actions=acts, readCan=rc, writeCan=wc, kind=kind, help='Token banao, copy karo, NOVA me wapas aao.')
conn = [
 C('github','GitHub','Repos, issues, workflow runs; naya repo / file commit; website publish.',True,'write','devboutique0-wq',[A('repos','Repos dikhao'),A('issues','Open issues',False,'owner/repo'),A('runs','Workflow runs',False,'owner/repo'),A('issue_new','Naya issue banao',True,'owner/repo title'),A('repo_new','Naya private repo',True,'repo naam'),A('commit','File commit karo',True,'owner/repo path text')],['Repos, issues, runs'],['Issue, repo, file commit']),
 C('vercel','Vercel','Projects, deployments, domains; redeploy.',True,'read','devboutique0',[A('projects','Projects dikhao'),A('deployments','Deployments dikhao'),A('domains','Domains dikhao'),A('redeploy','Redeploy karo',True,'project naam')],['Projects'],['Redeploy']),
 C('netlify','Netlify','Sites aur deploys; build trigger.',False,'off','',[A('sites','Sites dikhao'),A('build','Build chalao',True,'site naam')],['Sites'],['Build']),
 C('cloudflare','Cloudflare','Zones aur Pages (sirf padhna).',False,'off','',[A('zones','Zones dikhao')],['Zones'],[]),
 C('supabase','Supabase','Projects aur status (sirf padhna).',False,'off','',[A('projects','Projects dikhao')],['Projects'],[]),
 C('notion','Notion','Shared pages search (sirf padhna).',False,'off','',[A('search','Search',False,'kya dhundhna hai')],['Pages'],[]),
 C('lovable','Lovable','Lovable GitHub se sync hota hai: pehle GitHub connect karo.',False,'off','',[],[],[],'link')]
fake = fake.replace("if(k==='getLockGuard')return true;", "if(k==='getConnectors')return JSON.stringify(%s);if(k==='getLockGuard')return true;" % json.dumps(conn))
b64 = base64.b64encode(ui.replace("<head>", "<head><script>" + fake + "</script>", 1).encode('utf8')).decode()
btns = [("HOME","t_home"),("SKILLS","t_skills"),("LIMITS","t_limits"),("CONNECT","t_conn"),("CHAT","chatb"),("SETUP","gear"),("AUTOMATION","au_open"),("HUB MENU (3D)","hub_b"),("WEBSITE BANAO","hub_b+hub_i7"),("MADAD popup","chatb+c_help"),("AI chuno","chatb+c_ai"),("ACTIVATE","btn"),("CHUP","quiet")]
page = '''<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><title>NOVA UI Preview</title>
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><meta name="color-scheme" content="dark light">
<style>
:root{--bg:#f1f3f6;--fg:#12161c;--card:#fff;--line:#cfd6df;--acc:#0f766e;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}
@media (prefers-color-scheme:dark){:root:not([data-theme="light"]){--bg:#0b0e13;--fg:#e8edf3;--card:#151a22;--line:#2a3340;--acc:#2dd4bf}}
*{box-sizing:border-box}html,body{margin:0;background:var(--bg);color:var(--fg);font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
.wrap{max-width:460px;margin:0 auto;padding:10px}h1{font-size:14px;letter-spacing:2px;margin:2px 0}p.s{font-size:11px;opacity:.7;margin:0 0 8px}
.frame{width:100%;height:min(84vh,860px);border:1px solid var(--line);border-radius:22px;overflow:hidden;background:#000}
iframe{width:100%;height:100%;border:0;display:block}
.bar{display:flex;flex-wrap:wrap;gap:6px;margin-top:8px}
.bar button{flex:1 1 calc(33% - 6px);min-width:92px;padding:10px 4px;border-radius:12px;border:1px solid var(--line);background:var(--card);color:var(--fg);font-size:11px;font-weight:600}
.note{font-size:11px;opacity:.65;margin-top:8px;line-height:1.5}
</style></head><body><div class="wrap">
<h1>NOVA v6.99 - UI PREVIEW</h1><p class="s">Asli ui.html + nakli bridge. Phone par NOT TESTED.</p>
<div class="frame"><iframe id="f" title="NOVA UI"></iframe></div><div class="bar" id="bar"></div>
<div class="note">Neeche beech ka 3D dil = HUB: dabao to pages ka menu, dabaye rakho to ACTIVATE / STOP.</div></div>
<script>
var B64="__B64__";
var bin=atob(B64),u=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);
var f=document.getElementById("f");f.srcdoc=new TextDecoder("utf-8").decode(u);
var BT=__BT__;
function clk(id){var d=f.contentDocument;if(!d)return false;var e=d.getElementById(id);if(e)e.click();return !!e}
function run(v){var p=v.split("+");clk(p[0]);if(p[1])setTimeout(function(){clk(p[1])},700)}
var bar=document.getElementById("bar");
BT.forEach(function(b){var x=document.createElement("button");x.textContent=b[0];x.onclick=function(){run(b[1])};bar.appendChild(x)});
</script></body></html>'''.replace('__B64__', b64).replace('__BT__', json.dumps(btns))
open(sys.argv[1], 'w', encoding='utf8').write(page)
print('ok', len(page))
