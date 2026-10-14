# NOVA v43 — Popup regression fix + Parts 2–8 integration save point

## Owner requirements
- Owner works only from Android phone + GitHub web; replies must be short Hinglish with clickable links and one final `Aapko karna hai:` line.
- Do not report an Android feature as working/verified without evidence. No PC-based editing expected from the owner; GitHub Actions is the Android build path.
- Keep all original project files and toolchain: Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17. Package `com.nova.assistant`.
- ZIP name must remain `NOVA_v15_assistant.zip`, below 25 MB, no `.gradle`, build folders, `__pycache__`, APKs or secrets.
- GitHub repository: https://github.com/skfashion142/NOVA-Voice-Assistant, branch `main`.

## This save point
- Source versionName is `6.22`; default versionCode is `11`.
- Original files retained; additive changes and targeted fixes only.
- `NEXT_ACCOUNT_PROMPT_v43_PARTS_2_TO_8.md` is included in the ZIP.

## Popup issue
- Removed automatic Health Check dialog on Activity resume. `SAB TEST KARO` still runs HealthCheck explicitly.
- Update/Setup Center is not auto-opened on launch, resume, new version or missing permission; manual Settings route remains.
- Removed the greeting timer that invoked automatic update/setup checks.
- Floating card waits for command speech after a wake-word match to avoid the wake-only false-positive flash. A real command and confirmation may still show the card.
- The Android Settings self-test now expects protected text to be blocked; a protected Settings screen returning zero lines should not be reported as an app-reading failure.

## Feature coverage / changes
### Part 2 — Message Mode
- Separate persisted `MessageMode` flag; stays on after restart only when the owner enables it.
- Uses Notification Access; the owner has to switch this on in Android Settings.
- Reads only apps in an explicit selection list. WhatsApp/WhatsApp Business, Telegram and SMS are default; Instagram, X/Twitter, LinkedIn, Signal and Messenger are opt-in. Banking/payment/mail apps are excluded.
- Quick replies and dictated replies are independent from Driving mode. A send requires spoken local confirmation; dictated text is read back first. Notification text stays memory-only and is not sent to cloud providers.
- `FeatureCheck.kt` includes message-mode command tests.

### Part 3 — Scheduled tasks
- Existing pure Hinglish schedule rules, persistent store, AlarmManager receiver, boot rescheduling and voice list/cancel commands retained.
- Exact-alarm access is handled honestly. Without the Android special access, timing falls back to inexact scheduling; no exact timing is promised.
- Scheduled send actions ask for spoken `haan` at run time; timeout/unknown answer skips the message. Message text is not sent to AI.
- `FeatureCheck.kt` includes a daily-weather parser test; `LeadQueueRulesTest.kt` adds a JVM rule test.

### Part 4 — Task history/checkpoints
- Local bounded task ledger records state, safe summary, action checkpoint and whether a completed checkpoint is an idempotent candidate. Notification reports task outcome; voice command `task history` displays recent entries in the app chat.
- Model `done` is `REPORTED_COMPLETE`, not independently verified success.
- **Not fully implemented:** automatic resume after restart is deliberately disabled. The app marks in-flight UI tasks interrupted and reports them, rather than replaying a task without enough persisted context. Safe candidate metadata is recorded but no automatic resume loop is enabled.

### Part 5 — Calendar and contacts
- Existing calendar/meeting reading, contact access and event creation flow retained. Permission requests are owner-triggered from the app; event creation requires confirmation.

### Part 6 — Screen understanding
- Existing planner + bounded action loop remains. Every proposed action is checked locally; screen reads/screenshots stay blocked on protected screens (Settings, banking/payment/UPI/password/OTP and secure windows). The agent's task stop reason remains visible in chat.
- Screen content is only used for an owner-started task and only for non-protected screens; Settings includes the existing consent statement. The model never decides the spoken yes/no.

### Part 7 — Public leads / approved outreach
- Added `LeadRules.kt` and `LeadStore.kt`: capture only currently visible public business details from an allowlist of Maps/browser and selected public social pages; local bounded list; personal draft per lead.
- Added queue-based AI drafting for public business name/place; message bodies/private chats/notifications are not collected as leads.
- WhatsApp/WhatsApp Business outreach shows the exact saved draft list and requires one spoken batch approval. Draft text is not changed after approval. Default hard cap is 20 per app/day and the owner can lower it. First send error/block/accessibility failure stops the batch. Delivery/read is not claimed—only that the Send button was pressed.
- No anti-spam evasion. Unsolicited messages can lead to an account ban; WhatsApp Business is the safer route. Test with 2–3 of the owner's own numbers first.
- **Scope limit:** the batch sender is only WhatsApp/WhatsApp Business. Instagram/Telegram/X/LinkedIn can be opened/used through the general agent when their UI is exposed to Accessibility, but batch DM sending is not implemented for those apps.

