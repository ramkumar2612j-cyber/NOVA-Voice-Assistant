package com.nova.assistant

/**
 * v7.51 PART 12 - FRAME RULES. Pure Kotlin: no Android classes, no file access, no network (JUnit-testable).
 * Decides HOW MANY frames of a video, at WHICH time points, how big (max side 768 px), whether a frame is a near-duplicate of the one before
 * (32x32 grayscale mean difference), and what a failure sentence says. [FrameExtractor] (Android) only does the decoding with these answers.
 * Defaults: 6 frames, max 12, JPEG quality 80, 60 second watchdog. Less free memory -> fewer frames (down to 0 = honest "memory kam").
 */
object FrameRules {
    const val DEFAULT_FRAMES = 6
    const val MAX_FRAMES = 12
    const val MAX_SIDE = 768
    const val JPEG_QUALITY = 80
    const val WATCHDOG_MS = 60_000L
    const val SIG_SIDE = 32
    /** Mean absolute grayscale difference (0..255) at or below this = "same-looking frame". */
    const val DUP_THRESHOLD = 4.0
    /** The last frame is taken slightly before the very end (some decoders fail exactly at the end). */
    const val END_MARGIN_MS = 500L
    /** Rough memory of one decoded frame while it is being worked on: max side squared, 4 bytes per pixel, doubled for the scaled copy. */
    const val FRAME_COST = MAX_SIDE.toLong() * MAX_SIDE * 4 * 2

    /** Number of frames to try. [requested] null / <1 = default; capped at 12; short clips get fewer (one per half second at most);
     *  [freeHeapBytes] limits it so that at most a quarter of the free heap is used. 0 = not enough memory (say so, do not try). */
    fun frameCount(requested: Int?, durationMs: Long, freeHeapBytes: Long): Int {
        var n = if (requested == null || requested < 1) DEFAULT_FRAMES else requested
        if (n > MAX_FRAMES) n = MAX_FRAMES
        if (durationMs in 1 until 1000L) n = 1
        else if (durationMs > 0) { val byLen = (durationMs / 500L).toInt().coerceAtLeast(1); if (n > byLen) n = byLen }
        val byMem = (freeHeapBytes / 4L / FRAME_COST).toInt()
        if (byMem < 1) return 0
        if (n > byMem) n = byMem
        return n
    }

    /** Time points in milliseconds: count 1 = middle; otherwise the first at 0, the last [END_MARGIN_MS] (or 10 percent for short clips) before the end,
     *  the rest equally spaced (an odd count therefore includes the middle). Unknown / zero duration = [0]. Always sorted, never negative, never past the end. */
    fun timePointsMs(durationMs: Long, count: Int): List<Long> {
        if (count < 1) return emptyList()
        if (durationMs <= 0L) return listOf(0L)
        if (count == 1) return listOf(durationMs / 2)
        val margin = minOf(END_MARGIN_MS, durationMs / 10)
        val last = (durationMs - margin).coerceAtLeast(0L)
        val out = ArrayList<Long>(count)
        for (i in 0 until count) out.add((last * i) / (count - 1))
        return out
    }

    /** Output size: longest side at most [maxSide], aspect kept, never enlarged, at least 1 px. */
    fun targetSize(w: Int, h: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        if (w < 1 || h < 1) return Pair(1, 1)
        val m = maxOf(w, h)
        if (m <= maxSide) return Pair(w, h)
        val k = maxSide.toDouble() / m
        return Pair(maxOf(1, Math.round(w * k).toInt()), maxOf(1, Math.round(h * k).toInt()))
    }

    /** ARGB int -> grayscale 0..255 (integer weights 299 / 587 / 114). */
    fun gray(argb: Int): Int {
        val r = (argb shr 16) and 0xFF; val g = (argb shr 8) and 0xFF; val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    /** Mean absolute difference of two 32x32 grayscale signatures. Different sizes = 255.0 (never "same"). */
    fun meanAbsDiff(a: IntArray, b: IntArray): Double {
        if (a.size != b.size || a.isEmpty()) return 255.0
        var s = 0L
        for (i in a.indices) s += Math.abs(a[i] - b[i])
        return s.toDouble() / a.size
    }

    fun isDuplicate(a: IntArray, b: IntArray): Boolean = meanAbsDiff(a, b) <= DUP_THRESHOLD

    /** True when [sig] looks like any frame already kept (compared with the last kept one only: neighbours, in time order). */
    fun isDuplicateOfLast(kept: List<IntArray>, sig: IntArray): Boolean = kept.isNotEmpty() && isDuplicate(kept.last(), sig)

    /** Only photos and videos give frames; a link or unknown has none. */
    fun supports(kind: MediaIntake.Kind): Boolean = kind == MediaIntake.Kind.IMAGE || kind == MediaIntake.Kind.VIDEO

    fun stageText(done: Int, total: Int): String = "frames " + done.coerceAtLeast(0) + "/" + total.coerceAtLeast(0)

    fun errorText(reason: String): String = "video se frame nahi nikle: " + reason.trim().take(80)
    const val ERR_MEMORY = "phone ki memory kam hai, kuch app band karke dobara try karo"
    const val ERR_TIMEOUT = "60 second me video nahi khuli (bahut bhari ya kharab file)"
    const val ERR_NO_FILE = "file nahi mili (24 ghante ho gaye ya hata di)"
    const val ERR_KIND = "link se frame nahi nikalte, gallery se video share karo"
    const val ERR_BUSY = "pehle ka kaam chal raha hai"
    const val ERR_NONE = "koi frame decode nahi hua (video kharab ya format samajh nahi aaya)"

    /** After dropping duplicates the owner still gets at least one frame whenever any decoded; this is the honest summary line. */
    fun summary(kept: Int, wanted: Int, dropped: Int): String =
        if (kept <= 0) "Koi frame nahi nikla."
        else "$kept frame nikle" + (if (dropped > 0) " ($dropped milte-julte hata diye)" else "") + (if (kept < wanted && dropped == 0) " (zyada nahi ban paye)" else "") + "."
}
