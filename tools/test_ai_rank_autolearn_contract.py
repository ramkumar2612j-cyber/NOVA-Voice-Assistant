"""v6.37 structural contract (Kotlin not run): best-AI-first ranking + auto learn wiring, and the safety rules that must stay."""
from pathlib import Path
R = Path(__file__).resolve().parents[1] / "app/src/main"
J = R / "java/com/nova/assistant"
ns = (J / "NovaService.kt").read_text(); rk = (J / "ProviderRanker.kt").read_text(); al = (J / "AutoLearnRules.kt").read_text()
ls = (J / "LearnStore.kt").read_text(); ui = (R / "assets/ui.html").read_text(); ma = (J / "MainActivity.kt").read_text()
checks = {
    "all 3 free-chain runs use the ranked order": ns.count("            rankedProviders(),\n") == 3,
    "all 3 free-chain runs teach the ranking": ns.count("rankedCall(p, body, key)") == 3,
    "rankedCall ignores nothing but the store decides offline/timeout": "ProviderRankStore.record" in ns and "null" in rk and "code in 100..597" in rk,
    "ranking only reorders (no provider added or dropped)": ".map { it.value }" in rk and "sortedWith" in rk,
    "Gemini (the only tool-capable AI) path is unchanged: still first and separate": "askCloud(pcm, said)" in ns and "1) Gemini" in ns,
    "auto learn runs on its own thread and respects the switch": "ExtCfg.autoLearn(this)) return" in ns and '"nova-autolearn"' in ns,
    "auto learn only after a successful, non-action answer": "fail.isEmpty() && !cloudActed) autoLearnFrom" in ns,
    "auto learn never learns commands / personal / live / failures": all(w in al for w in ("ACTION", "PERSONAL", "LIVE", "BAD_ANSWER", "\\\\d{4,}")),
    "auto notes are text only and capped": "MAX_AUTO_NOTES = 100" in al and "overflow" in ls,
    "auto notes need a closer match than kept notes": "AUTO_MIN_SCORE = 0.8" in al and "minScoreFor" in ls,
    "owner can switch auto learn off and see the AI order": "al_on" in ui and "getAutoLearn" in ma and "getAiOrder" in ma,
    # v6.38 owner decisions (RAKHO restored, no spoken yes, no forced voice/face lock) + rules that still stay
    "RAKHO restored: auto learn default OFF": 'getBoolean(AUTO_LEARN, false)' in (J / "ExtCfg.kt").read_text(),
    "risky yes/no switched off by the owner (default ON = never ask)": "skipRiskyConfirm" in ns and '"skip_risky_confirm", true' in (J / "ExtCfg.kt").read_text(),
    "speaker recognition / voice lock / face gate / pattern are fully removed (v6.51)": not any((J / n).exists() for n in ("VoiceLock.kt","OnnxSpeaker.kt","SpeakerRules.kt","FaceGate.kt","PatternUnlock.kt","PatternSetupActivity.kt")) and "voiceAllows" not in ns and "faceAllows" not in ns,
    "all permissions asked together on first open": "REQ_ALL" in ma and "firstRunPermissions" in ma and "nextSpecialPermission" in ma,
    "protected screens (bank, payment, Settings, installer) still blocked": "BLOCKED_PACKAGES" in (J / "Control.kt").read_text(),
    "no lock-screen gate (PhoneLock removed v6.66)": not (J / "PhoneLock.kt").exists() and "lockedPhoneAllows" not in ns,
    "learn still refuses sensitive topics": "I will not learn that kind of thing" in ns,
}
for k, v in checks.items():
    print(("PASS " if v else "FAIL ") + k)
raise SystemExit(0 if all(checks.values()) else 1)
