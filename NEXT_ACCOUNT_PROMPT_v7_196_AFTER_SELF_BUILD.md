# NEXT ACCOUNT PROMPT - JARVIS v7.196 ke baad (agla account: GLM 5.3)

Tum JARVIS Android project ke agle account ho (pichla account Claude Sonnet tha, ab GLM 5.3). Owner NON-TECHNICAL hai: sirf Android phone (Chrome + Termux + GitHub app/web), Hinglish me baat, ek time par ek chhota step, exact github.com links do (copy-paste karne layak). Owner = "boss", Jarvis = CEO. App ka naam sirf **JARVIS** hai (kahin NOVA mat likhna) - lekin package `com.nova.assistant` aur saari `nova_*` saved-data keys **jaan-bujh kar purani hi rakhi gayi hain, UNHE MAT BADALNA** (badalne se purana saved data/settings sab phone par kho jayega).

## Pehle ye files padho (is order me)
1. `PART_STATUS_v7_196_SELF_BUILD.md` (abhi-abhi is session me kya bana)
2. `TEST_STATUS.md` (poora owner-test record, kabhi delete mat karna, sirf aage badhao)
3. `OWNER_SETUP_AND_APK_STEPS.md` (owner zip ko GitHub par kaise chadhata hai, APK kaise banta/install hota hai)
4. `.github/workflows/build.yml` (abhi-abhi isme ek naya release-publish step joda gaya hai)
5. `app/src/main/java/com/nova/assistant/GitHubSelfBuild.kt` (naya file, is session ka sabse bada kaam)
6. `app/src/main/java/com/nova/assistant/UpdateManager.kt` (`installApkFile` naam ka naya shared function)

## Confirmed facts (owner ne khud bataya, 2026-11-16)
- **Asli GitHub repo: `ramkumar2612j-cyber/JARVIS-Voice-Assistant`** (ek purani file me doosra naam `skfashion142/...` bhi mila tha - WRONG, ignore karo, ye vahi confirmed naam hai). `GitHubUpgradePublish.kt` me `OWNER_REPO` constant isi naam ka hai; `GitHubSelfBuild.kt` usi constant ko reuse karta hai (do jagah hardcode nahi kiya).
- Owner ne abhi tak is v7.195/v7.196 zip ka koi GitHub Actions build nahi chalaya (as of 2026-11-16 00:38 IST). Matlab **v7.189 se v7.196 tak ka Kotlin code is session ke pehle KABHI bhi compile nahi hua hai** - pehla real signal GitHub Actions hi dega. High chance hai ki pehli build RED aaye (chhoti syntax/type error jaisi cheez) - ye normal hai, iske liye ghabrana nahi.

## Owner ke fixed rules (har session follow karo)
1. Har part ke baad ek naya zip + uske ANDAR agla `NEXT_ACCOUNT_PROMPT_*.md` + ek `PART_STATUS_*.md`. Sirf ADD karo, kuch purana hatao mat (jab tak owner khud na bole).
2. Jarvis apne automation/agent khud use kare, baar-baar approval na maange - **SIRF ye HARD DENY hamesha rahega, chahe kuch bhi ho jaye**: payment karna, order dena (boss ke paise se), password, OTP, PIN, CVV. Risky kaam (jaise kisi ko call/message) par bola hua "haan" zaroor lo.
3. Kabhi bhi "DONE / WORKING / VERIFIED" mat likho bina saboot ke. Jo compile/phone-test nahi hua use hamesha "NOT RUN" likho - ye project ki sabse important habit hai, isse owner ka bharosa bana hai.
4. Jo bhi naya automation/agent bano, use **Settings > TEST CENTER** me bhi dikhao (ek hi jagah sab test).
5. `.kt` file khud app kabhi nahi badalta khud-se (self-build sirf already-reviewed `main` branch ko dobara build karta hai, naya code nahi likhta/patch nahi karta).

## SABSE PEHLA KAAM (zaroori, kuch bhi naya karne se pehle)
Owner se poocho: **"GitHub Actions ka build chalaya? `git log --oneline -3` aur Actions tab (green ya red) ka screenshot/result bhejo."**
- Agar **GREEN** hai: बधाई, aage badho (niche "SELF BUILD ko phone par test karo" section).
- Agar **RED** hai: sirf us failed step ka **laal (red) text** maango (puri log nahi, sirf error wali lines) - zyada chance in cheezon me hai:
  - `compileDebugKotlin`: koi chhoti syntax error (missing comma/bracket), ya `GitHubSelfBuild.kt` me `UpdateManager.installApkFile` ka signature match na ho raha ho.
  - `compileDebugUnitTestKotlin`: `SelfBuildRulesTest.kt` me JSON string escaping ya `JSONObject` import issue.
  - `run_all_checks.sh` (Source contract checks step): koi static check FAIL - pehle isi session ke `tools/test_self_build_contract.py` ko akela chala ke dekho error kis line ka hai.
- Fix karke, usi tarah se naya zip do jaisa pehle hota aaya hai (code + updated `PART_STATUS` + naya `NEXT_ACCOUNT_PROMPT`).

