# Feature regression sweep - v6.73 (Part 4)

Date 2026-10-14. Nothing was removed. No speaker recognition / voice lock exists in the source (checked: no source file, no UI, no manifest entry; two comments only mention that it was removed).

**How to read the columns**
- **Logic** = what the sandbox could run: JUnit (571 tests, real JUnit 4.13.2, real kotlinc 1.9.24, compiled against AOSP android-34 + stubs) and Python contract scripts (43). `PASS` = the listed tests ran and passed. `NOT TESTED` = no automated test reaches it.
- **Device** = a real phone with the real APK. **Every row is NOT TESTED**: there is no phone and no Gradle/AGP build here.
- A `PASS` in Logic never means "works on the phone". It means the decision rules and the source structure are as intended.
- FAIL found and fixed in this part: 1 (stale test `LogicTest.papaCallRoutesLocallyWithoutCloudKey`, see report). No FAIL remains.

| # | Feature | Evidence that ran | Logic | Device |
|---|---|---|---|---|
| 1 | Wake word (Vosk), strictness, adaptation, listen guard, 3-second command window | WakeRulesTest 7, WakeStrictTest 7, WakeAdaptTest 4, ListenGuardTest 17, ListenTest 7; test_three_second_command_listening, test_vosk_model_packaging, test_part1_voice_flow | PASS | NOT TESTED |
| 2 | Porcupine wake word (optional) + Vosk fallback | only compiled against a stub of the Porcupine API; no test | NOT TESTED | NOT TESTED |
| 3 | Local phone commands (battery, time, torch, volume, brightness, media, Home/Back/Recents, open apps, camera, settings) | LogicTest 37, RoutingTest 9, ControlTest 10, UnderstandTest 7, VoiceTextTest 7, PhoneActionRulesTest 4; test_understand_contract | PASS | NOT TESTED |
| 4 | Hindi / Hinglish commands and Hindi roman text | HindiRomanTest 12; test_hindi_contract | PASS | NOT TESTED |
| 5 | Offline Hindi pack download (HindiPack, ModelDownload) | structure only (test_hindi_contract); no JUnit | NOT TESTED | NOT TESTED |
| 6 | Calls and WhatsApp commands, DENY/CONFIRM | WhatsAppCommandRulesTest 3, LogicTest (call rules), SafetyInvariantsTest 16; test_call_delete_contract, test_part2_security | PASS | NOT TESTED |
| 7 | WhatsApp / Instagram auto reply, WhatsApp Business | AutoReplyRulesTest 9, WaBizTest 11; test_autoreply_contract | PASS | NOT TESTED |
| 8 | Message mode, driving mode, message delete | DrivingTest 18; test_call_delete_contract | PASS | NOT TESTED |
| 9 | Alarms and timers | TimersTest 8 | PASS | NOT TESTED |
| 10 | Scheduled tasks, weekday/priority, routines, morning routine, wake alarm | ScheduledDaysTest 3, RoutineRulesTest 2, WakeRulesTest | PASS | NOT TESTED |
| 11 | Battery guard + SOS, power guide | BatteryRulesTest 6, PowerGuideRulesTest 3 | PASS | NOT TESTED |
| 12 | Chat sessions: new chat, history list, open/delete (Part 1) | ChatSessionRulesTest 9; test_chat_sessions_contract | PASS | NOT TESTED |
| 13 | Backup AI keeps conversation + task; full-chat window; "setup check" report (Part 2, v6.71) | FallbackContextTest 15, ConfigReportTest 7, FreeProvidersTurnsTest 3; test_fallback_context_contract | PASS | NOT TESTED |
| 14 | AI provider chain and failover (Gemini keys/models, Groq, OpenRouter, GLM, free providers), ranking, usage counters | FreeProvidersTest 41, ProviderRankerTest 10, ApiUsageTest 7; test_chain_contract, test_ai_rank_autolearn_contract, test_fast_complete_contract | PASS (rules); real provider calls NOT TESTED | NOT TESTED |
| 15 | Gemini audio listening / transcript; Groq speech-to-text | GeminiTranscriptTest 6; test_gemini_listen_contract. GroqStt itself: no test | PASS (parser) / NOT TESTED (Groq call) | NOT TESTED |
| 16 | Offline model slot (local brain, local chat, local skills) | LocalBrainTest 9, LocalChatTest 10, LocalSkillsTest 14. **LlamaBrain.kt (real llama.cpp adapter) was NOT compiled** (needs llamacpp-kotlin + coroutines jars; a stub stood in) | PASS (slot/rules); LlamaBrain NOT TESTED | NOT TESTED |
| 17 | Agent loop: planner, executor, verifier, repair, in-app jobs, background agent | AgentRulesTest 20; test_agent_contract 47 checks, test_agent_plan_contract, test_agent_background_contract, test_inapp_jobs_contract 16 | PASS | NOT TESTED |
| 18 | Task ledger, resume, progress lines, interruption note (Part 3) | AgentTaskRulesTest 10, AgentProgressTest 9; test_agent_task_ledger_contract 23, test_agent_restore_contract 15 | PASS | NOT TESTED |
| 19 | Personal memory (YAAD), context memory, no secrets in memory | PersonalMemoryTest 14, ContextMemoryTest 3, SafetyInvariantsTest; test_memory_contract 20 | PASS | NOT TESTED |
| 20 | Teach / shortcuts / auto-learn | TeachRulesTest 5, AutoLearnRulesTest 10; test_learn_contract 16 | PASS | NOT TESTED |
| 21 | Local knowledge + public-knowledge fallback | KnowledgeMatcherTest 13, PublicKnowledgeFallbackTest 2; test_knowledge_contract 14, test_public_knowledge_contract | PASS | NOT TESTED |
| 22 | Screen monitor, screen alerts, screen session, safe reply drafts | ScreenAlertTest 6, ScreenSessionTest 12; test_screen_alert_contract, test_screen_session_contract, test_screen_reply_safety_contract 8 | PASS | NOT TESTED |
| 23 | Accessibility phone control (tap, type, scroll, screenshot, zoom), install-screen tap | InstallTapTest 2 + safety rules in SafetyInvariantsTest. The Accessibility service itself runs only on a phone | PASS (rules); service NOT TESTED | NOT TESTED |
| 24 | Safety invariants: sensitive/banking screens blocked, host allow-list, no secrets stored, size caps, update rules strict | SafetyInvariantsTest 16; test_part2_security 16, test_review_security_contract 14 | PASS | NOT TESTED |
| 25 | Audio / video recording, background video | RecordRulesTest 12, RecordingVoiceRulesTest 2, BackgroundVideoRulesTest 3, WavPcmTest 4; test_agent_background_contract. Services themselves: phone only | PASS (rules) | NOT TESTED |
| 26 | Image generation (free providers) | ImageIntentTest 6, FreeImageRulesTest 6; test_image_contract | PASS (rules); provider calls NOT TESTED | NOT TESTED |
| 27 | Website generator (SiteGen) | test_site_contract (structure only) | PASS (structure) | NOT TESTED |
| 28 | Lead finder queue | LeadQueueRulesTest 6; test_lead_queue_contract | PASS | NOT TESTED |
| 29 | Offline job queue + WorkManager worker | OfflineQueueRules referenced by a JUnit class and a contract script; the Worker itself compiled only against a WorkManager stub | PASS (rules) / NOT TESTED (worker) | NOT TESTED |
| 30 | In-app APK updater, feed rules | UpdaterTest 8; SafetyInvariantsTest updateRulesStayStrict; test_self_improve | PASS (rules) | NOT TESTED |
| 31 | UI (ui.html, HUD, orb, bridge) | 17/17 JavaScript blocks pass `node --check` (the 18th `<script>` is the GLSL shader, not JS); all 122 `Android.*` calls in ui.html have a `@JavascriptInterface` method; HudMathTest 4, HudMotionTest 6; test_ui_contract, test_ui_v37_contract, test_ui_tier_contract, test_hud_controls | PASS | NOT TESTED |
| 32 | Feature status box, health check, self test, crash note, error log, auto setup | AutoTestRulesTest 3, ErrLogTest 5, CrashNoteTest 3, AutoSetupPlanTest 6; test_feature_status_contract 18, test_feature_check_contract, test_crashnote_contract, test_autosetup_contract, test_no_automatic_health_popup | PASS | NOT TESTED |
| 33 | Music / YouTube play rules | MusicPlayRulesTest 4 | PASS | NOT TESTED |
| 34 | Quiet / silent mode | QuietTest 5, SilentRulesTest 11 | PASS | NOT TESTED |
| 35 | Calendar read + meeting reminders | no JUnit, no contract script | NOT TESTED (compiles) | NOT TESTED |
| 36 | GitHub publish (owner token) | no test | NOT TESTED (compiles) | NOT TESTED |
| 37 | Skill packs | `tools/validate_skillpack.py`: 193 entries / 1,157 variants OK | PASS | NOT TESTED |
| 38 | Spec contract | SpecContractTest 6; test_spec_contract | PASS | NOT TESTED |
| 39 | Voice flow / chat speaker button | VoiceFlowRulesTest 11; test_chat_voice_contract, test_part1_voice_flow | PASS | NOT TESTED |
| 40 | No auto-start at boot, optional permissions, permission list | test_autosetup_contract, SafetyInvariantsTest; manifest permission list unchanged (INTERNET ... CALL_PHONE, QUERY_ALL_PACKAGES) | PASS | NOT TESTED |
| 41 | Speaker recognition / voice lock | **Intentionally absent** (removed v6.51). Not added | n/a | n/a |

