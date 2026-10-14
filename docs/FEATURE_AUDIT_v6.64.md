# NOVA v6.64 - feature audit (honest status)

RUN here (static, no Android build possible in this environment):
- Python contract tests: all `tools/test_*.py` PASS (37 scripts).
- `ui.html`: all JavaScript blocks pass `node --check` (the GLSL shader block is not JS).
- UI -> Bridge: every one of the 116 `Android.*` calls in `ui.html` has a matching `@JavascriptInterface` method in `MainActivity`.
- Kotlin cross-reference scan: every `Object.member(` call on a project `object` resolves to a declared member; no unknown class names.
- Rule scan: no `catch (_` and no `!!` in `app/src/main`.
- Voice recognition / voice lock / face / pattern: removed in v6.51; no source files remain, leftover wording removed in v6.64.

NOT RUN (needs CI / a phone) - do NOT call these working until checked:
- Gradle compile of NovaService, MainActivity, NovaHud, LlamaBrain and all other Android classes; Kotlin unit tests (60+ files).
- Vosk model download in CI (`tools/prepare_vosk_model.sh`), APK install, permission chain on a real phone.
- Wake word (Vosk / Porcupine), false wakes, Gemini / Groq listening, offline Hindi pack.
- Phone control by Accessibility (open, tap, type, scroll, screenshot, zoom), calls, WhatsApp / Instagram auto reply and delete, alarms, battery SOS, screen alerts, recordings, in-app update, chat speaker icon and CHAT switch.

How to verify: run CI, install `nova-apk`, go through `docs/PHONE_CHECKLIST.md`, then SETTINGS -> SELF TEST inside the app and send its result.
