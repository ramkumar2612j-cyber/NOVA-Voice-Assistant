# NOVA autopsy - PART 3 of 6 (music / YouTube / WhatsApp / calls) - v6.99 source
Evidence: PASS = script in sandbox, NOT RUN = needs compile / phone.
## Found + fixed (written + statically checked; Kotlin compile NOT RUN, phone NOT TESTED)
1. MUSIC false success: musicPlay() used raw startActivity. Android 10+ blocks background starts WITHOUT an exception, so NOVA said "<app> mein <song> chala diya" while nothing opened. Fix: every start goes through launch() (checks the app came to front, 3 ways, honest reason). New flag lastLaunchBlocked stops the pointless extra fallbacks and gives the real reason at once.
2. CALL false success: callTool() with CALL_PHONE allowed used raw startActivity(ACTION_CALL) and said "call request sent" even when blocked, with no dialer fallback. Fix: ACTION_CALL goes through launch(); if it does not open, the old dialer fallback runs.
## Checked, no code change
- WhatsApp send: installed-app check, exact package (never the other WhatsApp), wa.me + setPackage, duplicate guard, Accessibility presses Send only for the exact confirmed text. OK (static).
- WhatsApp voice/video call: not possible via a public Android intent. NOVA refuses and offers the chat, never a normal call. By design.
- executeChosenCall (contact picker): left as is. The picker only exists while the NOVA screen is visible, where starts are not blocked, and launch() would sleep on the main thread.
- Answer / reject call: notification button first, TelecomManager needs ANSWER_PHONE_CALLS, "no_permission" is spoken. OK (static).
- YouTube: tool play_music only opens YouTube SEARCH RESULTS (no autoplay). "youtube kholo" = open app. Music apps in MusicPlayRules do not include plain YouTube; "youtube pe X chalao" goes to AppSearchRules (play-from-search, then search box, then tap first result via Accessibility).
- WhatsApp short form "whatsapp <name> <message>" takes the FIRST TWO words as the contact. One-word contact + message needs "<name> ko ... bhejo". Left as spec.
- WhatsApp READ: notification-based (NovaNotificationService + drive/message modes). NOT traced in this part -> Part 4 or 5.
## Tests
tools/test_autopsy_part3_contract.py (6 checks PASS). run_all_checks: 68 PASS, 1 FAIL = test_vosk_model_packaging (needs .github/workflows, not in zip: ignore).
Kotlin compile not possible here (android.jar host blocked): GitHub Actions build must be read first.
