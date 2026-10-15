# NOVA next account prompt - MERGE STAGE (16 Oct 2026) - attach NOVA-Voice-Assistant.zip

Owner: not a programmer, Android phone only (Chrome + GitHub website). Reply in Hinglish, short, ONE step at a time. Say clearly RUN vs NOT RUN. Nothing fake.
Repo: github.com/ramkumar2612j-cyber/NOVA-Voice-Assistant (branch wip13 = latest). Compile only on GitHub Actions. Zip top folder NOVA-Voice-Assistant-main.

## WHAT HAPPENED
Two lineages existed: OLD (wip10 -> wip13: music fix, MusicCatalog, connectors + chat "+" menu, rich searchable SKILLS registry, "NOVA FINAL" theme layer, orb fix) and NEW (wip9 zip: screen identify + search, convo ON, Lock Guard, DND, smart reply, Parts 3-10 tests). The wip9 upload overwrote the old files, so CI failed (test_music_play_contract: musicAltOffer missing).
Owner order: KEEP BOTH, remove nothing, everything must show in the app UI and in SKILLS, connectors like ChatGPT.

## DONE (this zip)
- Re-applied old diff 067ae63..0af6bd8 on top of wip13 tip 18dedf8 (git 3-way). Auto-merged clean: NovaService, MainActivity, MusicPlayRules, MusicCatalog, Connector*, tests.
- Hand-resolved: CHANGELOG (both kept), SiteIntent.kt (new parseLong kept + old notScope line), ui.html 5 conflicts: chat bar (new layout + connectors "+" button c_plus), auto-learn id = al_on, SKILLS = old rich registry (skRegistry) + new rows (screen identify, ringer/DND, screenshot/rotate, AI SMART), both theme layers kept (modern-v76 + core3d + nova-final).
- tools/run_all_checks.sh: 70 passed, 0 failed (RUN). Music test passes now. Exec bits kept.

## UPDATE (final stage of this account)
- ui.html loaded in headless Chromium (stub bridge): SKILLS screen renders 78 skills incl. old rich registry, new rows (screen identify, ringer/DND, screenshot, AI SMART) and connectors rows; both theme layers coexist (core3d tab button + nova-final). The few "forEach/filter is not a function" errors came from my stub returning wrong types, NOT from the app (real Android returns arrays) - not 100% proven on a phone.
- Static Kotlin check (RUN): bracket balance of merged files equals the source versions; every MusicPlayRules/MusicCatalog symbol used exists; musicBegin/musicPlay/verifyPlayback/musicAltOffer defined once. This is NOT a compile.
- Baat-cheet labels now say 6.

## NOT DONE / NOT VERIFIED
1. Kotlin NOT compiled; JUnit NOT RUN. First real compile = GitHub Actions: push zip to branch wip13, read the build log, fix compile errors first (NovaService.kt, MainActivity.kt songChoiceListener, ScreenSession.kt).
2. Phone checks NOT TESTED: popup + accessibility, identify answer, convo follow-ups, music play + "app par try karun?", connectors "+" menu, DND/ringer.
3. Version bump only after green build.
