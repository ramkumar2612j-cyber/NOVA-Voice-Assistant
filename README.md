# NOVA — Local-First Android Voice Assistant

NOVA is an Android voice assistant (Kotlin, package `com.nova.assistant`) that talks Hinglish/English. Wake word and common phone commands run **on the phone** (offline Vosk recognizer, no API key). Optional cloud providers are used only for open-ended questions.

| | |
|---|---|
| Version | 6.88 |
| Min / target SDK | 26 / 34 |
| Language / build | Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17 |
| Status | Sandbox: main compiles with real kotlinc (0 errors), Part 4 (v6.73): 571/571 JUnit PASS. Parts 5-6 (v6.75-6.76): 45/45 Python contract scripts PASS, new Kotlin/JUnit NOT RUN. **Gradle/AGP/GitHub-Actions build: NOT RUN. Real phone: NOT TESTED.** |

> Honest status (Part 6, v6.76): see [docs/TEST_REPORT_PART6.md](docs/TEST_REPORT_PART6.md), [docs/TEST_REPORT_PART5.md](docs/TEST_REPORT_PART5.md), [docs/TEST_REPORT_PART4.md](docs/TEST_REPORT_PART4.md) and [docs/FEATURE_REGRESSION_v6.73.md](docs/FEATURE_REGRESSION_v6.73.md). The compile was done against stubs for third-party libraries and an AOSP android.jar, so it is strong evidence but not a CI result. Run CI first, then the phone checklist - see [docs/BUILD.md](docs/BUILD.md).

## Capabilities

- **Connectors (v6.86):** CONNECT tab links GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion, Lovable (via GitHub) and custom MCP servers; permission sheet, token auto-detected from the clipboard, READ / READ + WRITE levels, writes need a yes. See [docs/CONNECTORS.md](docs/CONNECTORS.md).
- **Voice control:** battery, time, torch, volume, brightness, media, navigation, open apps, camera, settings pages, calls (dialer only), WhatsApp messages, alarms/timers.
- **Wake word:** Vosk by default; optional Porcupine (Settings, needs your own Picovoice AccessKey + nova.ppn) with automatic Vosk fallback.
- **In-app jobs (v6.67):** "chat open karo <name>", "youtube pe <song> chalao", "mera channel kholo": the agent searches, asks "Kaun sa? 1 .. 2 .." (answer 1-7), asks the message once and types it.
- **No lock rules:** NOVA has no voice recognition and no lock-screen gate. Anybody's voice works, locked or open, after the wake word.
- **Auto reply (v6.53):** WhatsApp / Instagram message aate hi NOVA owner ke apne text se turant jawab bhejta hai (poochhe bina), baad me bolkar batata hai. Group, code/password wale message aur bina Reply button wale notification skip. Home page ki ghanti me poori history.
- **No unlocking:** NOVA never unlocks the phone (feature removed in 6.48).
- **Schedule:** repeating tasks by weekday, priority (normal / zaruri / bahut zaruri) and a note — by voice or the *MERA SCHEDULE* box.
- **Morning routine:** first unlock of the day (06:00–11:59) → greeting + today's jobs; 25‑minute lead reminders; **wake alarm only 06:00–12:00, rings again and again until OFF** (DISMISS = 1 min snooze).
- **Battery guard:** one alert each below 50/40/30/20/10 %; at ≤5 % a WhatsApp SOS to Papa and Mummy.
- **No auto-start:** nothing starts at boot or app open. NOVA runs only after you tap ACTIVATE.
- **Safety invariants:** `CALL_PHONE` and `QUERY_ALL_PACKAGES` are declared since v6.54 (owner request), screen monitoring on by default (needs Accessibility), banking/payment/installer screens blocked for tap/type, secrets encrypted with Android Keystore.

Full feature list and known limits: [docs/FEATURES_AND_LIMITS.md](docs/FEATURES_AND_LIMITS.md).

## Repository layout

