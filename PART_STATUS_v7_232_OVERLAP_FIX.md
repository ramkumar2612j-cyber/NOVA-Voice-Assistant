# PART STATUS v7.232 — OVERLAP FIX (add-only)

## Root cause (asli)
7 elements apni `position:fixed/absolute` kho kar `relative` ban gaye the, isliye page flow me baith kar header / orb / bottom bar par chadh jate the:
- `#c3sheet, #aiw, #helpw, #c_hl, #hint` — v7.227 popup restyle ne `position:relative` laga diya tha.
- `#jh_chip, #v230back` (BACK button) — generic `button:not(#btn){position:relative}` ne ID rule ko hara diya.
Result: upar header ke upar ek khali box, BACK button kabhi orb ke upar kabhi beech me.

## Fix
- `ui.html`: sirf ek naya `<style id="v7232-pos-fix">` (sabse aakhir me). Koi purani line delete/change nahi (backup: ui_BEFORE_v7_232.html.bak).
- #c3sheet band rehta hai jab tak `.on` na ho (animation fill-mode ki wajah se khula reh jata tha).

## Check (sandbox, headless Chromium, 360x640 + 390x844)
- Detector: fixed/absolute override = 0 (pehle 8).
- BACK ab hamesha (10,8) par; popups full-screen/bottom me sahi.
- JS errors 0.
## NOT TESTED: asli phone, GitHub Actions APK build.
