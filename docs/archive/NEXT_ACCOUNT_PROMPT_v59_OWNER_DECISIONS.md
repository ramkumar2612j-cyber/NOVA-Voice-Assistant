# NOVA v6.38 - owner decisions (Hinglish request)

Owner said: restore what v6.37 removed; remove the spoken-yes rule for risky work and the voice/face check; ask all permissions together after install.

Changes (source only; NOT compiled here - no network/kotlinc in the sandbox; NOT CI-GREEN; NOT PHONE-TESTED):
- ExtCfg.autoLearn default ON -> OFF. The RAKHO flow for learned notes is back (the Auto-seekho checkbox in Settings can switch it on again).
- ExtCfg.skipRiskyConfirm (default ON): askFirst() runs at once even with must=true; risky taps and agent CONFIRM verdicts no longer ask a spoken yes.
  Kept on purpose: Control.BLOCKED_PACKAGES / HARD words (bank, payment, UPI, password, OTP, Settings, installer, Play Store, NOVA itself) stay blocked.
- Voice lock and face gate: ExtCfg.voiceLock()/faceGate() return false until the owner switches one ON in Settings (lock_opt_in). Old saved ON values are ignored.
  NovaService no longer turns the voice lock ON by itself at service start or after enrolling (the start-time auto-ON blocked every voice until a voiceprint existed).
- MainActivity.firstRunPermissions(): one Android dialog on first open for mic, camera, contacts, calendar, notifications (REQ_ALL = 9).
  Then nextSpecialPermission() (from onResume, one-time chain) opens, once each: overlay, write settings, battery optimisation, exact alarm, full-screen intent (Android 14+), notification access, accessibility.
  Android does not allow these special items in the normal dialog, and NOVA cannot grant them itself.
- tools/test_ai_rank_autolearn_contract.py updated to guard the v6.38 rules.
Known pre-existing failures (unchanged): test_agent_task_ledger_contract.py, test_autosetup_contract.py.
Risks to tell the owner: anyone near the phone can now give commands; a wrong order can send/delete without asking; a wrong AI answer is no longer saved automatically.

## v6.38 UI lag / screen-size pass (static audit only; no phone, no emulator)
- ui.html: every setInterval now skips its tick while the page is hidden (shim in <head>); chat/log lists keep only the last 80/120 messages; extra CSS (id resp-v38) for <=340px wide phones, short phones (orb canvas had a fixed 250px and overlapped), tablets/foldables (content max 640px, centred), text-size-adjust.
- AndroidManifest MainActivity: configChanges (orientation, screenSize, layout, uiMode, density, fontScale ...) so rotate / fold / font change / dark mode no longer recreates the activity and reloads the whole page + 3D orb shader.
- MainActivity.hardenWebView: textZoom capped at 115 (big system font no longer breaks the layout).
- Skills screen texts no longer promise a spoken yes (agent, publish, driving reply).
- Already present before this pass (kept): lowfx governor (auto + weak-phone start), global backdrop-filter kill, orb 60/30 fps cap, shader scale fallback.
- NOT measured: real frame rates. Test on one small (<=5.5"), one mid and one large/tablet device.

## v6.38 full audit / autopsy (static; no Kotlin compile, no phone)
Checked: 140 bridge methods vs every Android.* call in ui.html (0 missing); all inline JS blocks pass node --check; all 15 manifest components have a class; all R.* / @resource refs resolve; heuristic cross-file Kotlin member check (106 objects, 0 unresolved); all 25 Logic command kinds have a runCommand branch; Kotlin->JS callbacks (onFlow, onNova, onSite ...) all defined; skillpack validator 193 entries / 1157 variants; 34/34 tools/test_*.py pass.
Fixed: versionName 6.37 -> 6.38 (versionCode default 14 -> 15); test_agent_task_ledger_contract + test_autosetup_contract updated to the real code (recoverInterrupted wired via appCtx; 3 guarded runners); README yes-rule lines; SelfTest torch test now sends one extra OFF.
Known, harmless: ui.html has no #mode badge and no #tf canvas (modeTxt / transformer canvas code returns early). skillpacks/starter.json is only the update-feed sample, it is not packed in the APK.
Still unproven: Gradle build, runtime on a phone, the owner's reported torch-by-itself / repeating command / late words (no code path found in the audit except the manual SELF TEST torch step).
