#!/usr/bin/env python3
"""v7.136 contract: Jarvis voice replaces VoiSpark, is wired, safe, and the old Ultron-effect + normal voice stay as fallback. Source-level checks (Kotlin is compiled by local_compile_check.sh / GitHub Actions)."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
J = R / "app/src/main/java/com/nova/assistant"
rd = lambda p: p.read_text(encoding="utf-8")
api, cl, ns, ma, cfg = rd(J/"JarvisVoiceApi.kt"), rd(J/"JarvisVoiceClient.kt"), rd(J/"NovaService.kt"), rd(J/"MainActivity.kt"), rd(J/"Cfg.kt")
ui = rd(R/"app/src/main/assets/ui.html")
bad = []
def need(c, m):
    if not c: bad.append(m)
# VoiSpark is really gone from the app code
need(not (J/"VoiSparkApi.kt").exists() and not (J/"VoiSparkClient.kt").exists(), "VoiSpark files deleted")
for name, txt in (("kotlin", api+cl+ns+ma+cfg), ("ui", ui)):
    need(not re.search(r"voispark", txt, re.I), "no VoiSpark left in " + name)
need('"https://"' not in api or 'lower.startsWith("https://")' in api, "https parse")
need('scheme == "http" && host != "127.0.0.1" && host != "localhost"' in api, "http only for loopback")
need('"Bearer "' in cl and "JarvisVoiceApi.host(clean) != u.host.lowercase()" in cl, "bearer only to the saved host")
need("SecureStore.getSlot" in cl and "saveSlot" in ma and "JarvisVoiceClient.SLOT" in ma, "token in SecureStore slot")
need(not re.search(r'ErrLog\.\w+\([^)]*\b(token|key)\b', cl), "token must not go to ErrLog")
need(not re.search(r'(println|Log\.\w)\(', cl), "no console logging in client")
need(not re.search(r'Bearer [A-Za-z0-9]{20,}', api+cl+ui), "no hardcoded token")
need("c.instanceFollowRedirects = false" in cl, "no redirects")
# fallback chain: Jarvis -> Ultron effect -> normal voice
i_j, i_u = ns.find("startJarvis(t, spoken, id)) return true"), ns.find("cfg.ultronVoice && spoken.length <= 700 && startUltron(t, spoken, id)) return true")
need(0 < i_j < i_u, "Jarvis before existing Ultron path")
need("UltronPlayer.stop()" in ns and "fun startUltron" in ns and "t.speak(spoken, TextToSpeech.QUEUE_FLUSH" in ns, "old fallback voices kept")
need("res == JarvisSpeaker.FAILED" in ns and "startUltron(t, spoken, id)) return@Thread" in ns, "fallback on failure")
need(ns.count("JarvisSpeaker.stop()") >= 3, "stop wired")
need("JarvisSpeaker.generation()" in ns, "generation read on the calling thread")
need("pause()" in cl and "isPaused" in cl, "circuit breaker")
for k in ("jarvisOn", "jarvisUrl", "jarvisAnyLang", "jarvisMaxChars"): need(k in cfg, "cfg " + k)
for m in ["saveJarvisUrl","getJarvisUrl","saveJarvisToken","hasJarvisToken","clearJarvisToken","getJarvis","setJarvis","getJarvisAnyLang","setJarvisAnyLang","jarvisCheckStart","jarvisCheckResult"]:
    need("fun %s(" % m in ma, "bridge fun " + m)
    need('"%s"' % m in ui, "BRIDGE list " + m)
for i in ["jv_on","jv_any","jv_url","jv_key","jv_save","jv_del","jv_chk","jv_copy","jv_rep","jv_st"]:
    need('id="%s"' % i in ui, "ui id " + i)
srv = R/"tools/jarvis_server/server.py"
need(srv.exists() and (R/"tools/jarvis_server/README_HINGLISH.md").exists(), "server + README shipped")
# v7.137 one-tap pairing
need((J/"JarvisPairing.kt").exists() and (R/"app/src/test/java/com/nova/assistant/JarvisPairingTest.kt").exists(), "pairing code + JUnit")
need("fun jarvisPasteCode(" in ma and '"jarvisPasteCode"' in ui and 'id="jv_paste"' in ui, "paste bridge + button + BRIDGE list")
need("JarvisPairing.parse" in ma and "SecureStore.saveSlot(this@MainActivity, JarvisVoiceClient.SLOT, p.token)" in ma, "paste saves token encrypted")
nbk = rd(R/"tools/jarvis_server/NOVA_Jarvis_Colab.ipynb")
need("NOVA-JARVIS:" in nbk and "COPY CODE" in nbk, "notebook prints the pairing code")
# v7.138 phone-only Jarvis-style voice
need((J/"JarvisLocalVoice.kt").exists() and (R/"app/src/test/java/com/nova/assistant/JarvisLocalVoiceTest.kt").exists(), "local voice + JUnit")
j2, j3, u2 = ns.find("startJarvis(t, spoken, id)) return true"), ns.find("startJarvisLocal(t, spoken, id)) return true"), ns.find("cfg.ultronVoice && spoken.length <= 700 && startUltron(t, spoken, id)) return true")
need(0 < j2 < j3 < u2, "order: server Jarvis, phone Jarvis, Ultron, normal")
need("startJarvisLocal(t, spoken, id)) return@Thread" in ns and "JarvisVoiceApi.englishOk(spoken)" in ns and "cfg.applyVoice(t)" in ns, "fallback + English only + voice restored")
need("fun getJarvisLocal(" in ma and "fun setJarvisLocal(" in ma and '"getJarvisLocal"' in ui and 'id="jv_loc"' in ui and "jarvisLocal" in cfg, "switch wired")
if bad:
    print("FAIL:", *bad, sep="\n  "); sys.exit(1)
print("PASS jarvis voice contract")