```
app/                Android module (src/main, src/test)
docs/               BUILD, ARCHITECTURE, FEATURES_AND_LIMITS, PHONE_CHECKLIST, SETUP_STEPS, PROGRESS, HANDOFF
docs/archive/       Historical prompts, audits and reports (read-only history)
tools/              Python contract tests, knowledge/skill-pack builders, self-improve script
skillpacks/         Optional skill packs (JSON)
.github/workflows/  CI (unit tests + debug APK)
CHANGELOG.md        Version history
```

## Quick start

1. Push the repo to GitHub → Actions → **NOVA Android CI** (see [docs/BUILD.md](docs/BUILD.md)).
2. Install the debug APK, open NOVA, allow Microphone.
3. Tap **ACTIVATE**, say "nova", then a command. Run the [real-device checklist](docs/REAL_DEVICE_CHECKLIST_v6.83.md) (and the older [phone checklist](docs/PHONE_CHECKLIST.md)).
4. Local build on a PC: `./gradlew testDebugUnitTest assembleDebug` (JDK 17 + Android SDK 34, see [docs/BUILD.md](docs/BUILD.md)).

**Honest status (v6.84):** logic is covered by 646 JUnit tests + 48 contract scripts + 2 headless UI checks, all run only in a sandbox with stubs. The real Gradle build, the APK and every phone behaviour are NOT TESTED yet: see [docs/FINAL_TEST_REPORT.md](docs/FINAL_TEST_REPORT.md).

## Contributing / handoff

Read [docs/HANDOFF.md](docs/HANDOFF.md) first. Rules: small steps, a fresh ZIP after each, and always state what was **RUN** vs **NOT RUN**.

## Keys kahan daalni hain (API keys / signing)
- **Source me koi asli key nahi hai.** Build bina kisi key ke chalta hai; tab sirf phone ke local commands kaam karte hain.
- **AI keys (Gemini, Groq, OpenRouter, GLM, image, GitHub token):** app kholo > Settings > us provider ka box > key paste karo. NOVA unhe Android Keystore (AES-GCM) se encrypt karke phone me rakhta hai. Kaun si key kis ke liye: `docs/config_templates/nova-keys.template.txt`.
- **Porcupine wake word (optional):** AccessKey + `nova.ppn` Settings se; `*.ppn` git me nahi jaata.
- **Signed APK (optional, taaki in-app update chale):** GitHub repo > Settings > Secrets > `NOVA_KEYSTORE_*` (template: `docs/config_templates/github-actions-secrets.template.md`) ya local `signing.env.template`.
- **Local build:** `local.properties` me SDK path (`docs/config_templates/local.properties.template`), phir `./gradlew testDebugUnitTest assembleDebug` (wrapper Gradle 8.7). `local.properties`, `*.jks`, `*.keystore`, `*.ppn` `.gitignore` me hain.
## Intelligent Contact Resolution (Part 1/5)
NOVA resolves names using Android's real `ContactsContract.CommonDataKinds.Phone` rows and the existing `READ_CONTACTS` permission flow. Matching is kept separate from call/message execution. Duplicate names, multiple plausible people, low-confidence results, no matches, permission failures, and provider errors are reported without choosing a potentially incorrect contact. A retry re-runs lookup only; repeat the complete call/message command after the contact is identified. Unit mocks validate logic only and do not verify access to contacts on a physical device.

### PART 2/5 contact selection
Ambiguous call-contact matches can be shown in an Android selection dialog with masked phone suffixes. A selected candidate is routed through NOVA's existing confirmation/permission policy. Voice matching is restricted to the currently offered candidates. Device-level dialog, microphone, permission, and call verification must be performed on a real Android device; a successful unit test is not evidence of device verification.

### PART 3/5 centralized intent and routing foundation
`IntentParser` classifies explicit call, message, app-specific communication, app-open, and ambiguous requests without executing anything. `CommandRouter` creates a side-effect-free route plan with app identity separate from action, and records which later stages are required. The service uses this as a clarification preflight for incomplete/mixed commands; the existing specialist handlers remain responsible for real contact lookup, app launch, permissions, confirmations, execution, and reporting. WhatsApp Business is recognized as a distinct app identity for the next integration part; no Part 4 behavior is enabled here.
