package com.nova.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager

/** Pure rules: when may the automatic quiet self test run, and how is its result summarised. */
object AutoTestRules {
    const val MIN_GAP_MS = 60_000L

    /** Runs once per "state": first time keys + mic permission are ready, and again only when keys/permissions/switches change. */
    fun shouldRun(ready: Boolean, sig: String, lastSig: String, now: Long, lastAt: Long, running: Boolean): Boolean =
        ready && !running && sig != lastSig && (lastAt == 0L || now - lastAt >= MIN_GAP_MS)

    /** The hardware checks (torch, volume, mic, screen read) run once per installed app version, never again until the next update. */
    fun needsFull(doneVersion: String, nowVersion: String): Boolean = nowVersion.isNotBlank() && doneVersion != nowVersion

    fun summary(report: String): String {
        val m = Regex("SUMMARY pass=(\\d+) fail=(\\d+) warn=(\\d+)").find(report) ?: return "AUTO TEST: report nahi bana"
        val (p, f, w) = m.destructured
        return if (f == "0") "AUTO TEST: $p pass, koi FAIL nahi" + (if (w != "0") ", $w WARN (band features)" else "")
        else "AUTO TEST: $f FAIL, $p pass. Settings > TEST REPORT me dekho"
    }
}

/**
 * Automatic quiet self test. After the page has loaded and an AI key + microphone permission exist, NOVA tests itself without being asked,
 * again whenever keys / permissions / switches change. No popup, no sound, no torch, no screen jumps (SelfTest quiet mode).
 */
object AutoTest {
    @Volatile var lastReport: String = ""
    @Volatile private var lastSig = ""
    @Volatile private var lastAt = 0L
    @Volatile private var kicking = false
    @Volatile private var active = false   // Part 10: the 5-second watcher only runs while the NOVA screen is open

    /** Called from onPause: the watcher stops (no background polling). */
    fun stop() { active = false }

    private fun granted(a: Activity, p: String) = a.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun ready(a: Activity): Boolean = granted(a, Manifest.permission.RECORD_AUDIO)   // v6.97: the key-less browsers and offline AI need no key, so the test must not wait for one

    private fun signature(a: Activity): String {
        val sb = StringBuilder()
        sb.append(SecureStore.getKeys(a).size).append('|').append(SecureStore.hasGroq(a)).append(SecureStore.hasSlot(a, "openrouter")).append(SecureStore.hasSlot(a, "zai")).append('|')
        for (p in listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALENDAR, Manifest.permission.POST_NOTIFICATIONS)) sb.append(if (granted(a, p)) '1' else '0')
        sb.append('|').append(NovaService.running).append(NovaAccessibilityService.instance != null).append(DrivingBridge.listenerEnabled(a)).append(a.packageManager.canRequestPackageInstalls())
        sb.append('|').append(try { android.provider.Settings.System.canWrite(a) } catch (e: Exception) { false }).append(try { android.provider.Settings.canDrawOverlays(a) } catch (e: Exception) { false })
        sb.append(try { android.os.Build.VERSION.SDK_INT >= 23 && a.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true } catch (e: Exception) { false }).append(try { ActionLog.smartReplyOn(a) } catch (e: Exception) { false })
        sb.append('|').append(UpdateManager.appVersion(a))
        return sb.toString()
    }

    /** Called from onResume. Waits for the page and service to settle, then tests at +5 s and again at +25 s (second run only if state changed). */
    fun kick(act: Activity, show: (String, String) -> Unit) {
        if (kicking) { active = true; return }
        kicking = true
        active = true
        Thread {
            try {
                var round = 0
                while (true) {
                    // first test at +5 s, second at +20 s, then the watcher looks every 5 s and re-tests only when a permission, key or switch changed
                    Thread.sleep(if (round == 1) 20_000L else 5_000L)
                    round++
                    if (act.isDestroyed || act.isFinishing || !active) return@Thread
                    val ok = ready(act)
                    val sig = signature(act)
                    val now = System.currentTimeMillis()
                    if (AutoTestRules.shouldRun(ok, sig, lastSig, now, lastAt, SelfTest.running)) {
                        lastSig = sig
                        lastAt = now
                        // v6.97: no manual test any more. The FIRST run after an install / update also does the torch, volume, mic and screen-read checks by itself (full);
                        // later runs (a permission or key changed) stay quiet and read-only.
                        // v7.100: the automatic run is ALWAYS quiet and read-only. The old "full" run (torch flash, volume change, mic grab, Android Settings opened,
                        // scroll, BACK, NOVA pulled to the front) woke the phone by itself, showed popups, stole the mic from the wake word and produced the false FAILs.
                        // Those hardware checks now run ONLY when the owner presses POORI JAANCH ABHI in Settings.
                        SelfTest.start(act, { }, { rep ->
                            lastReport = rep
                            show(AutoTestRules.summary(rep), rep)
                        }, true, false)
                        // wait for it to finish before a second pass could start
                        var guard = 0
                        while (SelfTest.running && guard++ < 120) Thread.sleep(1000)
                    }
                }
            } catch (ignored: Exception) {
            } finally {
                kicking = false
            }
        }.start()
    }
}
