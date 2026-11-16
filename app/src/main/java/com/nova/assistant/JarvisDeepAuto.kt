package com.nova.assistant

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** v7.171: JARVIS deep voice model (20 MB, SHA-256 checked) downloads once by itself on Wi-Fi when the voice is ON and not installed yet. Failure just means "try at the next start". */
object JarvisDeepAuto {
    private val started = AtomicBoolean(false)

    fun start(app: Context, stopped: () -> Boolean) {
        try {
            if (!ExtCfg.jarvisDeep(app) || JarvisDeepVoice.ready(app)) return
            if (!AutopilotMedia.onWifi(app)) return
            if (!started.compareAndSet(false, true)) return
            Thread({
                try {
                    val err = JarvisDeepVoice.install(app, { }, stopped)
                    ErrLog.add("JarvisDeepAuto", if (err == null) "deep voice installed" else "deep voice download failed: " + err.take(80))
                } catch (e: Throwable) {
                    ErrLog.add("JarvisDeepAuto", "ruk gaya: " + e.javaClass.simpleName)
                } finally { started.set(false) }
            }, "jarvis-deep-auto").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        } catch (e: Throwable) { ErrLog.add("JarvisDeepAuto", "start: $e") }
    }
}
