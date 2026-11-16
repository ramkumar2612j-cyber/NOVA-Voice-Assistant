# PART STATUS v7.196 - SELF BUILD (JARVIS khud APK banwaye)

## Kya bana (sach, saboot ke saath)
Owner ki maang thi: ek baar install ke baad Jarvis khud GitHub se naya APK banwaye, baar-baar Termux chalana na pade. Ye poora loop ab likha hua hai (add-only, kisi purane file ka code hataya nahi):

1. **`GitHubSelfBuild.kt` (naya file)** - do objects:
   - `SelfBuildRules`: pure/testable rules (trigger/status/install bolne ke phrase, GitHub ke JSON se sahi run aur release-asset chunna, sha256 line parse karna, phase labels). Koi network nahi - isliye unit test ho saka.
   - `GitHubSelfBuild`: asli network calls -
     - `trigger(ctx)`: `POST /repos/<repo>/actions/workflows/build.yml/dispatches {"ref":"main"}` - GitHub Actions ko "main" build karne ko bolta hai. Koi `.kt` file yahan se nahi badalta, sirf already-reviewed `main` ka wahi code dobara build hota hai.
     - `poll(ctx)`: read-only GET, sirf HUMARI dispatch ki hui run dhoondta hai (`event=workflow_dispatch`, `branch=main`, dispatch ke time ke baad).
     - `install(ctx, done)`: build GREEN hone par GitHub Release "jarvis-latest" se APK + `.sha256` file download karta hai, khud sha256 nikaal kar match karta hai, tabhi `UpdateManager.installApkFile()` ko deta hai.
2. **`UpdateManager.kt`**: `applyApk()` ka install-core (`checkApk` + `PackageInstaller` + `InstallTap`) ek naye public function `installApkFile()` me nikal diya, taaki self-build usi verified, already-tested install path ko reuse kare - koi doosra/naya install mechanism nahi likha.
3. **`.github/workflows/build.yml`**: `build` job ko `permissions: contents: write` diya (sirf isi job ke liye, kam se kam access), aur ek naya step - test+assemble pass hone par, SIRF `main` push par (PR par nahi), APK ka sha256 banata hai aur GitHub ka apna built-in `GITHUB_TOKEN` (`github.token`) use karke "jarvis-latest" naam se ek Release banata/update karta hai, jisme APK + sha256 file attached hoti hai. **Isme owner ka koi naya secret/token nahi chahiye** - ye token sirf usi run tak limited hai aur naya build trigger nahi kar sakta (loop nahi banega).
4. **Voice**: `JarvisService.kt` me `UpgradeLedger` ke turant baad `GitHubSelfBuild.handleVoice(...)` jod diya. Bolo: **"jarvis apk banao"** (build shuru), **"apk status"** (kaha tak pahuncha), **"apk install karo"** (sirf tab kaam karega jab status GREEN ho - phir Android ki apni Install screen khulegi, aakhri tap hamesha owner ka).
5. **Test Center card** (Settings > TEST CENTER) me ek chhota naya box "JARVIS KHUD APK BANAO" - APK BANAO / STATUS DEKHO / INSTALL (3) button + status chip. `MainActivity.kt` me `selfBuildAct(act)` (trigger/poll/install, `act.take(8)` se capped, `upgradeAct` jaisa hi pattern) aur `selfBuildStatus()` bridge functions.
6. **HARD DENY** bilkul untouched hai: ye feature sirf code build + verified install karta hai, payment/order/password/OTP/PIN/CVV se koi lena-dena nahi.
7. **Owner ka GitHub token** (jo pehle se `Settings > GitHub` me save hai, `GitHubPublish.SLOT`) is feature ke liye bhi chahiye - sirf 2 kaam ke liye: (a) build trigger karna (scope: `repo` + `workflow`), (b) release se APK download karna. Agar token me `workflow` scope nahi hai to trigger 403 dega ("GitHub ne mana kiya. Token mein 'repo' aur 'workflow' scope chahiye") - owner ko token dobara banana pad sakta hai is scope ke saath.

## Test level (is session me jo chala)
| Test | Level | Result |
|---|---|---|
| tools/test_self_build_contract.py (naya, 23 checks) | static | PASS |
| tools/test_upgrade_publish_contract.py (regression) | static | PASS |
| tools/test_bridge_caps_contract.py (regression, 130 params) | static | PASS |
| tools/test_wiring_audit_contract.py (regression) | static | PASS (305 UI calls, 349 bridge funs, 32 command kinds) |
| tools/test_help_contract.py (regression, 81 topics) | static | PASS |
| tools/test_part6_skills_contract.py (regression) | static | PASS |
| tools/test_test_center_contract.py (regression) | static | PASS |
| tools/run_all_checks.sh (poora suite) | static | **ADHURA** - 2 minute me 82 scripts tak chale, 0 FAIL dikha, poora suite khatam hone se pehle time khatam ho gaya. Agle account ka pehla kaam: isse poora chalao. |
| `SelfBuildRulesTest.kt` (naya, 10 test) | Kotlin unit | **NOT RUN** - sandbox me kotlinc nahi. GitHub Actions chahiye. |
| Kotlin compile (poora app) | - | **NOT RUN** |
| Gradle build / assembleDebug | - | **NOT RUN** |
| Phone par "jarvis apk banao" / Test Center ka naya box | - | **NOT RUN** (owner test) |
| Asli GitHub Release publish (CI se) | - | **NOT RUN** (pehli baar main par build chalne ke baad hi pata chalega) |

## Jo abhi tak NAHI bana / khula hai (agle account ke liye)
1. **Kotlin v7.189 se v7.196 tak KABHI compile nahi hua is sandbox me.** Pehla real test GitHub Actions hai.
2. `run_all_checks.sh` ka poora run (171+ scripts) is session me end-to-end nahi dekha gaya (time khatam).
3. `test_native_smooth_contract.py` aur `test_perf_governor_contract.py`: pichle session se known flaky (sequential/parallel run me kabhi-kabhi FAIL, akele PASS) - isi run me PASS dikha, par stabilize karna abhi bhi baaki hai.
4. Owner ke GitHub token me `workflow` scope hai ya nahi - confirm nahi hai. Agar nahi hai to `trigger()` 403 dega.
5. Phone par koi bhi cheez test nahi hui - TEST_STATUS.md row 25-32 sab NOT REPORTED/NOT RUN.

## UPDATE (same session, thodi der baad) - 2 flaky test fix kiye
- `tools/test_native_smooth_contract.py` aur `tools/test_perf_governor_contract.py` me jahan fixed `wait_for_timeout(1500/600)` tha (isi wajah se pehle kabhi-kabhi FAIL aata tha load me), wahan ab `wait_for_function(..., timeout=5000-8000)` poll hai - result wahi hai, bas CPU busy hone par zyada der sahi se wait karta hai, galat FAIL nahi deta. Akele (3 baar) aur jaan-bujh kar parallel+CPU-load simulate karke (`yes` background me chalakar) dono test is session me check kiye - har baar PASS.
- **`tools/run_all_checks.sh` ka poora run is baar bhi end-to-end dekhne se PEHLE session khatam ho gaya** (background me chalaya tha; zip banate waqt 82/171+ scripts tak pahuncha tha, 0 FAIL abhi tak dikha, poora suite khatam nahi dekha gaya). **Agle account ka sabse pehla kaam: `./tools/run_all_checks.sh` ko akela, poora, bina ruke chalana aur result confirm karna.**
