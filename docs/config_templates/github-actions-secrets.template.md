# GitHub Actions secrets (all OPTIONAL; the plain debug build needs none)

Repo > Settings > Secrets and variables > Actions > New repository secret.

| Secret name | Value (placeholder) | Needed for |
|---|---|---|
| `NOVA_KEYSTORE_B64` | `<BASE64_OF_YOUR_nova.jks>` | fixed signing key, in-app APK update |
| `NOVA_KEYSTORE_PASSWORD` | `<YOUR_KEYSTORE_PASSWORD>` | same |
| `NOVA_KEY_ALIAS` | `<YOUR_KEY_ALIAS>` | same |
| `NOVA_KEY_PASSWORD` | `<YOUR_KEY_PASSWORD>` | only if different from the keystore password |
| `GEMINI_API_KEY` | `<YOUR_GEMINI_KEY>` | only the separate "Self-improve" workflow of your repo (not in this ZIP) |

How the signing secrets reach Gradle: the workflow decodes `NOVA_KEYSTORE_B64` into a file and exports
`NOVA_KEYSTORE_PATH`, `NOVA_KEYSTORE_PASSWORD`, `NOVA_KEY_ALIAS`, `NOVA_KEY_PASSWORD`, which `app/build.gradle.kts` reads.
The `build.yml` inside this ZIP does NOT do that step: it makes a debug-key APK. Signing workflows ("Make signing key",
"Release APK") live in the owner's own repo (see docs/SETUP_STEPS.md).
Never paste a secret into an issue, chat, commit or screenshot.
