# NOVA v7.114 - Features ki list + check

**Check kaise hua (maine kya dekha):** zip ke andar code files, har feature ka class, uska test file, aur kya wo app me kahin se use ho raha hai.
**Maine compile / build / phone test NAHI kiya.** Compile + JUnit ke numbers (1571 pass) zip ke CHANGELOG se hain, mere chalaye hue nahi.

Legend:
- CODE = code likha hua hai aur app me juda (wired) hai
- TEST = uska unit test file bhi hai
- PHONE = phone par abhi kisi ne chala kar nahi dekha (sab features ke liye abhi NO)

---

## 1. Basic voice control
| Feature | Code | Test | Note |
|---|---|---|---|
| Wake word "Nova" (offline Vosk) | CODE | TEST | v7.113 me horn guard laga, thresholds andaaza hain |
| Porcupine wake (optional) | CODE | - | Apni Picovoice key chahiye |
| Battery, time, date, torch, volume, brightness | CODE | TEST | Brightness ke liye "Modify system settings" |
| Media play/pause/next/prev | CODE | TEST | |
| Home/Back/Recents/Notifications | CODE | TEST | Accessibility ON chahiye |
| App kholna (koi bhi installed app) | CODE | TEST | |
| Wi-Fi/Bluetooth | CODE | - | Android allow nahi karta, sirf settings page kholta hai |
| Screen par tap / type / scroll | CODE | TEST | Accessibility; bank/payment/password screens blocked |

## 2. Call, message, contacts
| Feature | Code | Test | Note |
|---|---|---|---|
| Call (CALL_PHONE) | CODE | TEST | Call se pehle "haan" |
| SMS | CODE | TEST | |
| WhatsApp message | CODE | TEST | Bhejne se pehle confirm |
| WhatsApp Business watch | CODE | TEST | |
| Contact naam match + duplicate choose | CODE | TEST | |
| Papa / Mummy + 12 log save (v7.114) | CODE | TEST | Battery SOS wahi numbers use karta hai |
| Call uthana / kaatna awaaz se | CODE | TEST | |
| Auto-reply (WhatsApp/Instagram) | CODE | TEST | |
| Driving mode (msg bol kar sunana) | CODE | TEST | |
| Unread message check (notification se) | CODE | TEST | |
| Gmail notification padhna | CODE | TEST | Login/token nahi chahiye |

## 3. Reels / Instagram (naya, v7.114)
| Feature | Code | Test | Note |
|---|---|---|---|
| Reel scroll, agli/pichli | CODE | TEST (24) | STOP bolne par band |
| Like / save / comment / share | CODE | TEST | Comment max 10, share max 5 per run |
| Friend ki id pe jaake 5 reels like+comment | CODE | TEST | |
| Friend watch (naya post aate hi like/comment) | CODE | TEST | 7 din, 20 runs |

Risk: Instagram ke button naam English me match hote hain. Layout badla to tweak lagega.

## 4. Schedule, alarm, calendar
| Feature | Code | Test |
|---|---|---|
| Alarm, timer, reminder | CODE | TEST |
| Repeat schedule (weekday + priority) | CODE | TEST |
| Morning routine + wake alarm | CODE | TEST |
| Calendar meeting banana + yaad dilana | CODE | TEST |
| Routines (ek bol par kai kaam) | CODE | TEST |
| Battery guard (50/40/30/20/10%, 5% par SOS) | CODE | TEST |

## 5. AI / dimaag
| Feature | Code | Test | Note |
|---|---|---|---|
| Gemini / Groq / OpenRouter / free providers | CODE | TEST | Keys Settings me |
| Provider failover + ranking | CODE | TEST | |
| Offline AI pack (5 chhote models) | CODE | TEST | Wi-Fi + charging par auto download |
| Bada brain (Qwen 3B) | CODE | TEST | Sirf owner tap par, 8GB+ RAM |
| Yaad rakhna (sirf phone me) | CODE | TEST | |
| Wikipedia se naya topic seekhna | CODE | TEST | |
| Nickname sikhana + roz ka sudhaar | CODE | TEST | |
| Help / MADAD (58 topics, offline) | CODE | TEST | |

