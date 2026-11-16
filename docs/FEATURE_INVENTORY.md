# NOVA v6.99 - FEATURE INVENTORY (old / new / what is proven)
Evidence levels (honest):
- **A** = last real Kotlin compile + JUnit run recorded in CHANGELOG (v6.97 wip8: kotlinc 1.9.24, 834 JUnit PASS). Phone still NOT TESTED.
- **B** = written AFTER that, never compiled, never run on a phone. Static contract scripts PASS (74 scripts, 1 known unrelated FAIL).
- **U** = screen checked in headless Chromium with a fake bridge (RUN in this autopsy).
- NOTHING in this list is PASS on a real phone. "Works" below only means: code exists, is wired (UI -> bridge -> Kotlin -> manifest), and its checks pass in the sandbox.

## OLD features (v6.0 - v6.89) - level A (+U for screens)
- Wake word "nova" (Vosk offline), listening, spoken replies, CHUP stop button.
- Local commands without a key: battery, time, torch, volume, brightness, media, Home/Back/Recents, open apps.
- Calls, SMS, WhatsApp send with real contact lookup + duplicate-name picker (Contact Resolution parts 1-3).
- Screen control (tap / type / scroll, needs Accessibility), screen monitor, "analyze screen".
- Gemini keys with failover; free AI chain (Groq, Qwen, GLM, OpenRouter...); answers honest about what it did.
- In-app jobs: "chat open karo", "youtube pe song chalao", agent task memory after restart.
- Chat sessions: new chat, history, search, pin, rename, export, delete; memory manager.
- Connectors: GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion, Lovable, custom MCP.
- WhatsApp / Instagram auto reply (v6.53), driving mode (reads messages, quick replies), message mode.
- Schedule + morning routine + wake alarm + 25-min lead reminder, battery guard with SOS at 5 %.
- UI: HOME / SKILLS / LIMITS / CHAT / SETUP tabs, ACTIVATE button, 3D orb, floating card, API limits screen. (U: PASS)

## MIDDLE features (v6.90 - v6.97 wip8) - level A
- Chat honesty guard (no fake "bana raha hoon"), website follow-up ("isko professional banao").
- AI picker chip (Auto / Gemini / free AI) + fallback switch.
- 5 key-less browsers: Wikipedia, DuckDuckGo, weather (Open-Meteo), news, market / crypto.
- Local-first on-device AI for short questions.
- LOCK GUARD: locked phone = no commands, loud beep only for calls on silent / Papa / Mummy.
- Watch mic (untested on any real watch), adaptive listening (waits longer on long sentences).
- Pro button style layer, hang fixes (busy release + 120 s watchdog).

## NEW features (after the last compile) - level B (+U where a screen)
- HelpGuide + MADAD popup (24 topics; "tum kya kar sakte ho").
- ROZ KA SUDHAAR: daily self-improve suggestions, applied only after your HAAN (default OFF).
- 5 offline AI packs (Qwen 1.5B, Llama 3.2 1B, InternLM, SmolLM2, DeepSeek-R1 distill), Wi-Fi only download.
- Chrome tabs by voice ("youtube, google aur flipkart kholo"), Play Store "app download karo" (presses only Install).
- Owner safety decision: tap / type run directly on your order (bank / password reading still protected).
- Instagram / WhatsApp smart replies: insult = no reply, wishes = "Thank you", money talk = safe fixed line, OTP / password = no reply.
- AI smart reply, call answer / cut, DND, auto-rotate, screenshot, calendar meeting alert.
- Spoken STOP phrases, Hindi spellings of "download", PhoneFacts (NOVA knows its permissions), label "v6.99 . build N".
- Buttons for 15 settings that had no UI (self test, browser TEST, wait seconds, memory copy...), plus a real bug fix: Auto-seekho and battery alert shared one checkbox id.

## AUTOPSY fixes in this round (R1 - R6) - level B
- R1 connectors / web / learning; R2 open web / download / teach / photo; R3 video + recordings (4 fixes).
- R4 schedules: no false "saved" without an alarm; repeat jobs survive a storage failure; alarms come back after APK update / time change / app open; stale one-shot reported as missed.
- R6 (this message): WhatsApp / message reading now re-connects when Android drops the notification listener; one failing helper can no longer stop message reading; errors are logged, not swallowed; "play_music" says it only opened YouTube search results; a scheduled job is never silently lost when the service is dead.

## Known limits (not bugs, by design or Android rules)
- Weather / message scheduled jobs run only while NOVA is ACTIVE (else a notification); the mic never starts by itself.
- Plain YouTube play only opens search results (tap a video); real auto-play works for Spotify / YouTube Music / JioSaavn etc.
- Instagram "Report" taps are not automated. Android permission boxes and Accessibility / Notification-access switches always need your own tap.
- Hindi in Devanagari is not recognised by the offline English model; Hinglish spelling is.

## v7.00 / v7.01 additions (add-only)
- Website builder: AI pictures made automatically (MediaIntent, SiteMedia); "bina photo ke" skips them; "video banao" answers honestly (no AI video). Voice/chat only, no button by design.
- 3D HUB heart (hub_b) at bottom centre: tap = round popup (HOME SKILLS LIMITS CONNECT CHAT SETUP AUTO WEBSITE MADAD HISTORY AI YAAD NAYI CHAT ON/STOP), long-press = ACTIVATE/STOP.
- NOT BUILT YET: gallery photo/video placement in websites, Lovable/Vercel publish by NOVA, free AI photo editor (specs in NEXT_ACCOUNT_PROMPT_AUTO_MEDIA.md).
- v7.02 Settings: SPEED (ASLI NAAP) stopwatch + SMOOTH MODE (AUTO / HALKA / FULL EFFECTS). ui.html end block `nova-speed-js`.
