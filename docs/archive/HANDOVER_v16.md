# NOVA v16 handover (read this first)

## Owner and how to work with them
- Owner works ONLY from an Android phone (Chrome + GitHub web). No PC. Only GitHub Actions compiles.
- Reply in Hinglish. One step at a time, short messages (phone shows ~7 lines). Give full clickable URLs.
- Never write DONE / WORKING / VERIFIED without evidence. Say the evidence level: "tests passed on my computer", "Kotlin not compiled", "not phone tested".
- Owner wants results, not excuses. If something cannot be done, say plainly what and why, and offer the closest thing that works.
- Repo: https://github.com/devboutique0-wq/NOVA (public, branch main), package com.nova.assistant.
- Upload zip: https://github.com/devboutique0-wq/NOVA/upload/main (keep the zip name), Commit changes.
- Then https://github.com/devboutique0-wq/NOVA/actions : run "Unpack zip", then "Build and Test APK". If red, owner sends a screenshot of the red lines only.

## Hard rules (do not remove)
- Risky actions (call, message, payment, delete) need a spoken local "yes".
- No silent installs. API keys never in logs or repo. NOVA never rewrites its own code.
- Protected screens never go to the cloud.
- Android does not allow silent permissions: Accessibility, notification access, overlay, brightness need one manual switch in Settings.
- Do not build anything that automates logging out / into AI accounts to get around usage limits. Owner agreed to skip it.