## Agla kaam: SELF BUILD ko phone par test karo (build GREEN hone ke baad)
`GitHubSelfBuild.kt` likha ja chuka hai lekin **kabhi asli GitHub ke saath nahi chala** (is sandbox me network/Android/Gradle kuch nahi hai - sirf Python static checks + headless Chromium chalte hain, kabhi real phone/GitHub nahi). Isliye test order ye honi chahiye:
1. Owner se poocho: unka GitHub token (Settings > GitHub me pehle se saved hai, `GitHubPublish.SLOT`) me **`workflow` scope** hai ya nahi (sirf `repo` scope se `trigger()` 403 dega: "GitHub ne mana kiya. Token mein 'repo' aur 'workflow' scope chahiye"). Agar nahi pata, bol do: GitHub > Settings > Developer settings > Personal access tokens me jaa kar token ko re-generate karo, is baar "repo" + "workflow" dono checkbox tick karo.
2. Phone par app kholo > Settings > TEST CENTER > neeche "JARVIS KHUD APK BANAO" box dikhega. **APK BANAO** dabao.
3. 10-20 minute ruko (ya "jarvis apk status" bolo bar bar). Chip GREEN hone ka wait karo.
4. GREEN hote hi **INSTALL** dabao (ya "jarvis apk install karo" bolo). Android apni khud ki Install screen dikhayega - owner ko wahan khud "Install" tap karna hoga (ye Android ka apna rule hai, koi app silent install nahi kar sakta, bypass mat dhoondna).
5. Result (PASS/FAIL + kya hua) TEST_STATUS.md me likho, row # agla number lagao.

## Khule (open) technical items - inhe fix/stabilize karna hai
1. **`run_all_checks.sh` poora kabhi end-to-end nahi dekha gaya is session me bhi** (2 minute ke andar 82 scripts tak chale, saare PASS the, 0 FAIL, lekin poora suite 171+ scripts ka hai aur time khatam ho gaya beech me). Agle account ka doosra kaam: isse akele, poora, bina rukawat ke chalao aur result confirm karo.
2. `tools/test_native_smooth_contract.py` aur `tools/test_perf_governor_contract.py`: purane sessions se known FLAKY (sequential ya parallel run me kabhi-kabhi ek baar FAIL, akele chalane par PASS - timing-sensitive headless-browser test hain). Is session me dono PASS the lekin inhe STABLE banana (wait/retry jodna) abhi bhi pending hai - agar GitHub Actions ka "Source contract checks" step inhi ki wajah se kabhi RED aaye, to samajh lena ye wahi known-flaky cheez hai, naya bug nahi.
3. Phone test ka pura pending backlog - `TEST_STATUS.md` kholo, jahan "NOT REPORTED" ya "NOT RUN" likha hai wo sab row abhi bhi khule hain (rows 20-21 ke FAIL bhi abhi fix-verify nahi hue - Instagram search filler aur JARVIS ki awaaz/Ultron effect).
4. `GitHubUpgradePublish.kt` ke apne comment ke mutabik, AI-generated Kotlin patch wala part (pure AI se naya feature likhna aur khud push karna) **jaan-bujh kar nahi banaya gaya hai** - wo alag, bahut saavdhaani wala design maangta hai (300-file app me bina compile check ke patch girne ka risk). Self-build (v7.196) isse alag hai: wo sirf already-reviewed `main` branch ko REBUILD karta hai, naya code nahi likhta. Dono ko mat mix karna.

## Is session (v7.196) me jo bana uska poora technical summary (taaki tumhe khud se kuch dhoondna na pade)
- Naya file: `app/src/main/java/com/nova/assistant/GitHubSelfBuild.kt` - `SelfBuildRules` (pure, testable) + `GitHubSelfBuild` (network: `trigger`/`poll`/`install`/`handleVoice`).
- Badla hua: `UpdateManager.kt` me `applyApk()` se install-core nikal kar `installApkFile(ctx, f, keepName)` banaya (public, reusable).
- Badla hua: `GitHubUpgradePublish.kt` me `OWNER_REPO` ko `private` se `internal` kiya (taaki `GitHubSelfBuild.kt` wahi constant reuse kare).
- Badla hua: `JarvisService.kt` me `UpgradeLedger.handle(...)` ke turant baad `GitHubSelfBuild.handleVoice(this, Logic.norm(text))` ka hook joda.
- Badla hua: `MainActivity.kt` me `fullReport()` ke turant baad do naye `@JavascriptInterface` function: `selfBuildAct(act: String)` (trigger/poll/install) aur `selfBuildStatus(): String`.
- Badla hua: `app/src/main/assets/ui.html` - Test Center card (`#tc_box`) ke andar `#sb_box` naya mini-card (APK BANAO / STATUS DEKHO / INSTALL buttons + chip), ek naya `<script id="v196-self-build">` block, aur `BRIDGE`/`CB` auto-verify arrays me `selfBuildAct`, `selfBuildStatus`, `onSelfBuild` jode gaye.
- Badla hua: `.github/workflows/build.yml` - `build` job ko `permissions: contents: write` diya, aur "Upload test report" step ke baad ek naya step jo (sirf `main` push + success par) APK ka sha256 banata hai aur `gh release create jarvis-latest ...` se publish karta hai, built-in `github.token` se (koi naya owner secret nahi chahiye).
- Naya test: `app/src/test/java/com/nova/assistant/SelfBuildRulesTest.kt` (10 Kotlin unit tests, pure rules, kabhi compile nahi hua is sandbox me).
- Naya test: `tools/test_self_build_contract.py` (23 static checks, saare PASS).

## Honest limits (hamesha yaad rakhna)
Is sandbox me kotlinc, Gradle, Android SDK, emulator aur internet kuch nahi hai. Sirf Python static-text checks aur headless Chromium (nakli/fake bridge) chalte hain. Kabhi bhi koi number/promise "guaranteed" mat do - na profit ki, na build pass hone ki. Jo bhi "NOT RUN" hai use "NOT RUN" hi likhte raho jab tak owner khud result na de.
