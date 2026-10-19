#!/usr/bin/env python3
"""v7.71 PART 19 FULL REGRESSION glue (static). 1 FeatureStatus has the Part 16 callanswer and Part 18 safemode rows (warn only while safe mode is ON)
2 local_compile_check.sh builds the REAL org.json itself (the 29 old JUnit failures were a harness gap, not app bugs)
3 the Part 16 / 17 / 18 contracts all still pass"""
import os, subprocess, sys
H = os.path.dirname(os.path.abspath(__file__)); R = os.path.join(H, '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
fs, sh = rd('app/src/main/java/com/nova/assistant/FeatureStatus.kt'), rd('tools/local_compile_check.sh')
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
need('row(a, "callanswer"' in fs, 'FeatureStatus callanswer row')
need('row(a, "safemode"' in fs and 'if (safeNow) "warn" else "ok"' in fs, 'FeatureStatus safemode row (warn only while ON)')
need('stleary/JSON-java' in sh and 'JSON_JAR=$W/json.jar' in sh, 'compile script builds the real org.json')
for t in ('test_part16_contract.py', 'test_part17_contract.py', 'test_part18_contract.py'):
    r = subprocess.run([sys.executable, os.path.join(H, t)], capture_output=True, text=True)
    need(r.returncode == 0, t)
if bad: print('FAILED', len(bad)); sys.exit(1)
print('PASS part19 contract')
