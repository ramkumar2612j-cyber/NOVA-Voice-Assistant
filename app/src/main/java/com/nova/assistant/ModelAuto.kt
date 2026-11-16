package com.nova.assistant

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline Qwen brain (491 MB): downloaded automatically after app/service startup when the device has enough RAM.
 * The file is resumable, size+SHA-256 verified, and is never activated unless the real local engine loads it.
 * (The old voice-check model download was removed in v6.51 together with speaker recognition.)
 * Evidence level: written + statically checked. Not compiled here, not phone tested.
 */
object ModelAuto {
    private val qwenRunning = AtomicBoolean(false)

    /** Silent auto-install of the verified offline Qwen model. */
    fun silentOfflineQwen(ctx: Context): String? {
        val app = ctx.applicationContext
        if (UpdateManager.activeModelPath(app).isNotEmpty()) return null
        if (!ModelCatalog.ramOk(totalRamMb(app))) return "ram-low"
        if (!DeviceTier.allowsOfflineBrain(DeviceTier.of(totalRamMb(app), Runtime.getRuntime().availableProcessors(), android.os.Build.VERSION.SDK_INT))) return "ram-low"   // v6.56: weak phone keeps speed, no 491 MB offline model
        if (LocalBrains.factory == null) return "engine-unavailable"
        if (!qwenRunning.compareAndSet(false, true)) return "busy"
        return try {
            UpdateManager.offerOfflineBrain(app) ?: run {
                val latch = java.util.concurrent.CountDownLatch(1)
                var ok = false
                var msg = "install failed"
                UpdateManager.install(app, ModelCatalog.ID) { success, text ->
                    ok = success
                    msg = text
                    latch.countDown()
                }
                latch.await(90, java.util.concurrent.TimeUnit.MINUTES)
                if (ok) null else msg.ifBlank { "install timeout" }
            }
        } finally {
            qwenRunning.set(false)
        }
    }

    private fun totalRamMb(ctx: Context): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return 0L
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.totalMem / (1024L * 1024L)
    }
}
