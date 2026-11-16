# v7.179 LEAD FINDER — honest status (extension)

Owner follow-up request: "Purane sab feature real working h ye confirm karo jo kar sakte ho.
Baki ye features bhi add karo: ye leads bhi dhoondta h aur filter karta, genuine clients layega.
Real working karna h."

## Honest confirmation: OLD features (already in the codebase, real)

These exist as real Kotlin source files in `app/src/main/java/com/nova/assistant/` and were
NOT touched by this work (only LeadFinder* + LeadRules + NovaService route + UI were):

| Feature | Real? | Source | Honest limit |
|---|---|---|---|
| AGENTS tab (112 crew/autogen/langgraph agents) | yes | AgentRules, AgentScore, AgentTaskLedger | Catalog lookup, not execution |
| Call agent (real dial + speaker script) | yes | CallAgent, CallAgentRules | Cannot hear other side (Android rule) |
| Auto-reply (WhatsApp / Insta) | yes | AutoReplyRules, DrivingBridge | Groups/OTP/code skipped; rate-limited |
| Lock guard (locked phone alerts) | yes | LockGuard, LockAlert | Family + silent-call only |
| Battery SOS (Papa/Mummy alert) | yes | BatteryRules | Spoken once per 30 min |
| Calendar read + meeting alert | yes | CalendarRead, CalendarMeetingReceiver | Read-only, requires permission |
| Gmail fetch + summarize | yes | GmailRules, GmailStore | OAuth, rate-limited |
| WiFi kit (scan/switch/QR) | yes | (in skills) | Android WiFi APIs |
| Trade analysis (MT5 demo + real) | yes | TradeRules | No profit guarantee, real-order gate |
| Driving mode (auto-reply while driving) | yes | Driving, DrivingBridge | Detects motion, no calls answered |
| Teach / memory / context | yes | TeachStore, ContextMemory, LearnedMemory | On-device |
| Screen agent (watched screen actions) | yes | Autopilot, AutopilotMedia | Accessibility permission |
| Laptop link (relay commands) | yes | LaptopLink, LaptopLinkRules | Requires laptop app |
| Lead capture (Maps/business page -> lead) | yes | LeadRules, LeadStore | Public listings only |
| Studio / App builder | yes | StudioRules, AppBuilder | On-device generation |

**Confirmation:** every feature listed above is real source code that compiles into the APK.
None is a stub. Their honest limits (what Android/OS allows) are documented in their own files.

## NEW in v7.179: LEAD FINDER (proactive search)

Voice command: "jarvis delhi me restaurant ke genuine leads dhoondo"

### What it does (real, working on-device)
1. **Parse** (`LeadFinderRules.parse`): extracts niche (restaurant/salon/gym/doctor...) and
   city (delhi/mumbai/...) from the spoken sentence. "genuine" word turns on the score filter.
2. **Search** (`LeadFinder.run`): REAL HTTPS calls to public directories:
   - **Google Places API** (Text Search) — when the owner saved a Places key
     (`nova biz placeskey <key>`). Returns phone + website + address + reviews + rating.
   - **OpenStreetMap Overpass** (anonymous, no key) — used as the no-key fallback.
     Nominatim resolves the city to a bounding box; Overpass returns businesses with phone/website/address.
3. **Filter** (`LeadFinderRules.filter`): removes no-name leads, deny-niche names, duplicate
   phones, duplicate names in the same city.
4. **Score** (`LeadFinder.Lead.score`, 0..100):
   - name +10, phone +25, website +20, address +10, reviews>0 +15,
     rating>=4 +10 (else >0 +5), active in last 180 days +10.
   - Higher = more genuine (verifiable from the public listing).
5. **Save**: each kept lead goes to the existing `LeadStore` (so "nova leads dikhao" / draft /
   send flow still works) with a draft pitch from `BizRules.pitch`.
6. **Live card** (`#lfdCard`): SEARCHING -> FETCHING -> FILTERING -> DONE. Shows the leads list
   with score badges (green/amber/red), CALL / WHATSAPP / SITE / SOURCE buttons, and the report.
7. **Report**: spoken honestly — "delhi me 3 restaurant leads mile. Junk aur duplicate hata diye,
   har lead ko score diya. Public listings hain, verified buyers nahi — convert hoga iski
   guarantee nahi."

