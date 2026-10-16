"""v6.98 social reply contract (static): insult = no reply, wish / nice = fixed thanks, 2 per person per hour, wiring before the gate."""
import os, sys
H = os.path.dirname(os.path.abspath(__file__))
def rd(f): return open(os.path.join(H, '..', 'app/src/main/java/com/nova/assistant', f), encoding='utf-8').read()
SR, AR, NN, NS = rd('SocialReplyRules.kt'), rd('AutoReplyRules.kt'), rd('NovaNotificationService.kt'), rd('NovaService.kt')
bad = []
if 'SENDER_PER_HOUR = 2' not in AR: bad.append('per person per hour must be 2')
if 'Kind.INSULT -> Decision(Kind.INSULT, null,' not in SR: bad.append('insult decision must carry NO reply')
if 'THANKS_WISH' not in SR or 'THANKS_NORMAL' not in SR: bad.append('fixed thank-you texts missing')
i = NN.find('SocialReplyRules.decide'); j = NN.find('AutoReplyRules.gate.allow')
if not (0 < i < j): bad.append('insult check must run before the rate gate and the send')
if 'soc?.reply ?: ActionLog.autoReplyText' not in NN: bad.append('reply text must come from SocialReplyRules first')
if 'fun onSocialInsult' not in NS: bad.append('owner is not told about insults')
if 'sendReply' in SR: bad.append('rules file must stay pure')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS social reply contract')
