#!/usr/bin/env python3
"""v6.64 structural check (does NOT run Kotlin): chat replies are silent by default, speaking is optional, the speaker icon speaks one message."""
import os
here = os.path.dirname(os.path.abspath(__file__))
J = os.path.join(here, '..', 'app/src/main/java/com/nova/assistant')
rd = lambda p: open(os.path.join(J, p), encoding='utf-8').read()
ui = open(os.path.join(here, '..', 'app/src/main/assets/ui.html'), encoding='utf-8').read()
ns, ec, ma = rd('NovaService.kt'), rd('ExtCfg.kt'), rd('MainActivity.kt')
chk = [
    ('chat speak default OFF', 'getBoolean("chat_speak", false)' in ec and 'catch (e: Exception) { false }' in ec),
    ('typed() marks a chat turn', 'typedTurn = true' in ns),
    ('microphone turn clears chat flag', 'if (pcm.isNotEmpty()) typedTurn = false' in ns),
    ('finishTurn stays quiet for chat', 'val chatQuiet = typedTurn && !ExtCfg.chatSpeak(this)' in ns and '&& !chatQuiet' in ns),
    ('speaker click path', 'fun speakClick(' in ns and 'fun speakMsg(' in ma),
    ('click works without ACTIVATE (phone TTS)', 'private fun speakLocal(' in ma),
    ('UI speaker button + switch', 'Android.speakMsg' in ui and 'id="chat_speak"' in ui and 'Android.setChatSpeak' in ui),
    ('no voice-lock wording left in NovaService', 'voice lock' not in ns.lower()),
]
bad = [n for n, ok in chk if not ok]
print('FAIL: ' + ', '.join(bad) if bad else 'ALL PASSED (%d checks, structural only)' % len(chk))
raise SystemExit(1 if bad else 0)
