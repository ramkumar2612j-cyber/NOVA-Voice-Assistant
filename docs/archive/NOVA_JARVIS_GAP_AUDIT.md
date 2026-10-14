# NOVA JARVIS goal — honest gap audit (2026-10-11)

## Owner's required end state
The owner wants a hands-free Android agent: accept one natural-language/Hinglish prompt, plan the work, operate permitted phone UI, create/edit website or app source, use AI helpers when needed, commit/upload to GitHub, trigger build/tests, inspect failed logs, fix and retry within limits, and notify only after verifiable success.

## What the supplied source currently demonstrates (static only)
- `AgentRules.kt` + `NovaService.agentRun()` implement a bounded accessibility/UI agent: one action per model response, at most 30 steps and 5 minutes; screen text/screenshot may be sent to a configured AI provider after the task is started; protected screens are shielded; several actions require confirmation.
- `NovaAccessibilityService.kt` can inspect/tap/type/scroll some accessible app screens when Android Accessibility is enabled. Apps may expose incomplete or different accessibility trees.
- `GitHubPublish.kt` creates a public repository containing one generated `index.html` and enables GitHub Pages. It does not commit an arbitrary multi-file project, open pull requests, start the repository's build workflow, poll job status, fetch logs/artifacts, or patch and retry a failed Android build.
- Current agent logic remains a UI operator, not a source-code workspace agent. It does not have a general local project editor/compiler/Gradle runner. The repo workflow files are not included in this ZIP; they live in the GitHub repository per the prior handoff.
- Slice A has now been added in source: `AgentTaskRules.kt` + `AgentTaskLedger.kt`; it persists redacted status, step count and terminal state, accepts `agent status`, persists stop/cancel and marks stale tasks INTERRUPTED after service restart. It deliberately does not replay an interrupted UI task. A model `done` response is stored as `REPORTED_COMPLETE`, not independently verified `SUCCEEDED`. Local pure-rule/stub checks pass, but the Android project has not been built.
- The current UI agent relies on an available configured model/provider. It does not prove that the installed ChatGPT or Claude apps can be used as reliable APIs; UI automation in those apps can change or fail.

## Evidence available in this workspace
- 19 Python contract scripts passed when directly run during the prior static review; the knowledge pack validator reported 193 entries / 1,157 variants; 11 inline JavaScript blocks passed `node --check`; ZIP integrity passed.
- These are not an Android Gradle build. This workspace has no Android SDK/android.jar or Gradle build environment and no connected physical phone. The code is therefore not proven CI-GREEN or PHONE-TESTED.
- No independent external AI reviewer or actual multi-reviewer execution is claimed.

## Hard limits that must remain explicit
Android restricts background starts and microphone/camera foreground services, especially on recent OS versions. User-granted Accessibility, microphone, overlay and other permissions may be required; OEM battery management can stop services. Apps can refuse accessibility control, hide their screen tree, use secure windows, or change UI. Payment, banking, password/OTP and protected screens must stay blocked. Never silently bypass Android security/privacy restrictions.

## Implementation gaps before the requested JARVIS goal is met
1. A real source-workspace layer that safely creates/edits multiple project files and keeps backups/diffs.
2. A real GitHub repository workflow: verify owner/repo/branch, commit a complete project change set via API or a controlled upload, never expose tokens, and never overwrite unrelated work.
3. Build orchestration: trigger the exact workflow via GitHub API, poll status, distinguish old run vs run for the new commit, download/check logs and artifacts, and report the exact run URL/commit SHA.
4. A bounded repair loop that extracts compiler/test errors, changes only relevant files, reruns tests/build, has retry/time/token caps, stops on ambiguity, and never claims success without a green workflow and verified APK artifact.
5. Slice A task ledger is now written: bounded status/history, cancellation, step count and safe interrupted-task handling. Still missing: safe checkpoint/resume with idempotency, a UI/history view or notification report, and a complete end-to-end task verifier. The current intentional recovery behavior is stop-and-report, not automatic replay.
6. Explicit consent/privacy UI for screen content sent to AI; provider fallback and quota/error states; clear disclosure that screen text/images may leave the phone.
7. End-to-end integration tests and actual CI green runs. The current sandbox cannot produce this evidence.

## Slice A update (2026-10-11)
- Written and integrated the first persistent agent-task lifecycle slice. New task state/rules, SharedPreferences ledger with a backup field, redacted summary, synchronous commit, duplicate-active guard, restart interruption, stop persistence, step count and voice status query.
- Model `done` is not treated as independently verified success. UI agent completion becomes `REPORTED_COMPLETE` and asks the owner to verify the actual screen.
- Seven Kotlin unit test methods cover task transitions/privacy/retry budget/status routing; local stub harness passed them. Pure Kotlin rules were compiled with `kotlinc` and 18 harness assertions passed. The source-contract script adds 28 guards. The final overall rerun passed **20/20 Python contract scripts**, **11/11 JS blocks**, and the skillpack validator (**193 entries / 1,157 variants**). These are not Android Gradle/CI tests.
- All original project files must remain in the final ZIP; only additive task-ledger files/tests/docs and targeted integration edits are allowed.

## Status
This archive is an updated baseline with Slice A written, not a finished JARVIS implementation. Evidence remains WRITTEN + STATICALLY REVIEWED; NOT CI-GREEN and NOT PHONE-TESTED. Slice B (safe multi-file workspace), Slice C (complete project commits through GitHub API), and Slice D (workflow run/log/artifact polling plus bounded code-fix/rebuild) remain the next major engineering slices. Before starting them, the owner must obtain a real green GitHub build for this ZIP. The next-account prompt retains all 11 existing feature requirements and the hard safety rules.
