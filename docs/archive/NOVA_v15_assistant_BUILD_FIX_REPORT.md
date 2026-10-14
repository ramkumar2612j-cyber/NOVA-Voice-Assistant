# NOVA v15 — v55 BUILD FIX HANDOFF

## Exact CI root cause found from the owner's GitHub Actions screenshot

The build failure is a NOVA project code bug, not a user entry mistake.

`app/src/main/java/com/nova/assistant/NovaService.kt` had two separate fields named `lastAlertAt`:
- the quiet notification rate-limit map (`HashMap<String, Long>`)
- the low-battery alert timestamp (`Long`)

Kotlin therefore reported conflicting declarations / overload resolution ambiguity around lines 2292, 2297-2299, 3008, 3013-3014.

There was also a call-site bug in `askOrRun`:
`askFirst(prompt, run)` passed the lambda as the `must: Boolean` parameter and left the required `run` parameter missing. It is now explicitly `askFirst(prompt, run = run)`.

## Fix applied

- Renamed the quiet notification map to `quietAlertAt`.
- Renamed the battery timestamp to `batteryLastAlertAt`.
- Updated all corresponding references.
- Changed `askFirst(prompt, run)` to `askFirst(prompt, run = run)`.
- No unrelated feature logic was changed.

## Evidence

- NovaService brace count: 1055 / 1055.
- Python contract suite: all available `tools/test_*.py` contracts pass when run with their required invocation; Hindi contract passes from `tools/` as documented.
- Full Kotlin/Gradle/GitHub Actions compile has NOT been claimed here. The local Kotlin compiler download step could not run in this environment because external network access was unavailable.

## Next action

Upload the updated `NOVA_v15_assistant.zip` to the repository and run the existing GitHub Actions workflow. Only after the workflow is green should the APK be downloaded and installed.
