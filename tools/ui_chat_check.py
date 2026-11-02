"""Part 8 UI check: headless Chromium + a STATEFUL FAKE window.Android chat bridge (JS model of the Kotlin rules).
Evidence level: proves ui.html behaviour (search, pin, rename, export call, double-tap deletes, states, context line, layout) only.
It does NOT test the Kotlin bridge or a real phone. Usage: python3 ui_chat_check.py <outdir>"""
import sys, os, json
from playwright.sync_api import sync_playwright
HERE = os.path.dirname(os.path.abspath(__file__))
UI = os.path.join(HERE, '..', 'app', 'src', 'main', 'assets', 'ui.html')
FAKE = r"""
(function(){
var now=Date.now();
function mk(n,r){var a=[];for(var i=0;i<n;i++)a.push({r:i%2?'ai':'me',t:(r||'msg')+' '+i,at:now});return a}
var chats=[
 {id:'ca1',title:'torch jala do',updated:now-1000,pinned:false,msgs:[{r:'me',t:'torch jala do',at:now},{r:'ai',t:'Torch on',at:now}]},
 {id:'cb2',title:'kal ka mausam',updated:now-5000,pinned:true,msgs:[{r:'me',t:'kal ka mausam batao',at:now},{r:'ai',t:'Kal baarish hogi',at:now}]},
 {id:'cc3',title:'lambi chat',updated:now-9000,pinned:false,msgs:mk(300,'lamba')}];
var cur='ca1',calls=[],busy=false,failRead=false,pinned=function(){return chats.filter(function(c){return c.pinned}).length};
window.__calls=calls;window.__set=function(k,v){if(k==='fail')failRead=v;if(k==='busy')busy=v};
function item(c,q){var sn='';if(q){var m=c.msgs.filter(function(x){return x.t.toLowerCase().indexOf(q)>=0})[0];if(m)sn=m.t}
 return {id:c.id,title:c.title,updated:c.updated,count:c.msgs.length,label:c.msgs.length+' messages',current:c.id===cur,pinned:c.pinned,snippet:sn}}
function sorted(){return chats.slice().sort(function(a,b){return (b.pinned-a.pinned)||(b.updated-a.updated)})}
var B={
 searchChats:function(q){calls.push(['searchChats',q]);if(failRead)return JSON.stringify({error:'read'});q=(q||'').toLowerCase().trim();
   return JSON.stringify({ok:true,items:sorted().filter(function(c){return !q||(c.title+' '+c.msgs.map(function(m){return m.t}).join(' ')).toLowerCase().indexOf(q)>=0}).map(function(c){return item(c,q)})})},
 pinChat:function(id,on){calls.push(['pinChat',id,on]);var c=chats.filter(function(x){return x.id===id})[0];if(!c)return 'missing';if(on&&pinned()>=10)return 'limit';c.pinned=on;return 'ok'},
 renameChat:function(id,t){calls.push(['renameChat',id,t]);t=(t||'').trim();if(!t)return 'blank';var c=chats.filter(function(x){return x.id===id})[0];if(!c)return 'missing';c.title=t.slice(0,60);return 'ok'},
 exportChat:function(id){calls.push(['exportChat',id]);setTimeout(function(){window.onChatExport('export_ok')},50);return 'started'},
 deleteChat:function(id){calls.push(['deleteChat',id]);chats=chats.filter(function(c){return c.id!==id});if(cur===id)cur='';return 'ok'},
 deleteAllChats:function(){calls.push(['deleteAllChats']);if(busy)return 'busy';chats=[];cur='';return 'ok'},
 openChat:function(id){calls.push(['openChat',id]);if(busy)return JSON.stringify({error:'busy'});var c=chats.filter(function(x){return x.id===id})[0];if(!c)return JSON.stringify({error:'missing'});cur=id;return JSON.stringify({id:id,messages:c.msgs})},
 newChat:function(){calls.push(['newChat']);cur='cnew';chats.unshift({id:'cnew',title:'Nayi chat',updated:Date.now(),pinned:false,msgs:[]});return JSON.stringify({id:'cnew'})},
 currentChat:function(){return cur},
 contextInfo:function(){var c=chats.filter(function(x){return x.id===cur})[0];var n=c?c.msgs.length:0;var f=Math.min(n,200);return JSON.stringify({total:n,gemini:{sent:n,cut:0},free:{sent:f,cut:n-f}})},
 sendText:function(){return 'sent'}
};
var st={versionName:'6.80',versionCode:680,mic:true,notif:true,overlay:false,acc:true,notifAccess:false,installOk:true,assistant:false,contacts:true,writeSettings:false,battery:true,pattern:false,feedUrl:''};
window.Android=new Proxy({},{get:function(t,k){return function(){if(B[k])return B[k].apply(null,arguments);
 if(k==='getStatus')return JSON.stringify(st);if(k==='getSettings')return JSON.stringify({wake:'nova',lang:'hi-IN',rate:1,pitch:1,model:'x',models:['x'],hasKey:true,keyCount:1});
 if(/^(has|running|get(Convo|Alerts|FreeChain|FreeImg|FreeKeyless|AutoModels|MemoryInChain|ChatSpeak))/.test(k))return false;
 if(/^get(Errors|Learned|Updates|MemoryText|VoiceLimitText)$/.test(k))return k==='getUpdates'?'{"items":[]}':'';
 if(/^get(Wait|Auto|Memory|Voice)/.test(k))return 0;return ''}}});
})();
"""
out = sys.argv[1] if len(sys.argv) > 1 else '/tmp/ui_chat'
os.makedirs(out, exist_ok=True)
bad = 0
def check(n, c):
    global bad
    print(('PASS ' if c else 'FAIL ') + n)
    if not c: bad += 1
