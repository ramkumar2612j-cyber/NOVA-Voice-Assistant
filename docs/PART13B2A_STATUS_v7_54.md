# NOVA v7.54 : PART 13B-2a POORA (free vision fallback) - status (simple Hindi, honest)

## Progress: 13B-2a = 100%  |  13B-2 = 25% (a poora; b, c, d baki)  |  Part 13 = ~42%
## Kya naya (app me)
Pehle agar Gemini key na ho ya Gemini ki limit poori ho, ANALYSE sirf file ki size batata tha. Ab agar aapki **free Groq ya OpenRouter key** save hai (aur free chain ON hai), tasveer unko dikhai jati hai aur poori report banti hai.
- Pehle hamesha Gemini. Gemini fail ho (key nahi / limit / dusri galti) tabhi free AI. Internet na ho to free AI bhi nahi bulaya jata.
- Groq: `qwen/qwen3.8-27b` (tasveer dekh sakta hai). OpenRouter: `openrouter/free` (khud koi free vision model chunta hai).
- **Z.ai (China server) aur Pollinations (anonymous) ko photo kabhi nahi jati.** Text wale model (gpt-oss, llama-3.3) bhi nahi.
- Ek baar me max 3 frames (Groq ki limit). Report me saaf likha aata hai ki tasveer free AI ne dekhi, Gemini ne nahi, aur kuch frames chhute ho sakte hain.
- Limit / cooldown / usage count wahi hai jo text chain ka hai (same Groq / OpenRouter key). ROKO / STOP free call ke beech me bhi rok deta hai.
- Koi key na ho to pehle jaisa honest metadata report. MADAD aur media card ki line me likha hai ki frames Groq / OpenRouter ko bhi ja sakte hain (sirf ANALYSE dabane par).

## Technical (sankshep)
Naye: FreeVision.kt (pure), FreeVisionTest.kt (18 test), tools/test_free_vision_contract.py. Badle: AnalysisPipeline (Brain.freeVisionReady / callFree, default band), AnalysisPipelineRules (tryFreeAfter, freeUsedNote, batches(total, per)), NovaService.analysisBrain, HelpGuide + ui.html text. Version 7.54. Nayi permission nahi (30).

## Test status (honest) - owner order: test Part 13 ke end me
- Chala: Kotlin compile 0 error; JUnit ek baar (mere ek test me galat expected string thi, theek ki, dobara NAHI chalaya); 4 contract script PASS (free_vision, help, android_runtime, analysis_pipeline).
- NOT RUN: run_all_checks.sh, JUnit dobara, GitHub Actions, asli phone, asli Groq / OpenRouter call. Free model ids badalte rehte hain: 400 / 404 par agla model / provider try hota hai.
## Aapko kuch nahi karna. Agla: 13B-2b (cancel ke baad atki call). Prompt: NEXT_ACCOUNT_PROMPT_v7_54_PART13B2B.md