Totals (40 feature rows + 1 intentionally absent): Logic **PASS 36** (rows 14, 15, 16, 23, 25, 26, 29, 30 are PASS for their rules only; their network / service / native parts are NOT TESTED), Logic **NOT TESTED 4** (rows 2, 5, 35, 36). FAIL 0. Device: **0 of 40 tested**.


---
## Delta v6.75 (Part 5) - what changed since the table above
Status words: PASS = Python port / contract script agrees; Kotlin JUnit and device are NOT RUN / NOT TESTED for every row below.
| Area | Change | Existing behaviour that must still work | Check | Status |
|---|---|---|---|---|
| Torch / volume / brightness / media / navigation | negated sentences ("... mat karo") no longer run the positive command | "volume kam karo", "torch on", "brightness 70", "home", "back", "pause music" | corpus rows + `negationNeverRunsThePositiveCommand` | PASS (port) / NOT RUN |
| Do not disturb | unchanged: the negation guard is NOT around it | "do not disturb", "do not disturb on" -> Settings DND page | corpus rows | PASS (port) / NOT RUN |
| Open an app | "<app> khol do", "<app> ko kholo", "<app> open karo" added | "open whatsapp", "whatsapp kholo", "call of duty kholo", "camera kholo", settings pages; "is store open" stays no command | corpus rows + `openAppWithKholDoAndOpenKaro` | PASS (port) / NOT RUN |
| Media | "play music" / "pause music" only for sentences of 4 words or fewer | "gaana band karo", "gaana chalao", "next song", "pichla gaana" | corpus rows + `namedSongIsNotAPlainPlayCommand` | PASS (port) / NOT RUN |
| Alarm / timer | "cancel alarm", "timer cancel karo" answered honestly | "alarm band karo" = wake_off, "set alarm for 7 am", "5 minute ka timer lagao" | corpus rows + `cancelAlarmAndTimerGetAnHonestAnswer` | PASS (port) / NOT RUN |
| Power / reset | answered clearly (v6.74), covered by corpus | "unlock phone" stub | corpus rows + `unsupportedThingsNeverBecomeAnAction` | PASS (port) / NOT RUN |
| Voice diagnosis | JUnit added for v6.74 `VoiceDiagnosis` | wake / TTS / battery rows in FeatureStatus | `VoiceDiagnosisTest` 5 tests | NOT RUN |
Rows 1-41 above are otherwise unchanged. Speaker recognition / voice lock: still intentionally absent. Device: 0 of the rows tested.
