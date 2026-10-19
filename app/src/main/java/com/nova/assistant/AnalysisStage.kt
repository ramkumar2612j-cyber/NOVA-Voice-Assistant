package com.nova.assistant

/**
 * v7.51 PART 12 - one small event object for the analysis pipeline. Pure Kotlin. Part 12 (frame extraction) emits it,
 * Part 13 (vision / search / compose) and Part 14 (green scan overlay) share it. The scan progress must move ONLY on a real event of this kind.
 * [stage] is one of the STAGES names; [done] / [total] are real counts (frames saved / frames wanted), [message] is a short Hinglish line (never a path).
 */
data class AnalysisStage(val stage: String, val done: Int, val total: Int, val message: String) {
    companion object {
        const val INTAKE = "INTAKE"
        const val FRAMES = "FRAMES"
        const val VISION = "VISION"
        const val SEARCH = "SEARCH"
        const val COMPOSE = "COMPOSE"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
        val STAGES = listOf(INTAKE, FRAMES, VISION, SEARCH, COMPOSE, DONE, FAILED)
        fun isKnown(s: String?): Boolean = s != null && s in STAGES
    }

    /** Percent 0..100 from the real counts (never invented: total 0 = 0). */
    fun percent(): Int = if (total <= 0) 0 else (done.coerceIn(0, total) * 100) / total
    fun isTerminal(): Boolean = stage == DONE || stage == FAILED
    /** "frames 3/6" style line for the UI / scan. */
    fun label(): String = stage.lowercase() + " " + done + "/" + total
}
