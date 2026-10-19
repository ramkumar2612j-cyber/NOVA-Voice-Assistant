#!/usr/bin/env python3
"""v7.81 PART 4 contract: QuickScan budget file present, wired add-only into capture / pipeline / service / laser / voice parser; phrase rules mirrored here."""
import re, sys, os
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java", "com", "nova", "assistant")
def rd(n): return open(os.path.join(R, n), encoding="utf-8").read()
bad = []
def need(c, m):
    if not c: bad.append(m)
q = rd("QuickScan.kt"); cap = rd("MovieScanCapture.kt"); vp = rd("VideoScanPipeline.kt"); ns = rd("NovaService.kt"); lo = rd("LaserOverlay.kt"); lg = rd("Logic.kt")
g = open(os.path.join(R, "..", "..", "..", "..", "..", "..", "build.gradle.kts")).read()
vn = re.search(r'versionName = "7\.(\d+)"', g); need(vn and int(vn.group(1)) >= 81, "versionName must be >= 7.81")
need("TOTAL_MS = 10_000L" in q, "10 s budget missing")
need("FRAMES = 3" in q and "GAP_MS = 700L" in q, "quick frames / gap missing")
need("gapMs: Long = MovieFrameRules.GAP_MS" in cap and "src.sleep(gapMs)" in cap, "capture gap parameter (default kept) missing")
need("budgetStartMs: Long = -1L" in vp and "QuickScan.searchAllowed" in vp and "callTextWith" in vp, "pipeline budget missing")
need("QuickScan.FRAMES, QuickScan.GAP_MS" in ns and "budgetStartMs = quickStart" in ns, "service quick capture / budget not wired")
need("QuickScan.TEXT_READ_MS" in ns and "QuickScan.IDENT_READ_MS" in ns, "first reading timeouts not wired")
need("postFallback(body, 15000L, 30000L)" in ns, "old follow-up limits must stay (add-only)")
need("LaserMath.clock(now - t0, en)" in lo, "laser clock not drawn")
need("QuickScan.wantsLook(text)" in lg, "voice rule missing in Logic")
# old public constants still there (add-only)
need("const val FRAMES = 4" in rd("ScreenVideoRules.kt") and "GAP_MS = 1500L" in rd("MovieFrameRules.kt"), "old constants must stay")
# phrase rules (mirror of QuickScan.wantsLook, sets read from the source)
def sset(name):
    m = re.search(r"val %s = setOf\((.*?)\)\n" % name, q, re.S); return set(re.findall(r'"([a-z0-9]+)"', m.group(1)))
LOOK, GOING, ASK, POINT, OTHER = (sset(n) for n in ("LOOK", "GOING", "ASK", "POINT", "OTHER"))
def look(text):
    t = [x for x in re.sub(r"[^a-z0-9]", " ", text.lower()).split(" ") if x and x != "nova"]
    if len(t) < 2 or len(t) > 10 or any(x in OTHER for x in t) or not any(x in LOOK for x in t): return False
    return ("kya" in t and "chal" in t and any(x in GOING for x in t)) or (any(x in POINT for x in t) and any(x in ASK for x in t))
for s in ["nova dekho btao kya chal ra h", "nova dekho batao kya chal raha hai", "dekho kya chal rha hai", "ye dekho batao", "nova is screen ko dekho aur batao"]:
    need(look(s), "should start scan: " + s)
for s in ["battery dekho batao", "gaana kya chal raha hai dekho", "gallery dekho", "time batao", "dekho", "whatsapp kholo dekho batao", "kya chal raha hai"]:
    need(not look(s), "must NOT start scan: " + s)
if bad:
    print("FAIL"); [print(" -", b) for b in bad]; sys.exit(1)
print("PASS test_part4_quickscan_contract")
