# PART STATUS v7.188 - NOVA naam poori tarah hata, sirf JARVIS (mobile + laptop + watch)

Owner: "Nova service ab Nova nahi, sirf Jarvis - mobile, laptop, watch, har jagah."

## Kya hua (mechanical rename, 556 files)
- Kotlin classes/files: NovaService->JarvisService, NovaAccessibilityService->JarvisAccessibilityService, NovaNotificationService, NovaHud, NovaAssist, NovaCloudQueueWorker, NovaApp (+ manifest ke saare `.Jarvis*` naam), Theme.Nova->Theme.Jarvis.
- Wake/command bolne ke shabd: "nova" filler/strip/wake lists ab "jarvis" (duplicate entries harmless).
- Laptop (desktop/): IPC `jarvis:*`, "JARVIS-LINK:" pairing code, productName/title "JARVIS", package `jarvis-desktop`, appId `com.jarvis.desktop`, link key prefix `jarvis-`.
- Watch (WatchCommandActivity etc.), chat, notifications, ui.html (CSS/JS/text), workflows ("JARVIS Android CI", artifact jarvis-apk), docs/README/OWNER steps.

## JAAN-BUJH KAR NAHI BADLA (data/signing toot-te)
- applicationId/package `com.nova.assistant` (badalne se nayi app ban jaati hai: purani settings + permissions chali jaati).
- Saved-data keys `nova_*` (SharedPreferences "nova", localStorage, asset files nova_eye.jpg ...) - badalne se saved memory/queue/API keys ud jaate.
- GitHub secrets / keystore env: NOVA_KEYSTORE_B64/_PATH/_PASSWORD, NOVA_KEY_ALIAS/_PASSWORD (badalne se signing toot-ti).
- Purani PART_STATUS_* / CHANGELOG history files (history hai).

## Checks
- Desktop: node --test 48/48 pass (rename se pehle bhi 48/48).
- ui.html ke 86 JS blocks syntax OK (1 block GLSL shader hai, JS nahi).
- Kotlin: COMPILE NAHI HUA (yahan Gradle/network nahi). Pehle GitHub Actions build chalao.
- Phone / laptop / watch par test: NOT RUN.

## Baaki
- 3D orb ke andar shader (tools/orb + ui.html) mein neon "N" aakar abhi bhi bana hai - "J" banana alag part.
- Agar package id bhi com.jarvis.assistant chahiye: fresh install + permissions dobara dena padega.
