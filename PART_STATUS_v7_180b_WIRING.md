# v7.180b WIRING FIX - honest status

Owner request: "bachi hui wiring complete karo, zip fully complete karke do, APK ke liye."

## Kya mila aur kya theek kiya (add-only, kuch feature nahi hataya)
1. LEAD FINDER card ke buttons DEAD the: ui.html Android.call / Android.openWame / Android.openUrl bulata tha,
   par MainActivity me ye bridge methods the hi nahi (guard ki wajah se chup-chaap kuch nahi hota tha).
   -> MainActivity.kt me 3 bridge methods joda: call(number) = dialer khulta hai (khud call nahi lagta),
      openWame(number) = WhatsApp chat khulti hai (message khud nahi jata), openUrl(url) = sirf http/https.
      Teeno me length cap + number/URL check.
2. ui.html me LEAD FINDER card (#lfdCard) do baar likha tha (duplicate ids). Ek copy hata di.
3. CI blocker: 4 purane version-pin tests (part2/3/4/5) 7.178-7.180 aur versionCode 40 nahi maante the,
   isliye build.yml ka "Source contract checks" step FAIL karke APK rok deta. Pin lists me jod diya (sirf tools/*.py).
4. CALL TALK (v7.180) wiring dobara dekhi: CallAgent ke 7 hooks NovaService.wireCallAgent me sab bandhe hain,
   feed() hook mic loop me hai. Yahan kuch bacha nahi.

## Evidence (machine)
- tools/audit_dead_ui.py: REAL GAPS 0.  tools/test_autopsy_full_contract.py: REAL GAPS 0 (pehle 3).
- test_bridge_caps_contract, test_help_contract, test_call_agent_contract, test_call_talk_contract,
  test_part6_skills_contract, test_part2/3/4/5 contracts: PASS.
- Slow checks (p13_ui_runtime, p14_laser, part4_fx_render) 140 s limit me PASS (CI limit 150 s).
- Poori suite ek baar chali: 173 me se baaki sab PASS (upar ke fix ke baad).

## NOT DONE / NOT TESTED (honest)
- tools/test_native_smooth_contract.py (RUNTIME timing, 4 FAIL) sandbox me FAIL; ye original upload par bhi fail hota tha
  (v7.177 notes). GitHub CI par dekho; agar wahi red ho to sirf us step ki red lines bhejo.
- Kotlin compile (MainActivity ke 3 naye methods) yahan NAHI hua; GitHub Actions karega.
- Phone par koi test nahi. versionName 7.180 hi rakha (7.181 karne se bahut se version-pin tests tootte).
- Lead card ke CALL/WHATSAPP/SITE buttons phone par NOT TESTED.
