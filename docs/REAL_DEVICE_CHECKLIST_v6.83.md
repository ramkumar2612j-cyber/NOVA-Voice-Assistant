# NOVA real-device checklist (v6.83)
Do this ONLY after GitHub Actions is green and the debug APK is installed. Nothing here has been run on a phone yet. Mark PASS or FAIL; for each FAIL send phone model, Android version, a screenshot (no keys / private audio) and the Settings > AKHRI GALTIYAN > COPY REPORT text.
Setup: allow Microphone + Notifications; Accessibility ON; Battery "No restrictions"; one Gemini key saved (Settings).

| # | Step | Expected | PASS / FAIL |
|---|---|---|---|
| 1 | Tap ACTIVATE, say "nova torch jala do" | Torch turns on, short Hinglish reply | [ ] PASS [ ] FAIL |
| 2 | Lock screen, wait 2 min, say "nova" | NOVA still wakes (mic keeps running) | [ ] PASS [ ] FAIL |
| 3 | "nova WhatsApp kholo", then "<contact> ko message bhejo hello" | App opens, message typed and sent without asking yes/no | [ ] PASS [ ] FAIL |
| 4 | "nova <contact> ko call karo" | Call starts | [ ] PASS [ ] FAIL |
| 5 | "nova 2 minute ka timer lagao" | Timer set, rings after 2 min | [ ] PASS [ ] FAIL |
| 6 | Add a scheduled reminder 2 min ahead (Settings > schedule) | Fires once on time; after reboot a repeating one still exists | [ ] PASS [ ] FAIL |
| 7 | Driving mode ON, send yourself a WhatsApp from another phone | NOVA reads it aloud; OFF stops it | [ ] PASS [ ] FAIL |
| 8 | Chat: send 3 messages, NEW CHAT, ask "mera naam kya hai" after teaching "mera naam X hai" | Memory answers; old chat text is not in the new context | [ ] PASS [ ] FAIL |
| 9 | Part 8: chat search by a word from an old message | Chat found, snippet shown | [ ] PASS [ ] FAIL |
| 10 | Part 8: pin a chat, rename it, restart app | Pin and new name remain | [ ] PASS [ ] FAIL |
| 11 | Part 8: EXPORT a chat | System save box opens; text file contains the chat | [ ] PASS [ ] FAIL |
| 12 | Part 8: update from an older version that has old chats | All old chats still there (migration), nothing duplicated | [ ] PASS [ ] FAIL |
| 13 | Part 8: DELETE ALL: first tap only asks, second tap deletes | As described; refused while NOVA is busy | [ ] PASS [ ] FAIL |
| 14 | Part 8: on a low-RAM phone open many apps, return to NOVA | Page rebuilds (no crash); if it dies twice in 5 s the app closes cleanly | [ ] PASS [ ] FAIL |
| 15 | Remove/invalid the Gemini key, ask a follow-up to an earlier answer | Backup AI continues the same thread | [ ] PASS [ ] FAIL |
| 16 | Say "nova setup check" | Lists exactly what is really missing | [ ] PASS [ ] FAIL |
| 17 | Home box "KYA CHALTA HAI": press THEEK KARO on a missing permission | Opens the matching Android screen (overlay, accessibility, notification access, battery, brightness, contacts, mic) | [ ] PASS [ ] FAIL |
| 18 | Part 9: paste 1000 random characters into a key box and save | Rejected ("invalid"), no crash, old key unchanged | [ ] PASS [ ] FAIL |
| 19 | Part 9: Settings > AKHRI GALTIYAN after a failed WhatsApp send with a phone number in the command | Number / quoted text shown as # or "***" | [ ] PASS [ ] FAIL |
| 20 | Agent: "kaam chatgpt kholo aur ek joke poochho"; stop the service midway, ACTIVATE again | One notification with exact resume status; nothing repeats alone | [ ] PASS [ ] FAIL |
| 21 | Audio recording: start, stop after 2 min | Notification timer; file in the recordings list; play / share work | [ ] PASS [ ] FAIL |
| 22 | Video recording (NOVA visible, front camera) | Camera privacy indicator; file saved | [ ] PASS [ ] FAIL |
| 23 | Image generation: "nova ek billi ki photo banao" | A real image appears (needs a provider; free keyless one allowed) | [ ] PASS [ ] FAIL |
| 24 | Site generation + publish (own GitHub token) | Native confirm box; public https://<user>.github.io/<repo>/ opens | [ ] PASS [ ] FAIL |
| 25 | Updates: set your own feed URL, check, install a newer signed APK | SHA-256 + signature checked; Android install screen; NOVA presses Install or you tap it | [ ] PASS [ ] FAIL |
| 27 | Fresh install, open NOVA, do nothing else | One Android permission dialog (mic, camera, contacts, calendar, phone, notifications), then Settings pages open one after another (overlay, brightness, battery, exact alarm, full-screen, install, notification access, accessibility, and on Xiaomi/Oppo/Vivo/Huawei the brand autostart page); coming back continues; NOTHING starts the microphone by itself | [ ] PASS [ ] FAIL |
| 28 | Skip two of those pages, close and reopen NOVA | The skipped pages are offered again (up to 3 times each, brand autostart once); granted ones are not shown | [ ] PASS [ ] FAIL |
| 29 | Home box > SETUP DOBARA CHALAO | Another round starts for whatever is still missing | [ ] PASS [ ] FAIL |
| 26 | Banking / payment / password / OTP screen with NOVA active | NOVA refuses to act or type there | [ ] PASS [ ] FAIL |
| 30 | Tab bar > CONNECT | New CONNECT tab opens with cards: GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion, Lovable, Custom MCP | [ ] PASS [ ] FAIL |
| 31 | GitHub card > CONNECT | Sheet lists what NOVA can do (read / write), where the token lives, permission level choice | [ ] PASS [ ] FAIL |
| 32 | Sheet > ALLOW AUR GITHUB KHOLO | Browser opens github.com token page with repo, workflow, read:user ticked | [ ] PASS [ ] FAIL |
| 33 | Generate token, copy it, return to NOVA | NOVA picks the token from the clipboard by itself, shows "Connect ho gaya: <your login>", clipboard is cleared | [ ] PASS [ ] FAIL |
| 34 | Connected card > Mere repos | Repo list appears in the card | [ ] PASS [ ] FAIL |
| 35 | Level READ: tap "Issue banao" | Button is greyed, message says to turn on WRITE | [ ] PASS [ ] FAIL |
| 36 | Level READ + WRITE (confirm "HAAN, WRITE ON"), Issue banao with `you/repo | test` > HAAN, KARO | Issue is created on GitHub | [ ] PASS [ ] FAIL |
| 37 | Say "nova github repos dikhao" | Repos appear in chat, short spoken summary | [ ] PASS [ ] FAIL |
| 38 | Say "nova vercel redeploy karo <project>" (Vercel connected, WRITE on) | NOVA asks yes/no first; only after "haan" it redeploys; same with "skip confirmation" ON | [ ] PASS [ ] FAIL |
| 39 | DISCONNECT (tap twice) | Card returns to NOT CONNECTED; token is gone (reconnect needs a new paste) | [ ] PASS [ ] FAIL |
| 40 | Custom connector > a real https MCP server (+ token if needed) | "Custom connector jud gaya"; Tools ki list shows its tools | [ ] PASS [ ] FAIL |
| 41 | Netlify / Cloudflare / Supabase / Notion: connect with their token, run the read actions | Lists appear; wrong token gives a clear "Token galat" message | [ ] PASS [ ] FAIL |
| 42 | Chat screen | Bar above the input: ＋ CONNECTORS and chips for connected services | [ ] PASS [ ] FAIL |
| 43 | Type "vercel connect karo" in chat, answer "haan" | Vercel token page opens; after copying the token and returning to NOVA, NOVA says it is connected | [ ] PASS [ ] FAIL |
| 44 | Say "nova github write on karo" | NOVA lists what writing means and asks yes/no; only "haan" turns WRITE on | [ ] PASS [ ] FAIL |
| 45 | Say "github repos dikhao phir vercel deployments dikhao" | Both lists arrive in the chat, one after the other | [ ] PASS [ ] FAIL |
| 46 | Say "github naya repo banao testnova phir github repos dikhao", answer "nahi" | The repo is not created and the second step does not run | [ ] PASS [ ] FAIL |

Report the table back (PASS/FAIL per number). Features are called "complete" only after their rows are PASS on a real phone.
