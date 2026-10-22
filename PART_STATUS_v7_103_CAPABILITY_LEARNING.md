# NOVA v7.103 — capability-gap learning / recovery-first agent

## Changed
- Add `CapabilityLearningRules.kt`: deterministic, bounded categories and recovery guidance.
- Add `CapabilityLearningStore.kt`: stores only coarse categories/count/timestamps in private app preferences; no raw task, error, screen text, audio, key, or prompt is persisted.
- Wire failure recording into `NovaService.agentFinish`; one record at most per task.
- Inject prior recovery guidance into subsequent agent task context.
- Add recovery-first prompt policy: one materially different safe route before failure, complete safe independent steps, verify and report real blockers.
- App `versionName` is now `7.103`; default `versionCode` is now `30` (was 29) for update ordering.

## Checks
- PASS: `kotlinc CapabilityLearningRules.kt` pure Kotlin compile.
- PASS: `tools/test_capability_learning_contract.py`.
- PASS: `tools/test_agent_contract.py`.
- PASS: `tools/test_agent_plan_contract.py`.
- PASS: `tools/test_agent_restore_contract.py`.
- PASS: `tools/test_agent_task_ledger_contract.py`.
- PASS: `tools/test_review_security_contract.py`.
- PASS: `tools/test_selfimprove_inapp_contract.py`.
- PASS: `tools/test_vosk_model_packaging.py`.
- PASS: `tools/test_android_runtime_contract.py`.

## Not verified
- Android Gradle build and JUnit were not run in this environment.
- No APK assembled or signed.
- No physical Android phone testing, real speech, wake word, accessibility actions, call/WhatsApp, background service or live API provider testing.

## Limits
This is learning from repeated failure *categories* and choosing better safe recovery instructions. It does not autonomously write/install new Kotlin features, obtain API keys, grant Android permissions, bypass protected screens, or guarantee that every requested job is possible. Those require code/integration work, owner authorization, and runtime verification.
