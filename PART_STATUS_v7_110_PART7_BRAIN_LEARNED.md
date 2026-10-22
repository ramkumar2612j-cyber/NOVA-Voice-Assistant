# NOVA v7.110 - PART 7 of 8: bada local brain (owner picks) + "NOVA ne kya seekha" show / clear

## Goal
(1) Qwen 0.5B planning ke liye kamzor hai: phone ki RAM ke hisaab se Qwen 1.5B / 3B OFFER karo, owner khud chune, koi bada download chupke se na ho.
(2) Settings me dikhao NOVA ne jobs se kya seekha, aur ek CLEAR button jo TEENO stores saaf kare.

## Added (add-only)
- `BrainChoiceRules.kt` (pure Kotlin): `recommendedId` (RAM >= 8000 -> 3B, >= 4000 -> 1.5B, warna kuch nahi), `options(ram, freeBytes, installed)` (INSTALLED / OFFER / NEEDS_RAM / NEEDS_SPACE), `refusal(...)` (owner tap + RAM + storage + internet + Wi-Fi), `headline`, `isOfferedId`.
- `OfflinePack.kt`: naya `OWNER_PICK` = Qwen2.5-3B-Instruct Q4_K_M (repo `Qwen/Qwen2.5-3B-Instruct-GGUF`, min RAM 6000 MB, sanity size 1500-2600 MB). **MODELS list (5) aur `toDownload` / `runnable` bilkul nahi badle**, isliye automatic pack loop 3B ko kabhi download nahi karta. `byId` ab OWNER_PICK bhi dhundta hai; `tryOrder` me installed 3B sabse pehle aata hai.
- `BrainChoice.kt` (Android): `stateJson`, `start(id)` (sirf `BrainChoiceRules.refusal` pass hone par; worker thread; ek time par ek download; wahi verified `OfflinePackStore.installOne` = size + SHA-256 check), `cancel`. Koi loop / auto start nahi, `AutoSetup` me hook nahi.
- `LearnedMemory.kt`: `summary()` (Playbook + Site + Capability) aur `clearAll()` (teeno `clear()`; chat, keys, settings, models nahi chhute). `CapabilityLearningStore.clear()` naya. (Part 6 ka adhura kaam: `SiteLearningStore.clear()` ab wired.)
- `MainActivity` bridge: `getBrainChoice`, `startBrainInstall`, `cancelBrainInstall`, `getLearnedSummary`, `clearJobLearning`. (Purana `clearLearned()` = LearnStore alag hai, naam nahi takraya.)
- `ui.html`: script `pt7_brain_learned` = Settings me naya card "BADA BRAIN + NOVA NE KYA SEEKHA" (Part 17 SWITCHES card ke baad, SAVE row se pehle). DOWNLOAD **do baar tap** (pehle "PAKKA?"), CLEAR se pehle `confirm()`, clear ke baad honest "3 / 3" ya "Poora saaf nahi hua (n / 3)". Koi network, koi animation nahi; buttons >= 44 px. `BRIDGE` aur `SCR` lists me naye naam add.
- MADAD topic `brainlearn` (Kotlin `HelpGuide` + ui.html CAPS).
- Version 7.110 / code 37. Purane contract tests (Part 1-6, capability) ab 7.110 / 37 bhi maante hain.
- Tests: `BrainChoiceRulesTest.kt` (7 JUnit), `tools/test_part7_brain_learned_contract.py` (26 static checks), `tools/test_part7_render.py` (headless chromium, 20 checks).

## Unchanged on purpose
HARD DENY list, `Control.blocked()`, Parts 1-6 hooks, existing 5-model automatic pack download (Wi-Fi + charging + `ExtCfg.autoModels`), no automatic app install, no raw task / URL / text stored.

## Honest limits
- 3B ka file-size range (1500-2600 MB) aur repo naam Hugging Face se is sandbox me verify nahi hue (network nahi). Galat hone par app file refuse karti hai ("refused: no usable Q4_K_M file"), kharab file install nahi hoti.
- Bada brain phone par dheema ho sakta hai; asli speed / planning quality NOT TESTED. 3B ke liye 6 GB+ RAM chahiye.
- **Purani cheez jo maine badli nahi**: 1.5B jaise pack models ab bhi `ExtCfg.autoModels` ON hone par Wi-Fi + charging me chupke se download hote hain (v6.96 se). "No silent big download" sirf naye 3B ke liye lagu hai. Agar aap chahte ho ke 1.5B bhi sirf tap par aaye, bolo, ek chhota change hai.
- Bada brain installed hone par `LocalBrains.tryOrder` use sabse pehle try karta hai; kya wo planning me sach me behtar hai, ye phone par dekhna hoga.

## Checks (evidence)
- RUN PASS: `tools/test_part7_brain_learned_contract.py` (26/26, mutation check: card me `fetch` daalne par FAIL aaya).
- RUN PASS: `tools/test_part7_render.py` (headless chromium, fake bridge, 320 px).
- SUITE RESULT (173 tools/test_*.py, smooth tests skip): 146 PASS, 4 FAIL at the end of the run. Teen ke baad theek/samjhe: `test_offline_pack_contract` (mere 3B list se, test theek kiya, ab PASS), `test_part6_skills_contract` (naye topic `brainlearn` ka mapping add kiya, ab PASS), `test_part20_contract` (pehle se fail, v7.103 par bhi) aur `test_part4_fx_render` (pehle se fail: original v7.109 zip par bhi same 'stars did not spring back', mera change nahi). Dono fixed tests alag se dobara chale; poora suite fixes ke baad dobara NAHI chala.

## NOT RUN / NOT VERIFIED
Kotlin compile, `./gradlew testDebugUnitTest` (BrainChoiceRulesTest, HelpGuide tests), GitHub Actions, APK, phone. Agar build log me error aaye to bhejo, pehle wahi theek hoga.

## Owner test after build (optional until Part 8)
1. Settings > naya card dikhe; 8 GB phone par 3B "SUGGESTED", kam RAM par RAM-kam wali line.
2. DOWNLOAD ek tap: kuch download nahi hona chahiye (sirf PAKKA? dikhe). Dusre tap par Wi-Fi par shuru.
3. Koi job 2-3 baar chalao, REFRESH: seekha hua dikhe. SAB SEEKH MITAO > haan > "3 / 3", summary khali.

## SUITE RESULT after the fixes (full rerun)
149 tools/test_*.py ran (smooth tests skipped): 147 PASS, 2 FAIL: `test_part20_contract` and `test_part4_fx_render`, both fail the same way on the untouched v7.109 zip (not caused by Part 7).
