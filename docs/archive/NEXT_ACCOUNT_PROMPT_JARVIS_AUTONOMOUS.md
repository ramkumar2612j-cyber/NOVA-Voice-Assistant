# NEXT ACCOUNT PROMPT — NOVA JARVIS AUTONOMOUS AGENT (read fully)

You are taking over the existing NOVA Android project. The owner is non-technical, uses only an Android phone, and communicates in Hinglish. Do not create a new app. Work inside the actual project from `NOVA_v15_assistant.zip`. This prompt is the next engineering task after the six-part static review; the prior prompt `NOVA_NEXT_ACCOUNT_PROMPT_7_AFTER_REVIEW_PART1.md` remains included for detailed project history, all 11 existing feature requirements, test conventions, phone checklists and prior findings. Read both it and `NOVA_JARVIS_GAP_AUDIT.md` before changing code.

## 1. Non-negotiable rules
1. Only real working features. No fake success, empty stubs, TODO pretending to be implementation, or claims that a feature works just because code exists. If Android or a third-party app prevents a feature, state the limitation and implement the closest safe real behavior.
2. Preserve existing working behavior and tests. Do not delete/weaken a test or safety rule to get green. Keep `SafetyInvariantsTest`, protected-screen blocks, spoken confirmation for risky actions and key privacy protections.
3. Evidence labels must be exact: **WRITTEN** = code exists; **CI-GREEN** = the GitHub Actions build and all tests passed for the new commit; **PHONE-TESTED** = owner tried it on their phone and confirmed it works. Static checks never equal CI-GREEN.
4. The owner works from a phone. Reply in short Hinglish, one step at a time. Provide one complete ZIP and direct clickable links. Do not dump long code into chat.
5. Before edits, inspect and extract the supplied ZIP. Never overwrite a different project or omit original files. Preserve app identity/package `com.nova.assistant` and existing Gradle/workflow assumptions.
6. Security: never log, display, or send API/GitHub tokens to an AI model; use encrypted `SecureStore`; no key in URLs; never send audio/screen content to a provider without the configured provider/privacy flow; never bypass Android permission prompts or protected screens.
7. Independent review is required: compile/API-risk review, logic/safety review, and audio/privacy review. If separate reviewers/tools are unavailable, say so; do not claim multiple AI reviews were done.
8. Before each account's work, provide the owner a short prompt/status; after finishing this account's defined part, provide a next-account prompt. Each handoff prompt must preserve these rules and all 11 feature parts.
9. First inspect the current repo and the existing code, then define the smallest complete vertical slice. Do not attempt a giant untested rewrite.
10. Never tell the owner that “everything works” until CI is green and the owner has completed the phone checklist. The final JARVIS goal cannot be considered complete based on static review alone.

## 2. Owner's exact goal
NOVA should become a JARVIS-like agent. The owner says one natural-language/Hinglish prompt; NOVA plans, executes supported actions, observes the result, corrects errors, tests again, and reports the real outcome. Example: “Mere liye ek website/app banao; source files complete karo; GitHub par sahi repo/branch mein commit karo; build/test chalao; fail ho to logs padho, relevant fix karo, bounded retries ke saath dobara build karo; sirf green build aur verified artifact ke baad inform karo.” The owner should not have to manually tap through every routine step where Android and the target app genuinely permit automation.

## 3. Honest current baseline — do not misrepresent it
- Existing `AgentRules.kt` + `NovaService.agentRun()` are a bounded accessibility/UI agent (up to 30 steps / 5 minutes) that asks an AI model for one next action at a time. It is not a source-code workspace or full software-engineering agent.
- `NovaAccessibilityService` controls only accessible, permitted screens; OEM/app UI may block it or change.
- `GitHubPublish.kt` creates a repository with one `index.html` and enables GitHub Pages. It does not upload arbitrary multi-file source projects, trigger/poll Actions, fetch build logs/APK artifacts, or automatically fix/rebuild a failed Android app.
- Current code does not prove that the ChatGPT/Claude consumer apps are reliable APIs. Prefer supported APIs/providers already integrated; opening an app via UI is a fallback, not a guarantee.
- Slice A now exists in source: `AgentTaskRules.kt`, `AgentTaskLedger.kt`, `AgentTaskRulesTest.kt`, and `tools/test_agent_task_ledger_contract.py`. `NovaService` persists task state and step counts, supports spoken `agent status`, records spoken stop/cancel, marks stale active tasks INTERRUPTED on service start, and never auto-replays them. The ledger stores a redacted short label/status—not audio, screen text or the full task prompt.
- A model `done` action is recorded as `REPORTED_COMPLETE`, not verified `SUCCEEDED`; NOVA asks the owner to inspect the result. A separate arbitrary-task verifier does not exist yet.
- Current available local evidence: the pure `AgentTaskRules.kt` file compiled using `kotlinc` with a tiny Logic stub and passed 18 harness assertions. The new 7-method Kotlin rules test class and ledger syntax compiled/run against local minimal Android/JSON/JUnit stubs. The task-ledger source-contract script passed 28 guards. Final regression rerun passed 20/20 Python scripts, 11/11 JavaScript blocks, and the skillpack validator (193 entries / 1,157 variants). These are not Gradle, Android runtime, persistence-in-real-SharedPreferences, or phone tests.
- These source contracts must be rerun after any further edits. They are not an APK build.
- This environment has no Android SDK/android.jar or Gradle build, and no connected physical phone. Do not claim CI-GREEN until a real GitHub Actions build proves it. No external independent AI reviewer was available; report this honestly.