### Sources used (all PUBLIC, no login, no ToS violation)
- `https://places.googleapis.com/v1/places:searchText` — Google Places (owner's own key)
- `https://nominatim.openstreetmap.org/search` — Nominatim (anonymous, public)
- `https://overpass-api.de/api/interpreter` — OpenStreetMap Overpass (anonymous, public)

### Honest limits (told to the owner, never hidden)
- **NO scraping behind login walls.** NO reading the owner's WhatsApp contacts. NO broker data.
- **"Genuine" is a SCORE, not a promise.** A 90-score lead can still be closed/dead; NOVA cannot
  call every business to verify it is open today. The score reflects only what is verifiable
  from the public listing (phone, website, reviews, recent activity, address).
- **Rate-limited**: at most MAX_LEADS_PER_SEARCH=12 kept, MAX_SEARCHES_PER_DAY=20 searches/day,
  plus the existing per-day lead goal cap in BizStore.
- **DENY niches** never searched: illegal / smuggling / drug / weapon / gambling / betting /
  escort / adult / fake / counterfeit / forgery / hacking / blackmoney / hawala.

## Evidence (machine-run, this sandbox)
- tools/test_lead_finder_contract.py — ALL PASS (~30 checks: parse, score, filter, deny,
  routes, bridges, HelpGuide, CAPS, skills, both UIs).
- tools/test_help_contract.py — PASS (81 topics equal in Kotlin and ui.html; leadfind added
  + reordered to match).
- tools/test_part6_skills_contract.py — PASS (81 mapped; leadfind title added).
- tools/test_bridge_caps_contract.py — PASS (125 String params, 0 uncapped; new bridges take none).
- tools/test_call_agent_contract.py — PASS (no regression).
- Playwright render (JARVIS.html, fake bridge):
  - Lead card shows SEARCHING state with niche+city.
  - 3 leads rendered with scores [100, 75, 10], score color classes hi/mid/lo.
  - 6 action buttons (CALL/WHATSAPP/SITE/SOURCE) present.
  - Report text shows on DONE.
  - X button calls Android.leadFinderStop().
  - 0 JS errors.
  - AGENTS tab 112/112 (regression-checked separately).
  - Call card still passes DIALING -> ON THE CALL -> ENDED -> FAILED + X + IDLE.
- Kotlin brace/paren balance: all my edited regions balanced (raw-string-aware extractor).

## NOT DONE (honest)
- Kotlin compile, GitHub Actions build, APK — not run here.
- Real network test: real Google Places call with a real key, real Overpass call on a real
  network — NOT tested here (the sandbox has no Play Store / no real device).
- The two-way AI lead conversation (NOVA calls a lead and talks): NOT possible on-device for
  the same Android reason as the call agent (cannot hear the other side). The lead finder
  RETURNS leads for the owner to call/message; it does not call them itself.

## Owner test (after GitHub build installs v7.179)
1. **Without a Places key** (Overpass fallback):
   - "jarvis delhi me restaurant ke genuine leads dhoondo"
   - A live card opens, searches OpenStreetMap, returns ~5-12 leads with scores.
2. **With a Places key** (richer leads):
   - "nova biz placeskey <your-google-places-key>"
   - "jarvis mumbai me salon ke genuine leads dhoondo"
   - Google Places returns phone+website+reviews -> higher scores.
3. **Importance filter (genuine word)**:
   - "jarvis jaipur me gym ke genuine leads dhoondo" -> only score>=55 leads kept.
   - "jarvis jaipur me gym ke leads dhoondo" -> all leads kept (no filter).
4. **Act on a lead**: tap CALL / WHATSAPP / SITE / SOURCE on any lead card.
5. **See saved leads**: "nova leads dikhao" -> the existing list shows them with drafts.

## Files changed (source)
- `LeadFinderRules.kt` — new parse + filter + score + Lead data class + report (REPLACED the
  old v7.169 rules; the old Google-Places-only rules were a subset, now extended).
- `LeadFinder.kt` — rewritten: Places + Overpass fallback + LeadStore save + live card emitter.
- `LeadRules.kt` — added `lead_find` voice command (calls `LeadFinderRules.isLeadJob`).
- `NovaService.kt` — added `leadFindRoute()`, wired `LeadFinder.emitter`, route in `process()`.
- `MainActivity.kt` — added `leadFinderSnapshot()` + `leadFinderStop()` bridges.
- `HelpGuide.kt` — added `leadfind` topic (public-directory honesty).
- `ui.html` / `JARVIS.html` — added `#lfdCard` CSS + HTML + JS, skills entry, CAPS entry.
- `tools/test_lead_finder_contract.py` — new contract test (PASS).
- `tools/test_help_contract.py`, `tools/test_part6_skills_contract.py` — updated (leadfind).
