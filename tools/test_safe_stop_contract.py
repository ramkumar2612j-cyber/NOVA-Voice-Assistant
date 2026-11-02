#!/usr/bin/env python3
"""v7.39 Part 4 SAFE STOP: static wiring checks. Kotlin is NOT compiled here (see tools/local_compile_check.sh, reported separately); no phone test."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'); J = os.path.join(R, 'app/src/main/java/com/nova/assistant')
rd = lambda n: open(os.path.join(J, n), encoding='utf-8').read()
ns, ss, ec, mh = rd('NovaService.kt'), rd('SafeStop.kt'), rd('ExtCfg.kt'), rd('MainActivity.kt')
bad = []
def need(c, m):
    if not c: bad.append(m)
for n, t in (('SafeStop', ss),):   # NovaService / ExtCfg hold braces inside strings: the real compile is the structure check
    need(t.count('{') == t.count('}') and t.count('(') == t.count(')'), n + ' braces / parens unbalanced')
need('object SafeStop' in ss and 'android.' not in ss and 'import ' not in ss, 'SafeStop must be pure')
need('fun stopPowersOff(ctx: Context): Boolean' in ec and 'getBoolean(STOP_OFF, false)' in ec, 'stopPowersOff must default to false')
m = re.search(r'it\.onStop = \{\n(.*?)\n            \}\n', ns, re.S); body = m.group(1) if m else ''
need('SafeStop.hudStop(ExtCfg.stopPowersOff(this)) == SafeStop.HudStop.SOFT) { softStop() } else {' in body, 'HUD STOP is not routed through softStop')
need(body.index('softStop()') < body.index('stopSelf()'), 'softStop must come before the old power-off code')
need(body.rstrip().endswith('}   // v7.39 PART 4 end of the power-off branch'), 'power-off branch not closed')
need('fun softStop()' in ns, 'softStop missing')
sb = ns[ns.index('fun softStop()'):ns.index('fun stopSpeaking()')] if 'fun softStop()' in ns else ''
need('stopSelf' not in sb and 'cfg.active = false' not in sb and 'stopService' not in sb, 'softStop must never power NOVA off')
need('dropHeard = true' in sb and 'followWindow = false' in sb and 'answerWindow = false' in sb and 'Logic.Cmd("stop")' in sb and 'stopSpeaking()' in sb, 'softStop incomplete')
need(ns.count('if (dropHeard) {') == 1 and 'SafeStop.dropsHeard(capturing, cmdText.isNotEmpty())' in ns, 'mic loop drop block missing')
dp = ns[ns.index('if (dropHeard) {'):]; dp = dp[:dp.index('lastCaptureEnd = System.currentTimeMillis()') + 80]
for k in ('capturing = false', 'cmdText.setLength(0)', 'pcm = ByteArrayOutputStream()', 'heard = false'):
    need(k in dp, 'drop block lacks ' + k)
need(ns.index('if (dropHeard) {') > ns.index('lastAudioAt = System.currentTimeMillis()') and ns.index('if (dropHeard) {') < ns.index('if (busy || ttsActive) {'), 'drop block must sit right after the audio read and before the busy / tts check')
need('followWindow = false; answerWindow = false   // v7.39 PART 4: STOP also cancels' in ns, 'spoken stop must close the follow window')
# the explicit power button is untouched and is the ONLY normal way to switch NOVA off
need('putBoolean("user_stopped", true)' in mh and 'fun toggle(): String' in mh, 'ACTIVATE / deactivate button changed')
need(os.path.exists(os.path.join(R, 'app/src/test/java/com/nova/assistant/SafeStopTest.kt')), 'SafeStopTest.kt missing')
if bad:
    print('FAIL safe stop contract'); [print(' -', x) for x in bad]; sys.exit(1)
print('PASS safe stop contract (static only)')
