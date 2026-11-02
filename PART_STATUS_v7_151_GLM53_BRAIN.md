# v7.151 GLM 5.3 Flash = NOVA ka BRAIN (add-only, owner order 31 Oct)
CHANGELOG.md, build.yml, versionName NOT touched (stays 7.138).

## Kya jodha
- Naya provider `OPENROUTER_GLM` (id `openrouterglm`, model `z-ai/glm-5.3-flash:free`, SAME OpenRouter key, koi nayi key nahi).
- Brain kaam (pehle yahi chalta hai, Groq / Qwen / GLM 4.7 / OpenRouter peeche fallback): lamba typed chat, agent steps, website banana (SiteGen), bade cloud tasks (CloudTaskText), screen / photo analyse (vision, Groq ke baad).
- Chhote voice commands: GLM 5.3 NAHI chalta (reasoning slow hai). Sirf tab chalta hai jab owner AI chooser mein haath se GLM 5.3 chune.
- Reasoning ke liye max_tokens kam se kam 4000, HTTP wait 45 s (baaki providers jaise the).
- Lamba typed chat: Groq 6 s baad join karta hai (pehle 2.5 s). Agent steps: 2 s baad.
- ApiUsage mein alag row "GLM 5.3 Flash (brain)", SourceMap + FeatureStatus mein naam.

## Files
FreeProviders.kt (FreeProvider.brainOnly/minTokens, OPENROUTER_GLM, forJob, ProviderChain.run brain), FreeHttp.kt, FreeVision.kt, NovaService.kt (agentChain brain, rich stagger 6 s), CloudTaskText.kt, SiteGen.kt, ApiUsage.kt, SourceMap.kt, FeatureStatus.kt; tests FreeProvidersTest (+4), FreeVisionTest, 3 python contracts.

## NOT PROVEN
- Kotlin compile: NOT DONE here. Phone: NOT TESTED. OpenRouter ka free GLM 5.3 Flash sach mein chal raha hai ya nahi: NOT TESTED (web pages se pata chala, rate limited).
- Z.ai ki apni site par GLM 5.3 Flash free hai ya nahi, saaf nahi: `glm-4.7-flash` / `glm-4.5-flash` hi rakhe, 5.3 add nahi kiya.
- Full GLM 5.3 paid hai ($1.40 / $4.40 per 1M tokens): add nahi kiya.
