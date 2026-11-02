# NOVA next-account prompt (v6.88, connectors done)

Paste this whole file as the first message in the new account, attach `NOVA-Voice-Assistant-v6.87-connectors-chat-voice.zip`.

## Who / how
Owner talks Hinglish. Small steps, a fresh ZIP after each step, tasks list, say plainly what was RUN vs NOT RUN. Anything that must be downloaded must download automatically, never by hand.

## Project
NOVA = local-first Android voice assistant (Kotlin, `com.nova.assistant`, minSdk 26, AGP 8.5.2, Gradle 8.7, JDK 17). Invariants: no API key needed for local commands, Vosk offline wake word, dangerous actions only after a local spoken/typed yes, screen monitoring rules unchanged, no QUERY_ALL_PACKAGES-style additions, 22 permissions in the manifest (a contract test counts them), no new permission for connectors.

## Done in this session (all in the ZIP)
1. v6.85 automatic downloads: `gradle/vosk-model.gradle.kts` (task prepareVoskModel on preBuild, pure JVM, env NOVA_VOSK_MODEL_URL for a mirror).
2. v6.86 CONNECTORS: ConnectorCatalog / ConnectorStore / ConnectorApi / ConnectorParse / ConnectorRules, bridge methods in MainActivity (connector*), CONNECT tab in ui.html (script id `nova-conn-v686`). Services: GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion, Lovable (no public API: works through GitHub), custom MCP (https). Token page opens, token is picked from the clipboard, verified, stored in SecureStore. Levels OFF / READ / READ + WRITE. GitHub optional OAuth device login (owner's Client ID).
3. v6.87 chat + voice workflow: chat bar (＋ CONNECTORS picker + chips), voice/typed "github connect karo" (strict yes -> ConnectorFlow -> MainActivity.onWindowFocusChanged picks the clipboard token), "github write on karo", read only / off, chained steps with phir / uske baad / then (connQueue in NovaService), results as chat messages. Docs: `docs/CONNECTORS.md`, checklist rows 30-46 in `docs/REAL_DEVICE_CHECKLIST_v6.83.md`.
4. Tools: an "inspector" artifact page (claude.ai) to select UI parts; not part of the repo.

## Evidence
RUN: full Kotlin compile with kotlinc 1.9.24 + stubs, 671 JUnit PASS (`JSON_JAR=<real org.json jar> bash tools/local_compile_check.sh /abs/workdir`; the script path must be absolute; android.jar via sparse clone of github.com/Reginer/aosp-android-jar), contract scripts (`bash tools/run_all_checks.sh`), headless UI with a fake bridge.
NOT RUN: Gradle/AGP build, GitHub Actions, any real provider API call, any phone test. Provider endpoints are written from public docs and unverified.
Known: `tools/ui_chat_check.py` times out in the sandbox on the ORIGINAL zip too (not caused by connectors). `testImplementation org.json:json` was added because android.jar's org.json throws in unit tests.

## Next steps (in order)
1. Push to GitHub, run the CI workflow, fix any red step (first real Gradle build).
2. Real-device checklist rows 30-46; fix whatever fails (clipboard pick-up on Android 12-14, opening the token page from the service when the app is in the background, chip bar layout).
3. Verify each provider endpoint with a real token; adjust ConnectorApi/ConnectorParse and their tests.
4. Optional: OAuth approve screens for providers other than GitHub (needs the owner to register OAuth apps). Data passing between chained steps (`@last`) and commit_file by voice are DONE in v6.88.
