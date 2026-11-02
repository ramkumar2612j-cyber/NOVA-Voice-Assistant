package com.nova.assistant

import android.content.Context
import java.io.File

/**
 * v6.56: optional Porcupine wake-word engine (Picovoice). Much fewer false wakes than the Vosk word spotter.
 * It needs TWO things from the owner: an AccessKey (Picovoice Console) and a custom "nova" keyword file (.ppn, platform Android).
 * If either is missing, or anything fails (bad key, wrong .ppn version, no native library), [create] returns null and NovaService
 * keeps using the old Vosk wake recogniser: Porcupine can never leave NOVA deaf.
 * Licence note: Picovoice's free plan is for personal / non-commercial use; a paid product needs their commercial plan.
 * Evidence level: written, NOT compiled, NOT phone tested.
 */
class PorcupineWake private constructor(private val engine: ai.picovoice.porcupine.Porcupine) {
    private val frame = ShortArray(engine.frameLength)
    private var fill = 0

    /** Feeds one microphone chunk (16 kHz, 16 bit). True when the keyword was heard inside it. */
    fun feed(buf: ShortArray, n: Int): Boolean {
        var hit = false
        for (i in 0 until n) {
            frame[fill++] = buf[i]
            if (fill == frame.size) {
                fill = 0
                if (engine.process(frame) >= 0) hit = true
            }
        }
        return hit
    }

    fun reset() { fill = 0 }

    fun close() { try { engine.delete() } catch (t: Throwable) { } }

    companion object {
        const val SLOT = "porcupine"
        const val SENSITIVITY = 0.5f      // 0..1: higher hears more but also wakes falsely more often
        @Volatile var lastError: String = ""

        fun ppnFile(ctx: Context): File = File(File(ctx.filesDir, "porcupine"), "nova.ppn")
        fun hasKey(ctx: Context): Boolean = SecureStore.hasSlot(ctx, SLOT)
        fun hasModel(ctx: Context): Boolean = ppnFile(ctx).let { it.isFile && it.length() > 100 }
        fun configured(ctx: Context): Boolean = hasKey(ctx) && hasModel(ctx)

        /** Short Hinglish line for Settings. */
        fun status(ctx: Context): String = when {
            !hasKey(ctx) && !hasModel(ctx) -> "band (key aur nova.ppn dono chahiye)"
            !hasKey(ctx) -> "key baaki hai"
            !hasModel(ctx) -> "nova.ppn file baaki hai"
            lastError.isNotEmpty() -> "chal nahi paya ($lastError), purana wake chal raha hai"
            else -> "taiyaar (ACTIVATE dobara dabao)"
        }

        fun create(ctx: Context): PorcupineWake? {
            lastError = ""
            if (!configured(ctx)) return null
            return try {
                val p = ai.picovoice.porcupine.Porcupine.Builder()
                    .setAccessKey(SecureStore.getSlot(ctx, SLOT))
                    .setKeywordPath(ppnFile(ctx).absolutePath)
                    .setSensitivity(SENSITIVITY)
                    .build(ctx.applicationContext)
                PorcupineWake(p)
            } catch (t: Throwable) {
                lastError = t.javaClass.simpleName
                null
            }
        }
    }
}
