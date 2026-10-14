# NOVA v44 — Task checkpoint + safe manual resume upgrade

## Owner requirements
- Owner works only from Android phone + GitHub web; replies must be short Hinglish with clickable links and one final `Aapko karna hai:` line.
- Do not report an Android feature as working/verified without evidence. No PC-based editing expected from the owner; GitHub Actions is the Android build path.
- Keep all original project files and toolchain: Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, JDK 17. Package `com.nova.assistant`.
- ZIP name must remain `NOVA_v15_assistant.zip`, below 25 MB, no `.gradle`, build folders, `__pycache__`, APKs or secrets.
- GitHub repository: https://github.com/skfashion142/NOVA-Voice-Assistant, branch `main`.

## This save point
- Source versionName is `6.23`; default versionCode is `12`.
- Original files retained; additive changes and targeted fixes only.
- `NEXT_ACCOUNT_PROMPT_v43_PARTS_2_TO_8.md` remains as historical handover. This file is the latest handover.
- ZIP filename must stay `NOVA_v15_assistant.zip`, under 25 MB, without build outputs, `.gradle`, `__pycache__`, APKs or secrets.

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

### Part 4 — Task history/checkpoints (improved in v44)
- Local bounded task ledger stores a redacted short summary, lifecycle state, count, and bounded action checkpoint. It stores no raw screen excerpts or typed message content. Completion from the model is `REPORTED_COMPLETE`, not verified success.
- Before *each* UI action, NOVA synchronously writes a checkpoint intent. If that save fails, the action is not executed. This closes the crash window where a send/tap might otherwise happen without the ledger knowing about it.
- Added explicit local voice commands `resume last task`, `task resume`, `pichla task resume karo`, and `pichla kaam resume karo`. This is never automatic.
- Manual continuation is allowed only for the latest interrupted task, only when every persisted action was a completed `open_app` or `wait`, the original task text is safely represented by the short unredacted summary, no resume was already attempted for that interruption, and the original five-minute deadline is still active. The original deadline and step budget are preserved; continuation reads the current screen and replans.
- Any `tap`, `long_tap`, `type`, `scroll`, `back`, `home`, failed/incomplete action, unknown action, overlong/redacted task summary, old-format ledger record, expired deadline, or unsaved intent causes resume to fail closed. This is intentionally conservative to avoid duplicate messages, submissions, or other side effects.
- Task history now labels manual resume availability vs. blocked checkpoints. `FeatureCheck.kt` and the pure `AgentTaskRulesTest` include resume command/gating tests.

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

## Test evidence (this exact v44 source tree)
- `tests ran here`: all 26 `tools/test_*.py` scripts exited successfully. `tools/test_hindi_contract.py` ran without `python -I`; the other 25 ran with `python -I`.
- `tests ran here`: 10/10 `AgentTaskRulesTest` JVM tests passed, including manual-resume gating, explicit command matching, original deadline and rejection of non-idempotent actions. This used the available Kotlin 1.9.0 compiler plus a tiny test-only `Logic.norm` stub; it is not the app toolchain.
- `tests ran here`: 14/14 executable inline JavaScript blocks in `app/src/main/assets/ui.html` passed `node --check`; the GLSL shader block was correctly excluded.
- `tests ran here`: all 15 XML files under `app/src/main` parsed successfully using Python's XML parser. `xmllint` is not installed in this workspace.
- `tests ran here`: the prior save point's pure rules harness for lead/schedule/offline queue remains in place, and all source-contract checks for those parts passed again.
- **Full Android build not run:** attempted `bash tools/local_compile_check.sh /tmp/nova_task_check`, but it exited with status 6 while trying to download Kotlin 1.9.24 / `android.jar` (network download unavailable). The actual Android app/Kotlin files therefore remain **Kotlin not compiled** in this environment; CI must check them.
- **not phone tested:** this exact ZIP, safe-resume flow, and the popup fix have not been installed/tested on the owner's Xiaomi phone.
- No GitHub CI run for this exact ZIP has been verified yet. Do not call it green until the owner sees the latest `Build NOVA from zip` run and `nova-apk` artifact.

## Next owner steps (phone only)
1. Open https://github.com/skfashion142/NOVA-Voice-Assistant/upload/main, upload/replace the root file named `NOVA_v15_assistant.zip`, then tap **Commit changes**.
2. Open https://github.com/skfashion142/NOVA-Voice-Assistant/actions/workflows/build.yml → **Run workflow** → branch `main` → **Run workflow**.
3. Open the latest run and confirm it is green with artifact `nova-apk`. Install that APK. Then run Settings → `SAB TEST KARO` and send the full report. Also confirm whether the popup still appears without a command.
4. Task test after install: say `task history`; simulate only safe interruption with an `open_app`/`wait`-only task, then say `resume last task`. Test an interrupted task that includes a tap/type step only as a report check—the app must refuse resume. Do not simulate a real send/payment/delete.
5. If the CI run is red, send a screenshot of the red build lines; fix only those exact errors. Do not install the old artifact by mistake.

## Safety rules that must remain
- Calls, messages, payments, deletes and sends outside the explicit reviewed lead batch need a local spoken yes; confirmation expires in 30 seconds; unclear means no.
- Lead batch is the only batch-approval exception, and can send only exact, visible owner-approved drafts. No edits after approval.
- Do not automate or upload protected screen text/images; never automate banking/payment/UPI/password/OTP/secure-window actions.
- No silent permission grants; Accessibility, Notification Access, overlay and exact-alarm access require owner interaction.
- Safe task continuation is owner-triggered only. Never make it automatic; keep the write-ahead action checkpoint and fail-closed behavior.
- No bypassing platform/account rate limits; no offline AI; no self-rewriting code on phone.
