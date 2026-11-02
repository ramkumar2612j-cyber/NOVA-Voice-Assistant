#!/usr/bin/env python3
"""v7.03 G static contract: gallery photo/video in websites. Python mirror of MediaPlace + wiring checks. Evidence: static only (the Kotlin compile is tools/local_compile_check.sh)."""
import re, os, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf8').read()
J = 'app/src/main/java/com/nova/assistant/'
mp = rd(J + 'MediaPlace.kt')
def sset(n):
    m = re.search(r'private val ' + n + r' = setOf\((.*?)\)\n', mp, re.S); assert m, n
    return set(re.findall(r'"([a-z0-9]+)"', m.group(1)))
order = ['HERO', 'ABOUT', 'GALLERY', 'LOGO', 'MENU', 'FOOTER', 'BACKGROUND']
S = {n: sset(n) for n in order}
ACTS, REF = sset('ACTS'), sset('MEDIA_REF')
tok = lambda s: re.sub(r'[^a-z0-9]', ' ', s.lower()).split()
def parse(t):
    for w in tok(t):
        for n in order:
            if w in S[n]: return n
    return None
def idx(t):
    for w in tok(t):
        if w in sset('FIRST'): return 1
        if w in sset('SECOND'): return 2
        if w in sset('THIRD'): return 3
        if w in sset('FOURTH'): return 4
        if w in sset('LAST'): return -1
        if len(w) == 2 and w[0] == 'm' and w[1] in '123456': return int(w[1])
    return None
def is_cmd(t):
    k = tok(t)
    if not k or len(k) > 30 or parse(t) is None or not any(w in ACTS for w in k): return False
    return idx(t) is not None or any(w in REF for w in k)
bad = 0
def chk(n, c):
    global bad; print(('PASS ' if c else 'FAIL ') + n); bad += 0 if c else 1
chk('place hero', parse('ye photo hero mein lagao') == 'HERO')
chk('place gallery', parse('dusri photo gallery mein') == 'GALLERY')
chk('no place', parse('ek restaurant ki website banao') is None)
chk('index second', idx('dusri photo gallery mein') == 2 and idx('m3 logo') == 3 and idx('last photo') == -1)
chk('place command yes', is_cmd('ye photo hero mein lagao') and is_cmd('m2 gallery mein rakho'))
chk('place command no', not is_cmd('hero mein') and not is_cmd('photo lagao') and not is_cmd('aaj ka mausam batao'))
sm, sg, ns, ma, ui, st = (rd(J + 'SiteMedia.kt'), rd(J + 'SiteGen.kt'), rd(J + 'NovaService.kt'), rd(J + 'MainActivity.kt'), rd('app/src/main/assets/ui.html'), rd(J + 'SiteMediaStore.kt'))
chk('NOVA_USER_MEDIA in SiteMedia', 'NOVA_USER_MEDIA:' in sm and 'fun applyUser' in sm and 'fun userBlock' in sm)
chk('SiteGen uses userBlock 3 times', sg.count('SiteMedia.userBlock(SiteMediaStore.items())') == 3)
chk('NovaService applyUser + applyText + place command', 'SiteMedia.applyUser(' in ns and 'SiteMediaStore.applyText(' in ns and 'MediaPlace.isPlaceCommand(' in ns)
chk('lastSiteRawHtml keeps placeholders', 'lastSiteRawHtml = r.html' in ns)
chk('MainActivity REQ_SITE_MEDIA before data?.data', 'const val REQ_SITE_MEDIA' in ma and ma.index('requestCode == REQ_SITE_MEDIA') < ma.index('val uri = data?.data ?: return'))
chk('MainActivity bridge methods', all(('fun ' + n) in ma for n in ('pickSiteMedia', 'setSiteMediaPlace', 'clearSiteMedia', 'getSiteMedia')))
chk('video cap 5 MB', '5_000_000' in ma)
chk('store max 6', 'MAX_ITEMS = 6' in st)
for i in ('sm_pick', 'sm_clr', 'sm_list'): chk('ui id ' + i, ('id="%s"' % i) in ui)
chk('ui id sm_box', "box.id='sm_box'" in ui)
chk('ui onSiteMedia/Err', 'window.onSiteMedia=' in ui and 'window.onSiteMediaErr=' in ui)
blk = ui[ui.index('id="nova-site-media-js"'):]
chk("new JS has no $('literal') / g('literal')", not re.search(r"(?<![\w.])[$g]\('", blk))
chk('new JS uses document.getElementById', 'document.getElementById' in blk)
sys.exit(1 if bad else 0)
