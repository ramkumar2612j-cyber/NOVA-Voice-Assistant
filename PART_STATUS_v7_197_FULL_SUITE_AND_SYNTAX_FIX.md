# PART STATUS v7.197 - poora suite end-to-end + Kotlin syntax bug fix (zip: JARVIS_v7_196_SELF_BUILD_FINAL)

Version name app me **7.196 hi rakha** (jaan-bujh kar: purane "versionName" guards na tootein). Ye sirf fix + safety-net hai, naya feature nahi.

## Kya mila aur theek hua (sach, saboot ke saath)
1. **REAL BUG (GitHub build RED karta): `HelpGuide.kt` line 97.** `Topic("callagent", ...)` ke baad usi line me ek purane duplicate topic ke bache hue arguments (id/title/keys ke bina) chipke the. Is wajah se `listOf(...)` beech me band ho jaata tha aur Kotlin parser ne "syntax error: Property getter or setter expected" diya. Fix: wo stale duplicate hata diya (ui.html ke `CAPS` me callagent ka naya wala text pehle se tha, jo Kotlin ke pehle hisse se match karta hai). `test_help_contract.py` ab bhi PASS (81 topics Kotlin == ui.html).
   - Ye bug **purane sabhi Python contract tests se chhup gaya tha** (wo text regex se padhte hain, compile nahi karte).
2. **`docs/BUILD.md`**: wake-word file ka naam `nova.ppn` likha tha, code me asli naam `jarvis.ppn` hai. Doc theek kiya. Isi se `test_vosk_model_packaging.py` FAIL tha (poore suite me wohi akela FAIL).
3. **NAYA safety-net: `tools/test_kotlin_syntax_contract.py`.** Gradle ke andar jo Kotlin compiler (2.0.21) milta hai, usse saari 549 `.kt` files (main + test) parse karta hai, aur koi bhi "syntax error" mile to FAIL. Jaanch ke liye jaan-bujh kar ek bug daal kar dekha - test ne pakda. Agar machine par Gradle/Java na ho to "SKIPPED" likh kar PASS (CI ko kabhi nahi rokta). **Sirf syntax** - type/Android errors ye nahi pakadta.

