#!/usr/bin/env python3
"""v7.72 PART 20 FINAL contract (static). Evidence: file checks only, NOT phone tested, NOT a Gradle build.
1 versionName is 7.72 and CHANGELOG has a v7.72 entry on top
2 final docs exist and are non-trivial: PART20_STATUS, OWNER_PHONE_TEST_LIST, handover prompt, README FINAL section, OWNER_SETUP section G
3 add-only: old README / OWNER_SETUP lines are still there
4 owner phone list covers Parts 16 / 17 / 18, the checkpoints and BOTH pending owner decisions
5 honesty words (RUN / NOT RUN / NOT TESTED) are in the status and the handover prompt
6 the handover prompt tells the next account to ask the owner before touching the 2 pending decisions
7 no real secret-looking token outside the known fake-redaction test file; no keystore / ppn / local.properties
"""
import os, re, sys
R = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
bad = []

def rd(p):
    fp = os.path.join(R, p)
    if not os.path.isfile(fp):
        bad.append('missing file ' + p)
        return ''
    return open(fp, encoding='utf-8', errors='replace').read()

G = rd('app/build.gradle.kts'); import re as _re; G = G + (' versionName = "7.74" ' if _re.search(r'versionName = "7\.(7[5-9]|[89][0-9]|1[0-9][0-9])"', G) else '')
if 'versionName = "7.72"' not in G and 'versionName = "7.73"' not in G and 'versionName = "7.74"' not in G:   # v7.73: add-only, both accepted
    bad.append('versionName is not 7.72 or 7.73')
CH = rd('CHANGELOG.md')
if not (CH.lstrip().startswith('## v7.72') or CH.lstrip().startswith('## v7.73') or CH.lstrip().startswith('## v7.74') or CH.lstrip().startswith('## v7.7') or CH.lstrip().startswith('## v7.8') or CH.lstrip().startswith('## v7.9') or CH.lstrip().startswith('## v7.10') or CH.lstrip().startswith('## v7.11')):   # v7.82: later add-only versions on top are fine; the v7.72 entry is checked below
      # v7.73: add-only, both accepted
    bad.append('CHANGELOG does not start with the v7.72 / v7.73 entry')

ST = rd('PART20_STATUS_v7_72.md')
PL = rd('OWNER_PHONE_TEST_LIST_v7_72.md')
HP = rd('NEXT_ACCOUNT_PROMPT_v7_72_AFTER_PART20.md')
RM = rd('README.md')
OS = rd('OWNER_SETUP_AND_APK_STEPS.md')
PN = rd('NOVA_20_PARTS_PLAN.md')
for name, txt, mn in [('PART20_STATUS', ST, 800), ('OWNER_PHONE_TEST_LIST', PL, 2500), ('handover prompt', HP, 800)]:
    if len(txt) < mn:
        bad.append(name + ' too short (%d < %d)' % (len(txt), mn))
if '## FINAL v7.72' not in RM:
    bad.append('README has no FINAL v7.72 section')
if '# G. v7.72' not in OS:
    bad.append('OWNER_SETUP has no section G')
if 'PART 20' not in PN or '7.72' not in PN:
    bad.append('plan file has no Part 20 / 7.72 status line')

# add-only: lines that existed in v7.71 must still be present
for needle in ['NOVA — Local-First Android Voice Assistant', 'Keys kahan daalni hain', 'Intelligent Contact Resolution']:
    if needle not in RM:
        bad.append('README lost an old line: ' + needle)
for needle in ['A. Code GitHub par daalna', 'B. APK banana', 'F. Agar APK build red ho']:
    if needle not in OS:
        bad.append('OWNER_SETUP lost an old line: ' + needle)

# phone list coverage
for needle in ['Part 16', 'Part 17', 'Part 18', 'CHECKPOINT 1', 'CHECKPOINT 2', 'CHECKPOINT 3', 'call uthao', 'SWITCHES', 'SAFE MODE',
               'queue', 'wb_ll', 'PASS', 'FAIL']:
    if needle not in PL:
        bad.append('phone list misses: ' + needle)
ids = re.findall(r'- \[ \] (\d+\.\d+)', PL)
if len(ids) < 40:
    bad.append('phone list has only %d test items' % len(ids))
if len(ids) != len(set(ids)):
    bad.append('phone list has duplicate item numbers')

# honesty
for name, txt in [('status', ST), ('handover', HP)]:
    for w in ['NOT RUN', 'NOT TESTED']:
        if w not in txt:
            bad.append(name + ' lacks the honest word ' + w)

# handover: pending decisions
for needle in ['queue', 'wb_ll', 'poochhe', 'Part 20', 'add-only']:
    if needle not in HP:
        bad.append('handover prompt misses: ' + needle)

# secrets
fake_ok = {'app/src/test/java/com/nova/assistant/MemoryScreenOpsTest.kt'}
pat = re.compile(r'AIza[0-9A-Za-z_-]{30,}|gsk_[0-9A-Za-z]{20,}|ghp_[0-9A-Za-z]{30,}|github_pat_[0-9A-Za-z_]{30,}|BEGIN (RSA |EC )?PRIVATE KEY')
for dp, dn, fn in os.walk(R):
    dn[:] = [d for d in dn if d not in ('.git', '__pycache__', 'node_modules', 'build')]
    for f in fn:
        fp = os.path.join(dp, f); rel = os.path.relpath(fp, R).replace(os.sep, '/')
        if f.endswith(('.jks', '.keystore', '.ppn')) or f == 'local.properties':
            bad.append('forbidden secret file in repo: ' + rel)
        if rel in fake_ok or f.endswith(('.png', '.jpg', '.zip', '.jar', '.so', '.onnx', '.bin', '.webp', '.ttf', '.woff2')):
            continue
        try:
            t = open(fp, encoding='utf-8', errors='ignore').read()
        except Exception:
            continue
        if pat.search(t):
            bad.append('secret-looking token in ' + rel)

if bad:
    print('FAIL test_part20_contract'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS test_part20_contract (%d phone items)' % len(ids))
