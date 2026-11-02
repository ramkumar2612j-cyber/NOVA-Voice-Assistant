# PART 17 SETTINGS / SKILLS RESTYLE - status (v7.69)
Evidence: RUN = executed here; NOT RUN = could not run; NOT TESTED = needs the real phone.

## Kya bana (add-only, kuch hataya nahi)
1. Settings me naya **SWITCHES card** (ui.html `pt17_switches`, SAVE row ke upar): GRAPHICS QUALITY (AUTO / SHARP / SMOOTH), PERSONA (ON / OFF), STOP KAISE KAAM KARE (SIRF ROKE = default / ROKE + NOVA BAND).
2. Purane `#s_quality` select aur PERSONA button waise hi hain aur naye switch ke saath sync rehte hain.
3. STOP switch ke liye naya bridge `getStopPowersOff / setStopPowersOff` (MainActivity, ExtCfg.stopPowersOff pehle se tha, default OFF).
4. MADAD topic `switches` (48 topics, Kotlin == ui.html) + SKILLS registry entry.
5. Version 7.69; 4 version-pinned guards ab 7.69 bhi maante hain.

## Result
- RUN: Kotlin compile main + test = 0 errors (kotlinc 1.9.24, android.jar sparse git clone se; raw.githubusercontent.com block tha).
- RUN: JUnit 1347 run / 29 fail = wahi purane org.json-stub fail (ConnectorParse 11, WebBrowsers 6, WebSources 6, VercelPublish 2, ConnectorRules / FeatureVerify / MusicCatalog / NovaV73 1-1). HelpGuidePart17Test (3) + SafeStopTest: koi fail nahi.
- RUN: test_part17_contract.py PASS (headless 320 px, mutation check: STOP handler hatane par 3 FAIL), audit_dead_ui 0 gap, test_help_contract PASS, test_part16_contract PASS.
- RUN: run_all_checks 134 scripts: 1 fail (test_part6_skills_contract: naya topic mapping nahi tha) -> theek kiya, alag chalane par PASS. Poora run_all_checks theek ke BAAD dobara NOT RUN.
- NOT RUN: GitHub Actions. NOT TESTED: phone.

## Bacha hua (agla account)
1. `bash tools/run_all_checks.sh` poora dobara (background, ~7 min) - 0 fail confirm karo.
2. Phone test (owner): Settings -> SWITCHES card dikhe; QUALITY badalo, app band/khol, asar dikhe; PERSONA OFF -> "nova kaise ho" seedha jawab; STOP = SIRF ROKE par floating STOP dabao -> NOVA chalu rahe; ROKE + NOVA BAND par -> NOVA band ho.
3. Part 16 ke kaam bhi phone par NOT TESTED (call uthao permission).
4. Owner faisla pending (bina poochhe mat badlo): voice command jab NOVA busy ho queue me nahi jaata; wb_ll locked phone par wake word.
