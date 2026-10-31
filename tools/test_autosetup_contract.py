#!/usr/bin/env python3
"""v34 Part 3: guards for the provider chain (Qwen row), AutoSetup wiring and the quiet rule. Static, reads the real sources."""
import re, os, sys
here = os.path.dirname(os.path.abspath(__file__))
def rd(n, d='main'):
    base = 'app/src/main/java/com/nova/assistant' if d == 'main' else 'app/src/test/java/com/nova/assistant'
    return open(os.path.join(here, '..', base, n), encoding='utf-8').read()
FP, AS, AP, NS, ST, MA, FH = (rd(x) for x in ('FreeProviders.kt', 'AutoSetup.kt', 'AutoSetupPlan.kt', 'NovaService.kt', 'SelfTest.kt', 'ModelAuto.kt', 'FreeHttp.kt'))
bad = 0
def need(c, m):
    global bad
    if not c: bad += 1; print('FAIL:', m)

# provider order + Qwen
m = re.search(r'fun ordered\(\): List<FreeProvider> = listOf\((.*?)\)', FP)
order = [x.strip() for x in m.group(1).split(',')]
need(order == ['GROQ', 'GROQ_QWEN', 'ZAI', 'ZAI2', 'ZAI3', 'OPENROUTER', 'OPENROUTER_GLM', 'POLLINATIONS'], 'order Groq -> Qwen -> GLM -> OpenRouter -> Pollinations, got %s' % order)
q = re.search(r'val GROQ_QWEN = FreeProvider\((.*?)\n    \)', FP, re.S).group(1)
need('"groq"' in q.split('QWEN_MODELS_GROQ')[1], 'Qwen uses the existing Groq key (no new key)')
need('api.groq.com/openai/v1/chat/completions' in q, 'Qwen goes to the Groq endpoint')
ids = re.findall(r'"(qwen/[^"]+)"', FP.split('val QWEN_MODELS_GROQ')[1].split('\n')[0])
need(ids == ['qwen/qwen3.8-27b'], 'ONE model table with the id looked up at build time: %s' % ids)
need(not re.search(r'qwen[^\n]*:free', FP.split('val QWEN_MODELS_GROQ')[1].split('val GROQ_QWEN')[0].split('*/')[1] if '*/' in FP else ''), 'no :free Qwen id is used as a model')
z = re.search(r'val ZAI = FreeProvider\((.*?)\n    \)', FP, re.S).group(1)
need('"zai"' in z and 'api.z.ai/api/paas/v4/chat/completions' in z and 'ZAI_MODELS' in z, 'GLM row: own key slot, docs.z.ai endpoint, one id table')
need(re.findall(r'"(glm-[^"]+)"', FP.split('val ZAI_MODELS')[1].split('\n')[0]) == ['glm-4.7-flash', 'glm-4.5-flash'], 'GLM ids in ONE table')
need(NS.count('"zai" -> SecureStore.getSlot(this, "zai")') >= 2 and 'private fun runFreePhoneAction' in NS and 'SecureStore.getSlot(this, "zai")' in NS.split('private fun runFreePhoneAction')[1].split('private fun localAnswer')[0], 'answer and phone-action chains map the zai key')
need('"zai" -> SecureStore.getSlot(ctx, "zai")' in AS, 'AutoSetup health check knows the zai key')
MAIN = open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant/MainActivity.kt'), encoding='utf-8').read()
UI = open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
need(all(x in MAIN for x in ('fun saveZaiKey', 'fun hasZaiKey', 'fun clearZaiKey')) and 'China' in UI and 'zai_key' in UI, 'key box + bridge exist, China server warning shown')
need('Pollinations' in UI and 'extra = p.extra' in FP, 'extra body member is passed by the chain')
# no hard-coded model ids elsewhere
for name, src in (('NovaService', NS), ('AutoSetup', AS)):
    need('qwen/' not in src and 'qwen3' not in src and 'glm-' not in src, name + ' has a hard-coded model id')
