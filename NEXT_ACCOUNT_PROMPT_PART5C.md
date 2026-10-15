NOVA Part 5C. Base: NOVA-WIP-v75-PART5-B.zip (only source of truth). Pehle PART5_STATUS.md, PART4_STATUS.md, PART3_STATUS.md, PART2_STATUS.md padho. Owner Hinglish, non-technical, phone-only. Har part ke baad ZIP + agla NEXT_ACCOUNT_PROMPT do. Bich mein baar baar test mat chalao; `bash tools/run_all_checks.sh` sirf sabse aakhir mein ek baar.

STRICT: koi feature/screen/setting/API/fallback/permission/command/safety confirmation delete, disable ya replace nahi. Package/structure wahi. No mock/fake success. API keys logs mein nahi. Bina existing confirmation ke call/message nahi. STOP/SILENT (Part 3) aur v76 UI layer bachao. Jo run na ho NOT TESTED likho.

BAACHA KAAM (A, B, C, D, E ho chuke; PART5_STATUS.md padho):
F. Part 2/3 ke bache kaam sirf safe: FeatureStatus ("KYA CHALTA HAI") mein naye features ki rows (Auto-reply, Routines, Lock Guard, Battery, Offline pack, API limits), voice routes sirf agar compile ke bina safe ho warna NOT DONE likho.
FINAL: `bash tools/run_all_checks.sh` ek baar (test_part5_ui_screens.py Chromium chalayega; ~1 min), Gradle/JUnit sirf SDK ho to warna GitHub Actions. PART5_STATUS.md update, NOVA-WIP-v75-PART5.zip, NEXT_ACCOUNT_PROMPT_PART6.md, chhota Hindi status.
