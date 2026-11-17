# TEST STATUS (JARVIS) - owner ke phone test ka record

Rule: jab owner batata hai "ye test kiya, ye chala / ye nahi chala", main is file me tabhi PASS / FAIL likhta hu, **tareekh aur version ke saath**. Bina batae kabhi PASS nahi. Har naye zip me ye file saath aati hai, kuch bhi hataya nahi jata, sirf badalta hai.

Status: `PASS` (owner ne phone par chalaya, chala) / `FAIL` (chala nahi, note likho) / `NOT REPORTED` (owner ne abhi bataya nahi).

## Phone test (owner batayega)
| # | Cheez | Status | Version | Tareekh | Note |
|---|-------|--------|---------|---------|------|
| 1 | WhatsApp seedha order | NOT REPORTED | - | - | |
| 2 | "phir se" nahi katna | NOT REPORTED | - | - | |
| 3 | Play Store pe download | NOT REPORTED | - | - | |
| 4 | Reel kholo = app | NOT REPORTED | - | - | |
| 5 | Chrome me reel | NOT REPORTED | - | - | |
| 6 | Reel band | NOT REPORTED | - | - | |
| 7 | Reel pause | NOT REPORTED | - | - | |
| 8 | Call pehle verb | NOT REPORTED | - | - | |
| 9 | Calendar sawal | NOT REPORTED | - | - | |
| 10 | Trade words | NOT REPORTED | - | - | |
| 11 | Trade close | NOT REPORTED | - | - | |
| 12 | Shorts = YouTube app | NOT REPORTED | - | - | |
| 13 | Chrome me shorts | NOT REPORTED | - | - | |
| 14 | Laser scan (3D) | NOT REPORTED | - | - | |
| 15 | Jarvis 2-tap (Colab code paste) | NOT REPORTED | - | - | |
| 16 | Jarvis asli awaaz (server) | NOT REPORTED | - | - | |
| 17 | Jarvis STOP | NOT REPORTED | - | - | |
| 18 | VoiSpark box gaya | NOT REPORTED | - | - | |
| 19 | Jarvis phone voice (British male) | NOT REPORTED | - | - | |
| 20 | Instagram search (awaaz se) | **FAIL** | installed build (version owner ne nahi bataya) | 2026-11-14 | Bola: "instagram open girl or is with lucky id search guru". JARVIS ne poora "open girl or with lucky guru" search kar diya. Karan: "open/with/or" filler me nahi the, aur recognizer ne shayad "karo aur isme" ko "girl or is with" suna. FIX2 me "with/or/and/open" filler bane; "girl" jaisa galat suna hua shabd code se theek nahi hota. |
| 21 | JARVIS ki awaaz (chat jawab bolna) | **FAIL** | installed build | 2026-11-14 | Owner: "aawaz bahut bekar sunai de rahi hai". Ultron effect default ON hai (pitch 0.8, rate 0.84 + effect). Test baaki: ULTRON button OFF karke dobara sunna. |

