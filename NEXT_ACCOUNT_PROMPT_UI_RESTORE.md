# NOVA - RULE #1: PURANA KUCH BHI HATANA NAHI. SIRF NAYA ADD KARNA HAI. (Paste this FIRST + attach NOVA-Voice-Assistant.zip)

## Owner and rules
Owner is NOT a programmer, Android phone only. Reply in Hinglish, SHORT, ONE step at a time, full clickable links. Say PASS / FAIL / NOT TESTED, never "done" without evidence. Zip name exactly NOVA-Voice-Assistant.zip. After EVERY part give the zip AND the next-account prompt with FULL details (this file is the template). Do all work yourself. Repo: github.com/devboutique0-wq/NOVA (branch main). `.github/workflows/build.yml` lives in the repo, NOT in the zip: never add or overwrite it.

## RULE #1 - NO DELETION (the owner is angry: features keep "disappearing")
- NEVER delete, rename, replace, rewrite or "clean up" any existing feature, id, button, screen, popup, setting, connector, Kotlin function or test. NEVER rewrite ui.html or a Kotlin file from scratch. Only ADD, with small str_replace / append edits at the end of the file.
- If an old element is in the way, HIDE it with CSS (display:none) or move it. Keep it in the DOM, keep its id, keep its handler.
- Before AND after every change run: `python3 tools/test_no_feature_loss_contract.py` (297 baseline ids in tools/ui_ids_baseline.txt must all still exist), `python3 tools/test_autopsy_part7_contract.py`, `python3 tools/ui_owner_check.py`, `./tools/run_all_checks.sh` (75 PASS + 1 known FAIL test_vosk_model_packaging). A FAIL means you removed something: undo it.
- Diff your own work before giving the zip: `diff` the new ui.html / .kt against the one in the attached zip. Removed lines must be 0 (except lines you changed on purpose and listed to the owner).
- Why features SEEMED to vanish (honest cause, found in this audit): (1) many old settings are hidden inside collapsed accordions and in the v37 theme layers, so they look missing; (2) the CONNECT tab, connectors and the website builder are filled by Kotlin (`getConnectors`, SiteIntent / SiteGen / GitHubPublish) and look EMPTY in any browser preview that has a fake bridge; (3) a few features were only voice commands (website banao, driving mode, call answer, DND) with no screen. Nothing was deleted in this zip: all 297 ids of the uploaded ui.html are still present.

## What exists (never remove)
CONNECT tab: GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion, Lovable, custom MCP, n8n / webhooks (docs/CONNECTORS.md). Settings > GITHUB PUBLISH (website publish token), Settings > UPDATES (feed URL u_feed, SAVE FEED, CHECK NOW, ALLOW APP INSTALL). Website builder by chat / voice ("nova ek restaurant ki website banao", "isko professional banao", "website publish karo"; SiteIntent.kt, SiteGen.kt, GitHubPublish.kt). 24 HelpGuide topics, SKILLS, LIMITS, routines, schedules, auto reply, driving mode, memory, recordings, offline AI packs, etc. (docs/FEATURE_INVENTORY.md is the checklist).

## Added in this round (all additive, all in ui.html end blocks `nova-restore-*` and `nova-hub-*`)
1. Floating 3D HUB (id hub_b): rotating 3D heart, bottom centre above the tab bar, floats, ping ring, goes green when NOVA is ACTIVE. Tap = round menu with pop animation (HOME, SKILLS, LIMITS, CONNECT, CHAT, SETUP, AUTO, WEBSITE, ON/STOP). Long-press = ACTIVATE / STOP. Auto-hides in chat, settings, popups.
2. WEBSITE BANAO screen (ws_ov): prompt box + quick chips + buttons that send the existing chat commands ("nova <prompt> website banao", "nova isko professional banao", "nova website publish karo") + GITHUB TOKEN shortcut. No Kotlin change.
3. ChatGPT-style chat bar: "+" photo (calls the old c_photo / pickPhoto), ONE round button = voice when empty, send when text; old c_mic / c_kmic hidden (long-press the round button = phone voice typing).
4. AUTOMATION hub (au_open on HOME): 19 entries with ON/OFF chips and OPEN jumps (incl. website publish and feed URL).
5. TRAFFIC box in Settings (API calls today per provider, web vs local saved). Meaning is a GUESS (no "traffic" setting exists anywhere in the old zip): ask the owner once if it means road traffic.
6. DONE icon (check mark animation) on s_save, ACTIVATE + CHUP moved under the orb.

## Tools
`python3 tools/build_ui_preview.py OUT.html` builds the one-file preview (real ui.html + fake bridge incl. fake connectors). Publish it as an artifact for the owner to look at.

## Job now (one step at a time)
A. Owner looks at the preview and says what is still missing / ugly. Fix ONLY by adding. Re-run the guards.
B. Owner uploads `tools` BEFORE `app` to GitHub, runs Actions "NOVA Android CI" (https://github.com/devboutique0-wq/NOVA/actions), sends ONLY red lines. Fix compile errors one at a time (first suspects: ScheduledTaskStore.kt, ScheduledTaskReceiver.kt, BootReceiver.kt, MainActivity.kt onCreate, NovaNotificationService.kt, NovaService.kt; the bracket scan of MainActivity.kt and NovaService.kt was INCONCLUSIVE).
C. Phone tests one at a time from docs/AUTOPSY_FINAL_REPORT.md "Phone test order". Keep the label "v6.99 . build N".

## Evidence
RUN: contract scripts (75 PASS, 1 known FAIL), headless-Chromium UI checks with a FAKE bridge. NOT RUN: Kotlin compile, JUnit, Gradle, APK. NOT TESTED: real phone (hub, website builder, photo send, connectors, TRAFFIC).
