# NOVA next account prompt (v6.97 WORK IN PROGRESS) - paste as first message, attach this ZIP

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

## DONE SO FAR (this ZIP) - progress about 45%
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

## TODO (remaining, in order)
A. Fix NovaService Context import; try tools/local_compile_check.sh (needs android.jar android-34; if github raw blocked, ask owner to run CI) and fix real compile errors.
B. SourceMapTest + tests for AiRouter.plan(localFirst) + LocalChat.isUnsure/needsLive; HelpGuide topic "kahan se jawab"; wire SourceMap.isAsk in NovaService.handle (local answer SourceMap.explain) and SourceMap.aiFacts() into the cloud prompt.
C. UI (ui.html, see UI findings above): solid compact Update Center, ACTIVATE button under the orb, bottom padding for tab bar, chat bubble/speaker fix, ONE clean final style layer; new "BROWSERS" card (5 browsers state + TEST button via Android.testBrowser + window.onBrowserTest, switches WEB BROWSERS / LOCAL FIRST, "Mera shehar" input, savings counters from getBrowserStatus().saved); Playwright check 390x844 + 360x640 (script: tools/ui_shots.py FAKE bridge, add the new bridge methods to the fake), 0 JS errors.
D. Bump versionName 6.97 (build.gradle.kts says 6.95, CHANGELOG top 6.96), fix contract scripts pinning versionName/permission counts, run all tools/test_*.py, CHANGELOG + README + docs/FEATURES_AND_LIMITS.
E. Final honest report RUN / NOT RUN; owner builds on GitHub Actions "Build NOVA from zip" and tests weather/news/rates/meaning on the phone.