| 22 | v7.177 opening kinetic words | NOT REPORTED | - | - | headless OK, phone nahi |
| 23 | v7.177 website build front screen (typed + voice) | NOT REPORTED | - | - | headless OK, phone nahi |
| 24 | v7.177 app-wide motion (tabs/panels) | NOT REPORTED | - | - | headless OK, phone nahi |
| 25 | v7.192 L4 "Aaj ka business" card (orders / leads / autopilot / report) | NOT REPORTED | - | - | Kotlin COMPILE NAHI hua, unit tests NOT RUN. GitHub Actions build pehle chalao. |
| 26 | v7.192 L4 card tap = sunao / orders dikhao / leads dikhao / leads dhoondo | NOT REPORTED | - | - | phone par NOT RUN |
| 27 | v7.192 L4 naya order / lead = card kisi bhi app ke upar | NOT REPORTED | - | - | "Display over other apps" ON chahiye. phone par NOT RUN |
| 28 | v7.192 L4 website store orders (WooCommerce / Shopify, read-only) | NOT REPORTED | - | - | asli store key + internet chahiye. Kabhi chalaya nahi (NOT RUN) |
| 29 | v7.193 launcher modern look (neon cards, hero, dock tiles) | NOT REPORTED | - | - | Kotlin COMPILE NAHI hua, phone par NOT RUN. GitHub Actions build pehle chalao. |
| 30 | v7.194 launcher ANIMATED home (launcher.html WebView: rings, gauges, orb, apps grid, icons, taps) | NOT REPORTED | - | - | Kotlin COMPILE NAHI hua, phone par NOT RUN. GitHub Actions build pehle chalao. |
| 33 | v7.197 SELF PATCH ("jarvis khud code likho: ..." / Test Center box "JARVIS KHUD CODE LIKHE": AI ek Kotlin file likhe -> seedha main -> RED build par auto-revert) | NOT REPORTED | - | - | SelfPatchRules asli Kotlin compiler se chala (machine PASS). GitHubSelfPatch.kt network part kabhi asli GitHub / Gemini ke saath nahi chala: NOT RUN. Kotlin full compile NOT RUN. Phone par NOT RUN. |

| 34 | v7.197 launcher: JARVIS Home screen ka aana (Default home app me JARVIS chunna) | **FAIL** | installed build (SELF_PATCH_4 ke baad, version owner ne nahi bataya) | 2026-11-16 | Owner: "Launcher nhi aarha h abhi bhi". Code me koi button/prompt nahi tha jo JARVIS ko Home banata; sirf manifest me HOME category thi. v7.198 me: app-drawer me "Jarvis Home" icon + launcher khulte hi "JARVIS ko Home banao?" popup (RoleManager). Naya fix phone par NOT RUN. Agar naye APK me bhi JARVIS list me na dikhe to batao: pehle check karo GitHub build GREEN hai aur naya APK install hua hai (L1-L4 kabhi compile nahi hue the). |
| 35 | App Builder: "Manas ke liya scheduled assistant app jo yaad dikhae kab booking h" | **FAIL** | installed build | 2026-11-16 | Screenshot: research 0 facts, "Is topic par sirf 1 pakki detail ... isliye app nahi banayi". Karan: planner ne personal scheduler ko bhi needs_facts=true diya, to 6-facts gate ne rok diya. v7.198: scheduler/reminder/booking/tracker jaise personal app par gate band (forcePersonal). Phone par NOT RUN. |

