# v7.182 PART B (i) - JARVIS UPGRADE LEDGER - honest status

Owner order: JARVIS khud upgrade kare, hamesha hamse puche, undo ho, aage features JARVIS se hi bane.
Ye step SIRF: request list + HAAN gate + retry cap + undo list. Build / push / install is step me NAHI hai.

## Kya bana (add-only)
- UpgradeLedger.kt (UpgradeRules + UpgradeLedger): requests phone ki file (filesDir/upgrade_ledger.json) me. States:
  DRAFT -> APPROVED -> PUSHED -> BUILD_OK -> INSTALLED -> UNDONE (REJECTED / BLOCKED alag). Ulti-seedhi chhalang mana (DRAFT se seedha INSTALLED nahi).
  Phone ya GitHub badalne wali har move par owner ki HAAN zaroori (needsOwnerYes). Retry cap 5. Tag nova-up-<id> (undo ke liye agle part me).
  Signing / secret / build.yml / DENY list chhune wali request BLOCKED (auto-build band, sirf note).
- Voice/chat: "jarvis naya feature: <kya chahiye>" -> request likhta hai + poochta hai "theek hai?"; "upgrade haan" / "upgrade nahi" / "upgrade list". Local, AI call nahi.
  Reply saaf kehta hai: abhi kuch build ya install nahi hoga.
- UI: Settings > TEST REPORT ke neeche JARVIS UPGRADES box: list + HAAN / NAHI buttons (Android.upgradeList / upgradeAct, capped).

## Evidence
- tools/test_upgrade_ledger_contract.py (10 checks) PASS; test_full_check, part6_contract, autopsy_full, bridge_caps, help, ui_contract, part6_skills, p13_ui_runtime, part4_fx_render PASS; audit_dead_ui REAL GAPS 0; naye JS blocks node --check OK.
- UpgradeRulesTest.kt (7 JUnit tests) likhe, CHALAYE NAHI. Kotlin COMPILE NAHI hua (sandbox me kotlinc nahi): UpgradeLedger.kt, MainActivity/NovaService naye hisse. GitHub Actions judge.
- Phone par NOT TESTED.

## NOT DONE (agla)
(ii) GitHub push (branch nova-up-N) + Actions poll + red-log retry loop (cap 5) + har step par HAAN.
(iii) APK download + install (HAAN) + REAL undo (pichhla tag / APK reinstall). Abhi ledger me INSTALLED/UNDONE tak koi entry pahunch nahi sakti (kuch install hota hi nahi).
Bina asli green build ke kuch PASS nahi likhna.
