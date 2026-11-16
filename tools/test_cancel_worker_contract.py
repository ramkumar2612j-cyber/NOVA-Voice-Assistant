#!/usr/bin/env python3
"""v7.55 Part 13B-2b static contract: after cancel (ROKO / STOP) or the 120 s watchdog the stuck analysis worker is abandoned and a NEW single worker is made,
so a new analysis never queues behind an old network call; old results are still thrown away by the generation number; still ONE worker at a time, never the main thread.
Kotlin compile + JUnit not run here (owner order: only when Part 13 is complete)."""
import os, re, sys
R = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
P = open(os.path.join(R, 'app/src/main/java/com/nova/assistant/AnalysisPipeline.kt'), encoding='utf-8').read()
bad = []
if 'fun replaceWorker()' not in P or 'old.shutdownNow()' not in P or 'pool = newPool()' not in P: bad.append('replaceWorker missing')
if 'newSingleThreadExecutor' not in P: bad.append('worker is no longer a single thread')
cancel = P.split('fun cancel()')[1].split('private fun durationOf')[0]
if 'replaceWorker()' not in cancel: bad.append('cancel() does not replace the worker')
start = P.split('fun start(')[1].split('fun cancel()')[0]
wd = start.split('val wd = Runnable')[1].split('watchdog = wd')[0]
if 'replaceWorker()' not in wd: bad.append('watchdog does not replace the worker')
if 'pool.execute' not in start: bad.append('start() does not use the pool')
if 'gen.incrementAndGet()' not in cancel or 'private fun alive(g: Int)' not in P: bad.append('generation check gone (late results would be kept)')
if 'catch (x: InterruptedException) { return }' not in P: bad.append('interrupt is not handled in the frame wait')
if 'isDaemon = true' not in P: bad.append('worker is not a daemon')
if 'Looper.getMainLooper()' in P.split('pool.execute')[1].split('return "ok"')[0] : bad.append('main thread used inside the worker')
if bad: print('FAIL', *bad, sep='\n  '); sys.exit(1)
print('PASS cancel / watchdog worker contract (static; real phone NOT TESTED)')