| 36 | v7.198 UNDERSTAND-FIRST: lambi (7+ shabd) baat pehle AI brain samjhe, phir tool chale (\"samajh kar kaam\", set pattern nahi) | NOT REPORTED | - | - | Owner ne 2026-11-16 ko ye maanga. Kotlin COMPILE NAHI hua, JUnit NOT RUN, phone par NOT RUN. Test: bolo \"instagram open karo aur is lucky id ko search karo guru\" (row 20 wali baat) - ab \"Samjha: ...\" dikhna chahiye. AI key / internet chahiye; na ho to purana raasta. |

| 37 | v7.198 JARVIS baat + kaam + agent chunna: (a) \"kaun sa agent kab kaam aayega\" ka jawab, (b) lambi baat -> sahi agent (WhatsApp / Gmail / Music / website...) -> kaam ke baad \"Kaam poora\" ya \"Poora nahi hua\" line | NOT REPORTED | - | - | Kotlin COMPILE NAHI hua, JUnit NOT RUN, phone par NOT RUN. Test bolo: \"rahul ko bata dena main late aaunga\", \"arijit ke gaane laga do yaar\", \"kaun sa agent kab kaam aayega\". Specialist chain dobara chalana (handle(...) recursion) pehli baar hai: koi atak / double reply dikhe to batao. |

## GitHub build (owner ne screenshot bheja)
| Zip / Version | Test | Status | Tareekh | Note |
|---------------|------|--------|---------|------|
| JARVIS_v7_176_APPBUILDER | GitHub Actions `build` (Run unit tests and assemble debug) | **FAIL** | 2026-11-14 | `compileDebugKotlin` fail: HelpGuide.kt line 94 "Expecting ')'" - `autopilot` topic (line 93) ke baad comma nahi tha. Baaki errors (Type mismatch, Unresolved reference: Topic) isi ek comma se bane the. |
| JARVIS_v7_176_APPBUILDER_FIX1 | GitHub Actions `build`, branch v7176-appbuilder, commit 1c77e6d | **FAIL** | 2026-11-14 | HelpGuide.kt ka comma error GAYA, app `assembleDebug` tak compile hui. Naya fail: `compileDebugUnitTestKotlin` - AppBuilderRulesTest.kt line 25 "Unresolved reference: Plan" aur "Section" (test me `A.Plan(...)` likha tha, object ke instance se nested class nahi milti). |
| JARVIS_v7_176_APPBUILDER_FIX2 | AppBuilderRulesTest.kt line 25: `AppBuilderRules.Plan` / `AppBuilderRules.Section` | NOT REPORTED | - | Kotlin compile + JUnit yahan NOT DONE (sandbox me compiler nahi) - GitHub par dekhna. |

## Machine check (v7.138, 2026-10-29)
- 164 Python contract scripts: PASS (0 fail).
- Jarvis server FAKE test: PASS.
- Kotlin compile + JUnit (JarvisPairingTest, JarvisLocalVoiceTest): NOT DONE (sandbox me compiler nahi); GitHub Actions par dekhna.
- Asli model, Colab COPY CODE button: NOT TESTED.
- Emulator auto test (job `smoke`): script logic checked here with a fake adb (PASS case and crash case both reported correctly). Real emulator run: NOT RUN yet, runs on GitHub after the build; result is in the run summary and artifact `auto-test-report`.
- Auto test cannot cover: voice, wake word, WhatsApp, Play Store, MT5, Jarvis sound. Those need the phone table above.

## Machine check (v7.177 CHECKS, 2026-11-14)
- `tools/run_all_checks.sh` poora chalaya (171 scripts, do hisso me kyunki background process beech me mar gaya): shuru me 10 FAIL, sab 'versionName 7.10x/7.15x/7.17x ya naya' wali purani guards (list me 7.177 nahi tha). Fix: 7.177 list me jodi. 10 ki 10 ab PASS; baaki PASS.
- test_native_smooth_contract.py: machine busy ho to ek baar RUNTIME FAIL aaya, akele chalane par 2/2 PASS (timing wali flaky). Phone result nahi.
- Kotlin compile, GitHub build, phone test: NOT DONE.

## v7.177 DONE build (2026-11-14, machine only)
- App Builder (#apbScr) par kinetic caption (SOCH/KHOJ/SACH/CODE/PUSH/BUILD/APP TAIYAAR): ui.html add-only block v178-apb-kinetic. Headless: PASS (stage badalne par caption badla, idle / lowfx me hidden, 0 JS error). Phone: NOT REPORTED.
- JARVIS ki awaaz: Ultron effect ab default OFF (Cfg.kt, nayi key ultronVoiceV3). ULTRON button se ON hota hai. Kotlin compile NOT DONE, phone: NOT REPORTED (#21 FAIL tab tak jab tak owner na bataye).
- Instagram search filler (#20): ProfileSearchRules.kt me with/or/and/open pehle se filler hain. Galat suna hua shabd (girl) code se theek nahi hota. Compile + phone NOT DONE.
- Full regression v7.177 DONE: 171+ scripts PASS. test_perf_governor_contract.py (RUNTIME) ek baar machine busy me FAIL, baad me akele chalane par PASS (original aur naya dono) - timing wali flaky. test_native_smooth_contract.py bhi aisi hi. Phone result nahi.

## Laptop app (desktop/, Part 1, 2026-11-14)
- Command parser: 15 node unit tests PASS (machine). tools/test_desktop_contract.py PASS.
- Electron window / Windows exe: NOT RUN (sandbox me internet nahi). GitHub workflow `Desktop (JARVIS laptop app)` (desktop.yml, build.yml alag) chalana baaki. Laptop par asli test: NOT REPORTED.
- Laptop<->phone link (2026-11-14): desktop/link.js (AES-256-GCM, relay ntfy.sh) 24 node tests PASS incl. tamper / wrong key / stale / replay / end-to-end over fake relay. Phone side = docs/pending_LINK/LaptopLink.kt.txt UNCOMPILED, NOT wired into the app. Asli relay + phone + laptop test: NOT DONE.

## Laptop link Part 2b - phone side (2026-11-14, machine only)
- Phone side ab source me wired (LaptopLink.kt, bridge, JarvisService, Settings box, MADAD topic). Pehle wali line "NOT wired" ab purani hai.
- tools/test_laptop_link_contract.py PASS: static wiring + desktop/link.js <-> Java mirror of LaptopLink.kt crypto (topic, AES-GCM, AAD, wrong key / wrong topic / tamper / stale reject). Kotlin file khud compile nahi hua.
- LaptopLinkRulesTest.kt (6 tests) likha, chala NAHI. Kotlin compile, GitHub build, asli relay + phone + laptop end-to-end, phone test: NOT DONE.


## Laptop Part 4 brain (2026-11-14, machine only)
- desktop node tests 33/33 PASS (24 link+commands, 9 brain). tools/test_desktop_contract.py + test_laptop_link_contract.py PASS. Electron / exe / Kotlin compile / phone / laptop test: NOT DONE.

## Laptop Step A HUD (2026-11-14, machine only)
- desktop node tests 36/36 PASS. boot.html + HUD headless Chromium (Electron stub) PASS. Electron / exe / laptop screen / camera: NOT DONE (NOT REPORTED by owner).

## Laptop Step B workspace+undo (2026-11-14, machine only)
- desktop node tests 42/42 PASS. Electron / exe / asli apps kholna / laptop test: NOT DONE (NOT REPORTED by owner).

## Laptop Step C voice (2026-11-14, machine only)
- desktop node tests 48/48 PASS. HUD mic flow headless (fake mic + ipc stub) PASS. Asli Electron / mic / Groq / Hinglish accuracy: NOT DONE (NOT REPORTED by owner).


## v7.178 CALL AGENT (this session)
| Test | Level | Result |
|---|---|---|
| tools/test_call_agent_contract.py | machine (static) | PASS (28 checks) |
| tools/test_help_contract.py | machine (static) | PASS (80 topics) |
| tools/test_bridge_caps_contract.py | machine (static) | PASS (124 params, 0 uncapped) |
| tools/test_part6_skills_contract.py | machine (static) | PASS (80 mapped) |
| tools/test_laptop_link_contract.py | machine (static) | PASS (no regression) |
| Playwright card render (fake bridge) | machine (browser) | PASS (states, X, tags, AGENTS intact) |
| Kotlin compile | NOT RUN | kotlinc sandbox me nahi |
| GitHub Actions build | NOT RUN | owner ke kehne par |
| Real call on phone | NOT RUN | owner test: "jarvis <apna number> par call karke table book karo" |

## v7.178b CALL AGENT extension (custom msg + incoming auto-answer)
| Test | Level | Result |
|---|---|---|
| tools/test_call_agent_contract.py | machine (static) | PASS (~50 checks incl. custom msg, incoming importance) |
| tools/test_help_contract.py | machine (static) | PASS (80 topics, callagent title aligned) |
| tools/test_bridge_caps_contract.py | machine (static) | PASS (125 params, 0 uncapped) |
| tools/test_part6_skills_contract.py | machine (static) | PASS (80 mapped, callagent updated) |
| tools/test_laptop_link_contract.py | machine (static) | PASS (no regression) |
| Playwright custom-msg + incoming card + settings toggles | machine (browser) | PASS (9/9, AGENTS 112/112 intact) |
| Kotlin compile | NOT RUN | kotlinc sandbox me nahi |
| GitHub Actions build | NOT RUN | owner ke kehne par |
| Real outgoing custom-msg call | NOT RUN | owner: "jarvis <num> par call karke bolo main sham tak aaunga" |
| Real incoming auto-answer | NOT RUN | owner: Settings > CALLTYPE AGENT > family ON, doosre num se call |

## v7.179 LEAD FINDER (proactive genuine-lead search)
| Test | Level | Result |
|---|---|---|
| tools/test_lead_finder_contract.py | machine (static) | PASS (~30 checks: parse, score, filter, deny, routes, bridges, UI) |
| tools/test_help_contract.py | machine (static) | PASS (81 topics, leadfind added + reordered) |
| tools/test_part6_skills_contract.py | machine (static) | PASS (81 mapped, leadfind title) |
| tools/test_bridge_caps_contract.py | machine (static) | PASS (no regression) |
| tools/test_call_agent_contract.py | machine (static) | PASS (no regression) |
| Playwright lead card (SEARCHING + leads + scores + buttons + report + X) | machine (browser) | PASS (0 JS errors) |
| AGENTS tab regression (112/112) | machine (browser) | PASS |
| Call card regression (DIALING->ENDED->FAILED + X + IDLE) | machine (browser) | PASS |
| Kotlin compile | NOT RUN | kotlinc sandbox me nahi |
| GitHub Actions build | NOT RUN | owner ke kehne par |
| Real Google Places call (with key) | NOT RUN | owner: jarvis biz placeskey <key> |
| Real Overpass call (no key) | NOT RUN | owner: jarvis delhi me restaurant ke genuine leads dhoondo |

## v7.184 PART B (ii): upgrade PUSH + POLL
| Test | Level | Result |
|---|---|---|
| tools/test_upgrade_publish_contract.py | machine (static) | PASS (12 checks) |
| tools/test_upgrade_ledger_contract.py (regression) | machine (static) | PASS |
| tools/test_bridge_caps_contract.py (regression) | machine (static) | PASS |
| tools/test_app_builder_contract.py (regression) | machine (static) | PASS |
| Kotlin compile | NOT RUN | kotlinc sandbox me nahi |
| GitHub Actions build | NOT RUN | owner ke GitHub token/push ke bina nahi ho sakta |
| Real branch push + poll on phone | NOT RUN | owner test: ek upgrade request HAAN karke PUSH button dabao |

## v7.185: FREE photo EDIT (not just free photo generate)
| Test | Level | Result |
|---|---|---|
| tools/test_free_image_edit_contract.py | machine (static) | PASS (8 checks) |
| tools/test_bridge_caps_contract.py (regression) | machine (static) | PASS |
| tools/test_photo_publish_contract.py (regression) | machine (static) | PASS |
| tools/test_wiring_audit_contract.py (regression) | machine (static) | PASS |
| Kotlin compile | NOT RUN | kotlinc sandbox me nahi |
| GitHub Actions build | NOT RUN | owner ke kehne par |
| Real Hugging Face edit on phone | NOT RUN | owner: HF token save karo (wahi FREE IMAGE ka), Gemini key hata ke / FREE IMAGE fallback ON karke photo edit try karo |
| 31 | v7.195 TEST CENTER card (Settings: SAB TEST EK SAATH CHALAO, status chip, kharab list, copy/share) | NOT REPORTED | - | - | Headless Chromium + nakli bridge: PASS (teeno step chale, chip sahi). Kotlin COMPILE NAHI hua, phone par NOT RUN. GitHub Actions build pehle chalao. |

## v7.196 SELF BUILD (Jarvis khud GitHub se APK banwaye, this session 2026-11-16)
| Test | Level | Result |
|---|---|---|
| tools/test_self_build_contract.py (23 checks) | machine (static) | PASS |
| tools/test_upgrade_publish_contract.py (regression) | machine (static) | PASS |
| tools/test_bridge_caps_contract.py (regression, 130 params) | machine (static) | PASS |
| tools/test_wiring_audit_contract.py (regression) | machine (static) | PASS |
| tools/test_help_contract.py (regression, 81 topics) | machine (static) | PASS |
| tools/test_part6_skills_contract.py (regression) | machine (static) | PASS |
| tools/test_test_center_contract.py (regression) | machine (static) | PASS |
| tools/run_all_checks.sh (poora suite) | machine (static) | ADHURA - 82/171+ scripts tak 2 min me chale, 0 FAIL, poora run time khatam hone se pehle nahi dekha gaya |
| SelfBuildRulesTest.kt (10 unit tests) | Kotlin unit | NOT RUN - kotlinc sandbox me nahi |
| 32 | v7.196 "jarvis apk banao" / apk status / apk install karo + Test Center SELF BUILD box | NOT REPORTED | - | - | Static PASS. Kotlin COMPILE NAHI hua, phone par NOT RUN. GitHub Actions build pehle chalao. |

## Machine check (v7.197, 2026-11-16, sirf machine - phone/GitHub nahi)
- `tools/run_all_checks.sh` poora end-to-end chala (184 scripts + skill-pack validator): 183 PASS, 1 FAIL (`test_vosk_model_packaging.py`: docs/BUILD.md me `nova.ppn` likha tha, code me `jarvis.ppn`). Doc theek kiya, ab PASS.
- Gradle ke embedded Kotlin compiler se 549 `.kt` files parse: 1 REAL syntax error mila (HelpGuide.kt line 97, stale duplicate topic) - theek kiya. Naya guard `tools/test_kotlin_syntax_contract.py`. Types / Android API check NOT RUN (SDK nahi).
- desktop node tests 5/5 PASS. Kotlin full compile, JUnit (SelfBuildRulesTest sab), GitHub build, phone test: NOT RUN.

- Fix ke baad poora suite dobara: 185 PASS, 0 FAIL.
- Self-patch add-on ke baad: poore suite me sirf 1 FAIL aaya (mera apna comment bug, syntax test ne pakda), fix ke baad wo + self_patch + self_build contract PASS. Poora suite us ke baad dobara nahi chalaya.

## Machine check (v7.198, 2026-11-16)
- tools/test_app_builder_contract.py: PASS. tools/test_android_runtime_contract.py (exported count 9 -> 9 ya 10): PASS.
- Brain/TaskManager se jude contract scripts: 0 FAIL. tools/test_kotlin_syntax_contract.py: SKIPPED (is machine par Kotlin compiler nahi). Kotlin compile + AppBuilderRulesTest.personal_scheduler_needs_no_facts + launcher popup: NOT RUN. GitHub Actions build pehle chalao.

| 31 | v7.217 cloud brain: deploy + pairing + heartbeat (C1-C8) | NOT REPORTED | 7.221 | - | ANDROID_TEST_STEPS_v7_221.md |
| 32 | v7.218 device sync: laptop ko bolo / laptop ka status / memory sync (D1-D4) | NOT REPORTED | 7.221 | - | ANDROID_TEST_STEPS_v7_221.md |
| 33 | v7.219 TV: pairing + tv commands (T1-T6) | NOT REPORTED | 7.221 | - | Android TV/Google TV hone par hi; docs/OSCAR_TV.md |
| 34 | v7.220 website: Pages live + admin login + publish (W1-W4) | NOT REPORTED | 7.221 | - | ANDROID_TEST_STEPS_v7_221.md |
| 35 | v7.221 automation: daily health run + backup (A1-A3) | NOT REPORTED | 7.221 | - | GitHub Actions par |
| 36 | v7.221 regression spot-check (R1-R5) | NOT REPORTED | 7.221 | - | purane features waise hi chalna chahiye |
