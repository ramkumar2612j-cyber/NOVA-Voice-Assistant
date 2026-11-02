# Config templates (placeholders only)

Nothing in this folder is a real secret. Copy a template, replace every `<PLACEHOLDER>`, and keep the copy OUT of git
(`.gitignore` already blocks `local.properties`, `*.jks`, `*.ppn`, `keys.txt`, `.env`).

| File | Used for | Where the real value goes |
|---|---|---|
| `local.properties.template` | local Gradle build: where the Android SDK is | `local.properties` in the project root (not committed) |
| `github-actions-secrets.template.md` | signed APK from GitHub Actions (optional) | GitHub repo > Settings > Secrets and variables > Actions |
| `signing.env.template` | signed APK from a LOCAL Gradle build (optional) | shell environment, not a file in the repo |
| `nova-keys.template.txt` | which AI/API keys NOVA can use and where to paste each | typed into the NOVA app (Settings). NOVA stores them AES-GCM encrypted with the Android Keystore |

NOVA has NO key in source and no key in BuildConfig. A build without any key works: local commands run on the phone.
