#!/usr/bin/env python3
"""Part 5 contract: CommandCorpusTest.kt (150+ spoken commands) agrees with tools/classify_mirror.py (a Python PORT of the local
command pipeline, NOT a Kotlin run), and the 4 Part 5 fixes are present in the Kotlin source. Plain script, exits non-zero on failure."""
import os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import classify_mirror as m

SRC = os.path.join(HERE, "..", "app", "src")
def read(*p): return open(os.path.join(SRC, *p), encoding="utf8").read()
KT = read("test", "java", "com", "nova", "assistant", "CommandCorpusTest.kt")
LOGIC = read("main", "java", "com", "nova", "assistant", "Logic.kt")
UND = read("main", "java", "com", "nova", "assistant", "Understand.kt")
UNS = read("main", "java", "com", "nova", "assistant", "UnsupportedRules.kt")

fails = []
def check(ok, msg):
    if not ok: fails.append(msg)

rows = re.findall(r'^\s+(c|n)\("([^"]*)"(?:, "([^"]*)")?(?:, "([^"]*)")?(?:, num = (-?\d+))?\),\s*$', KT, re.M)
check(len(rows) >= 150, "corpus has %d rows, need >= 150" % len(rows))
check(len({r[1] for r in rows}) == len(rows), "duplicate phrases in corpus")
check(len(re.findall(r'^\s+[cn]\("', KT, re.M)) == len(rows), "a corpus row did not parse (format: c(\"say\", \"kind\"[, \"arg\"][, num = N]) / n(\"say\"))")

for typ, say, kind, arg, num in rows:
    got = m.classify(say)
    if typ == "n":
        check(got is None, "%r expected none, mirror gave %r" % (say, got)); continue
    if got is None: fails.append("%r expected %s, mirror gave none" % (say, kind)); continue
    check(got[0] == kind, "%r expected kind %s, mirror gave %r" % (say, kind, got))
    if arg: check(got[1] == arg, "%r expected arg %s, mirror gave %r" % (say, arg, got))
    if num: check(got[2] == int(num), "%r expected num %s, mirror gave %r" % (say, num, got))

# coverage of the command kinds the owner cares about (a corpus that lost a family would silently shrink)
kinds = {r[2] for r in rows if r[0] == "c"}
for k in ("open_app", "torch_on", "torch_off", "volume", "brightness_set", "brightness_step", "media", "global", "settings", "alarm", "timer", "call", "scroll", "tap", "type", "time", "date", "battery", "unsupported", "camera"):
    check(k in kinds, "corpus has no row of kind " + k)
check(sum(1 for r in rows if r[0] == "n") >= 8, "corpus needs at least 8 'goes to the AI' rows")

# the 4 Part 5 fixes are in the Kotlin source
check("private val NEGATION = setOf(" in LOGIC and LOGIC.count("if (negated) return null") >= 3 and "if (!negated && size <= 4" in LOGIC and "if (!negated) {\n        when (nav)" in LOGIC, "Logic.kt negation guard missing")
dnd = re.search(r'UnsupportedRules\.toggledSettingsPage', LOGIC)
check(dnd is not None and "negated" not in UNS, "do-not-disturb rule must stay outside the negation guard")
check("OPEN_TAIL_RE" in LOGIC and "removeSuffix(\" ko\")" in LOGIC, "Logic.kt open-app tail regex missing")
check("(?:open|launch)" in LOGIC and not re.search(r'\(\?:open\|launch\)\\\\s\+\(\?:" \+ OPEN_DONE_WORDS \+ "\)\)\?', LOGIC), "open/launch must need a karo-type word")
check('"play music" || hit == "pause music") && words.size > MEDIA_SHORT_MAX_WORDS' in UND, "Understand.kt long-media guard missing")
check("CANCEL_ALARM_PHRASES" in UNS and "CANCEL_TIMER_PHRASES" in UNS, "UnsupportedRules cancel alarm / timer missing")
check(not ({"alarm band karo", "alarm off", "alarm band", "alarm stop"} & (m.CANCEL_ALARM | m.CANCEL_TIMER | m.POWER | m.RESET)), "UnsupportedRules must not touch the wake-up alarm switch")
check(m.classify("alarm band karo") and m.classify("alarm band karo")[0] == "wake_off", "alarm band karo must stay wake_off")

if fails:
    print("FAIL (%d):" % len(fails)); [print("  -", f) for f in fails[:40]]; sys.exit(1)
print("PASS: %d corpus rows agree with the Python port; Part 5 source checks OK (Kotlin NOT run here)" % len(rows))