## 6. Aankh (screen / camera / photo / video)
| Feature | Code | Test |
|---|---|---|
| Screen padhna + analyze | CODE | TEST |
| Screen alerts (error/warning bolna) | CODE | TEST |
| Screen scan (laser) | CODE | TEST |
| Movie ka naam guess (scene se) | CODE | TEST |
| Camera Eye (camera se dekhna) | CODE | TEST |
| Photo / video analyse | CODE | TEST |
| Video se frames nikalna | CODE | TEST |
| Photo edit (crop, bright, bg hatao) | CODE | TEST |
| AI photo banana | CODE | TEST |
| Recording (sirf bolne par) | CODE | TEST |
| Profile search (naam / username) | CODE | TEST |

## 7. Website / online kaam
| Feature | Code | Test | Note |
|---|---|---|---|
| Website banana (ek line se) | CODE | TEST | Quality AI key par depend, abhi weak (owner feedback) |
| Apni photo website me | CODE | TEST | |
| Vercel / GitHub publish | CODE | TEST | |
| Connectors (GitHub, Vercel, Netlify, Cloudflare, Supabase, Notion, MCP) | CODE | TEST | |
| Koi bhi site kholna + kaam (Part 4) | CODE | TEST | CAPTCHA/OTP par ruk kar bolta hai |
| Saved login jobs Gmail/Chrome (Part 5) | CODE | TEST | Password kabhi type/store nahi |
| Doosra rasta dhundhna (Part 3) | CODE | TEST | |
| Playbook: kaam ka route yaad (Part 2) | CODE | TEST | |
| Site / blocker seekhna (Part 6) | CODE | TEST | |
| Seekha hua dekhna / mitana (Part 7) | CODE | TEST | |
| App download (Play Store) | CODE | TEST | Silent install nahi, tap chahiye |
| Live jankari: mausam, khabar, rate | CODE | TEST | |
| Trading (MT5 practice + analysis) | CODE | TEST | Sirf practice/analysis |
| Leads (business chats se) | CODE | TEST | |

## 8. Safety / extra
- Lock Guard (locked phone), Quiet / Silent mode, Safe mode (crash ke baad)
- Watch mic (smartwatch se bulana)
- Share karke NOVA ko bhejna (MEDIA)
- API usage / Update Center
- Hard deny words, bank/payment screens blocked, payment sirf future opt-in
- Secrets Android Keystore me encrypted

Sab ke liye: CODE + TEST hai, PHONE = abhi NO.

---

## Jo mujhe ADHURA / SHAKI laga

**A. 4 files jo app me kahin use nahi ho rahi (dead code):**
1. `ChatStore.kt` - koi use nahi
2. `NovaAssist.kt` - koi use nahi, manifest me bhi nahi
3. `VoiceFlowRules.kt` - koi use nahi, test bhi nahi
4. `CrashGuard.kt` - sirf ui.html aur test me naam hai, Kotlin se call nahi

(Inme se koi feature toot nahi raha, bas ye files faltu hain ya adhoori.)

**B. Owner ne jo problems batayi thi, abhi open:**
1. TV ki awaaz se NOVA khud jaag jata hai (v7.113 me sirf horn guard laga)
2. Wake ke baad "samajh nahi aaya"
3. Website maker ki quality kamzor
4. UI lag (v7.113/114 me fix likha hai, phone par confirm nahi)

**C. Jo kabhi nahi chala:**
- GitHub Actions build
- APK
- Koi bhi phone test

**D. Purane docs purane hain:** README me abhi v6.88 / v7.72 likha hai, v7.114 nahi. `docs/FEATURES_AND_LIMITS.md` v6.x ka hai.

## Conclusion
Features ki list lambi aur code me likhi hui hai, lekin "kaam karta hai" bolne ke liye pehle build + phone test chahiye.
