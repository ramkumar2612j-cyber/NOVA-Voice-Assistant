package com.nova.assistant

/**
 * v7.64 PART 14B-2 - the capture loop of the movie scan. Pure Kotlin: the Android parts come in through [Source], so JUnit can drive it.
 * Frames stay in memory (the returned Result); nothing is written to disk, nothing is sent anywhere here.
 */
object MovieScanCapture {
    interface Source {
        /** null = the screen service is not running; true = protected screen; false = fine to capture. */
        fun foregroundProtected(): Boolean?
        /** One small JPEG, or null when Android refused (secure window, error, timeout). */
        fun shot(): ByteArray?
        fun cancelled(): Boolean
        fun sleep(ms: Long)
        /** false on Android older than 11. */
        fun supported(): Boolean
    }

    class Result(val stop: MovieFrameRules.Stop, val frames: List<ByteArray>)

    /** Takes up to [count] frames, [MovieFrameRules.GAP_MS] apart. A protected screen at ANY point discards everything. */
    fun run(src: Source, count: Int, gapMs: Long = MovieFrameRules.GAP_MS): Result {
        if (!src.supported()) return Result(MovieFrameRules.Stop.OLD_ANDROID, emptyList())
        val n = MovieFrameRules.frameCount(count)
        val kept = ArrayList<ByteArray>()
        for (i in 0 until n) {
            if (src.cancelled()) return Result(MovieFrameRules.Stop.CANCELLED, emptyList())
            val prot = src.foregroundProtected() ?: return Result(MovieFrameRules.Stop.NO_SERVICE, emptyList())
            if (MovieFrameRules.gate(prot) != AnalysisRules.Gate.OK) return Result(MovieFrameRules.Stop.PROTECTED, emptyList())
            val f = src.shot()
            // re-check right after the shot: the screen may have changed to a protected one while the picture was taken
            if (f != null) {
                val after = src.foregroundProtected()
                if (after == null || MovieFrameRules.gate(after) != AnalysisRules.Gate.OK) return Result(MovieFrameRules.Stop.PROTECTED, emptyList())
                if (MovieFrameRules.usable(f) && !MovieFrameRules.isDuplicate(kept, f) && MovieFrameRules.fitsBudget(kept, f)) kept.add(f)
            }
            if (i < n - 1) src.sleep(gapMs)   // v7.81 PART 4: the quick scan passes a shorter gap; every other caller keeps GAP_MS
        }
        if (src.cancelled()) return Result(MovieFrameRules.Stop.CANCELLED, emptyList())
        return if (kept.isEmpty()) Result(MovieFrameRules.Stop.NO_FRAMES, emptyList()) else Result(MovieFrameRules.Stop.OK, kept)
    }
}
