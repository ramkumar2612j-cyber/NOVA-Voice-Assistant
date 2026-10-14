# NOVA v47 handover (read after v46, v45, v44, v40 and HANDOVER_v16 hard rules)
Owner: phone only (Xiaomi 24116RNC1I, Android 36), Hinglish, short steps, evidence level on every claim, one "Aapko karna hai" line. Zip name MUST be NOVA_v15_assistant.zip.

## Report the owner sent (APK from run #6, app 6.23): 55 pass, 3 fail
- FAIL Accessibility: "Switch is ON but Android did not connect it" = Settings.Secure lists NOVA Phone Control but NovaAccessibilityService.instance == null (HealthCheck.kt). FAIL "NOVA service chal nahi rahi" + WARN "NOVA listening - Press ACTIVATE": ACTIVATE was not pressed. Task agent was "Band" only because of the Accessibility state.
- All "Samajh" tests, leads, offline queue, message mode, schedule, resume lines PASS (rules only, not phone behaviour).
- Owner says the switch is ON and the setting looks right on his phone but it does not work.
## What is known / unknown
- Accessibility config, manifest entry and onServiceConnected are unchanged from the original zip (read via diff). So no code change of mine/ChatGPT's alters how the service binds.
- UNKNOWN (HYPOTHESES only): (a) HyperOS/MIUI kills the process (no ACTIVATE = no foreground service) and leaves the service marked malfunctioning until it is toggled; (b) an exception on the main thread crashed the process. ErrLog is memory-only, so "RECENT ERRORS: none" does NOT rule out a crash.
## Changes (Kotlin NOT compiled, phone NOT tested)
- CrashNote.kt (+ class NovaApp, manifest android:name=".NovaApp"): writes last_crash.txt (uncaught exception: thread, class, masked message, 10 frames) and acc_events.txt (last 8 service events: connected / unbound / destroyed with times). Local only. SelfTest report now prints "LAST CRASH" and "ACCESSIBILITY EVENTS".
- NovaAccessibilityService: onAccessibilityEvent, tick and scan are wrapped in try/catch (an exception there can no longer kill the process); onServiceConnected/onUnbind/onDestroy record events.
- Tests: CrashNoteTest.kt (JVM, not run here), tools/test_crashnote_contract.py (structural, passes).
## Next
1. Owner builds, installs, presses ACTIVATE FIRST (starts the foreground service), then toggles Accessibility OFF/ON, then SAB TEST KARO > COPY REPORT.
2. Read "LAST CRASH" and "ACCESSIBILITY EVENTS" in that report: a crash text = fix that line; "none yet" = Android never connected it (OS/permission side: Allow restricted settings, Autostart, lock NOVA in recents).
