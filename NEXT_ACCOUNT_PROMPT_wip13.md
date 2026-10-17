# NOVA wip13 -> next account handover
Base: wip12. Changed: Kotlin music flow (see CHANGELOG top entry), build.yml restored.
Contract checks: 59/59 PASS in sandbox. Kotlin compile + unit tests: NOT RUN in sandbox, run GitHub Actions "NOVA Android CI" first and fix any red line.
Not verifiable without a real phone: real Spotify playback, Notification-access verification, popup tap, voice pick, duplicate contacts, WhatsApp vs Business on a device.
Next: if CI is red, fix compile errors in NovaService.kt (musicBegin/musicPlay/verifyPlayback/songAsk), MusicPlayRules.kt, MusicCatalog.kt, MainActivity.kt. Then Part 5 voice/face, Part 2A.
