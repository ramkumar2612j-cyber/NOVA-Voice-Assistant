# STEP 7 OF 10 (v7.147): KYA BAND HAI
Owner order 30 Oct. Add-only. CHANGELOG.md not touched.

## What it does
- BlockedRules.kt (pure) + BlockedSnapshot.kt (reads real switches, read-only): lists what is off and blocks NOVA: Accessibility, Battery unrestricted, Mic, Overlay, Notifications (important); Notification access, exact Alarms, Write settings, DND access, Install unknown apps, Contacts, Calendar (one feature each). Autostart is shown ONLY as a "check" row (only on brands that have a screen): Android cannot tell apps if autostart is on, so NOVA never says ON/OFF for it.
- MainActivity bridge: getBlocked() (read-only JSON) and fixBlocked(id) (opens the exact Settings page via the existing open* methods; owner does the last tap; id capped).
- Home: new card "KYA BAND HAI (NOVA KO KYA ROK RAHA)" under the KYA CHALTA HAI card; tap to open; THEEK KARO / GUIDE KHOLO per row; refreshes when the owner comes back from Settings. Names added to the BRIDGE self-check list.
- Voice (read-only, FeatureVoice key "blocked"): "nova kya band hai batao", "nova ko kya rok raha hai".
- MADAD topic + Skills entry (66 topics). BlockedRulesTest (5 tests).

## Evidence
- test_step7_blocked_contract PASS; help (66 equal), skills, feature voice, bridge caps (102 params capped), render smoke PASS. All 161 contract scripts PASS except two known load-flaky ones that PASS when run alone (test_part6_smooth_contract, test_part5_ui_screens); test_part4_fx_render needs playwright (not run here).
- NOT DONE: Kotlin NOT compiled (no kotlinc in this sandbox), Android build NOT CONFIRMED, phone NOT TESTED. Real behaviour per phone brand (autostart screen, Settings page names) unverified.
