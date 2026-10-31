# v7.152 FINAL: full check + fix + handover (owner order 31 Oct: "ye complete kr ke zip do")
ADD-ONLY. CHANGELOG.md, build.yml, versionName (7.138) — teeno NOT touched (diff se verify kiya).

## Kya kiya (AUDIT_v7_152.md me poori list)
1. **FULL AUDIT**: saara source padha (273 Kotlin files, ui.html, manifest, tools, skillpacks, docs) + saare 162 contract tests chalaye. Ab **163/163 PASS** — pehli baar `test_part4_fx_render` bhi chala (playwright is sandbox me mil gaya): PASS. Do load-flaky tests akela chala kar PASS kiye. `tools/audit_dead_ui.py`: 0 dead controls, 0 missing topics.
2. **GLM 5.3 brain wiring VERIFY kiya** (v7.151 ka kaam poora sahi hai): OPENROUTER_GLM provider, forJob (short job me nahi, brain job me pehla), agentChain brain=true, SiteGen brain-first, CloudTaskText brain=true 90 s, FreeVision order Groq->GLM->OpenRouter, rich-chat stagger 6 s, ApiUsage row, FeatureStatus naam. Sab OK.
3. **FIX 1 — Settings switch**: LIGHT POPUP ab Settings me checkbox hai (`s_light`, opt-in, default OFF). Save/get dono jagah joda.
4. **FIX 2 — light popup ka chat box**: three-dots ka chat box pehle HAMESHA dark tha; ab light popup ON ho to halka rang + dark ink (dark wale me koi badlav nahi).
5. **FIX 3 — sawal ab setting nahi badalta**: "popup light hai?" pehle theme chupchap ON kar deta tha. Ab sawal par NOVA imaandaar jawab deta hai; purane order ("light popup karo", "popup dark", "popup halka kar do") pehle jaise chalte hain.
6. **FIX 4 — GLM 5.3 model id EK jagah**: pehle FreeProviders + FreeVision dono me tha; ab FreeVision.FreeProviders.OPENROUTER_GLM se leta hai. Id badle to sirf ek file me fix.
7. **NOVA apne aap ko janta hai (Part 4)**: naya HelpGuide + MADAD topic `aibrain` (67 topics), MADAD rows "GLM 5.3 Flash brain" + "Light popup", FeatureVoice bolkar status `popuptheme` ("popup light hai ya dark?"), FeatureStatus row "Popup ka rang".

## Files changed (9 + 6 naye)
Changed: HudTheme.kt, FeatureVoice.kt, NovaHud.kt, NovaService.kt (sirf popup-theme route), MainActivity.kt (get/saveSettings), FreeVision.kt, FeatureStatus.kt, HelpGuide.kt, ui.html (checkbox + 2 MADAD rows + 1 CAPS topic).
New: AUDIT_v7_152.md, PART_STATUS_v7_152_FINAL.md, OWNER_PHONE_TEST_LIST_v7_152.md, NEXT_ACCOUNT_PROMPT_v7_152.md, tools/test_v7152_light_popup_contract.py; tests: HudThemeTest (+2), FeatureVoiceTest (+2), FreeProvidersTest (+1), test_part6_skills_contract me 1 mapping line.

## Evidence — IMAANDAARI SE
- 163/163 contract scripts PASS (is sandbox me chale, exit-code se gina; playwright wala bhi PASS).
- JUnit (HudThemeTest/FeatureVoiceTest/FreeProvidersTest ke naye cases): **NOT COMPILED** (is sandbox me kotlinc nahi hai) — par structure purane passing tests jaisa hi, pure Kotlin.
- Android build: **NOT CONFIRMED** (yahan Android compile nahi hota). NovaHud/NovaService/MainActivity ke changes pehli baar GitHub build me hi compile honge.
- Phone: **NOT TESTED** — OWNER_PHONE_TEST_LIST_v7_152.md par owner khud bolega.
- GLM 5.3 free OpenRouter par abhi sach me chal raha hai ya nahi: **NOT TESTED** (yahan network nahi).
- Watch root cause, "samajh nahi aaya", hang, Instagram false "blocked": **khule hue** — sirf owner ke phone-evidence par fix honge (guess nahi kiya, rules ke mutabik).
