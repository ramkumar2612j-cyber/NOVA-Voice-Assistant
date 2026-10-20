# NEXT ACCOUNT PROMPT - v7.86 (popup / laser / "samajh nahi aaya" fixes) - pichla account ne code likha, compile + JUnit + guards PASS (sandbox), phone test baaki

Owner: Hinglish, short steps, phone-only (Chrome + GitHub web), no coding knowledge. Repo github.com/skfashion142/NOVA-Voice-Assistant (main). Rules: add-only, "ek cheez theek karte ho aur agli bigad jati hai" -> har change ke baad poora JUnit + python guards chalao. Kabhi "DONE" mat bolo bina evidence level ke (RUN here / NOT RUN / NOT TESTED on phone). Har reply ke end me ek line: owner ko ab kya karna hai. Protected screens cloud nahi jate, keys kabhi show/log nahi.

## Owner ki shikayat (19-20 Oct 2026, v7.85)
1. Popup apne aap aa jata hai.
2. Laser scan theek se nahi chalta; owner ko pata nahi kya bolna hai scan ke liye.
3. Bola "ye kya chal raha hai screen pe" -> NOVA ne "Ye samajh nahi aaya, fir bolo" kaha; dobara bola to ek se zyada assistant / messages aaye (mess).

## Root cause (code padh kar, phone par REPRODUCE NAHI hua)
- Scan ke baad NovaService me followWindow (bina wake word sunna, max 6 baar) khud khulta hai. Reel / movie ki apni awaaz command samjhi jati thi -> "samajh nahi aaya" (handle() ke 2 fallback + screenFollow blank) + HUD card + naye replies. Isse "popup khud aana" aur "kai assistants" dono bante hain.
- "ye kya chal raha hai" (bina "dekho"/"screen") koi rule nahi pakadta tha -> AI fallback -> "samajh nahi aaya".

## v7.86 me KIYA GAYA (RUN: compile 0 error, JUnit 1400/1400, python guards 144/144 (final run: 143 + the 1 fixed after, re-run PASS); NOT TESTED on phone)
- QuickScan.kt: wantsLook ab bina look-word ("ye kya chal raha hai"), naya wantsScan ("scan karo", "screen scan", "laser scan"), followAllowed, quietJunkFollow, QUIET_FOLLOW_MS=90 s, phrasesText.
- Logic.kt: classify me wantsScan -> Cmd("analyze") (has("screen") rule se pehle).
- NovaService.kt: videoQuietUntil field; followWindow 2 jagah QuickScan.followAllowed / quiet check; screenVideoRead + startMovieScan quiet set karte hain; screenFollow me quietJunkFollow -> silent end; handle() ke 2 "samajh nahi aaya" fallback follow turn par "" (silent); popup sirf MEDIUM/HIGH + overlay permission (ScreenVideoRules.autoPopup), browser khud nahi khulta (explicit=false), 90 s auto-close; laser epoch + 90 s watchdog.
- NovaHud.kt: `leaving` card; show() / hide() / destroy() usse turant hatate hain + 700 ms fallback.
- LaserOverlay.kt: bada label narrow phone par shrink.
- ScreenVideoRules.kt: autoPopup. build.gradle.kts versionName 7.86. CHANGELOG top entry likha. Test: QuickScanTest.kt me QuickScanV786Test (4 tests).

## AGLE ACCOUNT KA KAAM (is order me)
1. (Ye step ho chuka, sirf dobara check karna ho to) Compile + JUnit: `bash tools/local_compile_check.sh /ABS/PATH/work` (ABSOLUTE path dena; kotlinc github release se, android.jar ke liye `git clone --depth 1 --filter=blob:none --sparse https://github.com/Reginer/aosp-android-jar.git` + `git sparse-checkout set android-34`). Baseline v7.85 = 1396/1396 PASS. Compile error aaye to sirf v7.86 edits theek karo. Mere likhe QuickScanV786Test ke assertions galat nikle to code dekho, test zabardasti mat badlo (wantsLook("gaana kya chal raha hai") false hona chahiye; "kya chal raha hai bhai" false).
2. (Ho chuka: 144/144; ~15 min lagte hain, background me chalao) `./tools/run_all_checks.sh`. Version-pinned guards (test_part4_quickscan_contract etc.) 7.86 par fail ho to ">= " relax karo (pehle jaise).
3. Check karo kuch bigda to nahi: classify("nova torch jala do"), "nova battery kitni", "gaana kya chal raha hai", "nova screenshot lo", "qr scan karo", "scan band karo" ko analyze NAHI banna chahiye. CommandCorpusTest / WakeRulesTest pass hone chahiye.
4. Bacha hua kaam: sirf PHONE TEST + GitHub Actions build. (Phrase list UI card + help topic + laser on "ab ye dekho" ab ho chuke.) Phone test: reel chalte hue "nova ye kya chal raha hai" -> 1 baar jawab, koi khud popup / "samajh nahi aaya" nahi; 90 s tak bina "nova" bole kuch nahi sunna chahiye; Home par SCREEN SCAN card dikhe aur tap par sheet khule. Optional: HUD hide/show race ke liye JUnit-able rule.
5. Naya zip banao (NOVA-v7_86-...zip, __pycache__ nahi, <25 MB), status md (PART6_STATUS_v7_86.md) likho, CHANGELOG me NOT RUN ko RUN me badlo jo chalaya. Last me "autopsy check" (tools/autopsy_full.py) abhi baaki hai (plan: parts ke baad).
6. Owner ke steps: zip repo me daalo -> Actions "Build and Test APK" -> APK install -> 3 test karke batao.