## Evidence (is session me machine par chala)
| Check | Result |
|---|---|
| `tools/run_all_checks.sh` POORA, end-to-end, bina rukawat (184 scripts + skill-pack validator) | **183 PASS, 1 FAIL** (vosk packaging, upar #2) - ab tak ka pehla poora run jo khatam hua. Slowest: test_ui_render_smoke 59s, test_part5_ui_screens 57s. Koi script timeout nahi hui. |
| `test_vosk_model_packaging.py` fix ke baad | PASS (akele) |
| `test_help_contract.py` fix ke baad | PASS (81 topics) |
| `test_kotlin_syntax_contract.py` (naya) | PASS - 549 files, 0 syntax error (fix ke baad); fix se PEHLE 1 real error pakda |
| Kotlin parse: poora `app/src/main` + `app/src/test` | 0 syntax error |
| Kotlin: project-level unresolved symbol (jaise `UpdateManager.installApkFile`, `OWNER_REPO`, `SelfBuildRules.*`) | koi error nahi; bache saare errors sirf Android/org.json ke hain (SDK is machine par hai hi nahi) |
| `.github/workflows/*.yml` (build, desktop, chat-perf-fix) YAML parse | OK |
| desktop node tests (brain, commands, hudinfo, link, voice) | 5/5 PASS |

## Jo ab bhi NOT RUN hai (honest)
- **Poora Kotlin compile (types, Android API) aur `SelfBuildRulesTest` + baaki JUnit: NOT RUN.** Android SDK + org.json is machine par nahi hai. Pehla asli signal GitHub Actions hi dega. Pehli build abhi bhi RED aa sakti hai (type error jaisi chhoti cheez) - normal hai, red lines bhejo.
- Poora suite fix ke baad dobara chala: **185 PASS, 0 FAIL** (184 purane + naya syntax test). Self-patch add-on ke baad poora suite: 186 me se 185 PASS, 1 FAIL = mera apna nested-comment bug (`/*` comment ke andar) jo naye syntax test ne pakda; theek karke wo test + self-patch contract + self-build contract dobara PASS. Poora suite us fix ke baad dobara NAHI chalaya (sirf comment badla).
- GitHub token me `workflow` scope, phone par SELF BUILD, rows 20-32 of TEST_STATUS.md: owner ke test baaki.
- AI se code likhwa kar khud push karna: jaan-bujh kar nahi banaya (v7.196 ka decision same).

## ADD-ON (same session, owner ne bola): JARVIS KHUD CODE LIKHE + SEEDHA MAIN PAR PUSH (SELF PATCH)
**Owner ka faisla (2026-11-16, AskUserQuestion me khud chuna): "Seedha main par, bina puche".** Pichhle session ka "jaan-bujh kar nahi banaya" owner ka faisla NAHI tha - isliye ab bana diya. NEXT_ACCOUNT_PROMPT_v7_196 ka rule 5 ("app khud .kt nahi badalta") aur "dono ko mat mix karna" ab PURANE hain; ye add-on unhe badalta hai. SELF BUILD (sirf rebuild) alag hi hai.

Kaise kaam karta hai (bina har baar puche, lekin safety ke saath):
1. Bolo: **"jarvis khud code likho: <kya chahiye>"** (ya Settings > TEST CENTER > "JARVIS KHUD CODE LIKHE" box). Kisi chhoti file ko badalna ho to naam likho (Foo.kt); warna AI nayi file banata hai.
2. Wish HARD DENY/secrets/workflow wali ho (payment, password, OTP, PIN, CVV, token, keystore, build.yml...) to mana. Sirf `app/src/main/java/com/nova/assistant/*.kt`; protected files (SecureStore, UpdateManager, GitHub*, UpgradeLedger, BlockedRules, PayKit, PayRules, Cfg, Logic, ye feature khud) kabhi nahi.
3. Gemini se ek POORI file maangi jaati hai (2 koshish). Reply validate: `package com.nova.assistant`, brackets balanced, mana kiye hue calls (SecureStore, ProcessBuilder, Runtime.getRuntime, PackageInstaller, api.github.com...) nahi, adhoora/chhota jawab nahi.
4. GitHub Contents API se seedha **main** par commit. Roz max 5. Ek time par ek hi patch.
5. GitHub Actions build chalti hai. **RED/timeout = purani file khud wapas** (nayi file = khali stub). **"code status"** se dekho, **"code wapas karo"** se haath se wapas.
6. Phone me APK ye khud nahi badalta; naya green build install karna alag owner-tap wala flow hai ("apk banao" / "apk install karo").
Files: `SelfPatchRules.kt` (pure), `GitHubSelfPatch.kt` (network), JarvisService hook (UpgradeLedger se PEHLE), MainActivity `selfPatchRun/selfPatchAct/selfPatchStatus`, ui.html `sp_box` + `v197-self-patch`, `SelfPatchRulesTest.kt`, `tools/test_self_patch_contract.py`, `tools/kt/SelfPatchRulesCheck.kt`.

Evidence: SelfPatchRules **asli Kotlin compiler se compile + 35 behaviour checks PASS**; contract 61 PASS; related contracts (self_build, bridge_caps, wiring_audit, help, test_center, upgrade_publish) PASS.
**NOT RUN:** `GitHubSelfPatch.kt` kabhi asli GitHub / Gemini ke saath nahi chala (sirf static); Kotlin full compile, `SelfPatchRulesTest.kt` (JUnit), phone test. **Jaanne wali baat:** main par seedha push ka matlab har patch ek real commit hai; RED build me auto-revert ek aur commit banata hai. Agar revert ke baad bhi build RED rahe (kisi aur wajah se), status me dikhega - haath se dekhna padega. Agar ek hi file par do patch ek ke baad ek aaye to doosra tabhi shuru hota hai jab pehle ka build khatam ho (PUSHED phase me naya request mana hai).

## FIX 2 (GitHub run ke baad)
Pehli asli GitHub run: 185 PASS, 1 FAIL = `test_self_patch_contract.py` (IndexError: runner ke Gradle me kuch extra jar nahi the). Test ab jar kam hon to SKIP karta hai, source error ho to hi FAIL. `test_kotlin_syntax_contract` GitHub par PASS (37s). Kotlin compile / JUnit abhi bhi NOT RUN (contract step me ruk gaya tha).

## FIX 3 (pehli asli Gradle compile ke baad) - GitHub `compileDebugKotlin` ne ye errors diye, theek kiye
1. `JarvisNotificationService.kt` 212/220/224/226: `JarvisService.alive`, `emit`, `hud` private the. Fix: JarvisService me public `isRunning` (sirf padhne) aur `announce(role, text, hudLine)`; private members private hi rahe (ek test unki privacy dekhta hai).
2. `JarvisService.kt` 1184 (`leadFindRoute`): `LeadFinder.start` ke 4 parameter hain, call me 5 the (extra "permission" lambda). Extra lambda hataya.
Kaise mila: contract tests ye nahi pakad sakte the - sirf asli compiler (Android SDK ke saath) pakadta hai. Is session me wapas check: JarvisService ke private members ka scan (koi aur bahari access nahi mila), 116 related contracts PASS, kotlin syntax PASS. Poora Kotlin compile phir bhi NOT RUN yahan - doosri wave ki errors GitHub par aa sakti hain.

## FIX 4 (GitHub: Kotlin compile PASS, unit tests 1974 chale, 3 fail) - sab "NOVA -> JARVIS" rename ke leftover
- `LaptopLinkRulesTest.topicsAreStableAndDifferByDirection`: topic "jarvis-"(7)+40 hex = 47, test 45 maang raha tha -> test 47 (desktop/link.js bhi 'jarvis-'+40, code sahi).
- `FallbackContextTest.localEarlierIsShort`: "jarvis: " 8 akshar, test limit LOCAL_CHARS+6 -> +8.
- `ChatStoreTest.exportTextHasTitleCountAndSpeakers`: export label "Jarvis" tha, test "JARVIS" -> `ChatSessionRules.roleLabel` ab "JARVIS" (brand).
Evidence: GitHub ne compileDebugKotlin + compileDebugUnitTestKotlin PASS dikhaya; SelfPatchRulesTest PASS. Ye 3 fix yahan machine par poore Kotlin test se NOT RUN (dependency chain) - hisab se verify kiye (topic length Python se = 47).
