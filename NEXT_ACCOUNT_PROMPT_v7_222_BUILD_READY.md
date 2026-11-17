# NEXT ACCOUNT PROMPT (after v7.222 BUILD-READY)
Owner Hinglish bolta hai, Android phone se kaam karta hai, ek-ek step, chhota jawab, PASS / FAIL / NOT TESTED evidence ke saath. Rules: add-only, build.yml mat chhuo, GitHub par push tabhi jab owner bole, koi file delete nahi, jo chala nahi uska "NOT TESTED".
Pehle padho: `PART_STATUS_v7_222_BUILD_READY.md`, `PART_STATUS_v7_217_221_JARVIS_EVERYWHERE.md`, `ANDROID_TEST_STEPS_v7_221.md`, `TEST_STATUS.md`.

## Agla kaam (order me)
1. Owner repo me push kare -> Actions "JARVIS Android CI" chale. Red aaye to log dekho aur fix.
2. Owner `ANDROID_TEST_STEPS_v7_221.md` ek-ek chalaye; PASS/FAIL TEST_STATUS me.
3. OSCAR TV ka type (docs/OSCAR_TV.md §1). TV backend se nahi, phone se seedha judta hai.
4. Backend deploy: docs/DEPLOY.md Option B (Render). Phir phone Settings > CLOUD BRAIN, laptop `cloud jodo JARVIS-CLOUD:...`.
5. Ultron voice default OFF (owner haan bole to).
Local compile: `JSON_JAR=<json.jar> bash <abs path>/tools/local_compile_check.sh <workdir>` (absolute path do; kotlinc + android.jar GitHub se aate hain, Maven blocked ho sakta hai).
