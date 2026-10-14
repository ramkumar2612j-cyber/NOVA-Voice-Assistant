# NOVA v42 — Stop automatic Health Check popup

## Kya badla
- `MainActivity.onResume()` no longer triggers `HealthCheck.auto()`.
- `ui.html` startup `ucAuto()` no longer opens Update/Setup Center on first run, new version, resume, or missing permissions. The owner can still open it manually via Settings > Safe Updates. Optional update-feed refresh remains silent.
- Removed automatic HealthCheck dialog behavior. Health report collection (`HealthCheck.run/report`) remains available to the explicit SAB TEST KARO flow.
- Floating HUD is deferred during a fresh wake capture until speech for a command is detected, preventing a wake-only false positive from briefly opening the card. Confirmation flows can still show the card immediately.
- `versionName` bumped to 6.21 and default `versionCode` to 10.
- `ui.html` CHANGES now states this exact behavior and evidence level.
- Added `tools/test_no_automatic_health_popup.py` regression contract.

## Evidence
- Source inspection found the repeated dialog trigger: `MainActivity.onResume()` called `HealthCheck.auto()`, which waited 1.5 seconds and displayed an AlertDialog for missing important permissions. Its duplicate guard was process-local, so a cold launch could show it again.
- `tests ran here`: all 25 `tools/test_*.py` scripts PASS, including the new no-auto-popup contract. `node --check` passed on all 14 inline JavaScript blocks.
- No XML files were changed. Kotlin compiled: NO. Full Android build: NOT RUN here because Android Gradle tooling is not installed in this workspace. Phone tested: NO.

## Safety / compatibility
- This change only removes automatic health-check popup behavior. It does not remove permission checks, self-test reporting, the wake-word listener, floating HUD on a genuine wake, or Android-required manual special permission switches.
- Keep all existing files. Do not commit API keys/tokens.

## Next
1. Verify the packaged ZIP contains all original paths plus this handover and `tools/test_no_automatic_health_popup.py`; keep it under 25 MB with no build/.gradle/__pycache__.
2. Upload `NOVA_v15_assistant.zip` to the owner's repo and run the `Build NOVA from zip` workflow. Check green run and `nova-apk` artifact. Owner then installs and confirms the popup no longer appears.