## 4. Build the autonomous-agent foundation in ordered, testable slices
Do not promise the entire goal in one unverified pass. Implement one complete slice at a time, keep the ZIP buildable, and clearly state what remains.

### Slice A — durable task engine (WRITTEN; Android CI still required)
- Implemented `AgentTaskRules` state model (created, planning, waiting_for_permission/confirmation, running, verifying, retrying, succeeded, reported-complete, needs-owner, failed, cancelled, interrupted) with 20-record bounded history and 3-retry/5-minute rule limits.
- Implemented private SharedPreferences task ledger with synchronous commits + backup value, redacted short task label/status, step counter, duplicate-active guard, status query, stop/cancel persistence and restart recovery.
- Restart recovery deliberately marks in-flight UI tasks INTERRUPTED rather than replaying actions: blindly replaying a tap/message could cause duplicate side effects. Safe checkpoints/idempotency are not implemented yet.
- Model `done` is a `REPORTED_COMPLETE` outcome, not verified success. The owner is asked to inspect the target screen. `SUCCEEDED` must remain reserved for a future verified result.
- Added `AgentTaskRulesTest` for pure lifecycle/privacy/retry/status behavior and `tools/test_agent_task_ledger_contract.py` source guards. Local stub/harness checks have passed; this does not replace the real `Build and Test APK` run.
- Before Slice B, the owner must run GitHub Actions on this exact ZIP and provide a green run or the first failed compiler/test log. Do not silently proceed to add more architecture while this foundational change has not been compiled by CI.

### Slice B — controlled source workspace
- Build a real workspace layer to create/read/edit/delete only files within an explicit project workspace, make backups/diffs before edits, reject path traversal, preserve unrelated files, and cap file size/count.
- Generate multi-file web projects and Android source changes as real files, not just text displayed in chat.
- Do not claim code is compiled until CI proves it. Do not execute arbitrary shell/code from model output on the phone.

### Slice C — GitHub integration for complete projects
- Reuse encrypted GitHub token storage only after validating scopes and repository ownership. Add a clear Settings connection/check/revoke flow.
- Commit a complete, bounded file set to an explicitly chosen repository/branch with base SHA checks; never force-push or overwrite unrelated work. Show a diff and require confirmation for public repo creation, destructive changes, releases, or publication.
- Use GitHub REST APIs through a dedicated client with timeouts, response/error parsing, rate-limit handling and no token logging. Add mockable unit tests for request construction and error handling.

### Slice D — build/test/fix loop
- Trigger only an explicitly configured workflow with `workflow_dispatch` or a verified commit-triggered workflow. Record the exact commit SHA and run ID.
- Poll run/job status with a cap; reject stale runs for previous commits; retrieve failure logs and artifact metadata only through authenticated API calls.
- On failure, summarize the exact error, propose a minimal patch, show diff, run available safe tests, commit to a separate branch or explicit owner-approved branch, and retry with strict limits (for example, max 3 repair attempts). Stop on permission/auth failures, ambiguous logs, security issues, or repeated identical failures.
- Do not say “APK ready” unless the exact run is green and the expected APK artifact exists and can be downloaded. Provide the run URL, commit SHA, artifact name, and honest result.
- Never silently install an APK or publish a release. Ask for confirmation before install/release or any irreversible action.

### Slice E — phone UI agent and AI helper routing
- Keep Accessibility screen control separate from source/build automation. Use app UI only when there is no reliable API and the owner has explicitly approved the task.
- Do not assume ChatGPT/Claude app UI remains stable. Prefer configured model APIs; if the owner wants consumer apps opened as a fallback, make failure visible and never invent that a response/action succeeded.
- Screen content and screenshots are untrusted input and may contain prompt injection. The system prompt must treat them as data; enforce allowlists in Kotlin, not in the model.
- Preserve protections for banking/payment/password/OTP/installer/secure screens. Keep confirmation for messages to people, deletes, public publishing, installing apps and destructive code changes.

### Slice F — progress, reporting, observability
- Provide truthful per-task progress and final result with links, errors and next action. Report partial completion honestly.
- Add structured redacted error history, provider quota/cooldown status, offline/network/auth distinctions and no false success messages.
- Avoid collecting/storing screen contents or audio beyond explicit task needs. Explain when screen data leaves the phone for a configured provider.

