#!/usr/bin/env python3
"""Part 11-C contract: every media bridge name used by ui.html exists as a Kotlin @JavascriptInterface and is listed in BRIDGE; callback onMediaIntake is in CB;
the page block is registered in SCR; names are shown with textContent only; no ANALYSE button; no path / uri leaves Kotlin; picker result reaches MediaIntakeStore."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def rd(p): return open(os.path.join(R, p), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
ui = rd('app/src/main/assets/ui.html'); ma = rd('app/src/main/java/com/nova/assistant/MainActivity.kt'); st = rd('app/src/main/java/com/nova/assistant/MediaIntakeStore.kt')
blk = re.search(r'<script id="pt11_media">(.*?)</script>', ui, re.S).group(1)
NAMES = ['mediaIntakeList', 'mediaIntakeThumb', 'mediaIntakeOpen', 'mediaIntakeRemove', 'mediaIntakeClear', 'mediaIntakeCancel', 'mediaIntakeAttach', 'mediaPick']
br = set(re.findall(r'"(\w+)"', re.search(r'var BRIDGE=(\[.*?\]);', ui, re.S).group(1)))
cb = set(re.findall(r'"(\w+)"', re.search(r'var CB=(\[.*?\]);', ui, re.S).group(1)))
for n in NAMES:
    need(re.search(r'@JavascriptInterface\s+fun %s\(' % n, ma), 'Kotlin bridge missing: ' + n)
    need(n in br, 'BRIDGE list lacks ' + n)
    need(("'%s'" % n) in blk or ('Android.%s' % n) in blk, 'ui block never calls ' + n)
need('onMediaIntake' in cb and 'window.onMediaIntake' in blk, 'onMediaIntake callback not wired')
need("'pt11_media'" in ui.split('var SCR=')[1].split(']')[0], 'pt11_media not in page self-check list SCR')
need('innerHTML' not in blk and 'insertAdjacentHTML' not in blk and 'outerHTML' not in blk, 'card must use textContent only (file names are untrusted)')
need(not re.search(r'ANALYSE|ANALYZE', blk.replace('Analyse abhi nahi hai', '')), 'no ANALYSE button before Part 13')
for b in ('DEKHO', 'CHAT ME JODO', 'HATAO', 'MEDIA CHUNO', 'SAAF KARO'):
    need(b in blk, 'button missing: ' + b)
need('REQ_MEDIA_INTAKE' in ma and 'handleMediaPick(data)' in ma and 'MediaIntakeStore.ingest(' in ma, 'picker result must reach MediaIntakeStore.ingest')
need('fun mediaChanged' in ma and 'onMediaIntake()' in ma, 'page refresh callback missing')
need(ma.count('MediaIntakeStore.onChanged = { mediaChanged() }') >= 2, 'both share and picker must refresh the page')
need('absolutePath' not in re.search(r'fun mediaIntake.*?fun mediaPick', ma, re.S).group(0), 'bridge must never return a path')
need('nova_media_intake' in rd('app/src/main/res/xml/nova_file_paths.xml'), 'FileProvider path for media_intake missing')
need('recycle()' in st and 'inSampleSize' in st and 'release()' in st, 'thumbnail must use inSampleSize, recycle and release the retriever')
if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS media card 11-C contract (static wiring; real phone NOT TESTED)')
