#!/usr/bin/env python3
"""v7.00 AUTO MEDIA static contract (python mirror of MediaIntent + wiring checks). Evidence: static only, NOT a Kotlin compile."""
import re, os, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf8').read()
mi = rd('app/src/main/java/com/nova/assistant/MediaIntent.kt')
def sset(n):
    m = re.search(r'private val ' + n + r' = setOf\((.*?)\)\n', mi, re.S); assert m, n
    return set(re.findall(r'"([a-z0-9]+)"', m.group(1)))
BUILD, SITE, IMG, VID, OTHER, NOMEDIA = (sset(n) for n in ('BUILD', 'SITE_NOUNS', 'IMG_WORDS', 'VIDEO_WORDS', 'OTHER', 'NO_MEDIA'))
tok = lambda s: re.sub(r'[^a-z0-9]', ' ', s.lower()).split()
def classify(text):
    t = tok(text)
    if not t or len(t) > 60: return None
    b = s = i = v = False
    for w in t:
        if w in OTHER: return None
        b |= w in BUILD; s |= w in SITE; i |= w in IMG; v |= w in VID
    if not b: return None
    if s: return 'SITE' if (i or v) else None
    if v and not i: return 'VIDEO'
    return None
def wants_images(text):
    t = tok(text)
    for k, w in enumerate(t):
        if w in NOMEDIA and any(t[j] in IMG for j in range(max(0, k - 2), min(len(t) - 1, k + 2) + 1)): return False
    return not ('text' in t and ('sirf' in t or 'only' in t))
bad = 0
def chk(n, c):
    global bad; print(('PASS ' if c else 'FAIL ') + n); bad += 0 if c else 1
chk('website + photos -> SITE', classify('nova ek restaurant ki website banao jisme photos ho') == 'SITE')
chk('plain photo -> old ImageIntent (None)', classify('nova ek billi ki photo banao') is None)
chk('plain website -> old SiteIntent (None)', classify('nova ek gym ki website banao') is None)
chk('video banao -> VIDEO', classify('nova ek video banao') == 'VIDEO')
chk('background video recording -> None', classify('nova background me video recording on karo') is None)
chk('bina photo -> SITE, images off', classify('nova bina photo ke ek gym ki website banao') == 'SITE' and not wants_images('nova bina photo ke ek gym ki website banao'))
chk('images default on', wants_images('nova restaurant website banao'))
ns = rd('app/src/main/java/com/nova/assistant/NovaService.kt'); sg = rd('app/src/main/java/com/nova/assistant/SiteGen.kt'); sm = rd('app/src/main/java/com/nova/assistant/SiteMedia.kt')
for n, c in [('NovaService mediaRoute', 'private fun mediaRoute' in ns and ns.count('mediaRoute(') >= 3), ('NovaService lastSiteRawHtml'.replace('NovaService ', ''), 'lastSiteRawHtml' in ns),
             ('SiteMedia.resolve in siteBegin', 'SiteMedia.resolve(this' in ns), ('SiteGen uses SiteMedia.promptBlock', sg.count('SiteMedia.promptBlock') >= 3),
             ('SiteMedia has no publish / no token', 'GitHubPublish' not in sm and 'SecureStore' not in sm), ('SiteMedia caps pictures', 'MAX_IMAGES = 4' in sm)]:
    chk(n, c)
ui = rd('app/src/main/assets/ui.html')
for i in ['c_imode', 'c_photo', 'st_free', 'st_img', 'i_on', 'i_tog', 'i_hf', 'vr_start', 'vr_stop', 't_home', 't_orb', 'hub_b']:
    chk('ui id still present: ' + i, ('id="' + i + '"' in ui) or ("id='" + i + "'" in ui) or ("'" + i + "'" in ui))
chk('hide css for old image buttons', '#c_imode,#c_photo,#st_free,#st_img{display:none!important}' in ui)
chk('old HOME tab not hidden', '#t_home{display:none' not in ui)
sys.exit(1 if bad else 0)
