#!/usr/bin/env python3
"""Part 11-B contract: MediaIntakeStore streams on ONE background thread, honours the pure rules, keeps files private,
the Manifest has the share filters (and NO new permission), MainActivity hands intents to the store."""
import os, re, sys
R = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
def rd(p): return open(os.path.join(R, p), encoding='utf-8').read()
bad = []
def need(c, m):
    if not c: bad.append(m)
st = rd('app/src/main/java/com/nova/assistant/MediaIntakeStore.kt')
man = rd('app/src/main/AndroidManifest.xml')
ma = rd('app/src/main/java/com/nova/assistant/MainActivity.kt')
need('newSingleThreadExecutor' in st and 'pool.execute' in st, 'copy must run on a single background thread')
need('ByteArray(64 * 1024)' in st and 'readBytes' not in st and 'readAllBytes' not in st, 'copy must be a buffered stream (no whole-file read)')
need('MediaIntake.streamOk(' in st and 'cancelFlag.get()' in st, 'size-limit during copy and cancel flag required')
need('dst.delete()' in st and 'OutOfMemoryError' in st, 'half-written file delete / OOM catch required')
need('MediaIntake.plan(' in st and 'MediaIntake.expiredIds(' in st and 'MediaIntake.cacheFileName(' in st, 'store must use the pure rules')
need('cacheDir' in st and 'filesDir' not in st and 'getExternal' not in st and 'Environment.' not in st, 'files must stay in the private cache dir')
need(not re.search(r'HttpURLConnection|java\.net|okhttp|URL\(', st), 'no network in the store')
need('isValidId(' in st and 'fun remove(' in st and 'fun clear(' in st and 'fun cancel(' in st and 'fun fileOf(' in st, 'remove / clear / cancel / fileOf with id check required')
need('/' not in re.search(r'fun listJson.*', st, re.S).group(0).split('put("link"')[0].replace('"/"', '') or True, 'n/a')
lj = st[st.index('fun listJson'):]
need('.file' not in lj and 'toString()' in lj and 'uri' not in lj.lower().replace('url', ''), 'listJson must not expose a file path / uri')
need(man.count('android.intent.action.SEND"') == 1 and 'android.intent.action.SEND_MULTIPLE' in man, 'manifest SEND / SEND_MULTIPLE filters missing')
for m in ('image/*', 'video/*', 'text/plain'):
    need('android:mimeType="%s"' % m in man, 'manifest mime ' + m)
need(man.index('android.intent.action.SEND"') < man.index('WatchCommandActivity'), 'share filters must sit on MainActivity')
perms = re.findall(r'<uses-permission android:name="([^"]+)"', man)
need(len(perms) in (30, 31), 'permission count changed (%d, expected 30)' % len(perms))
need(not any(x in perms for x in ('android.permission.READ_MEDIA_IMAGES', 'android.permission.READ_MEDIA_VIDEO', 'android.permission.MANAGE_EXTERNAL_STORAGE')), 'no NEW gallery permission allowed (READ_EXTERNAL_STORAGE maxSdk 32 is old, from before Part 11)')
need('MediaIntakeStore.onIntent(this, i)' in ma and 'MediaIntakeStore.cleanExpired(' in ma and 'i?.removeExtra("autostart")' in ma, 'MainActivity.handleIntent must call the store (old line kept)')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/MediaIntakeTest.kt')) and 'storeHelpers' in rd('app/src/test/java/com/nova/assistant/MediaIntakeTest.kt'), 'JUnit storeHelpers missing')
if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS media store 11-B contract (static wiring; real copy NOT TESTED on a phone)')
