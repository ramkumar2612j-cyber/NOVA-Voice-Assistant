#!/usr/bin/env python3
"""v7.178 desktop (laptop) app contract: files exist, workflow is separate from build.yml, node unit tests for the command parser pass. NOT a run of Electron."""
import os,shutil,subprocess,sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..'); D=os.path.join(R,'desktop')
bad=[]
for f in ('package.json','main.js','preload.js','commands.js','actions.js','sync-ui.js','test/commands.test.js'):
    if not os.path.exists(os.path.join(D,f)): bad.append('missing desktop/'+f)
if not os.path.exists(os.path.join(R,'.github/workflows/desktop.yml')): bad.append('missing desktop.yml')
m=open(os.path.join(D,'main.js'),encoding='utf-8').read() if not bad else ''
if m and ('contextIsolation: false' in m) and ('loadFile' not in m): bad.append('contextIsolation off needs local loadFile only')
if m and 'will-navigate' not in m: bad.append('navigation guard missing')
if shutil.which('node') and not bad:
    r=subprocess.run(['node','--test','test/commands.test.js'],cwd=D,capture_output=True,text=True,timeout=60)
    if r.returncode!=0: bad.append('node tests failed: '+r.stdout[-200:])
else: print('SKIP node tests (no node)')
for x in bad: print('FAIL',x)
print('FAIL desktop contract' if bad else 'PASS desktop contract'); sys.exit(1 if bad else 0)
