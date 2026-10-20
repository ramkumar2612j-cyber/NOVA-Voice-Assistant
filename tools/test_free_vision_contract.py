#!/usr/bin/env python3
"""v7.54 Part 13B-2a static contract: the free vision fallback (FreeVision.kt) only asks providers that REALLY take images (Groq qwen vision + OpenRouter free router),
never Z.ai / Pollinations / text-only models, needs the owner's key and the free-chain switch, shares cooldowns + ApiUsage with the text chain (same provider ids, FreeHttp),
is used only AFTER Gemini cannot be used, stops on cancel, keeps the 35 s call rule, sends no more than 3 frames per call, tells the owner honestly (limit line + MADAD + card line),
adds no new HTTP client, no new permission, and does not remove the old Brain / bridge surface. Kotlin compile + JUnit run separately; real phone NOT TESTED."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
rd = lambda p: open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
F = rd(J + 'FreeVision.kt'); P = rd(J + 'AnalysisPipeline.kt'); Q = rd(J + 'AnalysisPipelineRules.kt'); S = rd(J + 'NovaService.kt')
U = rd('app/src/main/assets/ui.html'); H = rd(J + 'HelpGuide.kt'); MF = rd('app/src/main/AndroidManifest.xml'); FP = rd(J + 'FreeProviders.kt')
bad = []
# only image-capable providers
fp = F.split('fun providers()')[1].split('\n')[0]
if 'GROQ, OPENROUTER' not in fp: bad.append('vision providers are not exactly Groq + OpenRouter')
if 'qwen/qwen3.8-27b' not in F or 'openrouter/free' not in F: bad.append('vision model ids missing')
v = F.split('val GROQ')[1].split('fun providers()')[0]
for no in ('zai', 'pollinations', 'gpt-oss', 'llama-3.3'):
    if no in v: bad.append('vision provider list mentions ' + no + ' (text-only or foreign server)')
if '"groqqwen"' not in F or '"openrouter"' not in F: bad.append('vision providers must reuse the text-chain ids (shared cooldown / ApiUsage)')
if 'image_url' not in F or 'data:image/jpeg;base64,' not in F: bad.append('request is not the OpenAI image_url data-URL pattern')
if 'FRAMES_PER_CALL = 3' not in F: bad.append('frame cap per call is not 3 (Groq limit)')
if 'FreeProviders.parseReply' in F.split('fun replyText')[1].split('fun codeFor')[0]: bad.append('replyText must not use the spoken-text cleaner (it shortens JSON)')
# chain rules: key needed, switch, cooldown, stop, budget, offline
run = F.split('fun run(')[1]
for need in ('if (!enabled)', 'cd.cooling(', 'keyOf(p.keyName)', 'cd.set(', 'stop()', 'budgetMs', 'r.code == 598'):
    if need not in run: bad.append('FreeVision.run lacks ' + need)
# Android side: same switch, same keys, same cooldowns, FreeHttp (no new HTTP client), cancel aware
cf = S.split('override fun freeVisionReady()')[1].split('private fun msg(')[0]
for need in ('ExtCfg.freeChain(', 'freeCooldowns', 'FreeHttp.post(', 'SecureStore.getGroq(', '"openrouter"', 'stop'):
    if need not in S.split('fun analysisBrain()')[1].split('private fun msg(')[0]: bad.append('NovaService free vision lacks ' + need)
if 'HttpURLConnection' in F or 'URL(' in F: bad.append('FreeVision opens its own connection')
# pipeline: Gemini first, free only after Gemini cannot be used; no free call on offline; frames capped
if 'fun freeVisionReady()' not in P or 'fun callFree(' not in P: bad.append('Brain has no free vision methods')
if 'fun blocked(): String' not in P or 'fun call(json: String)' not in P: bad.append('old Brain surface was removed')
if 'tryFreeAfter' not in P or 'fun tryFreeAfter' not in Q or 'outcome != "OFFLINE"' not in Q: bad.append('free chain is not gated by tryFreeAfter / offline rule')
if 'FreeVision.pickIndexes(' not in P: bad.append('frames are not capped for the free chain')
if 'brain.callFree(' not in P or '{ !alive(g) }' not in P: bad.append('free call is not cancel aware')
# v7.60: 13D added the `chat` parameter (chat photo bytes) to finishMetadata; the behaviour (no key / limit and no free vision -> honest metadata report) is unchanged.
if 'brain.blocked()' not in P or not ('finishMetadata(g, id, file, chat, isVideo, blocked, en)' in P or 'finishMetadata(g, id, file, isVideo, blocked, en)' in P): bad.append('honest metadata report when nothing can see is gone')
if 'freeUsedNote' not in P: bad.append('report does not say a free AI looked at the picture')
if 'MAX_VISION_CALLS' not in rd(J + 'AnalysisRules.kt'): bad.append('2-call cap gone')
# honesty text: MADAD, CAPS, card line
if 'Groq / OpenRouter' not in H.split('Topic("analysis"')[1].split('\n')[0]: bad.append('MADAD analysis topic does not tell frames may go to Groq / OpenRouter')
if 'Groq / OpenRouter' not in U.split("m11_foot")[0].split("Analyse dabane par hi")[-1] and 'Analyse dabane par hi photo / frames AI ko bheje jate hain: pehle Gemini' not in U: bad.append('card consent line not updated')
# nothing removed / no new permission
if len(re.findall(r'<uses-permission ', MF)) not in (30, 31): bad.append('permission count changed (30 expected)')
for keep in ('val GROQ_QWEN', 'val OPENROUTER', 'fun ordered()'):
    if keep not in FP: bad.append('FreeProviders lost ' + keep)
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS free vision contract (static wiring; Kotlin compile + JUnit run separately; real phone / real Groq / OpenRouter NOT TESTED)')
