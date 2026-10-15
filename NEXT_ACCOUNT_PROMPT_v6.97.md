# NOVA next account prompt (v6.97 WORK IN PROGRESS, wip7) - paste as first message, attach this ZIP

Owner: not a programmer, Android phone only (Chrome + GitHub website). Reply in Hinglish, short, ONE step at a time.
Compile only on GitHub Actions: github.com/skfashion142/NOVA-Voice-Assistant. Always say clearly what is RUN vs NOT RUN.
Project folder in ZIP: nova_part4_work (Kotlin, package com.nova.assistant, UI = app/src/main/assets/ui.html).

## OWNER REQUEST (Hinglish, 15 Oct 2026)
1. Add 5 key-less "browsers" (web sources, no API key) so NOVA can fetch info about almost anything.
2. NOVA must understand which need -> which source (what is needed when, where each thing is found) so commands act instantly, no long waits.
3. The offline (on-device) AI must also work while ONLINE, so cloud APIs get less load.
4. Everything real, nothing fake. 5. The app UI is broken/ugly ("fatta"): make it better.

## DECISIONS TAKEN
- 5 browsers: WIKI (Wikipedia + Wiktionary), INSTANT (DuckDuckGo Instant Answer), WEATHER (Open-Meteo), NEWS (Google News RSS), MARKET (Frankfurter currency + CoinGecko crypto). Page-reader / gold / stock = NOT included (no stable keyless source) - say so honestly.
- Only a short topic/city/currency is sent, never the full sentence/memory. Live kinds (weather/news/currency/crypto) must NEVER fall through to a cloud AI that could invent numbers: on failure answer honestly "abhi nahi mila". FACT/DEFINE may fall through.
- Route order planned in NovaService.handle(): local commands -> LocalSkills -> knowledge -> NEW web browsers (before LocalBrains.ask translator, which is slow) -> AiRouter (local-first when online for short simple chat) -> cloud.
- Local-first online: only short (<=14 words), non-heavy, non-live questions; skip if device tier LOW; online budget ~9 s then cloud; uncertain local answers ("pakka nahi pata") escalate to cloud; no "Offline:" prefix when online. Default AiRouter.plan behavior must stay unchanged (new param default false) so RoutingTest keeps passing.
- UI findings (headless screenshots, 390x844): Update Center popup (#uc, rgba .82 bg) auto-covers home with a wall of changelog and home text bleeds through; ACTIVATE button sits under the tab bar; chat speaker button overlaps bubble; ui.html has ~15 stacked <style> layers (v2, v17, v37, v690, v695...). Plan: one clean final style layer + small markup fixes (solid compact Update Center, ACTIVATE under orb, bottom padding for tab bar, chat bubble fix), plus a card showing the 5 browsers (ON/OFF, test), toggles LOCAL FIRST + WEB BROWSERS + "Mera shehar".

## OWNER ORDERS (15 Oct 2026, latest - COMPULSORY)
- Finish ALL remaining work completely, real, nothing half-done. Check for bugs / missing files for real.
- NO manual tests: every test must run AUTOMATICALLY after the app is installed (remove manual test buttons / MANUAL lines). Owner is non-technical.
- Always hand over: the ZIP (name it NOVA-v6_97-wipN.zip, folder nova_part4_work) + an updated NEXT_ACCOUNT_PROMPT. Reply in Hinglish, short, one step at a time. Say clearly RUN vs NOT RUN.

## STATE (wip4) - progress about 65%
SANDBOX LIMIT: no kotlinc, no internet in the last sandbox -> NO Kotlin compile and NO JUnit run for anything below except what is marked RUN. GitHub Actions is the first real compile.
RUN: WebSources.kt + WebBrowsers.kt with their tests (25/25, earlier account, real kotlinc). Python contracts: tools/run_all_checks.sh = 56/56 PASS (this account).
DONE this account (written + statically cross-checked, NOT compiled):
- Static audit: NovaService already imports android.content.Context (earlier TODO A was a false alarm). Checked by hand that every symbol used by the new code exists with the right signature (DeviceTier.of, LocalBrains.chat(timeoutMs), ExtCfg.*, SavedStats.json, OfflineJobQueue.hasInternet, runJs ...). Build files exist (gradle wrapper jar, vosk-model.gradle.kts, workflow, manifest INTERNET permission, assets ui.html / knowledge.json / orb_poster.png).
- SourceMap wired: NovaService.handle answers "tum jawab kahan se laate ho" locally (lastKind source_map); SourceMap.aiFacts() added to the Gemini prompt and the free-chain prompt. SourceMap.Entry got a short spoken `say` field; explain() now ~420 chars.
- HelpGuide: new topic "live" (Mausam, khabar, rate, matlab) in HelpGuide.kt AND in ui.html CAPS (tools/test_help_contract.py PASS, 16 topics). Keys are browser / live jankari / live info so "kya tum mausam bata sakte ho" is still NOT a help ask (existing HelpGuideTest).
- New tests written, NOT RUN: SourceMapTest.kt, LocalFirstRoutingTest.kt, AutoTestRulesTest.needsFull.
- tools/test_public_knowledge_contract.py regex relaxed for the new `&& !ExtCfg.webBrowsers(this)` condition (was FAIL, now PASS).
- AUTOMATIC TESTS: AutoTest no longer waits for a Gemini key (only mic permission). The FIRST auto run after each installed versionCode runs in "full" mode (torch, volume, mic, hearing, screen read by itself, no speaking, no questions); later runs stay quiet. SelfTest.start(..., quiet, full). Removed: the spoken "Awaaz test" question and ALL "MANUAL" lines. New auto section 6e in SelfTest: asset files present, source-map classify cases, local-first rule, offline AI ready, and a REAL probe of each of the 5 browsers (WARN if a service does not answer, never a fake PASS).

## DONE in wip5 (this account, NOT compiled, Kotlin only comments changed)
- TODO 1(a) DONE: SELF TEST box is now "TEST REPORT (apne aap chalta hai)"; st_run/st_img/st_free buttons + handlers removed; fsRender text, FeatureStatus/HealthCheck/MainActivity comments, knowledge entry nova-self-test (knowledge.json rebuilt) and tools/test_feature_status_contract.py updated. tools/run_all_checks.sh = 56/56 PASS (RUN).
- TODO 1(b) DONE: new BROWSERS card (accordion, id wb_box) in ui.html: switches WEB BROWSERS / LOCAL FIRST, "Mera shehar" input, 5 browsers with real state from Android.getBrowserStatus(), savings counters, no test button. Fake bridge in tools/ui_shots.py has getBrowserStatus. Playwright 390x844 + 360x640: 0 JS errors, card renders (RUN, headless only).
- TODO 1(d) PARTLY: bridge fake added; final re-shoot after 1(c) still needed.

## DONE in wip6 (NOT compiled; Kotlin edits below need the first GitHub Actions compile)
Owner request 15 Oct (latest): find features that exist but are unused/broken, make NOVA check its own work silently in the background, make every connector fully usable after connecting; fix, build, deliver.
- Connectors audit (static): MCP tools/list and tools/call showed a server error as success -> ConnectorParse.mcpError() + ConnectorApi.mcpRun now report the real error. Vercel only saw the personal account -> vercelScopes() adds up to 3 team ids (projects, deployments, domains, redeploy). MCP clientInfo version 6.97. Tests added in ConnectorParseTest (NOT RUN).
- Hidden feature found: NOISE CANCEL (ExtCfg/MicEffects/bridge existed, no UI) -> switch added in Settings card "BROWSERS AUR AWAAZ".
- Scan result: 191 bridge methods; unused by ui.html: listChats, selfTest*, clearErrors, getWaitSec/setWaitSec (listening wait time, no UI), getMemoryText, getAutoLearn/setAutoLearn, requestCalendarRead (only if permission list misses it). Not yet decided/wired: wait-seconds control, autoLearn switch.
- STILL TODO from this request: silent background self-check of NOVA actions (ToolVerify/SelfImprove exist, check they run before every risky action), Notion/Supabase/Cloudflare/GitHub still read-only or limited, compile on CI.

## DONE in wip7 (NOT compiled, NOT tried on a real n8n/Zapier/Make/IFTTT account)
Owner request: add n8n and other agent/automation connectors and confirm NOVA works with them.
- New connector families (ids hook_xxx and n8n_xxx, added in CONNECT tab "AUTOMATION" card): (1) WEBHOOK = any https hook (n8n webhook, Zapier catch hook, Make webhook, IFTTT maker URL, Pipedream): POST {"source":"NOVA","text":...,"time":...}, optional Bearer token. Always a WRITE action (strict spoken yes). It cannot be verified without firing it, so it is only saved; the answer says the platform ACCEPTED the trigger, never that the workflow succeeded. (2) n8n REST API: base address + API key, verified with a real read-only call (GET /api/v1/workflows?limit=1, header X-N8N-API-KEY); actions workflows, executions (READ), activate, deactivate (WRITE, by id or name).
- Files: ConnectorCatalog (HOOK_PREFIX, N8N_PREFIX, isDynId, n8nBase), ConnectorStore (dynList/addDyn/removeDyn/dynDef, summaryJson), ConnectorApi (hookRun, n8nVerify, n8nRun, isWrite), ConnectorParse (n8nWorkflows, n8nExecutions), ConnectorRules.parseDynamic, NovaService (voice reaches added connectors; before this, custom MCP connectors were UI-only), MainActivity (connectorAddHook, connectorAddN8n), ui.html. Tests: ConnectorRulesTest (4 new, NOT RUN), tools/test_automation_contract.py (13 PASS).
- Unverified endpoint facts (written from n8n public API docs memory): /api/v1/workflows, /api/v1/executions, POST /api/v1/workflows/{id}/activate|deactivate. First real test needs an n8n account.
- Not done: Zapier/Make as full API connectors (no fixed API base; use WEBHOOK or custom MCP), parsePlan (two-step voice) for added connectors.

## TODO (remaining, in order; 1(a) and 1(b) are DONE, see above)
1. UI (ui.html): (a) replace the SELF TEST box: remove buttons st_run / st_img / st_free (and their JS handlers or guard them), rename the box "TEST REPORT (apne aap chalta hai)", keep AKHRI GALTIYAAN, BATTERY / AUTOSTART, COPY REPORT, SHARE, and keep window.onAutoTest filling #st_out. Also change the text in fsRender (line ~1351 "Settings > SAB TEST KARO"), FeatureStatus.kt/HealthCheck.kt/MainActivity comments, knowledge entry "nova-self-test" in tools/knowledge_src/k_nova.py (then run tools/build_knowledge.py so knowledge.json matches), and tools/test_feature_status_contract.py line 18. (b) NEW "BROWSERS" card: 5 browsers with REAL state from Android.getBrowserStatus() (unused / ok / failing / paused + calls), switches WEB BROWSERS and LOCAL FIRST (Android.get/setWebBrowsers, get/setLocalFirst), "Mera shehar" input (Android.setHomeCity), savings counters (saved.webToday/localToday). NO manual TEST button: the auto test probes them. MainActivity.testBrowser(id) can stay unused or be removed. (c) UI fixes from the findings above (solid compact Update Center #uc, ACTIVATE under the orb, bottom padding for the tab bar, chat speaker/bubble overlap, ONE clean final style layer). (d) Playwright check at 390x844 and 360x640 with tools/ui_shots.py FAKE bridge: add the new bridge methods to the fake, 0 JS errors.
2. Version bump to 6.97: app/build.gradle.kts versionName (still 6.95), workflow summary line (still says v6.95), tools/test_android_runtime_contract.py pins, UA string already says 6.97, CHANGELOG top entry (top is 6.96), README, docs/FEATURES_AND_LIMITS. Add a tools/test_*_contract.py for the BROWSERS card + auto-test (no manual buttons left).
3. Re-run tools/run_all_checks.sh (must stay all PASS). If kotlinc + android.jar become reachable run tools/local_compile_check.sh and fix real compile errors first.
4. Build the ZIP + final honest report RUN / NOT RUN. Owner builds on GitHub Actions ("Build NOVA from zip"), installs the APK and does nothing else: the app tests itself.

## OLDER NOTES (kept)
### Earlier state (wip3)
RUN (real kotlinc 1.9.24 + JUnit, 25/25 PASS, fixtures only, no live network): WebSources.kt (classifier, URLs, 8 parsers) + WebSourcesTest.kt (14); WebBrowsers.kt (engine: parallel race, TTL cache, breaker, offline, honest live failures, probe()) + WebBrowsersTest.kt (11).
WRITTEN, NOT COMPILED (Android classes, needs Gradle/CI or tools/local_compile_check.sh which needs android.jar from github raw - was blocked in sandbox):
- SourceMap.kt (routing table, localCanHandle, aiFacts, explain, isAsk) - pure, NO test yet, NOT wired to HelpGuide yet.
- LocalChat.kt: AiRouter.plan(..., localFirst=false) new param (old tests unchanged); LocalChat.ONLINE_TIMEOUT_MS, crisisAnswer, needsLive, isUnsure.
- LocalBrains.kt: chat(..., timeoutMs), isLoaded().
- ExtCfg.kt: webBrowsers (default ON), localFirst (default ON), homeCity/setHomeCity.
- SavedStats.kt: per-day counters web/local.
- NovaService.kt: web step in handle() before LocalBrains.ask; local-first plan; localAnswer(said, online, canEscalate); deviceTier lazy; old PublicKnowledgeFallback skipped when browsers ON. CHECK: NovaService has no `import android.content.Context` line (uses Context.ACTIVITY_SERVICE in new deviceTier) -> add the import or use android.content.Context fully qualified.
- MainActivity.kt bridge: get/setWebBrowsers, get/setLocalFirst, get/setHomeCity, getBrowserStatus(), testBrowser(id) -> window.onBrowserTest(id, ok, text).
NOT DONE: UI (ui.html), SourceMapTest, AiRouter/LocalChat tests, HelpGuide topic, FeatureStatus row, contract scripts/versionName bump 6.97, CHANGELOG/README/docs, Gradle build, phone test.