with sync_playwright() as pw:
    b = pw.chromium.launch(args=['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'])
    ctx = b.new_context(viewport={'width': 360, 'height': 740}, device_scale_factor=1)
    pg = ctx.new_page()
    errs = []
    pg.on('pageerror', lambda e: errs.append(str(e)))
    pg.add_init_script(FAKE)
    pg.goto('file://' + os.path.abspath(UI))
    pg.wait_for_function("document.body.classList.contains('ready')", timeout=20000)
    pg.wait_for_timeout(1500)
    pg.evaluate("document.getElementById('chatb').click()")
    pg.wait_for_timeout(900)
    pg.screenshot(path=os.path.join(out, '1_chat_open.png'))
    r = pg.evaluate("""(function(){var t=document.getElementById('tabs').getBoundingClientRect(),c=document.getElementById('chat').getBoundingClientRect(),i=document.querySelector('.chi').getBoundingClientRect();
      return {tabTop:t.top,chatBottom:c.bottom,inputBottom:i.bottom,tabsVisible:getComputedStyle(document.getElementById('tabs')).display!=='none'}})()""")
    check('chat panel bottom is above the tab bar (no overlap) %s' % r, r['chatBottom'] <= r['tabTop'] + 0.5 and r['inputBottom'] <= r['tabTop'] + 0.5)
    check('context line shown for the open chat', 'AI ko gaya: 2 / 2' in pg.inner_text('#c_ctx'))
    pg.click('#c_hist'); pg.wait_for_timeout(400)
    rows = pg.evaluate("Array.from(document.querySelectorAll('#c_hll .hrow')).map(function(r){return r.querySelector('b').textContent})")
    check('history lists 3 chats, pinned first %s' % rows, len(rows) == 3 and rows[0].endswith('kal ka mausam'))
    pg.screenshot(path=os.path.join(out, '2_history.png'))
    pg.fill('#c_hs', 'torch'); pg.wait_for_timeout(500)
    rows = pg.evaluate("Array.from(document.querySelectorAll('#c_hll .hrow b')).map(function(r){return r.textContent})")
    check('search "torch" gives 1 chat %s' % rows, rows == ['torch jala do'])
    check('search shows a snippet', 'torch' in pg.inner_text('#c_hll .hsn').lower())
    pg.screenshot(path=os.path.join(out, '3_search.png'))
    pg.fill('#c_hs', 'zzzz'); pg.wait_for_timeout(500)
    check('search with no result shows empty text', 'nahi mili' in pg.inner_text('#c_hll'))
    pg.fill('#c_hs', ''); pg.wait_for_timeout(500)
    # pin the 'torch' chat (row 2 after pinned one)
    pg.evaluate("document.querySelectorAll('#c_hll .hrow')[1].querySelector('[aria-label=pin]').click()"); pg.wait_for_timeout(300)
    rows = pg.evaluate("Array.from(document.querySelectorAll('#c_hll .hrow b')).map(function(r){return r.textContent})")
    check('pin moves chat to the top group %s' % rows, sum(1 for x in rows if x.startswith('\U0001F4CC')) == 2)
    # rename
    pg.evaluate("document.querySelectorAll('#c_hll .hrow')[2].querySelector('[aria-label=rename]').click()"); pg.wait_for_timeout(200)
    pg.fill('#c_hll .hrn', 'meri lambi chat'); pg.keyboard.press('Enter'); pg.wait_for_timeout(300)
    check('rename works', 'meri lambi chat' in pg.inner_text('#c_hll'))
    pg.evaluate("document.querySelectorAll('#c_hll .hrow')[2].querySelector('[aria-label=rename]').click()"); pg.wait_for_timeout(200)
    pg.fill('#c_hll .hrn', '   '); pg.keyboard.press('Enter'); pg.wait_for_timeout(300)
    check('blank rename is refused with a message', 'khali' in pg.inner_text('#c_hst'))
    pg.screenshot(path=os.path.join(out, '4_rename_blank.png'))
    pg.fill('#c_hs', ''); pg.evaluate("document.getElementById('c_hs').dispatchEvent(new Event('input'))"); pg.wait_for_timeout(400)
    # export
    pg.evaluate("document.querySelectorAll('#c_hll .hrow')[0].querySelector('[aria-label=export]').click()"); pg.wait_for_timeout(300)
    check('export calls the bridge and shows the result', any(c[0] == 'exportChat' for c in pg.evaluate('window.__calls')) and 'save ho gayi' in pg.inner_text('#c_hst'))
    # delete needs two taps
    n0 = pg.evaluate("document.querySelectorAll('#c_hll .hrow').length")
    pg.evaluate("document.querySelectorAll('#c_hll .hrow')[1].querySelector('.hdel').click()"); pg.wait_for_timeout(200)
    n1 = pg.evaluate("document.querySelectorAll('#c_hll .hrow').length")
    pg.evaluate("document.querySelectorAll('#c_hll .hrow')[1].querySelector('.hdel').click()"); pg.wait_for_timeout(300)
    n2 = pg.evaluate("document.querySelectorAll('#c_hll .hrow').length")
    check('single delete needs a second tap (%d,%d,%d)' % (n0, n1, n2), n0 == 3 and n1 == 3 and n2 == 2)
    # busy blocks delete all
    pg.evaluate("window.__set('busy',true)")
    pg.click('#c_hall'); pg.click('#c_hall'); pg.wait_for_timeout(300)
    check('delete all while NOVA is busy is refused', 'kaam kar raha' in pg.inner_text('#c_hst') and pg.evaluate("document.querySelectorAll('#c_hll .hrow').length") == 2)
    pg.evaluate("window.__set('busy',false)")
    pg.click('#c_hall'); pg.wait_for_timeout(200)
    check('delete all first tap only asks', 'PAKKA' in pg.inner_text('#c_hall') and pg.evaluate("document.querySelectorAll('#c_hll .hrow').length") == 2)
    pg.screenshot(path=os.path.join(out, '5_delete_all_ask.png'))
    pg.click('#c_hall'); pg.wait_for_timeout(400)
    check('delete all second tap empties the list', 'koi purani chat nahi' in pg.inner_text('#c_hll'))
    pg.screenshot(path=os.path.join(out, '6_empty.png'))
    # error state
    pg.evaluate("window.__set('fail',true)"); pg.click('#c_hx'); pg.click('#c_hist'); pg.wait_for_timeout(400)
    check('read error shows a message and a retry button', 'padh nahi paye' in pg.inner_text('#c_hst') and pg.evaluate("!!document.querySelector('#c_hll button')"))
    pg.screenshot(path=os.path.join(out, '7_error.png'))
    pg.evaluate("window.__set('fail',false)"); pg.click('#c_hll button'); pg.wait_for_timeout(300)
    check('retry recovers', 'padh nahi paye' not in pg.inner_text('#c_hst'))
    check('no JavaScript errors %s' % errs, not errs)
    # long chat on a fresh page
    pg2 = ctx.new_page(); pg2.add_init_script(FAKE.replace("cur='ca1'", "cur='cc3'"))
    pg2.goto('file://' + os.path.abspath(UI)); pg2.wait_for_function("document.body.classList.contains('ready')", timeout=20000); pg2.wait_for_timeout(1200)
    pg2.evaluate("document.getElementById('chatb').click()"); pg2.wait_for_timeout(900)
    nodes = pg2.evaluate("document.getElementById('clog').childNodes.length")
    check('long chat: DOM capped (%d) with a note about older messages' % nodes, nodes <= 121 and 'purane messages' in pg2.inner_text('#clog'))
    ctxt = pg2.inner_text('#c_ctx')
    check('context line shows cut for free AI: %r' % ctxt, 'free AI' in ctxt and '100 cut' in ctxt)
    pg2.screenshot(path=os.path.join(out, '8_long_chat.png'))
    b.close()
sys.exit(1 if bad else 0)
