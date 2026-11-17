# ANDROID TEST STEPS v7.221 — naye parts ke phone tests (owner bharega)

> Rule wahi: jo chala usi ka PASS likho, jo nahi chala uska FAIL + note. Bina chalaye kabhi PASS nahi.
> Ye list TEST_STATUS.md ke format me hai — wahan rows add hoti rahengi. Purane parts ki list wohi purani files me.

## PART 1 — CLOUD BRAIN (v7.217)

| # | Test | Kaise | Kya hona chahiye |
|---|---|---|---|
| C1 | Backend deploy | docs/DEPLOY.md Option B/C se | URL khule: `https://…/api/status` par `{"ok":true,...}` dikhe |
| C2 | Admin login | website admin.html + backend URL localStorage me, password se | Dashboard khule, "—" ki jagah numbers |
| C3 | Pairing code | admin me "PAIRING CODE BANAO" | `JARVIS-CLOUD:...` code dikhe + COPY ho |
| C4 | Phone juda | app Settings > CLOUD BRAIN me code paste > CLOUD SAVE | "(juda hua: <host>)" dikhe |
| C5 | Heartbeat | 5 min wait, admin dashboard refresh | Phone row me battery/net/status, "online" |
| C6 | Cloud status | chat me likho: `cloud` | "☁ Cloud brain juda hai — server chalu, online devices: N" |
| C7 | Laptop juda | laptop app me: `cloud jodo JARVIS-CLOUD:...` | "☁ Cloud brain juda gaya" |
| C8 | Memory sync | laptop par: `yaad rakho laptop wali baat` → 10 min wait → phone par: `meri yaad` | "laptop wali baat" phone ki memory me aaye |

## PART 2 — DEVICE SYNC (v7.218)

| # | Test | Kaise | Kya hona chahiye |
|---|---|---|---|
| D1 | Task handoff | phone chat: `laptop ko bolo chrome kholo` | "kaam rakh diya" → 2 min me laptop par chrome khule + "Cloud kaam done" |
| D2 | Laptop status | phone: `laptop ka status` | RAM/CPU line (laptop app chalu ho to 10 min me aata hai) |
| D3 | Phone status (laptop se) | laptop par: `phone ka status` | "☁ Cloud se N task mile…" |
| D4 | Link-only memory | cloud OFF karke dono par app chalu, laptop par yaad rakho | 10 min me phone ko "nayi baat" message |

## PART 3 — TV / OSCAR (v7.219)

| # | Test | Kaise | Kya hona chahiye |
|---|---|---|---|
| T1 | TV ka type pata karo | docs/OSCAR_TV.md §1 | Android TV / Google TV / Fire TV / webOS… likh lo |
| T2 | (sirf Android/Google TV) Pairing | TV + phone same Wi-Fi; app Settings > TV LINK me TV ka IP > PAIR | TV par 6-anka code → PIN box me → "(juda hua)" |
| T3 | TV keys | `tv volume badhao`, `tv back`, `tv home`, `tv upar jao` | TV par wahi ho jo remote se hota |
| T4 | TV app | `tv netflix kholo`, `tv youtube chalao` | App TV par khule |
| T5 | TV band/on | `tv band karo` / `tv on karo` | sleep chalega; on sirf TV jaag rahi ho to |
| T6 | TV nahi mila | galat IP / TV band | Honest error message — jhootha success NAHI |

## PART 4 — WEBSITE (v7.220)

| # | Test | Kaise | Kya hona chahiye |
|---|---|---|---|
| W1 | Website live | GitHub Pages ON, website workflow chalao | `https://<user>.github.io/<repo>/` khule |
| W2 | Status LIVE | backend deploy + localStorage 'brain' set | "LIVE" pill + devices count |
| W3 | Admin panel | admin.html login | dashboard: devices table, reports, versions |
| W4 | Changelog publish | admin me version+note > PUBLISH | versions table me row aaye |

## PART 5 — AUTOMATION (v7.221)

| # | Test | Kaise | Kya hona chahiye |
|---|---|---|---|
| A1 | Daily health | Actions > "JARVIS Daily Health Check" > Run | DAILY_REPORT artifact bane, sab checks ka status |
| A2 | Report push | secrets (BRAIN_URL + PASSWORD) set karke run | Admin panel > Daily Reports me entry dikhe |
| A3 | Backup | `./tools/backup.sh test` (laptop par) | backups/ me zip + ledger line |

## Regression (purane features — 5 min ka spot check)

| # | Test | Kya hona chahiye |
|---|---|---|
| R1 | `jarvis` wake word + `torch jala do` | Torch jale (purana jaisa) |
| R2 | `laptop par chrome kholo` (bina cloud) | Seedha link se chale (purana v7.177 raasta) |
| R3 | MADAD kholo | 83 topics dikhe (83 = ab care topic bhi) |
| R4 | Launcher home | v7.216 jaisa hi dikhe (HUD, reactor, tiles) |
| R5 | Settings > SAARE FEATURES | Skills list me "Phone care" naya entry dikhe |

**Kisi bhi cheez me galat dikhna = TEST_STATUS.md me FAIL likho + agle prompt me batao. Jhooth nahi, sach.**
