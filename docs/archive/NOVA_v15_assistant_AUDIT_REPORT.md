# NOVA v15 — Final Autopsy + Security + Build Audit
Date: 2026-10-12

## A. Files inspected
- Unzipped and inspected the complete `NOVA_v15_part1.zip` first.
- Original archive entries: 350 files.
- Main Android/Kotlin sources, tests, assets, resources, manifest, Gradle files, tools, and existing audit reports were inspected.
- Android manifest component classes were cross-checked; classes declared in shared Kotlin files were confirmed present.

## B. Changed
1. `app/src/main/java/com/nova/assistant/Logic.kt`
   - Contact selection no longer falls back to an unrelated shortest contact.
   - Unmatched/ambiguous contact names now return no match so the caller can ask safely.
2. `app/src/main/java/com/nova/assistant/NovaService.kt`
   - Explicit WhatsApp commands are routed through the strict local parser before generic AI routing.
   - WhatsApp send is always confirmation-protected, even if ordinary confirmation skipping is enabled.
   - AI/chat text is sanitized before reaching the production chat listener.
   - Self-heal is diagnosis + notification only: it no longer downloads models or asks a free AI to repair/operate the phone.
   - Corrected-transcript WhatsApp commands use the same safe parser.
3. `app/src/test/java/com/nova/assistant/CrashNoteTest.kt`
   - Replaced an API-key-looking test token with a non-secret placeholder. The masking test remains intact.

## C. Added
- `WhatsAppCommandRules.kt` — strict explicit WhatsApp parser.
- `ChatText.kt` — raw JSON envelope sanitizer for user-facing chat.
- `WhatsAppCommandRulesTest.kt` — recipient/message parsing and no-guess contact tests.
- `ChatTextTest.kt` — raw JSON never shown to user.
- `tools/test_part2_security.py` — source-level security/UI regression contracts.
- `.github/workflows/build.yml` — GitHub Actions Android test + debug assemble workflow.

## D. Missing / deleted
- No original archive files were deleted.
- No original source/resource/test files were silently removed.
- No build cache, `__pycache__`, `.gradle`, or temporary build output is included in the final package.

## E. Bugs / risks found and fixed
### WhatsApp
- `Aman Biswan` could previously be reduced to a generic contact lookup path where an unmatched name could fall back to an unrelated shortest contact.
- Fixed with strict parsing and safe contact resolution.
- `Biswan` is not an app name and is not accepted as a standalone WhatsApp recipient.
- WhatsApp send confirmation is mandatory; `skipOrderConfirm` cannot bypass it.
- No code claims a successful send before the Accessibility send callback reports success.

### Raw JSON / debug UI
- Added a final user-facing sanitizer so JSON envelopes such as `{"status":"success"}` become normal text.
- Existing production UI was checked for the requested `MIC DEBUG` / raw diagnostic panel; those strings are absent.
- Internal logging/diagnostic paths remain available without exposing raw backend JSON in chat.

### HindiPack
- Existing implementation was checked for HTTPS, host allow-list, compressed and uncompressed size limits, ZIP-slip protection, folder-layout handling, readiness detection, retry spacing, and duplicate-download prevention.
- These protections remain enabled.
- No claim is made that the Hindi model works on-device because Android/Gradle/phone execution was unavailable here.

### Self-heal
- Self-heal previously performed automatic model-download actions and could call the AI chain for repair advice.
- It is now strictly diagnosis + notification only.
- It does not rewrite source, grant permissions, install apps/models, publish code, send messages, make payments, or send protected information to an AI for repair.

## F. Root causes
- Contact resolver had an unsafe fallback selection rule.
- WhatsApp had a confirmation bypass tied to the general skip-confirmation setting.
- Chat rendering trusted provider response text too literally.
- Self-heal mixed diagnosis with remediation actions.

