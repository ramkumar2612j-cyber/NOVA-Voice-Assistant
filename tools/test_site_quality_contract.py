#!/usr/bin/env python3
"""Part 8 contract: website generation is 2-pass (structure, then ONE polish pass) behind a strict gate; the old spec repair and edit patch stay."""
import os, sys
here = os.path.dirname(os.path.abspath(__file__)); root = os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/')
def rd(n): return open(os.path.join(root, n), encoding='utf-8').read()
sg, sq, ns = rd('SiteGen.kt'), rd('SiteQuality.kt'), rd('NovaService.kt')
bad = []
def need(c, m):
    if not c: bad.append(m)
need('SiteQuality.check(bestHtml)' in sg and 'SiteQuality.polishPrompt(' in sg, 'SiteGen must run the gate and the polish pass')
need('SiteQuality.better(' in sg, 'polished page must replace pass 1 only when better')
need('POLISH_BEFORE_MS' in sg and 'BUDGET_MS' in sg, 'time limits must stay')
need('if (!isEdit)' in sg and 'PATCH_SYSTEM' in sg and 'repairPrompt' in sg, 'edit patches and the spec repair call must stay')
need('val quality: List<String>' in sg and 'r.quality' in ns, 'open quality gaps must be told to the owner honestly')
for k in ['viewport', 'prefers-reduced-motion', 'lorem', 'alt text', 'dead links', 'not closed', 'stub']:
    need(k in sq, 'gate check missing: ' + k)
if bad:
    print('FAIL'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS site quality contract')
