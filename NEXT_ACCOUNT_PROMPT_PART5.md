NOVA Part 5. Base: NOVA-WIP-v75-PART4.zip (only source of truth). Pehle PART4_STATUS.md, PART3_STATUS.md, PART2_STATUS.md padho. Owner Hinglish, non-technical, phone-only. Poora ZIP `NOVA-WIP-v75-PART5.zip`, `NEXT_ACCOUNT_PROMPT_PART6.md` aur chhota Hindi status do.

STRICT: koi feature/screen/setting/API/fallback/permission/command/safety confirmation delete, disable ya replace nahi. Package aur structure wahi. No mock/fake success. API keys logs/reports mein nahi. Bina existing confirmation ke call/message nahi. STOP/SILENT (Part 3) aur v76 UI layer (Part 4) bachao. Jo run na ho use NOT TESTED likho, PASS tabhi jab chalaya.

PART 5 (Part 4 ka baaki):
1. Chromium (Playwright, fake Android bridge) se in screens ke screenshots lo aur dekho: Chat + HAAN/NAHI, Settings, History, Help/MADAD, Update Center, Skills, Limits, FeatureStatus, sheet. 320/360/412/820 width par, SILENT ON aur OFF dono. Overlap, kati hui text, chhote targets theek karo (sirf CSS/ARIA, action semantics nahi).
2. Native HUD/popup (NovaHud.kt) ki padhne layak size/contrast aur HAAN/NAHI vs STOP alag hone ki jaanch; sirf safe visual badlav.
3. Duplicate id `al_on` (ui.html) ko behavior badle bina suljhao ya document karo.
4. tools/test_part4_ui_contract.py ko screenshot-based checks se badhao (agar Playwright CI mein chal sake).
5. `bash tools/run_all_checks.sh`; Android Gradle build + JUnit sirf tab jab SDK ho (pichli baar offline FAIL: SDK nahi), warna GitHub Actions par chalane ko kaho. Har item PASS/FAIL/NOT TESTED.
6. Part 2/3 ke bache kaam (voice routes Auto-reply/Routines/Lock Guard/Battery/Offline pack/API limits, FeatureStatus rows) sirf agar safe ho.