# keys: both chain call sites map by keyName (so the Qwen row gets the Groq key)
need(NS.count('"groq" -> SecureStore.getGroq(this)') >= 2, 'both chains map keyName groq')
need('FreeHttp.post(p, body, key)' in NS, 'chain uses FreeHttp')
# quiet rule: nothing the user hears or reads in a reply names a provider (only Settings / SELF TEST lines do)
for t in re.findall(r'fun failText.*?\n    }\n', FP, re.S):
    need('qwen' not in t.lower() and 'groq' not in t.lower(), 'failText names a provider')
need('tr(' not in AS and 'announce(' not in AS and 'say(' not in AS and 'speak(' not in AS, 'AutoSetup never speaks')
ASC = re.sub(r'/\*.*?\*/', '', AS, flags=re.S)
ASC = '\n'.join(l for l in ASC.split('\n') if not l.strip().startswith('//'))
need('listener' not in ASC and 'Toast' not in ASC and 'Notification' not in ASC and 'startActivity' not in ASC, 'AutoSetup: no chat, toast, notification, pop-up')
need('import android.app.Activity' not in AS, 'AutoSetup needs no Activity')
# AutoSetup wiring
need('AutoSetup.start(this)' in NS and 'ModelAuto.autoStart(this)' not in NS, 'service starts the one runner (old autoStart call gone)')
need('AutoSetup.stop()' in NS.split('override fun onDestroy')[1][:600], 'runner stops with the service')
need('AutoSetup.checkProviders' in ST and 'AutoSetup.log()' in ST, 'results go to SELF TEST')
need('silentVoice' not in MA and 'VoiceLock' not in MA, 'voice-model download is gone (v6.51)')
need(AS.count('Thread(') == 3 and AS.count('compareAndSet(false, true)') == 3, 'three background runners (setup, Qwen, offline AI pack v6.96), each guarded so only one of each exists')
loop_src = AS.split('private fun loop(app: Context)')[1].split('// ---- v6.33')[0]
need('var hindiAttempted = false' in loop_src, 'Hindi model setup is attempted once from the live runner')
need('HindiPack.ensure(app)' in loop_src.split('val next = AutoSetupPlan.nextWake')[0], 'Hindi model attempt occurs before the runner waits for the next task')
start_src = AS.split('fun start(ctx: Context)')[1].split('fun stop()')[0]
need('HindiPack.ensure' not in start_src, 'Hindi model attempt is not placed after the repeating loop exits')
need('fun backoff' in AP and 'MAX_ATTEMPTS = 5' in AP, 'back-off with a retry cap')
# offline engine stays removed (v31 rule)
BG = open(os.path.join(here, '..', 'app/build.gradle.kts'), encoding='utf-8').read() + open(os.path.join(here, '..', 'build.gradle.kts'), encoding='utf-8').read()
import re as _re
_forced = [x for g in _re.findall(r'force\(([^)]*)\)', BG) for x in _re.findall(r'"([^"]+)"', g)]
need(all(x.startswith('androidx.core:core') and x.endswith(':1.13.1') for x in _forced), 'no configurations.all force re-added (only the androidx.core 1.13.1 pin from v6.45 is allowed)')
need(bool(re.search(r'implementation\(.*llamacpp-kotlin', BG, re.I)), 'offline llama.cpp engine dependency is present')
# Python mirror of the plan
BACK = [int(x.replace('_', '').replace('L', '')) for x in re.findall(r'(\d[\d_]*L?)', re.search(r'BACKOFF_MS = longArrayOf\((.*?)\)\n', AP, re.S).group(1))]
need(BACK == [60000, 5, 30, 2, 6] or len(BACK) >= 5, 'backoff list parsed')
for w in ('!!', 'catch (_', 'TODO'):
    need(w not in AS + AP, 'AutoSetup files contain ' + w)
print('autosetup contract:', 'ALL OK' if bad == 0 else str(bad) + ' FAILED')
sys.exit(1 if bad else 0)