## State of this zip (v16 = v15 zip from the previous account + fixes)
Verified by reading the zip:
- ui.html scroll fix present (`app.className='wrap '+...`, the `wrap` class is kept).
- Update Center / "KYA NAYA HAI" popup does not auto-open: only the UPDATE CENTER button calls ucOpen; ucMaybe is a no-op.
- FREE FALLBACK checkbox (id i_on) is hidden by a script near the end of ui.html (still in DOM).
Changed in v16:
- Removed the "LOCAL MODE - no API key needed" badge (ui.html), modeTxt is null-safe.
- NEW website by voice: SiteIntent.kt (parser, pure), SiteGen.kt (writes one index.html with the owner's Gemini key(s), then Groq key; saves to Downloads > NOVA; failure text never names a provider), NovaService.siteBegin + siteListener, MainActivity wiring, ui.html onSite (chat message + DEKHO preview in a sandboxed iframe, scripts allowed, no same-origin so it cannot reach the Android bridge).
- tools/test_site_contract.py (15 cases) mirrors SiteIntent word sets.
Evidence: all 9 Python tests in tools/ pass on my computer; node --check finds no JS syntax error in ui.html. Kotlin NOT compiled (GitHub build will tell). Phone NOT tested.

## Known leftovers
- DONE in this zip: Update Center CHANGES text no longer claims an offline llama engine; versionName is 6.16.
- NOT REAL: there is NO offline AI model. The llama.cpp engine was removed (needs Kotlin 2.x / AGP 8.9). LocalBrains.factory is null, so offline = local commands + the fixed knowledge pack only. Do not tell the owner an offline Qwen backup exists.
- ADDED (written + statically checked + Python contract tests, Kotlin NOT compiled, phone NOT tested):
  * Conversation mode: after a SPOKEN reply NOVA listens again up to 3 times without the wake word (NovaService followWindow/followCount/MAX_FOLLOW, ExtCfg.convo default ON, Settings box BAAT-CHEET MODE). Not used after stop/sleep/agent/image/site/learn or yes/no questions.
  * Learn from the internet: LearnRules.kt (pure) + LearnStore.kt (learned.json in app folder) + NovaService.learnBegin ("nova <topic> ke baare mein seekho": Gemini google_search note; if it fails the free chain writes it, source "ai"). Notes are PENDING until the owner presses RAKHO in Settings > KYA SEEKHA; only kept notes answer questions (checked before KnowledgeStore). tools/test_learn_contract.py.
  * GitHub publish: GitHubPublish.kt (new public repo -> index.html -> Pages) with a token saved in SecureStore slot "github" (Settings > GITHUB PUBLISH). PUBLISH button under the website in the chat; native HAAN box first. No voice command for publish on purpose.
- ADDED LATER (same evidence level): voice publish ("nova website publish karo" -> spoken yes -> GitHubPublish; NovaService.publishBegin/publishRun, SiteIntent.isPublish); better voice (Cfg.pickBestVoice picks the highest-quality OFFLINE voice already installed for the language; defaults rate 0.95 / pitch 0.9; the owner may need to install a better Hindi/English voice in Android Settings > Text-to-speech); proactive alert: low battery spoken at most every 30 min when idle (ACTION_BATTERY_LOW receiver in NovaService, ExtCfg.alerts, switch in Settings).
- NOT BUILT: calendar/meeting alerts (needs READ_CALENDAR permission + a reader), and ANY offline AI model. The offline model is blocked by the toolchain: llama.cpp needs Kotlin 2.x / AGP 8.9 and this project is on Kotlin 1.9.24 / AGP 8.5.2. Do not claim it exists. A possible future path is a toolchain upgrade done in its own step with CI feedback.
- Last CI-green + phone-tested version is v27 (build #61 green, SELF TEST 25 pass / 0 fail / 1 skip). v28 (task agent, Gemini listening), v30 (free image), v31 (quiet mode, scroll fix) and v16 (website) were NEVER compiled or phone tested. First job: get the GitHub build result and fix red lines.
- Settings text "Gemini ..." labels and the old checkbox are still there; owner asked that chat never shows provider names. Chat errors are already generic; Settings is where keys are entered, so names there are fine.

## Owner's vision (backlog, in this order)
1. Build + check the website feature on the phone (first GitHub build).
2. Phone-test, one at a time: website (voice), PUBLISH, seekho + RAKHO, conversation mode. Fix only what the owner reports.
3. Better TTS voice; proactive spoken alerts (battery, calendar).
6. Free brain order: cloud free keys first (Groq, Gemini), offline Qwen as backup. Make "understand a task then act" (Brain.kt / Logic.kt / AgentRules.kt) stronger.
7. Older backlog: diagnose not-speaking bug, pattern unlock test, scheduled actions, speaker allow-list, screenshot analysis, bottom-tab layout, HealthCheck error log, Settings self test that runs every feature.

## Prompt to paste at the start of the next chat
"Read HANDOVER_v16.md in the attached NOVA zip, then continue from the Known leftovers and backlog item 1. Follow the owner rules in that file: Hinglish, one step at a time, short, evidence for every claim."

## Latest change: v17 visual-only UI refresh (2026-10-11)
See `UI_REFRESH_NOTES_v17.md`. The updated `ui.html` is presentation-only and keeps existing DOM ids, JavaScript handlers and Android bridge methods. The existing floating HUD (`NovaHud.kt`) now has minus-to-minimize, tap-mini-chip-to-restore, and X-to-close controls with animated transitions. Python contracts and inline JS syntax passed in the local sandbox; Kotlin/Gradle and phone remain unverified.

## v37 note (2026-10-11)
See NEXT_ACCOUNT_PROMPT_v37_UI_ORB.md. Added tools/orb (WebGL2 orb shader + still renderer). ui.html is still the OLD UI; ApiUsage backend exists but has no screen yet.

## v6.18b fix (2026-10-12, owner reported on phone: website came out about the wrong topic)
- Cause (found by reading code + Python mirror): SiteIntent dropped "nova"/"ek"/... from the sentence, the model only saw e.g. "jaha app log download karke use sake". Fix: Parsed.raw carries the owner's full sentence; SiteGen.userPrompt(subject, raw) sends it plus a note that NOVA is the owner's Android assistant (NOVA download page with placeholder Download APK link id=apk-link). Evidence: tests ran here, Kotlin NOT compiled (android.jar download blocked here), phone NOT tested.
- OPEN: "chatgpt kholo aur usse website banwao" opens ChatGPT (AgentRules.isOpenDo -> agent) but typed nothing. Reason not yet known; need the chat/sys lines NOVA showed (agentNote) from the owner.
- v6.18b agent plan step: NovaService.agentPlan (one JSON call, AgentRules.planSystem/planNote) analyses the spoken job and writes the text to type; system prompt got a rule for "get something made BY an AI app" (website/text/code) which was missing (only picture/edit rules existed). Cause of "ChatGPT opened, nothing typed" is a HYPOTHESIS (missing rule); not confirmed. Kotlin not compiled, phone not tested.
- v6.18b smoothness (reading code + headless render only, NOT profiled on a phone): old 2D orb loop no longer redraws while the 3D orb runs (ui.html loop()); lowfx governor reacts after 40 bad frames / ema>24 (was 90 / >26). Agent now writes the stop reason (done/fail/ask + app) and "answer not understood"/"screen unreadable" into the chat as sys lines.
- Known heavy spots NOT changed: ~5 s intro sequence before the 3D orb starts (9 s hard fallback), 28 backdrop-filters, 19 infinite animations, 600 ms sync() and 1.5 s Android.running() timers.
- v6.18b 3D INTRO: ui.html adds a WebGL2 layer (#fx3d, shader inlined in the intro script as VS_SRC/FS_SRC template strings, fn fx3()) behind the old 2D panel intro: perspective warp tunnel + streaks + core glow + amber seam ring + burst shockwave, driven by the same state P as the panels; phone tilt (deviceorientation) or finger moves the vanishing point and CSS-3D-tilts the 2D canvas. Timeline, sound and haptics unchanged (5.6 s). Intro colours moved to graphite/teal/violet/amber. Failure or >26 ms frames removes the layer (old 2D intro runs alone). Evidence: headless Chromium/SwiftShader showed ONE intro frame (~1.2 s) correctly; later frames not captured; not phone tested.
- v6.18c AUTO TEST: FeatureCheck.kt (new) is called from SelfTest.body (section 6b): sentences understood (site, NOVA-download site, publish, image, learn, agent x3, JSON parse, plan note, SiteGen.cleanHtml/userPrompt), keys present (never printed), which features are TAIYAAR/BAND, ChatGPT/Gemini app installed, plus one real tiny planner call (NovaService.testPlan). Report ends with a "KAAM KI BAAT" line. Evidence: tools/test_feature_check_contract.py (structural + Python mirror of SiteIntent), Kotlin NOT compiled, phone NOT tested. Existing SELF TEST button: Settings > SAB TEST KARO.

## v6.18d voice fixes (2026-10-12, from owner's screenshot)
SiteIntent: added banani/banane/bnani/bnane/banwani/banwane/banwana; isPublish false when a build verb is present. AgentRules.isOpenDo: a second open-verb is allowed when >=6 words follow the joiner. Tests ran here (python mirrors); Kotlin not compiled; phone not tested. See NEXT_ACCOUNT_PROMPT_v40_VOICE_FIXES.md.

 v6.18d typing hardening: setTextChecked (read-back + paste fallback), AgentRules.retargetType, agent sys lines for plan/refused steps. Kotlin not compiled, phone not tested. See NEXT_ACCOUNT_PROMPT_v40_VOICE_FIXES.md.
 v6.18d background jobs: screen wake lock during agent jobs, polite refusal on a locked phone (no bypass). Kotlin not compiled, phone not tested.

 v6.24: CrashNote (crash + accessibility event black box shown in SAB TEST KARO report), accessibility handlers guarded. See NEXT_ACCOUNT_PROMPT_v47_CRASH_NOTE.md. Kotlin not compiled, phone not tested.
