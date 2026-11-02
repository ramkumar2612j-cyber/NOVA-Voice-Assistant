#!/usr/bin/env python3
"""v7.05 I + H static contract: offline photo editor never uses the network, AI edit asks first, Vercel token only in a header, UI wiring. Static only."""
import re, os, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf8').read()
J = 'app/src/main/java/com/nova/assistant/'
pp, pe, vp, pt, ma, ns, ig, ui, gh = (rd(J + n) for n in ('PhotoPro.kt', 'PhotoEditIntent.kt', 'VercelPublish.kt', 'PublishTarget.kt', 'MainActivity.kt', 'NovaService.kt', 'ImageGen.kt', 'app/src/main/assets/ui.html'.replace(J, '') if False else 'ImageGen.kt', 'GitHubPublish.kt'))
ui = rd('app/src/main/assets/ui.html')
bad = 0
def chk(n, c):
    global bad; print(('PASS ' if c else 'FAIL ') + n); bad += 0 if c else 1
chk('PhotoPro has no network classes', not re.search(r'java\.net|HttpURLConnection|okhttp|URL\(', pp))
chk('PhotoPro catches OutOfMemoryError', 'OutOfMemoryError' in pp)
chk('PhotoEditIntent is pure (no android import)', 'import android' not in pe)
chk('PublishTarget is pure', 'import android' not in pt)
chk('ImageGen still has Gemini photo path', 'fun generate(' in ig)
blk = ma[ma.index('fun editPhoto('):ma.index('fun editedToSite(')]
chk('AI edit asks HAAN first', 'HAAN, BHEJO' in blk and 'AlertDialog' in blk)
chk('AI edit blocked words refuse', 'pe.blocked' in blk)
chk('level 3 honest fallback text', 'AI ne nahi' in ma)
chk('original never overwritten (history only)', 'editHistory' in ma and 'pickedPhoto = ' not in blk)
chk('Vercel token only in header', 'setRequestProperty("Authorization", "Bearer " + token)' in vp and not re.search(r'api\.vercel\.com[^"]*\$\{?token|[?&]token=', vp))
chk('Vercel needs WRITE permission', 'allows(ConnectorStore.perm' in vp)
chk('Vercel URL comes from the answer', 'parseUrl(text)' in vp)
chk('NovaService asks before Vercel', 'publishVercelRun(subject, html)' in ns and 'askFirst(' in ns[ns.index('private fun publishTargetBegin'):ns.index('private fun publishVercelRun')])
chk('Lovable never types passwords', 'passwords' in ns[ns.index('private fun lovableHandoff') - 200:ns.index('private fun lovableHandoff') + 3000] or 'Password' in ns[ns.index('private fun lovableHandoff'):ns.index('private fun lovableHandoff') + 3000])
chk('GitHub publish accepts bigger sites', '12_000_000' in gh or 'SiteGen.MAX_FINAL' in gh)
for i in ('pp_row', 'onPhotoEdit', 'editedToSite', 'LOVABLE PAR DAALO', 'PUBLISH (VERCEL)'): chk('ui has ' + i, i in ui)
sys.exit(1 if bad else 0)
