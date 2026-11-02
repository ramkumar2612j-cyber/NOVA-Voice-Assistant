#!/usr/bin/env python3
"""v7.78 PART 2 speed contract: old caps kept, fixed sleeps replaced by early-exit waits, add-only."""
import re, sys
B = "app/src/main/java/com/nova/assistant/"
svc = open(B + "NovaService.kt", encoding="utf-8").read()
cloud = open(B + "CloudTaskText.kt", encoding="utf-8").read()
rules = open(B + "SpeedRules.kt", encoding="utf-8").read()
bad = []
def need(c, m):
    if not c: bad.append(m)
need("fun waitReady(minMs: Long, maxMs: Long" in svc, "waitReady helper missing")
need("waitReady(500L, 1800L)" in svc and "Thread.sleep(1800)" not in svc, "in-app search wait not replaced / cap changed")
need("waitReady(400L, 1300L)" in svc and "Thread.sleep(1300)" not in svc, "share wait not replaced / cap changed")
need("waitReady(100L, 400L) { am.isMusicActive == want }" in svc, "media confirm wait missing")
need("SpeedRules.steps(6400L, 300L)" in svc, "gmail poll missing")
need("SpeedRules.HEAR_RETRY_MS" in svc and "Thread.sleep(700) } catch (e: InterruptedException) { return first }" not in svc, "hearTwice retry")
need("GeminiSpeed.withFastThinking(model, body)" in cloud and "first.first == 400" in cloud, "CloudTaskText fast thinking / 400 fallback")
need("fun shouldStop" in rules and "HEAR_RETRY_MS = 350L" in rules, "SpeedRules incomplete")
# safety: music verification and WhatsApp batch waits must be untouched
need("Thread.sleep(1500) } catch (e: InterruptedException) { return Triple(MusicPlayRules.Outcome.CANNOT_TELL" in svc, "verifyPlayback must stay unchanged")
need("latch.await(17, TimeUnit.SECONDS)" in svc, "WhatsApp batch safety wait changed")
print("PASS test_part2_speed_contract" if not bad else "FAIL: " + "; ".join(bad))
sys.exit(1 if bad else 0)
