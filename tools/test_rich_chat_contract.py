"""v7.3 contract: ChatGPT-style typed chat (rich prompt, web context, markdown in the chat bubble, fast thinking, Samjha line)."""
import os, sys
H = os.path.dirname(os.path.abspath(__file__)); R = lambda p: open(os.path.join(H, '..', p), encoding='utf-8').read()
NS = R('app/src/main/java/com/nova/assistant/NovaService.kt'); FP = R('app/src/main/java/com/nova/assistant/FreeProviders.kt'); UI = R('app/src/main/assets/ui.html')
bad = []
def need(c, m):
    if not c: bad.append(m)
need('richTurn = true' in NS and 'richTurn = false' in NS, 'richTurn is set in typed() and reset in finishTurn')
need('rich = richTurn' in NS and 'richSystemPrompt' in NS, 'rich prompt used on the typed path')
need('at most 3 short sentences' in FP, 'the spoken prompt keeps its 3-sentence rule')
need('webItemsFor(said, en)' in NS and 'WebContext.needsWeb(said)' in NS and 'if (web.isEmpty()) FreeProviders.chainPreAnswer' in NS, 'web context only on rich turn, live-source line kept as fallback')
need('GEMINI_RICH_NOTE' in NS, 'Gemini prompt gets the typed-chat note')
need('GeminiSpeed.withFastThinking' in NS, 'fast thinking wired')
need('SiteSummary.samjha' in NS, 'Samjha line wired')
need('function mdRender' in UI and 'function copyBtn' in UI, 'markdown renderer + COPY in ui.html')
need('innerHTML' not in UI.split('function mdInline')[1].split('var WELCOME')[0], 'markdown renderer never uses innerHTML')
need('FreeProviders.speakable' in NS, 'spoken text has markdown stripped')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS rich chat contract')
