# NOVA Parts 6A–11 — integration and static review (2026-10-11)

## Evidence level (do not overstate)
- Parts 6A–11: **WRITTEN** in this project tree; the UI bridge and service routes are present.
- Python source-contract scripts: **17/17 PASS** after correcting two stale source assertions to match the current implementation (`SpeakerRules.effectiveThreshold`, and the two Z.ai provider routes / service stop window).
- Android/Kotlin Gradle build and JUnit suite: **NOT VERIFIED in this environment** (no Gradle wrapper, Gradle executable, Kotlin compiler or Android SDK available here).
- Phone behavior: **NOT PHONE-TESTED**. No feature is labelled CI-GREEN or PHONE-TESTED in this report.

## Integration inventory
- **6A Audio recording:** RecordRules + RecordSession; the single NovaService mic loop feeds recording chunks; stop notification / Settings / voice routes; storage export; bounded queue and time/storage stops.
- **6B Background video:** BackgroundVideoRules keeps normal camera-open requests on the Part 1 path; VideoRecordService has camera+microphone foreground notification, STOP action, front/back selection, file-growth check, max-time/storage stop, and MediaStore export.
- **6C Recordings manager:** list/play/share/delete with UI confirmation, voice count/play-last, auto-delete choices default OFF, and best-effort recovery for parser-readable crash files.
- **7 Free AI phone actions:** PhoneActionRules strictly parses allow-listed JSON and returns an action only; execution continues through the existing safe tool/confirmation path. Provider health/cooldown status is exposed in Settings.
- **8 Context memory:** ContextMemory resolves short follow-ups in memory with expiry and excludes risky actions from automatic repetition.
- **9 Teach once:** TeachRules/TeachStore and Settings bridge support add/list/delete plus export/import validation.
- **10 Routines:** RoutineRules + BrainStore-backed routines; safe kinds only, capped steps, and Settings add/edit/delete/list UI.
- **11 See the screen:** ScreenSession / NovaAccessibilityService routes; protected apps/screens stay blocked; screen text remains session-local unless the owner explicitly requests analysis and configured AI is used.

## Review passes completed here
1. **Wiring/source pass:** checked the MainActivity bridge methods, ui.html controls, NovaService routes, Android manifest declaration for VideoRecordService, and the mic feed hook.
2. **Logic/safety pass:** ran all 17 `tools/test_*.py` scripts individually; all passed. Existing `SafetyInvariantsTest.kt` includes tests for dangerous model tools, camera-parser separation, and protected screen blocks.
3. **Test inventory pass:** verified pure-rule JUnit files exist for BackgroundVideoRules, ContextMemory, PhoneActionRules, RecordRules, RecordingVoiceRules, RoutineRules, ScreenSession, and TeachRules. They were **not run** because Kotlin/Gradle are unavailable.

## Important limits
- Screen-off camera behavior is OEM/Android-version dependent; service reports failures rather than claiming a recording succeeded without file growth. Start video while NOVA is visible. Notification and camera/microphone privacy indicators remain visible.
- Video recording uses legacy Android Camera/MediaRecorder APIs; compatibility must be confirmed on the owner's specific phone by the CI APK and phone checklist.
- Audio recording records NOVA's own spoken replies/beeps and requires NOVA active; Android's mic indicator/recording notification cannot be hidden.
- Crash recovery can only promote media that Android's parser can read; it cannot guarantee repair of corrupted files.
- Provider quotas/models and server availability change; no provider is promised to always work.
- Accessibility cannot read/tap protected banking, payment, password, or FLAG_SECURE screens.

## Owner phone checklist after CI is green
1. Activate NOVA; confirm it stays quiet until the wake word is spoken and does not interrupt ordinary conversation/TV. Repeat in a noisy room and with another person's voice.
2. Audio: start from Settings, confirm visible notification + STOP, record 2 minutes with screen off, stop by voice and notification, play the saved file.
3. Video: start from the visible app, test front and back camera, confirm the status changes to ON only after file growth, lock screen for 2 minutes, stop via notification, play the file; report exact phone/Android version if screen-off capture stops.
4. Recordings manager: play/share one file, delete only after the confirmation dialog, test recovery while no recording is active, and confirm auto-delete is OFF by default.
5. Free-provider action: test harmless actions first (open app / torch); verify calls/messages still ask for spoken yes and sensitive screens remain blocked.
6. Context/teach/routines/screen: test “isko band karo” after a safe action; teach an alias then delete it; run a 2-step safe routine; try a protected app and confirm NOVA refuses to read/tap it.

## Contract-script corrections
- `tools/test_autosetup_contract.py` now checks both provider-chain routes and the actual `onDestroy` region, rather than assuming a narrower old code layout.
- `tools/test_voice_contract.py` now checks the active `SpeakerRules.effectiveThreshold(...)` call, which preserves the owner's tuned threshold. This changes a stale assertion only; it does not remove any app test or safety rule.
