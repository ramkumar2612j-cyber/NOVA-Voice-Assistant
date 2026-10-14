# NOVA v36 handover: NEW UI + 3D LIVE ICON + API LIMITS (read first, then also read HANDOVER_v16.md hard rules)

## Owner
Phone only (Chrome + GitHub web). Reply in Hinglish, short steps. Never say DONE/WORKING without evidence level
("tests ran here", "Kotlin not compiled", "not phone tested"). Zip must stay < 25 MB (GitHub web upload) and keep the name NOVA_v15_assistant.zip.
Hard rules unchanged: risky actions need spoken "yes", no silent installs, keys never shown/logged, protected screens never to cloud, no account-limit bypass automation.

## Owner's request (Hinglish, 2026-10-11)
Make the app look original, "world's best fastest agent assistant that not only talks but does work". UI must show EVERY feature,
and show for EVERY API how much limit is total and how much is left. Final full zip. Also modify the icon images at every step:
3D, 8K quality, LIVE (animated) icon.

## DONE in this zip (evidence: kotlinc 1.9.24 compiled ApiUsage.kt against tiny stubs + 7/7 ApiUsageTest passed here; full Android build NOT run, phone NOT tested)
- app/src/main/java/com/nova/assistant/ApiUsage.kt (UsageMath + ApiUsage): per-service counters for today (gemini, groq, groqqwen, groqstt, zai, openrouter, pollinations, together, hf, pollimg).
  Groq live headers (x-ratelimit-limit/remaining-requests, per day) override local count. Limits: Groq 1000/day, Whisper 2000/day, OpenRouter 50/day (1000 with 10+ credits, editable),
  Gemini 250 = ESTIMATE (editable; Google gives no fixed numbers in docs), Z.ai/Pollinations/Together/HF = no published limit (show count only).
- Hooks added (one line each): FreeHttp.post, NovaService.postOnce (Gemini), ImageGen.post, SiteGen.gemini/groq, GroqStt.transcribe, FreeImage.call. ApiUsage.init in MainActivity.onCreate and NovaService.onCreate.
- Bridge (MainActivity): getApiUsage() JSON {day,totalToday,rows[{id,label,group,note,limitKind,keySet,used,sent,failed,quotaHits,limit,remaining,percent,source,liveReset,lastTs,lastCode,defLimit}]}, setApiLimit(id,n)->"ok"|"invalid", resetApiUsage().
- app/src/test/.../ApiUsageTest.kt.
- NOT DONE: ui.html still the OLD transformer-theme UI (does not call getApiUsage yet). Icons NOT changed yet.

## TODO (in this order)
1. Compile risk check: edited files FreeHttp/NovaService/ImageGen/SiteGen/GroqStt/FreeImage/MainActivity were only reviewed by eye (android.jar not downloadable here). Run GitHub "Build and Test APK".
2. New ui.html (keep EVERY existing element id and Android.* call; tools/test_voice_contract.py + test_review_security_contract.py + test_hud_controls.py + test_public_knowledge_contract.py pin ids/strings; update tests only for pinned legacy bits like 'tf'/'mode' ids, app.className 'wrap ').
   Design: dark graphite + aurora (teal #35F2C2 / violet #7C5CFF / amber), bottom tabs: HOME (live orb, ACTIVATE, CHUP, provider dots), SKILLS (all features grouped, live ON/OFF/needs-key chips, "say:" examples, allow buttons),
   LIMITS (ring gauge per API from getApiUsage: used / limit / remaining, source badge live|local|count, editable limit, reset today, merge getProviderHealth cooldown), CHAT (image mode, photo, site preview, publish: port onImage/onSite/onPublish/onPhoto), SETUP (all old settings in accordions).
   Preserve JS callbacks Kotlin calls: onNova, onFlow, onLearn, onPublish, onSite, onImage, onPhoto, onPhotoErr, onImgCancel, onFaceTest, onSelfTest, onSelfTestDone, onRecordingsChanged, onTeachBackup, novaBack. Keep disclosure texts (Wikipedia topic-only, Pollinations "keyless try; may fail", Qwen/Z.ai/offline fallback). WebView blocks all network: system fonts only; assets under file:///android_asset/ are allowed.
3. Icons: WebGL2 works in headless Chromium here (args --use-gl=angle --use-angle=swiftshader --enable-unsafe-swiftshader, max 8192). Plan: one GLSL ray-march orb (glass sphere + inner glowing N + 2 chrome gyro rings + satellite). Use it LIVE in the app (states idle/listening/thinking/speaking, quality governor, PNG poster fallback),
   render stills for launcher (drawable-*dpi/ic_launcher_foreground.png 108/162/216/324/432 px; delete drawable/ic_launcher_foreground.xml; keep monochrome xml), animated splash AVD (values-v31 themes use splash_icon), and an 8192x8192 master + webm loop delivered OUTSIDE the zip.
   Feature/step icons: SVG glyph on 3D glass tile (bevel, specular, float/tilt animation), plus exported PNG pack.
4. Bump versionName 6.18, update Update-Center CHANGES text, update tools/test_ui_contract.py for new ui, run all tools/test_*.py + node --check on inline scripts + Playwright screenshots at 360x640 and 390x844 with a fake Android bridge.
5. Zip (exclude __pycache__), send, state evidence honestly.
