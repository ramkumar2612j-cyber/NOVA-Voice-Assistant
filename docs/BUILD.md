# Build & test (v6.83)

Status words used everywhere: **PASS** = ran and passed, **FAIL** = ran and failed, **NOT TESTED / NOT RUN** = nobody ran it.
Read [TEST_REPORT_PART4.md](TEST_REPORT_PART4.md) for what has really been run.

## 1. GitHub Actions (recommended, no PC needed)

The workflow `.github/workflows/build.yml` ("NOVA Android CI") runs on every push, pull request and manual run.
Tools it sets up: JDK 17 (Temurin), Android SDK platform 34 + build-tools 34.0.0, Gradle 8.7 (AGP 8.5.2 needs >= 8.7), Kotlin 1.9.24 (from the Gradle plugin).

Steps, in order:
1. checkout
2. JDK 17, Android SDK 34, Gradle 8.7
3. **Source contract checks** `./tools/run_all_checks.sh` (47 Python scripts + skill-pack validator; no Android needed)
4. **Vosk model** `./tools/prepare_vosk_model.sh` downloads `vosk-model-small-en-us-0.15` from alphacephei.com and fails closed if `am/final.mdl`, `graph/HCLr.fst`, `graph/Gr.fst` are missing (`graph/words.txt` is optional: the small model has none)
5. `gradle --no-daemon testDebugUnitTest assembleDebug` (JUnit, then the debug APK)
6. uploads artifact **nova-apk** (`app/build/outputs/apk/debug/*.apk`) and **test-report** (`app/build/reports/tests/`)

How to run it:
1. Make a **private** GitHub repo and upload the ZIP contents. Keep the `.github` folder. (The workflow installs Gradle 8.7 itself; a `gradlew` wrapper is also in the zip for PC builds, never run against the real Gradle download in the sandbox.)
2. Repo > **Actions** > "NOVA Android CI" > **Run workflow** (or just push).
3. Green: open the run > Artifacts > download `nova-apk`, unzip, install `app-debug.apk` (allow "Install unknown apps").
4. Red: open the failed step, copy the red lines, fix, push again. A red "Run unit tests" step = open artifact `test-report`.
5. Version code: the workflow does not set `NOVA_VERSION_CODE`, so the APK uses the default `29`. For in-app updates set the env var to a number higher than the installed one (see `config_templates/signing.env.template`).

Signed builds (optional, for in-app APK update): the signing workflows are in the owner's own repo ("Make signing key", "Release APK"), see [SETUP_STEPS.md](SETUP_STEPS.md) and [config_templates/github-actions-secrets.template.md](config_templates/github-actions-secrets.template.md). This ZIP's `build.yml` builds with the normal debug key unless the `NOVA_KEYSTORE_*` environment variables are provided.

## 2. Local Gradle (PC)

Needs: JDK 17, Android SDK 34 (`sdkmanager "platforms;android-34" "build-tools;34.0.0"`), Gradle 8.7+ (or the shipped `./gradlew`, Gradle 8.7), `curl`, `unzip`, network to `alphacephei.com`, `dl.google.com`, `maven.google.com`, `repo.maven.apache.org`.

```sh
cp docs/config_templates/local.properties.template local.properties   # then put your SDK path in it
# Vosk model: downloaded AUTOMATICALLY by Gradle (task prepareVoskModel runs before every build). Nothing to do by hand.
./tools/run_all_checks.sh              # Python contract checks
gradle testDebugUnitTest assembleDebug # JUnit + debug APK
# APK: app/build/outputs/apk/debug/app-debug.apk ; JUnit report: app/build/reports/tests/testDebugUnitTest/index.html
```
Do **not** use `python3 -m unittest discover tools`: the scripts are plain scripts that call `sys.exit()`, so unittest reports false "errors". Use `tools/run_all_checks.sh`.

Optional signed local build: `source` a filled copy of `config_templates/signing.env.template`, then run the same gradle command.

## 3. Sandbox check without Gradle/AGP (what Part 4 ran)

`tools/local_compile_check.sh [workdir]` compiles `app/src/main` + `app/src/test` with a real **kotlinc 1.9.24** against an AOSP **android-34 android.jar**, then runs all `*Test` classes with real **JUnit 4.13.2** (`JUnitCore`, from `app/`). It needs `java`, and `github.com` access (kotlinc zip; if `raw.githubusercontent.com` is blocked, clone `Reginer/aosp-android-jar` with `git clone --filter=blob:none --sparse` and copy `android-34/android.jar` into the workdir).
Third-party libraries that cannot be downloaded offline are replaced by tiny stubs in `tools/local_stubs/` (vosk, androidx fragment/biometric/core FileProvider/NotificationCompat, androidx.work, Porcupine, a stand-in `R`, and a stand-in `LlamaBrain` because the real one needs llamacpp-kotlin + kotlinx-coroutines).
**It is NOT a replacement for CI**: it does not prove AGP packaging, resource merging, manifest merging, lint, the real third-party APIs, `LlamaBrain.kt`, or anything at runtime on a phone.

## 4. Offline-model and wake-word notes
- Vosk English model: fetched AUTOMATICALLY at build time by Gradle (`gradle/vosk-model.gradle.kts`, task `prepareVoskModel`, pure JVM so it works on Windows too; skipped when already valid; mirror via env `NOVA_VOSK_MODEL_URL`). `tools/prepare_vosk_model.sh` is kept only as the CI pre-check.
- Optional Porcupine wake word needs the owner's own Picovoice AccessKey and `nova.ppn` (entered in the app, never in the repo); Vosk stays as fallback.
- Optional Hindi pack and the offline GGUF model are downloaded by the app on demand.
- Secrets: none in source. `.gitignore` blocks `local.properties`, `*.jks`, `*.ppn`, `keys.txt`, `.env`. Placeholders only in `docs/config_templates/`.

## 3. Sandbox checks without Gradle (what was really run for v6.83)
These are NOT a replacement for CI: they compile app/src/main + app/src/test against small stubs (androidx, Vosk, WorkManager), not the real libraries.
1. `bash /ABSOLUTE/PATH/tools/local_compile_check.sh /some/workdir` -> compile + JUnit (646 tests at v6.84). Needs java 17 and github.com access (kotlinc 1.9.24 zip; android-34 android.jar: if raw.githubusercontent.com is blocked, sparse-clone github.com/Reginer/aosp-android-jar and copy android-34/android.jar into the workdir). The path MUST be absolute.
2. `bash tools/run_all_checks.sh` -> Python contract scripts (48 at v6.84).
3. `python3 tools/ui_chat_check.py <outdir>` (19 checks) and `python3 tools/ui_feature_status_check.py <outdir>` (27 checks): headless Chromium (Playwright) + a FAKE Android bridge, they test ui.html only.