## G. Exact fixes
- Removed arbitrary shortest-contact fallback; only exact or unique word matches are accepted.
- Added `WhatsAppCommandRules` before generic AI routing and for corrected cloud transcripts.
- Changed WhatsApp confirmation to `askFirst(..., must = true)`.
- Added `ChatText.clean(...)` before AI text is emitted to the UI.
- Removed automatic model downloads and AI repair calls from `healRound()`.
- Added regression tests and a GitHub Actions workflow.

## H. Tests / checks actually executed
### Python contract suite
Command executed:
`for f in tools/test_*.py; do python3 "$f"; done`

Result:
`PYTHON total=33 pass=33 fail=0`

### Python bytecode syntax
`python3 -m py_compile tools/*.py` executed successfully before final cleanup.

### New security contract
`python3 tools/test_part2_security.py`
Result: `ALL PASSED` (15/15 checks).

### Kotlin syntax checks
- `ChatText.kt` compiled successfully with `kotlinc`.
- `WhatsAppCommandRules.kt` compiled successfully with a minimal compile stub for its Logic dependency.
- These are syntax checks only, not an Android build.

### Workflow YAML
- `.github/workflows/build.yml` parsed successfully with PyYAML.

## I. Exact test counts
- Python contract scripts discovered/run: **33**.
- Python contract scripts passing: **33/33**.
- New Part 2 security checks: **15/15**.
- Full Android JUnit/Gradle test suite: **NOT RUN**.
- Physical-device tests: **NOT RUN**.

## J. Android build result
**UNVERIFIED — not a successful build.**

The repository has no Gradle wrapper in the supplied archive and the sandbox has no `gradle` executable. The supplied local compile helper was attempted, but its required Android/Kotlin downloads could not resolve because network/DNS access was unavailable (`curl: (6) Could not resolve host: raw.githubusercontent.com`).

Therefore no `PASS`, `DONE`, or `BUILD SUCCESS` is claimed.

GitHub Actions workflow was added so the owner can run:
- `gradle --no-daemon testDebugUnitTest assembleDebug`

## K. Remaining unverified
1. Real Android Gradle compilation and dependency resolution.
2. Android unit tests under Gradle/JUnit.
3. APK install and runtime behavior on a physical phone.
4. Real WhatsApp contact resolution and Accessibility SEND callback.
5. Real HindiPack download/model load.
6. Real TTS/microphone/3-second silence behavior.
7. Device-specific permissions, lock-screen and foreground-service behavior.

## L. Phone-test checklist
1. Say: `WhatsApp Aman Biswan kab aaoge`.
2. If `Aman Biswan` exists exactly once in Contacts, NOVA should ask for confirmation; it must not send immediately.
3. Say a clear `haan`; only then should the WhatsApp send path run.
4. Say a non-existent/ambiguous contact; NOVA must ask safely and must not invent a recipient.
5. Say `Biswan` without WhatsApp; it must not be treated as an app.
6. Trigger a provider response containing JSON and confirm the chat shows normal text, not braces/JSON.
7. Confirm no `MIC DEBUG` / temporary diagnostic panel appears in production UI.
8. Run the GitHub Actions workflow and require `testDebugUnitTest` + `assembleDebug` to finish green before calling the Android build verified.
9. Test enrollment 5/5 and confirm it says `Awaaz save ho gayi` once and does not restart.
10. Test a normal command with a short pause; THINKING should begin only after the configured 3 seconds of continuous silence.

## QWEN ACTIVATION (2026-10-12)
- Hosted Qwen is ACTIVE in the online fallback chain as `Qwen (via Groq)`.
- It uses the existing Groq key; no separate Qwen key is required.
- Order is Groq -> Qwen -> GLM -> OpenRouter -> Pollinations.
- Qwen provider id: `groqqwen`; model: `qwen/qwen3.8-27b`.
- Verified by `tools/test_autosetup_contract.py`: ALL OK.
- Verified by `tools/test_chain_contract.py`: ALL PASSED.
- Offline Qwen2.5-0.5B remains intentionally unavailable in this build because `LocalBrains.factory` is disabled; it is NOT falsely marked as active.