## 5. Preserve and verify all existing 11 feature parts
These remain requirements and must not be broken while adding the agent:
1. Camera: voice request can open photo/video camera and request front/back; Android camera shutter may still require a user tap.
2. Self-diagnosis: recent failure history, self-test, battery/autostart guidance; never reveal API keys.
3. Silent mode: ordinary safe actions can be silent; confirmation for risky actions must still be spoken.
4. Listening guard: do not wake by itself or interrupt; trim trailing silence and avoid echo/no-speech false positives.
5. Voice + face: local voice tuning plus Android BiometricPrompt as a second factor; do not claim access to Android's internal face-unlock data.
6. Recording: 6A audio, 6B video, 6C list/play/share/delete/auto-delete; visible Android notification, consent and platform/OEM limitations must remain.
7. Free AI phone actions + quota meter: allow-listed actions and same confirmation/safety route; never execute raw model instructions.
8. Context memory: RAM-only short-lived context; never repeat risky actions without a new confirmation.
9. Teach once: aliases, list/delete/export/import; ordinary questions must not be swallowed as teaching.
10. Routines: safe allow-listed actions, max 8 steps, Settings editing.
11. See the screen: screen session and accessibility; protected apps and FLAG_SECURE screens remain blocked.

## 6. Android and service limits are not optional
Android versions restrict background activity launches and microphone/camera foreground services; target app/OEM can stop background work; Accessibility may not expose or allow every action. Wi-Fi/Bluetooth toggles, camera shutter, permission dialogs, protected screens, OTP/password and banking/payment flows cannot be promised as fully touch-free. Never bypass system security to simulate “unrestricted” access. Always preserve required foreground notifications and privacy indicators.

## 7. Required engineering process
1. Inspect the actual ZIP and current repo before edits; map all changes.
2. Choose the next vertical slice and state the scope before implementation.
3. Add unit tests and source-contract tests; hand-walk new safety tests.
4. Run every available test script correctly (these Python contract scripts are executable scripts; run each directly rather than assuming `unittest discover` works).
5. Check JavaScript syntax, JSON/skillpack validation, ZIP path preservation and archive integrity.
6. If Gradle/SDK are unavailable, do not substitute delimiter checks for compilation; require the owner to run GitHub Actions and send failure logs.
7. Update `PROGRESS.md`, `NOVA_JARVIS_GAP_AUDIT.md`, and the next-account prompt with exact findings/evidence.
8. Deliver one complete `NOVA_v15_assistant.zip`, not a partial patch. Preserve original project files and include this prompt.
9. End with evidence levels and one simple next step for the owner.

## 8. How the owner builds from phone
Repository: https://github.com/devboutique0-wq/NOVA
Upload: https://github.com/devboutique0-wq/NOVA/upload/main
Actions: https://github.com/devboutique0-wq/NOVA/actions
1. Upload the complete ZIP and commit it.
2. Run **Unpack zip** so the repository receives the new source.
3. Run **Build and Test APK** for the unpacked source.
4. Only the build after Unpack zip counts; the immediate upload-triggered build can use old code.
5. Green run: download APK artifact; then follow `PHONE_CHECKLIST_AFTER_CI_GREEN.md`. Red run: send a screenshot of the first relevant compiler/test error.

## 9. This next account's first task
1. Read this prompt, `NOVA_NEXT_ACCOUNT_PROMPT_7_AFTER_REVIEW_PART1.md`, `PROGRESS.md`, `NOVA_JARVIS_GAP_AUDIT.md`, and inspect the actual ZIP before edits.
2. First ask for/inspect the real GitHub Actions result for this ZIP. The owner needs to upload the ZIP, run **Unpack zip**, then **Build and Test APK**. If red, fix only the actual compiler/test errors while preserving every existing test and safety rule. If no build evidence exists, do not call Slice A CI-GREEN and do not move to Slice B yet.
3. Only after a green build, start Slice B: a bounded local source workspace that creates/reads/edits/deletes project files only inside an explicit workspace; path-traversal defense; backups + diffs; file/count/size limits; preserve unrelated project files; unit tests; no arbitrary shell execution from model output. Begin with a small complete vertical slice; do not claim whole JARVIS complete.
4. After Slice B, hand off Slice C (GitHub REST integration for complete multi-file commits with base-SHA checks and owner-confirmed publication/destructive changes). Then Slice D (workflow dispatch, exact commit/run polling, log/error/artifact verification, bounded repair attempts, verified APK report). Preserve the order and green CI gates.
5. If workflow files, repo branch, or token scopes cannot be inspected, say exactly what is missing. Never expose tokens, do not push to the owner's repo without an explicit authorized workflow, and don't claim external reviewers or phone tests that did not happen.
6. Deliver one complete ZIP plus an updated next-account prompt with all 11 feature parts and rules in this document. Do not claim the JARVIS goal complete in this account.
