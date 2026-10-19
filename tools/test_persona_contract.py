#!/usr/bin/env python3
"""v7.48 PART 9 JARVIS PERSONA static contract: engine is pure + safe, wired in ONE place, switch default ON,
auto-reply path untouched (owner rule v7.46: no fixed text to other people), nothing old removed."""
import re, sys, pathlib
R = pathlib.Path(__file__).resolve().parent.parent
J = R / 'app/src/main/java/com/nova/assistant'
rules = (J / 'PersonaRules.kt').read_text(encoding='utf8')
svc = (J / 'NovaService.kt').read_text(encoding='utf8')
ext = (J / 'ExtCfg.kt').read_text(encoding='utf8')
main = (J / 'MainActivity.kt').read_text(encoding='utf8')
ui = (R / 'app/src/main/assets/ui.html').read_text(encoding='utf8')
nns = (J / 'NovaNotificationService.kt').read_text(encoding='utf8')
bad = []
def need(c, m):
    print(('PASS ' if c else 'FAIL ') + m)
    if not c: bad.append(m)
need('import android' not in rules and 'http' not in rules and 'Context' not in rules, 'PersonaRules is pure Kotlin: no Android, no network')
safe = re.search(r'SAFE_KINDS: Set<String> = setOf\((.*?)\)', rules, re.S).group(1)
kinds = set(re.findall(r'"(\w+)"', safe))
for k in ['whatsapp_send', 'whatsapp_call_unsupported', 'rec', 'video_rec', 'recording_manager_voice', 'screen', 'screen_end', 'screen_blocked', 'agent', 'agent_resume', 'publish', 'site', 'gmail', 'connector', 'image', 'video_gen', 'teach', 'learn', 'app_download']:
    need(k not in kinds, 'persona never touches kind: ' + k)
need('hasPending' in rules and 'failed' in rules and 'silent' in rules and 'looksLikeProblem' in rules, 'engine skips confirmations, failures, silent turns, problem words')
need('MAX_REPLY' in rules and 'MIN_GAP_MS' in rules, 'engine has length limit + cooldown')
need(svc.count('personaTouch(') == 2, 'NovaService: one helper + one call only')
need('finishTurn(withNudge(personaTouch(reply)), lastKind)' in svc, 'persona runs before withNudge / finishTurn')
need('PersonaRules' not in nns and 'personaTouch' not in nns, 'auto-reply (NovaNotificationService) does NOT use persona')
users = [f.name for f in J.glob('*.kt') if 'PersonaRules.' in f.read_text(encoding='utf8') and f.name != 'PersonaRules.kt']
need(users == ['NovaService.kt'], 'PersonaRules used only by NovaService (found: %s)' % users)
need('getBoolean(PERSONA, true)' in ext and 'fun setPersona' in ext, 'ExtCfg.persona default ON + setter')
need('fun getPersona(): Boolean' in main and 'fun setPersona(on: Boolean): String' in main, 'bridge getPersona / setPersona present (no String params)')
m = re.search(r'var BRIDGE=(\[.*?\]);', ui, re.S); br = set(re.findall(r'"(\w+)"', m.group(1)))
need({'getPersona', 'setPersona'} <= br, 'ui.html BRIDGE lists both persona methods')
need('id="pt9_persona"' in ui and "'pt9_persona'" in ui and 'PERSONA ON' in ui, 'ui.html persona button block + self-check entry')
need(ui.rstrip().endswith('</body></html>'), 'ui.html still ends with </body></html>')
b = ui[ui.index('<script id="pt9_persona">'):]
need('fetch(' not in b and 'XMLHttpRequest' not in b, 'pt9 block: no network')
need('Android.getAutoReply' not in rules and 'autoReplyText' not in rules, 'engine does not read or write auto-reply text')
for old in ['pt8_chat', 'p7_motion', 'p6_home', 's_save', 'ar_on', 'p7_hap', 'getOwnerName', 'setOwnerName', 'getLockGuard']:
    need(old in ui, 'old item still present: ' + old)
need((R / 'app/src/test/java/com/nova/assistant/PersonaRulesTest.kt').exists(), 'PersonaRulesTest.kt present')
print('persona contract:', 'PASS' if not bad else 'FAIL'); sys.exit(1 if bad else 0)
