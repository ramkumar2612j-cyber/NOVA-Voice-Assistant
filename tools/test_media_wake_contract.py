#!/usr/bin/env python3
"""v7.204 MEDIA WAKE GUARD contract: static wiring only. Kotlin compile / JUnit NOT run here (GitHub does that)."""
import sys
A = "app/src/main/java/com/nova/assistant/"
g = open(A + "MediaWakeGuard.kt", encoding="utf-8").read()
svc = open(A + "JarvisService.kt", encoding="utf-8").read()
test = open("app/src/test/java/com/nova/assistant/MediaWakeGuardTest.kt", encoding="utf-8").read()
fails = []
def ck(c, l):
    print(("PASS " if c else "FAIL ") + l)
    if not c: fails.append(l)
ck("LOUD_MULTIPLIER = 2.5" in g and "COOLDOWN_MULTIPLIER = 2.0" in g, "media: 2.5x loudness, 2x cooldown")
ck("MediaWakeGuard.allowWake(peak, needLoud, mediaOnW)" in svc, "Porcupine wake path uses the guard")
ck("MediaWakeGuard.cooldownMultiplier(mediaOnW)" in svc, "cooldown uses the guard")
ck("isMusicActive" in g, "reads media state from AudioManager")
ck("PackageInstaller" not in g and "SecureStore" not in g, "guard touches no install or secrets")
for t in ("normalVoiceStillWakesWithoutMedia", "mediaBlocksFaintAndMidLoudness", "multipliers"):
    ck("fun " + t in test, "unit test " + t)
sys.exit(1 if fails else 0)
