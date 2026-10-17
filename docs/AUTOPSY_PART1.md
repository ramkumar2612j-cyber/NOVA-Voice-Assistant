# NOVA v75 - PART 1: FULL AUTOPSY (jaanch report)

Kya kiya: poora code padha aur gina (koi code change nahi, kuch hataya nahi). Source: NOVA-WIP-v75.zip.
Kya NAHI hua: Kotlin compile, JUnit, phone par test. Is sandbox mein internet aur Android SDK nahi hai. Wo GitHub Actions "Build and Test APK" par hoga. Isliye yahan "build pass" ka koi dava nahi hai.

## A. Jo code padh kar theek mila (PAKKA)
- UI -> Kotlin buttons: ui.html ke 156 bridge calls ke liye MainActivity mein 201 methods hain. Koi button bina method ke nahi (2 naam addEventListener/getChannelData JS ke apne hain).
- Python contract tests: 59 mein se 59 pass.
- 158 Kotlin files: har ek kisi na kisi se judi hai. Koi poori tarah laawaris (dead) file nahi mili. (BootReceiver, NovaAssist service manifest se chalte hain.)
- Home ka "KYA CHALTA HAI / KYA NAHI" box (FeatureStatus) 17 cheezein asli phone state se dikhata hai: wake, awaaz, accessibility, popup card, apps ke andar kaam, driving, contacts, brightness, battery, Gemini, backup AI, website publish, auto-heal, wake diagnosis, last job, capabilities, notification.
- Popup (NovaHud) mein HAAN / NAHI buttons code mein hain.
- CHUP button home par hai (bolti hui awaaz rokta hai). "quiet mode on/off" aur "voice replies off/on" bol kar kaam karta hai. Chat ke jawab by default bolte nahi, sirf dikhte hain (owner ka pichla rule).

## B. SKILLS LIST ki kami (sabse badi)
App ki skills list (MADAD popup = HelpGuide ke 16 topics) aur NOVA ka AI (HelpGuide.aiFacts) sirf yeh 16 jaanta hai: call/SMS, WhatsApp, alarm/timer, torch/volume/wifi, app kholna, gaana/raasta, camera, photo banana, website, screen padhna, yaad rakhna, naya topic seekhna, driving, recording, mausam/khabar, sawal ka jawab.

Code mein ye features hain, par skills list mein NAHI hain, isliye user ko list mein nahi dikhte aur NOVA ka AI inke baare mein "nahi pata" jaisa jawab de sakta hai:
1. WhatsApp Business (WaBiz) - alag app target
2. Auto-reply (AutoReplyRules + notification service)
3. Connectors (GitHub, Vercel, n8n, MCP, Zapier/Make)
4. Routines
5. Lock Guard (locked phone mein wake band, alert)
6. Nickname sikhana (Teach) aur ROZ KA SUDHAAR (self-improve)
7. Battery / background guide (PowerGuide)
8. Gmail
9. Agent / background jobs (lambe kaam, progress)
10. Screen alerts
11. Watch mic (smartwatch se bulana)
12. Quiet / Silent mode (command hai, par skills list mein nahi)
13. Offline AI pack (5 on-device AI)
14. Leads (business chats se lead queue)
15. Update Center / API usage

Isse sab ke "NOVA ko pata hai" wale hisse mein gap hai: HelpGuide.aiFacts mein yeh nahi hain.

## C. Owner ki nayi maang vs abhi ki sthiti
| Maang | Abhi |
|---|---|
| Popup mein NOVA ko STOP karne ka button | NAHI hai. Popup mein sirf HAAN/NAHI + chhota close/minimize. Stop sirf home ke CHUP button ya bol kar. |
| SILENT ka option | ADHURA. Sirf command + CHUP (abhi ki awaaz rokta hai). Home par ek saaf, hamesha dikhne wala Silent switch nahi. |
| Message (jawab) screen par dikhe | Card par jawab kuch der dikhta hai (holdMs). Silent mode mein bhi dikhna pakka karna hai. |
| UI modern | Abhi bhi "fatta" laga to Part 4 mein poora redesign. |

## D. Aage ka kaam (5 parts)
1. Part 1 (yeh): autopsy report - DONE
2. Part 2: Skills list mein saari missing cheezein (ui.html CAPS + HelpGuide + aiFacts + MADAD test contract), jo adhura mile use poora karna
3. Part 3: Popup mein STOP button, Silent switch, messages screen par
4. Part 4: Modern UI redesign (purani cheezein rakhte hue)
5. Part 5: Build + test (GitHub Actions) + final ZIP

## E. Sachchai (honest status)
- Phone par kuch test NAHI hua. Compile/JUnit bhi yahan NAHI chale.
- Jo "pakka" likha hai wo sirf code padhne aur Python checks se hai.
