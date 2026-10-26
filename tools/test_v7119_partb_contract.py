#!/usr/bin/env python3
"""v7.119 Part B: mic loop uses PreRollRing (no per-chunk allocation). Static only: does NOT compile Kotlin."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
J = R/'app/src/main/java/com/nova/assistant'
ok = True
def chk(c, m):
    global ok
    print(('PASS ' if c else 'FAIL ') + m)
    ok = ok and c
svc = (J/'NovaService.kt').read_text(encoding='utf-8')
ring = (J/'PreRollRing.kt').read_text(encoding='utf-8')
test = (R/'app/src/test/java/com/nova/assistant/PreRollRingTest.kt').read_text(encoding='utf-8')
chk('class PreRollRing(' in ring and 'capacity: Int = 5' in ring, 'PreRollRing exists, default 5 slots')
chk('copyOf' not in ring, 'ring never allocates per chunk (no copyOf)')
chk('pre.add(buf.copyOf(n))' not in svc, 'old per-chunk copyOf removed from NovaService')
chk('val pre = PreRollRing(5, buf.size)' in svc, 'NovaService builds the ring once')
chk('pre.add(buf, n)' in svc, 'mic loop copies into a reused slot')
chk('pre.removeAt' not in svc, 'no ArrayList removeAt on pre')
chk('pre.arrayAt(gi), pre.lengthAt(gi)' in svc, 'gate replay uses lengthAt (reused arrays are longer than the chunk)')
chk('pre.lengthAt(pi)' in svc and 'for (c in pre)' not in svc, 'wake capture replay uses lengthAt, not array.size')
chk('WakeGateRules.replayCount(pre.size, gateOpen, true)' in svc, 'replayCount semantics untouched (all but newest)')
chk(len(re.findall(r'@Test', test)) >= 10, 'PreRollRingTest has >= 10 tests')
sys.exit(0 if ok else 1)
