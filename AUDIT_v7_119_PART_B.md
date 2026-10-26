# NOVA v7.119 Part B - audit (zip repack, code NOT changed)

Kya badla: sirf zip ka `nova/` top folder hataya (ab build.gradle.kts, app/, .github/ seedhe root par) + ye file + NEXT_ACCOUNT_PROMPT_v7_119_AUDIT.md. Koi .kt / .html / .yml / .gradle nahi badla.

## RUN (sandbox)
- Python contract checks: 149 PASS. 8 headless-browser tests timeout (25 s, sandbox me browser slow). Ye tests playwright na ho to SKIP exit 0 karte hain, GitHub par rokne ki ummeed nahi (andaaza, CI me nahi chala).
- Manifest: 16 classes source me maujood, theme + xml resources maujood.
- NovaService me PreRollRing + WakeGateRules sahi jude, tests maujood.
- Zip me asli key / keystore / APK nahi.

## NOT RUN
- Kotlin compile (sandbox me kotlinc / internet nahi; status file ka "0 errors" dawa maine verify nahi kiya).
- Real JUnit, GitHub Actions, APK, phone.
- Status file ke 33 JUnit fail (stub harness) - original zip se compare nahi hua.

## Dhyan do
1. Repo root par build.gradle.kts, app/, .github/ dikhne chahiye.
2. versionName 7.114 hai (jaan-boojh kar); versionCode = 2000 + run number.
3. Signing secrets nahi to throwaway key: purani app ke upar update fail ho to pehle uninstall.

## Agla step
Upload -> "NOVA Android CI" chalao -> red ho to sirf red lines bhejo -> green par OWNER_PHONE_TEST_LIST_v7_119.md.
