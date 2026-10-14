#!/usr/bin/env python3
"""v6.63 structural check (does NOT run Kotlin): Gemini listening is a fallback after Groq, before the offline Hindi pack, and its text is cleaned."""
import os
here = os.path.dirname(os.path.abspath(__file__))
rd = lambda p: open(os.path.join(here, '..', 'app/src/main/java/com/nova/assistant', p), encoding='utf-8').read()
ns, lg = rd('NovaService.kt'), rd('ListenGuard.kt')
g, ge, h = ns.find('geminiHear(pcm) })'), ns.find('hearTwice { groqHear(pcm) }'), ns.find('silentStrip(hindiHear(pcm))')
chk = [
    ('order groq -> gemini -> hindi pack', 0 < ge < g < h),
    ('gemini only when no local command', 'if (c == null && groqText.isBlank() && pcm.size >= MIN_HEAR_BYTES) {\n            groqText = silentStrip(hearTwice { geminiHear(pcm) })' in ns),
    ('gemini text is cleaned', 'ListenGuard.cleanCloudTranscript(candidateText(resp)' in ns),
    ('cleaner exists', 'fun cleanCloudTranscript(' in lg),
    ('gemini cannot approve a yes/no', 'never approve' in ns),
    ('no key in log', 'x-goog-api-key' not in lg),
    ('no !! and no catch (_', '!!' not in lg and 'catch (_' not in lg),
]
bad = [n for n, ok in chk if not ok]
print('FAIL: ' + ', '.join(bad) if bad else 'ALL PASSED (%d checks, structural only)' % len(chk))
raise SystemExit(1 if bad else 0)
