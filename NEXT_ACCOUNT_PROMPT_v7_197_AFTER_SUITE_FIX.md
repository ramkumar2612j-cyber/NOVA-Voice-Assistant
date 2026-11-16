# NEXT ACCOUNT PROMPT - JARVIS v7.196 FINAL (v7.197 fix) ke baad

Pehle `NEXT_ACCOUNT_PROMPT_v7_196_AFTER_SELF_BUILD.md` ke saare rules padho (owner non-technical, Hinglish, ek time par ek chhota step, app ka naam sirf JARVIS, package `com.nova.assistant` aur `nova_*` keys NA badlo, HARD DENY = payment/order/password/OTP/PIN/CVV, bina saboot "DONE/WORKING" mat likho, sirf ADD karo). Phir `PART_STATUS_v7_197_FULL_SUITE_AND_SYNTAX_FIX.md` padho.

## Is zip me kya badla (v7.197, sirf fix)
- `HelpGuide.kt` ka syntax bug (stale duplicate topic) hata; `docs/BUILD.md` me nova.ppn -> jarvis.ppn; naya `tools/test_kotlin_syntax_contract.py`.
- Poora `tools/run_all_checks.sh` pehli baar end-to-end chala: 183 PASS + 1 FAIL (fix kiya). Open item "run_all_checks poora chalao" ab BAND hai.

## SABSE PEHLA KAAM (ab bhi wahi)
Owner se poocho: "Zip GitHub par chadha ke Actions build chalaya? Green ya red? Red ho to sirf laal (error) lines bhejo."
- RED + `compileDebugKotlin`: pehle owner ki error lines dekho; bache hue type/Android errors yahin dikhenge (is sandbox me SDK nahi, isliye ye pehle kabhi nahi dikhe).
- RED + `compileDebugUnitTestKotlin`: `SelfBuildRulesTest.kt` ya koi aur test.
- RED + "Source contract checks": ab sirf asli failure hoga (suite poora PASS tha), flaky guard ho to pehle akele chalao.
- GREEN: TEST_STATUS.md ke GitHub-build table me row jodo, phir v7.196 ka "NEXT" file ka "SELF BUILD ko phone par test karo" section follow karo (token me `workflow` scope!).

## Khule items
1. Poora Kotlin compile + JUnit (NOT RUN) - GitHub Actions se.
2. TEST_STATUS.md rows 20-32 phone par (NOT REPORTED / FAIL). Rows 20-21 fix verify nahi hui.
3. `tools/test_kotlin_syntax_contract.py` sirf syntax pakadta hai. Agar Android `android.jar` + org.json ka koi stub milta ho to type-check bhi jodna (tools/local_compile_check.sh purane notes me hai, internet chahiye).

## ADD-ON: SELF PATCH (v7.197) - owner ne "seedha main par, bina puche" chuna
Padho: `PART_STATUS_v7_197_FULL_SUITE_AND_SYNTAX_FIX.md` ka ADD-ON hissa. Purana rule "app khud .kt nahi badalta / AI patch jaan-bujh kar nahi" ab owner ke faisle se BADAL gaya hai (SelfPatchRules/GitHubSelfPatch). HARD DENY ab bhi hamesha: payment, order, password, OTP, PIN, CVV. Protected files / path allowlist / auto-revert ko KAMZOR mat karna.
Pehli build me `GitHubSelfPatch.kt` (type/Android errors) aur `SelfPatchRulesTest.kt` pehli baar compile honge - red aaye to yahan bhi dekho. Phone test: TEST_STATUS row 33.
