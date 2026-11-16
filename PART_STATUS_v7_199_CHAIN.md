# PART STATUS v7.199 - CHAIN: ek kaam, kai agents, ek ke baad ek

Owner ne manga (2026-11-16): JARVIS se baat bhi ho, wo meri baat samjhe, kaam bhi kare, jaane kaun sa agent kab aur kaise kaam aata hai, aur kaam poora karke de.

## Pehle se tha (v7.191 - v7.198, dobara nahi banaya)
Baat-cheet + brain (JarvisBrain), 22 agents ka registry (TaskManagerRules: kab / kaise / example), "kaun sa agent kab" ka turant jawab, ek agent chunna + sentence rewrite, kaam ke baad sach-line (doneLine).

## Jo kami thi
Brain sirf EK agent chunta tha. "Website banao, publish karo aur link rahul ko WhatsApp karo" jaisa kaam (2+ agents ek ke baad ek) poora nahi hota tha.

## Ab kya bana (add-only)
- `TaskManagerRules`: `parseSteps` (brain ke jawab se "steps":[{agent,say},...], max 5, kam se kam 2, har id registry me ho, har sentence "jarvis ..." se), `stepFailed`, `isBackground` (site, publish, leads, autopilot, appbuild, agent, video, learn, image, laptop = lambe kaam), `encodeSteps/decodeSteps`, `isContinue` ("aage badho"), `chainLine` (sach-line). AI prompt me steps ka rule.
- `TaskManagerStore`: lambe kaam ke baad bache hue steps 24 ghante yaad (`savePending/pending`).
- `JarvisService`: `runPlanText` + `runChain`, dono brain hooks me single plan se PEHLE. Har step ka natija line me; step fail = chain ruk jata hai ("Ruk gaya"), kabhi poora nahi likhta; lambe kaam ke baad ruk kar bolta hai "poora hone par aage badho bolo"; "aage badho" bache steps chalata hai (sirf jab kuch pending ho).
- HARD DENY (paisa, bank, password, OTP, PIN, CVV) har step me dobara check: parseSteps, decodeSteps, runChain.
- Tests: `TaskManagerRulesTest` me 9 naye test, `tools/test_task_chain_contract.py` (25 check).
- Fix: `tools/test_wiring_audit_contract.py` v7.198 ke `.LauncherEntry` activity-alias ko class samajh kar FAIL kar raha tha (GitHub "contract checks" lal karta). Ab alias skip.

## Evidence (is machine par)
- test_task_chain_contract 25/25 PASS; step regex ka Python mirror sahi; wiring_audit, understand, brain_learned, help, agent contracts PASS.

## NOT RUN (honest)
- Kotlin COMPILE aur JUnit: is machine par compiler nahi. Pehla asli signal GitHub Actions. Lal aaye to red lines bhejo.
- Phone par koi test nahi. Dekhna: "jarvis restaurant ki website banao, publish karo aur rahul ko link bhej do" -> website banti hai, ruk kar "aage badho" bolne par publish + WhatsApp.
- Brain AI par tika hai (key / free chain chalu ho tabhi). Ye nahi jaanch hua ki lambe agents (site, leads...) sach me turant return karte hain; isi liye unke baad chain ruk jata hai (surakshit). Agar koi agent asal me turant poora hota hai to BACKGROUND list se hata do.
- Chain me "aage badho" haath se bolna padta hai (auto-resume jaan-bujh kar nahi, kyunki lambe kaam ke khatam hone ka pakka signal nahi mila).
