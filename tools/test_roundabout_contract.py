#!/usr/bin/env python3
"""v7.50 Part 10 contract: ROUNDABOUT COMMANDS. STATIC wiring checks on the real files + a Python MIRROR of RoundaboutRules.affirmative / needsWho.
The mirror is not the Kotlin code (no kotlinc here): it reads the word lists out of RoundaboutRules.kt and applies the same rules, so a changed list is caught,
but only the JUnit file RoundaboutRulesTest.kt (NOT RUN) can prove the Kotlin itself."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
def rd(p): return open(os.path.join(R, p), encoding='utf-8').read()
J = 'app/src/main/java/com/nova/assistant/'
rr, logic, cm, svc, hg, ui, persona = rd(J+'RoundaboutRules.kt'), rd(J+'Logic.kt'), rd(J+'ContextMemory.kt'), rd(J+'NovaService.kt'), rd(J+'HelpGuide.kt'), rd('app/src/main/assets/ui.html'), rd(J+'PersonaRules.kt')
bad = []
def need(c, m):
    if not c: bad.append(m)
need('RoundaboutRules.affirmative(w)' in logic, 'Logic.parseAnswer must fall through to RoundaboutRules.affirmative')
need(logic.index('if (w.any { it in NO }) return false') < logic.index('RoundaboutRules.affirmative(w)'), 'NO check must stay before the new yes rule')
need('RoundaboutRules.repeat(t)' in cm and 'RoundaboutRules.undo(t)' in cm, 'ContextMemory must use the new follow-ups')
need('"call", "sms", "whatsapp", "delete"' in cm and 'x.kind in NEVER_REPEAT) return null' in cm, 'ContextMemory NEVER_REPEAT guard must stay')
need('RoundaboutRules.needsWho(' in svc and 'RoundaboutRules.hint(' in svc and 'RoundaboutRules.whoQuestion(' in svc, 'NovaService must call needsWho / hint / whoQuestion')
need(svc.index('RoundaboutRules.needsWho(') < svc.index('ContextMemory.resolve(text'), 'who-question must run before ContextMemory.resolve')
need('Roundabout' not in persona, 'PersonaRules must stay untouched by Part 10')
need('"hint"' not in persona and '"clarify"' not in persona, 'persona must never decorate hint / clarify turns')
need(not re.search(r'import android|android\.|HttpURLConnection|fetch\(', rr), 'RoundaboutRules must stay pure Kotlin (no Android, no network)')
need('Topic("roundabout"' in hg and 'Topic("persona"' in hg and '"id": "roundabout"' in ui and '"id": "persona"' in ui, 'MADAD / HelpGuide topics missing')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/RoundaboutRulesTest.kt')), 'JUnit file missing')
# ---- mirror
def lst(name): return re.findall(r'"([^"]+)"', re.search(r'private val %s = (?:setOf|listOf)\((.*?)\n?\s*\)\n' % name, rr, re.S).group(1))
YES = set(lst('YES')); FILLER = set(lst('FILLER')); STRONG = set(lst('STRONG')); RISKY = set(lst('RISKY_WORDS')); REFS = lst('REFS')
def affirmative(w):
    w = [x.lower() for x in w if x and x != '[unk]']
    if not w or len(w) > 4 or any(x not in YES and x not in FILLER for x in w): return None
    if not any(x in FILLER and x not in YES for x in w): return None
    pair = re.search(r'\b(kar|kr|karo) do\b', ' '.join(w)) is not None
    return True if (any(x in YES for x in w) or any(x in STRONG for x in w) or pair) else None
NOS = {'no','nope','nah','cancel','stop','dont',"don't",'not','nahi','nahin','mat','ruko','wait'}
def parse(t):
    w = [x for x in t.lower().split() if x != '[unk]']
    if not w: return None
    if any(x in NOS for x in w): return False
    if len(w) <= 3 and all(x in YES for x in w): return True
    return affirmative(w)
def needs_who(t):
    tk = t.split()
    if 'reply' in tk or re.search(r'\d{6,}', t): return False
    ref = any((' ' + t + ' ').find(' ' + r) >= 0 for r in REFS) or re.search(r'\b(call|message|msg|text|send|pay|delete) (him|her|them)\b', t)
    return bool(ref) and (any(x in RISKY for x in tk) or 'phone laga' in t)
for t, exp in [('ha kr do', True), ('haan kar do', True), ('haan karo', True), ('kar do', True), ('theek hai', True), ('bilkul', True), ('ok chalo', True), ('yes', True), ('okay sure ji', True),
               ('nahi kar do', False), ('mat kar do', False), ('ha ruko', False),
               ('do', None), ('hai', None), ('karo', None), ('sabko bhej do abhi', None), ('haan sabko kar do', None), ('yes please', None), ('yes yes yes yes', None), ('ha kar do kar do kar do', None), ('yes call him now', None), ('maybe', None)]:
    need(parse(t) == exp, 'mirror parse(%r) = %r, expected %r' % (t, parse(t), exp))
for t, exp in [('wo jo kal wale ko bhej do', True), ('usi ko call karo', True), ('call him', True), ('usko payment kar do', True), ('mummy ko call karo', False), ('usko display karo', False),
               ('usi ko dobara', False), ('pichle hafte ka mausam batao', False), ('usko reply bhej do 2', False), ('usko 9876543210 par call karo', False)]:
    need(bool(needs_who(t)) == exp, 'mirror needsWho(%r) expected %r' % (t, exp))
if bad:
    print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS roundabout contract (static wiring + mirror; Kotlin / JUnit NOT RUN)')