### Part 8 — No-internet cloud queue
- Added persistent `OfflineJobQueue`, `OfflineQueueRules`, `NovaCloudQueueWorker` and `CloudTaskText`, using WorkManager connectivity constraints and bounded retries.
- Website, learning note, image and lead-draft jobs can be queued and later save outputs under `Downloads/NOVA` (older Android may use the app-specific Downloads folder) and post a result notification.
- Screen/UI-agent tasks are never queued or auto-replayed offline. The offline AI model remains absent.

## App control and AI provider routing
- Spoken app aliases cover common names such as Instagram/insta, WhatsApp/WA, X/Twitter, LinkedIn and browser/Chrome. The launcher can open installed apps; in-app automation works only when Android and the app expose accessible controls. Some apps, secure windows and custom-rendered screens cannot be controlled reliably.
- Text features use the owner's configured Gemini key pool first, then configured Groq, Qwen-via-Groq, Z.ai/GLM, OpenRouter and optional Pollinations text fallback where enabled and compatible. Website generation now uses the configured text-provider fallback chain too. Image generation uses its own supported image-provider path and configured image keys.
- **Keys are capability-specific:** a text/chat key is not automatically an image-generation key. Keys remain encrypted/header-only and must never be shown, logged or committed. Some calls may use quota/paid credits; 429/rate limits and network/provider failure can still stop an action.

## Test evidence
- `tests ran here`: all 26 `tools/test_*.py` scripts exited successfully. `tools/test_hindi_contract.py` was run without `python -I`; the other 25 were run with `python3 -I`.
- `tests ran here`: 14/14 executable inline JavaScript blocks in `app/src/main/assets/ui.html` passed `node --check`. One additional inline block is a GLSL shader (`type="x-shader/x-fragment"`), not JavaScript, so it was intentionally excluded.
- `tests ran here`: all 15 XML files under `app/src/main` parsed successfully with Python's XML parser. `xmllint` is not installed in this workspace.
- `tests ran here`: new pure rules (`LeadRules.kt`, `ScheduledTaskRules.kt`, `OfflineQueueRules.kt`) compiled and ran in a small local Kotlin smoke harness with stubs for project-only types; schedule/lead/queue checks passed.
- **Kotlin not compiled:** full Android source/app was not compiled. No Gradle CLI/wrapper or Android SDK `android.jar` is available in this environment. Full Android build must run through GitHub Actions.
- **not phone tested:** the popup fix and newly integrated parts have not been installed/tested on the owner's Xiaomi phone from this exact ZIP.
- No GitHub CI run of this exact ZIP has been verified yet; do not call it green until the owner sees the new run and `nova-apk` artifact.

## Next owner steps (phone only)
1. Open https://github.com/skfashion142/NOVA-Voice-Assistant/upload/main, upload/replace the root file named `NOVA_v15_assistant.zip`, then tap **Commit changes**.
2. Open https://github.com/skfashion142/NOVA-Voice-Assistant/actions/workflows/build.yml → **Run workflow** → branch `main` → **Run workflow**.
3. Open the latest run and confirm it is green with artifact `nova-apk`. Install that APK. Then run Settings → `SAB TEST KARO` and send the full report. Also confirm whether the popup still appears without a command.
4. If the CI run is red, send a screenshot of the red build lines; fix only those exact errors. Do not install the old artifact by mistake.

## Safety rules that must remain
- Calls, messages, payments, deletes and sends outside the explicit reviewed lead batch need a local spoken yes; confirmation expires in 30 seconds; unclear means no.
- Lead batch is the only batch-approval exception, and can send only exact, visible owner-approved drafts. No edits after approval.
- Do not automate or upload protected screen text/images; never automate banking/payment/UPI/password/OTP/secure-window actions.
- No silent permission grants; Accessibility, Notification Access, overlay and exact-alarm access require owner interaction.
- No bypassing platform/account rate limits; no offline AI; no self-rewriting code on phone.
