#!/usr/bin/env python3
"""v7.129 contract: VoiSpark Ultron voice is wired, safe and add-only. Source-level checks (Kotlin is compiled by GitHub Actions)."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
J = R / "app/src/main/java/com/nova/assistant"
rd = lambda p: p.read_text(encoding="utf-8")
api, cl, ns, ma, cfg = rd(J/"VoiSparkApi.kt"), rd(J/"VoiSparkClient.kt"), rd(J/"NovaService.kt"), rd(J/"MainActivity.kt"), rd(J/"Cfg.kt")
ui = rd(R/"app/src/main/assets/ui.html")
bad = []
def need(c, m):
    if not c: bad.append(m)
need('const val BASE = "https://api.voispark.com"' in api, "base url")
need("/api/v2/tts/generate" in api and "/api/v2/tts/task/" in api and "/api/benefit" in api and "operation=tts" in api, "documented endpoints")
need('"fish-audio/drama-3"' in api, "model id")
need("ad2612df-ff6e-4291-bcc6-902767a6a5fd" in api, "voice id")
need('"Bearer "' in cl and 'u.host != VoiSparkApi.HOST' in cl, "bearer + host pin")
need('u.protocol != "https"' in cl, "https only")
need("SecureStore.getSlot" in cl and "saveSlot" in ma and "VoiSparkClient.SLOT" in ma, "key in SecureStore slot")
# key never reaches logs / report / source
need(not re.search(r'ErrLog\.\w+\([^)]*\bkey\b', cl), "key must not go to ErrLog")
need(not re.search(r'(println|Log\.\w)\(', cl), "no console logging in client")
need(not re.search(r'sk-[A-Za-z0-9]{10,}|Bearer [A-Za-z0-9]{20,}', api+cl+ui), "no hardcoded token")
need("generateBody" in cl and '"language"' not in api, "no language field (auto detect)")
# fallback chain intact: VoiSpark -> Ultron effect -> normal voice
i_v, i_u = ns.find("startVoiSpark(t, spoken, id)) return true"), ns.find("cfg.ultronVoice && spoken.length <= 700 && startUltron(t, spoken, id)) return true")
need(0 < i_v < i_u, "VoiSpark before existing Ultron path")
need("UltronPlayer.stop()" in ns and "fun startUltron" in ns and "t.speak(spoken, TextToSpeech.QUEUE_FLUSH" in ns, "old voice paths kept")
need("res == VoiSparkSpeaker.FAILED" in ns and "startUltron(t, spoken, id)) return@Thread" in ns, "fallback on failure")
need("VoiSparkSpeaker.stop()" in ns and ns.count("VoiSparkSpeaker.stop()") >= 3, "stop wired")
need("pause()" in cl and "isPaused" in cl, "circuit breaker")
need("voiSparkOn" in cfg and "voiSparkMaxChars" in cfg, "cfg")
for m in ["saveVoiSparkKey","hasVoiSparkKey","clearVoiSparkKey","getVoiSpark","setVoiSpark","voiSparkCheckStart","voiSparkCheckResult"]:
    need("fun %s(" % m in ma, "bridge fun " + m)
    need('"%s"' % m in ui, "BRIDGE list " + m)
for i in ["vs_key","vs_save","vs_del","vs_chk","vs_copy","vs_on","vs_rep"]:
    need('id="%s"' % i in ui, "ui id " + i)
if bad:
    print("FAIL:", *bad, sep="\n  "); sys.exit(1)
print("PASS voispark contract")
