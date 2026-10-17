NOVA Part 5B. Base: NOVA-WIP-v75-PART5-A.zip (only source of truth). Pehle PART5_STATUS.md, PART4_STATUS.md, PART3_STATUS.md, PART2_STATUS.md padho. Owner Hinglish, non-technical, phone-only. Har part ke baad ZIP + agla NEXT_ACCOUNT_PROMPT do. Bich mein baar baar test mat chalao; `bash tools/run_all_checks.sh` sirf sabse aakhir mein ek baar.

STRICT: koi feature/screen/setting/API/fallback/permission/command/safety confirmation delete, disable ya replace nahi. Package/structure wahi. No mock/fake success. API keys logs mein nahi. Bina existing confirmation ke call/message nahi. STOP/SILENT (Part 3) aur v76 UI layer bachao. Jo run na ho NOT TESTED likho.

BAACHA KAAM:
B. `python3 tools/ui_screens.py <outdir> --quick --silent-off` (aur --silent-on, alag process mein; har shot ~10s). Pehle finding: `.sm` buttons 43px -> CSS min-height 44px (ui.html v76 layer mein). Phir 320x568 aur 412x915 (--quick ke bina sizes) ke screenshots Read karke dekho: overlap/kati text/chhote targets, sirf CSS/ARIA fix.
E. tools/test_part4_ui_contract.py mein ui_screens.py wale checks jodo (Playwright na ho to SKIP, FAIL nahi).
F. Part 2/3 ke bache kaam sirf safe: FeatureStatus mein rows, voice routes (Auto-reply/Routines/Lock Guard/Battery/Offline pack/API limits) sirf agar compile ke bina safe ho.
FINAL: run_all_checks.sh ek baar, Gradle/JUnit sirf SDK ho to warna GitHub Actions. PART5_STATUS.md update, NOVA-WIP-v75-PART5.zip, NEXT_ACCOUNT_PROMPT_PART6.md, chhota Hindi status.
