"""wip13 contract: music commands never fake success and never switch apps silently. Source-level checks (no Android needed)."""
import pathlib, re, sys
R = pathlib.Path(__file__).resolve().parents[1] / "app/src/main"
svc = (R / "java/com/nova/assistant/NovaService.kt").read_text()
rules = (R / "java/com/nova/assistant/MusicPlayRules.kt").read_text()
main = (R / "java/com/nova/assistant/MainActivity.kt").read_text()
fails = []
def need(cond, msg):
    if not cond: fails.append(msg)

i = svc.index("private fun musicPlay(")
j = svc.index("v6.92: works with ANY installed app")
body = svc[i:j]
need("verifyPlayback(" in body, "musicPlay must verify playback")
need("MEDIA_PLAY_FROM_SEARCH" in body, "musicPlay must use the standard play-from-search intent")
need("setPackage(pkg)" in body, "musicPlay must target ONLY the requested app package")
for other in ("youtube", "Youtube", "YouTube"):
    need(other not in body, "musicPlay must not mention " + other + " (no auto redirect)")
need("openApp(req.appLabel)" in body, "only the requested app may be opened as fallback")
# every place that starts a different app must go through the yes/no question
need("musicAltOffer(" in body, "alternative apps only through musicAltOffer")
k = svc.index("private fun musicAltOffer(")
alt = svc[k:svc.index("private fun verifyPlayback(")]
need("pending = Pending(" in alt and "askFirst" not in alt, "alternative-app question must be a real pending yes/no, not skippable askFirst")
# the old unconditional success text must be gone
need("chala diya" not in body and "Playing $q on" not in body, "old unverified success text still present")
need("songChoiceListener" in svc and "songChoiceListener" in main, "song popup must be wired in service and activity")
need("matchSpoken(" in svc, "spoken song choice must be wired")
need("fun judge(" in rules and "VERIFIED_MATCH" in rules, "judge/Outcome missing")
need("musicBegin(musicReq)" in svc and "musicPlay(musicReq)" not in svc.replace("private fun musicPlay(", ""), "call sites must use musicBegin")
for keep in ("fun appControl(", "WhatsAppTarget.App.BUSINESS", "contactChoiceListener"):
    need(keep in svc or keep in main, "existing feature removed: " + keep)
if fails:
    print("\n".join("FAIL: " + f for f in fails)); sys.exit(1)
print("music contract OK")
