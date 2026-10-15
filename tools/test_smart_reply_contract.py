#!/usr/bin/env python3
"""Part 7 contract: AI smart reply is wired end to end (rules, service call, listener hook, bridge, UI toggle) and OFF by default."""
import os, re, sys
here = os.path.dirname(os.path.abspath(__file__)); root = os.path.join(here, '..', 'app/src/main')
def rd(p): return open(os.path.join(root, p), encoding='utf-8').read()
J = 'java/com/nova/assistant/'
rules, svc, lst, al, ma, ui = rd(J+'SmartReplyRules.kt'), rd(J+'NovaService.kt'), rd(J+'NovaNotificationService.kt'), rd(J+'ActionLog.kt'), rd(J+'MainActivity.kt'), rd('assets/ui.html')
bad = []
def need(c, m):
    if not c: bad.append(m)
need('getBoolean(KEY_AR_SMART, false)' in al, 'smart reply must default OFF')
need('fun smartDraft(' in svc and 'fun onSmartReplied(' in svc, 'NovaService smartDraft/onSmartReplied missing')
need('SmartReplyRules.systemPrompt(' in svc and 'SmartReplyRules.unsafeReason(' in svc, 'service must use the safe prompt and the unsafe-text check')
need('Thread {' in lst and 'smartAutoReply' in lst, 'AI call must run on its own thread, not the notification main thread')
need('AutoReplyRules.skipReason(' in lst and 'm.hidden' in lst, 'old skip rules (groups, codes, no reply button) must stay')
need('GmailStore.notifyPhone(' in lst and 'ActionLog.add(' in lst, 'owner must be informed (notification + history)')
need('getSmartReply' in ma and 'setSmartReply' in ma and 'id="ar_smart"' in ui and 'Android.setSmartReply' in ui, 'bridge / UI toggle missing')
need('[OWNER]' in rules and '[SKIP]' in rules and 'NEVER invent or guess a fact' in rules and 'NEVER give or promise prices' not in rules, 'prompt must stop invented facts but allow money talk (Part 10)')
need("assistant of" in rules, 'AI must say it is the assistant, not pretend to be the owner')
# Part 8: business watch + owner name
need('private fun bizSmart(' in lst and 'bizSmart(pkg, key, title, body' in lst, 'Business Watch smart reply hook missing')
need('WaBiz.autoReply' in lst and 'd.item.draft.isNotBlank()' in lst, 'business smart reply must need watch AUTO ON and never replace the catalogue answer')
need('smartAutoReply(ctx, m, true)' in lst and 'business: Boolean' in svc and 'business: Boolean = false' in rules, 'business flag must reach the prompt')
need('do NOT know stock' in rules, 'business prompt must stop stock / size / rate claims')
need('fun cleanName(' in rules and 'KEY_AR_OWNER' in al and 'ActionLog.ownerName(this)' in svc, 'owner name must be cleaned, stored and sent to the AI')
need('getOwnerName' in ma and 'setOwnerName' in ma and 'id="ar_owner"' in ui and 'Android.setOwnerName' in ui, 'owner name bridge / UI missing')
need('SmartReplyRules.seen.fresh(' in lst, 'repeat notifications must be answered once')
# Part 8: voice on/off must always ask first
need('"smart_on" -> smartReplyVoice(true)' in svc and 'private fun smartReplyVoice(' in svc and 'private fun autoReplyVoice(' in svc, 'voice switches missing in NovaService')
need(svc.count('askFirst(q, on)') == 2, 'voice ON/OFF of smart reply and auto reply must go through askFirst (HAAN/NAHI)')
need('SmartReplyRules.command(n)' in rd(J+'Logic.kt'), 'Logic must call SmartReplyRules.command')
need('QUESTION_WORD' in rules and 'on == off' in rules, 'voice parser must ignore questions and unclear sentences')
# Part 10: owner facts + only secrets held back
need('fun addFact(' in rules and 'SmartReplyRules.convo.facts(sender)' in svc and 'SmartReplyRules.convo.addFact(name, message)' in svc, 'owner facts must reach the AI prompt')
need('MONEY' not in rules and 'SECRET' in rules, 'money/link/number blocking must be gone, secrets kept')
if bad:
    print('FAIL'); [print(' -', b) for b in bad]; sys.exit(1)
print('PASS smart reply contract')
