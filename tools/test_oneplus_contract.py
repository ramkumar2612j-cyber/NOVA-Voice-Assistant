#!/usr/bin/env python3
"""v7.74 contract (static, NOT a phone test): photo / video / file live under the + menu; paperclip + MEDIA buttons hidden (ids kept); no old line removed."""
import os, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
H = open(os.path.join(R, 'app/src/main/assets/ui.html'), encoding='utf-8').read()
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
need('id="p21_oneplus"' in H and "id=\"p21_oneplus_css\"" in H, 'p21 script + css exist')
need("click('c_photo')" in H and "click('m11_pick')" in H, '+ menu items call the existing photo and media pickers')
need('html body #c_attach,html body #m11_btn{display:none!important}' in H, 'paperclip and MEDIA buttons hidden')
need("id=\"c_plus\"" in H and "b.id='c_attach'" in H and "tb.id='m11_btn'" in H, 'old buttons / ids still in the page (add-only)')
need('Main abhi busy hoon' not in H, 'no prefilled auto-reply message in the page')
need('versionName = "7.74"' in open(os.path.join(R, 'app/build.gradle.kts')).read(), 'versionName 7.74')
if bad: print('FAILED', len(bad)); sys.exit(1)
print('PASS oneplus contract')
