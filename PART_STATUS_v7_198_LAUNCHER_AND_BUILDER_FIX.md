# PART STATUS v7.198 - launcher "nahi aa raha" + App Builder "6 facts" fix

Owner report (2026-11-16, 3 screenshots): (1) Launcher abhi bhi nahi aa raha. (2) App Builder ne "Manas scheduled assistant app" banane se mana kiya: "sirf 1 pakki detail, kam se kam 6 chahiye".

## Fix 1 - Launcher
- Jad: app me kahin bhi JARVIS ko Home banane ka button / prompt nahi tha. Sirf manifest me HOME category thi.
- `AndroidManifest.xml`: naya `activity-alias .LauncherEntry` (label "Jarvis Home", LAUNCHER) = app-drawer me alag icon jo seedha Home screen kholta hai.
- `LauncherActivity.kt`: `isDefaultHome()`, `askDefaultHome()`, `openHomeChooser()`. Home screen khulte hi (agar JARVIS default nahi) popup: HAAN / ABHI NAHI. HAAN = Android 10+ RoleManager ROLE_HOME, warna Home settings.
## Fix 2 - App Builder
- Jad: planner ne personal scheduler ko needs_facts=true diya; research ne 0 facts nikale; 6-facts gate ne app roki.
- `AppBuilderRules.kt`: `isPersonalTool()` + `forcePersonal()`, PLAN_SYSTEM me personal tool = needs_facts false. `AppBuilder.kt` plan par forcePersonal lagata hai. Rules/puja/recipe/price jaise topic par gate waise hi rehta hai.
- Test: `AppBuilderRulesTest.personal_scheduler_needs_no_facts`.

## NOT RUN
Kotlin compile, JUnit, phone test. Pehle GitHub Actions build.

## Owner decision (2026-11-16): "JARVIS hi ab se default launcher rahega"
- "DOBARA MAT POOCHO" button hata diya: jab tak JARVIS Home nahi, har baar launcher khulne par poochta hai. Drawer list me "Jarvis Home" alias chhupa hai (duplicate icon nahi). Home dabane par home page par wapas (onNewIntent), back se launcher band nahi hota (pehle se). Phone par NOT RUN.

## Fix 3 - UNDERSTAND-FIRST (owner: "JARVIS samajh kar kaam kare, set kiya hua nahi")
- Pehle: fixed word-patterns (Logic/Understand/*Rules) PEHLE chalte the, AI brain sabse baad me. Isliye galat suna shabd galat set command chala deta tha (TEST_STATUS row 20).
- Ab: `JarvisBrain.longNatural` (7+ shabd, 28+ akshar) wali baat `JarvisService` me pattern chain se PEHLE brain ko jaati hai. Brain tool chunta + sentence rewrite karta hai, purana tested tool chalta hai (HARD DENY wahi). Brain na samjhe / key ya internet na ho = purana raasta. Chhote seedhe command (torch, volume, call) abhi bhi turant local.
- Brain ek baar hi poochha jaata hai (`brainTried`), baad wala brain call dobara nahi.
- Test: `JarvisBrainTest.longNaturalSentenceGoesToBrainFirst`. Kotlin compile + JUnit + phone: NOT RUN.

## Fix 4 - baat + samajh + agent chunna + kaam poora (owner ne 2026-11-16 maanga)
- `TaskManagerRules`: har agent ke liye HOW (kaise), naye agents wa / gmail / music, `guideText` ("kaun sa agent kab, kaise" turant jawab, AI call nahi), `doneLine` (kaam ke baad sach: "Kaam poora" ya "Poora nahi hua"; error ko kabhi poora nahi likhta). AI catalog me ab how bhi jaata hai.
- `JarvisService.runRewritten`: brain ne agent chuna + sentence rewrite kiya -> pehle Logic tool, nahi to poori specialist chain (WhatsApp, Gmail, music, website...) `handle(...)` se, `rewriting` guard (dusra brain call nahi), phir doneLine. Pehle sirf Logic.classify wale tool chalte the.
- Baat-cheet: brain "answer" mode ya jo tool na mile = purana AI chat road, badla nahi.
- Tests: TaskManagerRulesTest (guide, doneLine, naye agents). Kotlin compile + JUnit + phone: NOT RUN.
