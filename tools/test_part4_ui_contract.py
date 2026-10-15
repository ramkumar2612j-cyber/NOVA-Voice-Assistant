#!/usr/bin/env python3
"""Part 4 UI contract: the modern v76 layer is additive, keeps every id / bridge call, and keeps
HAAN/NAHI, STOP/ACTIVATE and SILENT separate and readable. Static checks only (no device)."""
import re, sys, os
here = os.path.dirname(os.path.abspath(__file__))
ui = open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)

m = re.search(r'<style id="modern-v76">(.*?)</style>', ui, re.S)
js = re.search(r'<script id="modern-v76-js">(.*?)</script>', ui, re.S)
need(m and js, 'modern-v76 style/script block missing')
css = m.group(1) if m else ''
code = js.group(1) if js else ''

# 1. every screen / control id that existed before is still present
for i in ['app','btn','quiet','silent','s_silent','st','log','chat','clog','c_in','c_ask','c_askq','c_yes','c_no','c_close',
          'panel','s_save','s_close','hist','h_close','h_clear','helpw','hw_x','aiw','ai_save','sheet','uc','tabs',
          't_home','t_skills','fs_list','fstat','feats','smsg','u_check','u_save','u_perm','ph_refresh','rt_save','ar_on','cv_on','chat_speak']:
    need(('id="%s"' % i) in ui, 'missing id ' + i)
for v in ['v_skills', 'v_limits']:
    need(('id="%s"' % v) in ui, 'missing view ' + v)

# 2. the layer is additive: it never replaces handlers or calls action bridges
need('.onclick' not in code and 'onclick=' not in code, 'v76 JS must not assign onclick (it would replace handlers)')
need('removeEventListener' not in code, 'v76 JS must not remove handlers')
calls = set(re.findall(r'Android\.(\w+)', code))
need(calls <= {'getSilentMode'}, 'v76 JS may only call read-only getSilentMode, found %s' % sorted(calls))
need('answerAsk' not in code and 'setSilentMode' not in code and 'stopService' not in code, 'v76 JS must not drive actions')
need('textContent=e' not in code and 'e.message' not in code, 'error toast must not print error text (could contain a key)')

# 3. HAAN / NAHI never auto-dismiss: Escape closes only the named close buttons, never c_yes/c_no/c_ask
esc = re.search(r"var order=\[(.*?)\];", code, re.S)
need(esc is not None, 'Escape close list missing')
if esc:
    need('c_yes' not in esc.group(1) and 'c_no' not in esc.group(1) and 'c_ask' not in esc.group(1), 'Escape must not touch HAAN/NAHI')
need('#c_yes,#c_no' in css and 'min-height:52px' in css, 'HAAN/NAHI must be large separate buttons')
need(not re.search(r'#c_ask[^{]*\{[^}]*pointer-events\s*:\s*none', css), 'confirmation must stay tappable')
need(re.search(r'c_ask[^\n]*addEventListener\(.click', ui) is None, 'no outside-tap handler on c_ask')
need(re.search(r"g\('c_yes'\)\.onclick=function\(\)\{ans\(true\)\};g\('c_no'\)\.onclick=function\(\)\{ans\(false\)\}", ui) is not None, 'HAAN/NAHI handlers changed')

# 4. STOP and ACTIVATE toggle on #btn is untouched, SILENT handlers untouched
need("btn.textContent=o?'STOP':'ACTIVATE'" in ui, 'ACTIVATE/STOP toggle text changed')
need("$('silent').onclick=function(){" in ui and "$('s_silent').onclick=function(){" in ui, 'SILENT handlers changed')
need('Android.setSilentMode(!on)' in ui, 'SILENT bridge call changed')

# 5. SILENT readability
need('html.nova-silent .msg' in css and 'font-size:16.5px' in css, 'silent mode must enlarge answer text')
need('html.nova-silent .log' in css and 'max-height:46vh' in css, 'silent mode must give answers room')
need('id="v76silent"' in ui and 'SILENT ON' in ui, 'visible SILENT indicator missing')
need("classList.toggle('nova-silent'" in code, 'silent class sync missing')
need(not re.search(r'nova-silent[^{]*\{[^}]*display\s*:\s*none', css), 'silent mode must not hide any feature')

# 6. accessibility
need('min-height:var(--v76-tap)' in css and '--v76-tap:44px' in css, '44px touch target missing')
need(':focus-visible' in css, 'focus ring missing')
need('prefers-reduced-motion:reduce' in css, 'reduced motion missing')
need('prefers-contrast:more' in css, 'high contrast missing')
need('env(safe-area-inset' in css, 'safe-area insets missing')
for k in ["'role','log'", "'role','status'", "aria-live"]:
    need(k in code, 'ARIA piece missing: ' + k)

# 7. no feature hidden by the new layer
need(not re.search(r'(#panel|\.box|\.f|#tabs|\.tb|#feats|#fstat)[^{]*\{[^}]*display\s*:\s*none', css), 'v76 must not hide features')

if bad:
    print('\n'.join('FAIL ' + b for b in bad)); sys.exit(1)
print('ALL PASSED')
