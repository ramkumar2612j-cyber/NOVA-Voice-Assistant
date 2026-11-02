# v7.122 status (Ultron UI popup + idle popup)
Sandbox: dl.google.com, Maven Central, services.gradle.org = HTTP 403, so Gradle / Android SDK / kotlinc could NOT run. Everything Kotlin below is STATIC REVIEW ONLY.

## Done this session
- NEW idle popup (NovaHud state "idle"): "How can I help you today?" + VOICE / MESSAGE / MORE chips (hit ids 11/12/13). VOICE -> wakeNow(), MESSAGE -> typing box, MORE -> three-dots menu. Auto-closes after 20 s, never opens while busy/listening.
- Entry: notification button POPUP, home dock chip POPUP (ui.html), Android.showPopup() (MainActivity) -> NovaService.ACTION_SHOW_POPUP.
- Files: NovaHud.kt, NovaService.kt, MainActivity.kt, assets/ui.html, CHANGELOG.md.
- Fixed 4 stale Python guards that already failed in the v7.121 zip and would stop CI before Gradle: test_p14_laser_contract, test_part20_contract, test_part5_ui_screens, test_part6_skills_contract. Fixed test_part6_contract (BRIDGE list: getApiUsage + showPopup). No feature removed.

## Evidence
- NovaHud.kt / NovaService.kt: read line by line, { } ( ) balanced. NOT COMPILED.
- ui.html: 65/65 JS scripts node --check PASS; headless 390x844 render: POPUP chip visible in dock, click reaches Android.showPopup, no page errors. (Fake bridge, not a phone.)
- Python contract checks: see final count in chat.
- JUnit tests: NOT RUN. Phone test of popup (listen/think/speak/idle): NOT TESTED.
- Idle popup layout (3 chips, text wrap) NOT SEEN on a phone.

## Next
1. Push zip to GitHub, run Actions "Build and Test APK", send red lines of the failed step.
2. Phone test list: idle popup (VOICE / MESSAGE / MORE / auto-close), listen / think / speaking, SILENT, STOP, X, dots menu, HAAN/NAHI, minimize, typing box, long reply.
3. v7.120 Ultron voice: build + slider tuning.

## Added later in v7.122/v7.123
- Ultron voice rewritten (UltronFx.kt): pitch shift down + octave-down layer + metal ring + bass + comb + echo; slider scales all. Numeric port check PASS (200 Hz -> 188/158/144 Hz). NOT COMPILED, NOT HEARD on phone: owner must listen, then tune slider.
- ULTRON chip back in the home dock.
- Owner reported "Ultron voice not heard / not robotic" with the switch ON: the old effect was too weak.
