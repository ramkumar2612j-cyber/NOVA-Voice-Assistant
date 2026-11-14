package com.nova.assistant

/**
 * v7.173 VIDEO STUDIO - pure rules (no Android imports, JUnit-able).
 *
 * The owner picks 2-8 videos and (optionally) types what he wants. NOVA measures how much each clip MOVES (frame
 * differences, done in VideoStudio), then this file decides the edit like a simple human editor would:
 *   - keeps the best part of every clip (the window with the most action) so the total fits the target length,
 *   - the single strongest moment of a clip with real action becomes SLOW-MOTION (muted, 0.5x),
 *   - calm parts get a slow PUSH-IN zoom, and every cut after the first lands with a PUNCH zoom that settles,
 *   - a light contrast lift is applied to everything.
 * Instructions it understands (Hinglish / English): reels or 9:16 / youtube or 16:9, "30 second", "slow motion nahi",
 * "zoom nahi", "best pehle". Everything else is ignored, never guessed. Exact "at second 5 do X" commands are NOT built yet.
 */
object VideoStudioRules {
    const val K_STUDIO_OPEN = "studio_open"

    const val MIN_CLIPS = 1
    const val MAX_CLIPS = 8
    const val DEFAULT_REEL_S = 45
    const val DEFAULT_WIDE_S = 60
    const val STEP_MS = 500L
    const val SLOW_SPEED = 0.5f
    const val SLOW_SRC_MS = 1500L          // 1.5 s of source -> 3 s on screen
    const val MIN_WINDOW_MS = 2000L
    const val LEAD_MS = 1500L
    const val MAX_VERTICAL_S = 90          // Instagram Reels limit; YouTube Shorts allows up to 3 min, so one 9:16 file fits both

    enum class Zoom { NONE, PUSH, PUNCH }

    data class Options(val vertical: Boolean = true, val targetS: Int = DEFAULT_REEL_S, val slow: Boolean = true,
                       val zoom: Boolean = true, val bestFirst: Boolean = false, val understood: List<String> = emptyList())

    /** One clip as measured: duration + motion score (0..1) for every STEP_MS from 0. */
    data class Clip(val index: Int, val durMs: Long, val motion: List<Float>, val stepMs: Long = STEP_MS)

    /** One piece of the final video. speed 1.0 or 0.5; audio only for speed 1.0. */
    data class Seg(val clip: Int, val startMs: Long, val endMs: Long, val speed: Float, val zoom: Zoom) {
        val srcMs: Long get() = endMs - startMs
        val outMs: Long get() = (srcMs / speed).toLong()
    }

    fun parseOptions(textRaw: String): Options {
        val t = textRaw.lowercase()
        val said = ArrayList<String>()
        val wide = Regex("(16\\s*:\\s*9|landscape|lamba|horizontal)").containsMatchIn(t)
        val vert = Regex("(reel|reels|9\\s*:\\s*16|shorts|short|vertical|khada|instagram|insta)").containsMatchIn(t)
        val ytOnly = Regex("(youtube|\\btv\\b)").containsMatchIn(t)
        val vertical = if (wide && !vert) false else if (vert) true else !ytOnly
        said.add(if (vertical) "9:16 (Reels + YouTube Shorts dono ke liye)" else "16:9 (YouTube)")
        var target = if (vertical) DEFAULT_REEL_S else DEFAULT_WIDE_S
        Regex("(\\d{1,3})\\s*(?:sec|second|seconds|sekand|s\\b)").find(t)?.let {
            val v = it.groupValues[1].toInt()
            if (v in 10..180) { target = if (vertical) minOf(v, MAX_VERTICAL_S) else v; said.add(if (target != v) "$target second (Reels ki limit)" else "$v second") }
        } ?: Regex("(\\d{1,2})\\s*(?:min|minute|minutes)").find(t)?.let {
            val v = it.groupValues[1].toInt() * 60
            if (v in 10..180) { target = if (vertical) minOf(v, MAX_VERTICAL_S) else v; said.add(if (target != v) "$target second (Reels ki limit)" else "${v / 60} minute") }
        }
        val slowOff = Regex("(slow\\s*(motion|mo|mow)?\\s*(nahi|nhi|mat|no|off|band)|no\\s*slow|bina\\s*slow)").containsMatchIn(t)
        val zoomOff = Regex("(zoom\\s*(nahi|nhi|mat|no|off|band)|no\\s*zoom|bina\\s*zoom)").containsMatchIn(t)
        if (slowOff) said.add("slow-mo nahi")
        if (zoomOff) said.add("zoom nahi")
        val best = Regex("(best\\s*(clip\\s*)?(pehle|first)|hook|sabse\\s*(achha|best|zyada\\s*action).{0,12}pehle)").containsMatchIn(t)
        if (best) said.add("best clip pehle")
        return Options(vertical, target, !slowOff, !zoomOff, best, said)
    }

    private fun mean(l: List<Float>, from: Int, to: Int): Float {
        if (to <= from) return 0f
        var s = 0f; for (i in from until to) s += l[i]; return s / (to - from)
    }

    /** The window of [len] ms with the most action (clip kept whole if it is shorter). Returns start..end ms. */
    fun bestWindow(c: Clip, lenMs: Long): Pair<Long, Long> {
        if (c.durMs <= lenMs + 400) return 0L to c.durMs
        val st0 = c.stepMs.coerceAtLeast(1L)
        val steps = (lenMs / st0).toInt().coerceAtLeast(1)
        if (c.motion.size <= steps) return 0L to minOf(c.durMs, lenMs)
        var bestI = 0; var bestV = -1f
        for (i in 0..(c.motion.size - steps)) {
            val v = mean(c.motion, i, i + steps)
            if (v > bestV + 0.0001f) { bestV = v; bestI = i }      // earlier window wins ties
        }
        // start a little BEFORE the action so the cut has a lead-in (and the peak is not glued to the first frame)
        val st = (bestI * st0 - minOf(bestI * st0, LEAD_MS)).coerceAtLeast(0L)
        return st to minOf(c.durMs, st + lenMs)
    }

    /** How "active" a clip is overall (for best-first and for deciding slow-mo). */
    fun activity(c: Clip): Float = if (c.motion.isEmpty()) 0f else c.motion.sum() / c.motion.size

    /** Builds the edit. Total on-screen length <= target (+ a little from slow-mo, which is counted). Never empty for valid input. */
    fun plan(clips: List<Clip>, o: Options): List<Seg> {
        val valid = clips.filter { it.durMs >= 800 }
        if (valid.isEmpty()) return emptyList()
        val ordered = if (o.bestFirst && valid.size > 1) {
            val top = valid.maxByOrNull { activity(it) }!!
            listOf(top) + valid.filter { it !== top }
        } else valid
        val targetMs = o.targetS * 1000L
        val per = (targetMs / ordered.size).coerceIn(3000L, 15000L)
        val out = ArrayList<Seg>()
        val maxAct = ordered.maxOf { activity(it) }.coerceAtLeast(0.0001f)
        for ((n, c) in ordered.withIndex()) {
            val (ws, we) = bestWindow(c, per)
            val len = we - ws
            if (len < 800) continue
            val first = n == 0
            val calm = activity(c) < maxAct * 0.6f
            // strongest moment inside the kept window, only when there is real action and room for 3 parts
            var slowAt = -1L
            if (o.slow && len >= 4500 && c.motion.isNotEmpty()) {
                val sm = c.stepMs.coerceAtLeast(1L)
                val i0 = (ws / sm).toInt().coerceIn(0, c.motion.size - 1)
                val i1 = ((we - SLOW_SRC_MS) / sm).toInt().coerceIn(i0, c.motion.size - 1)
                var pk = i0
                for (i in i0..i1) if (c.motion[i] > c.motion[pk]) pk = i
                val avg = mean(c.motion, i0, minOf(c.motion.size, i1 + 1))
                val peak = c.motion[pk]
                val at = pk * sm
                if (peak >= 0.12f && peak >= avg * 1.6f && at >= ws + 1200 && at + SLOW_SRC_MS <= we - 800) slowAt = at
            }
            val body = if (!o.zoom) Zoom.NONE else if (first) (if (calm) Zoom.PUSH else Zoom.NONE) else Zoom.PUNCH
            if (slowAt < 0) {
                out.add(Seg(c.index, ws, we, 1f, body))
            } else {
                out.add(Seg(c.index, ws, slowAt, 1f, body))
                out.add(Seg(c.index, slowAt, slowAt + SLOW_SRC_MS, SLOW_SPEED, if (o.zoom) Zoom.PUSH else Zoom.NONE))
                out.add(Seg(c.index, slowAt + SLOW_SRC_MS, we, 1f, Zoom.NONE))
            }
        }
        return out
    }

    fun totalOutMs(p: List<Seg>): Long = p.sumOf { it.outMs }

    /** Scale for a zoom animation at progress 0..1 of a segment. */
    fun zoomAt(z: Zoom, p: Float): Float {
        val x = p.coerceIn(0f, 1f)
        return when (z) {
            Zoom.NONE -> 1f
            Zoom.PUSH -> 1f + 0.12f * x            // slow push-in
            Zoom.PUNCH -> 1.18f - 0.18f * (1f - (1f - x) * (1f - x))   // starts tight (1.18), eases out to 1.0
        }
    }

    /** Frame difference 0..1 between two small luminance arrays (same length). */
    fun motionBetween(a: IntArray, b: IntArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var s = 0L
        for (i in a.indices) s += kotlin.math.abs(a[i] - b[i])
        return (s.toFloat() / a.size / 255f * 4f).coerceIn(0f, 1f)   // x4: ordinary hand-held change is small, make the scale usable
    }

    // ---------------------------------------------------------------- voice

    /** "nova video edit karo / video studio kholo / video banao" opens the screen (a voice command cannot pick files). */
    fun parse(text: String): Logic.Cmd? {
        if (text.isBlank() || text.length > 120) return null
        val low = text.lowercase().replace(Regex("\\s+"), " ").trim()
        if (Regex("\\bnova\\s+biz\\b").containsMatchIn(low)) return null
        val studio = Regex("video\\s*(studio|editor|edit|editing)").containsMatchIn(low) || Regex("(edit|editing)\\s*(my|meri|ye|yeh)?\\s*video").containsMatchIn(low)
        if (!studio) return null
        val act = Regex("(kar|karo|karna|kholo|khol|open|chalu|start|banao|bana|dikhao|shuru|chahiye|edit)").containsMatchIn(low)
        return if (act) Logic.Cmd(K_STUDIO_OPEN) else null
    }

    // ---------------------------------------------------------------- wording

    fun planSummary(p: List<Seg>, clipsUsed: Int, o: Options): String {
        val slow = p.count { it.speed < 1f }
        val zoom = p.count { it.zoom != Zoom.NONE }
        val sec = totalOutMs(p) / 1000
        return "$clipsUsed video, ${sec}s, " + (if (o.vertical) "9:16 (Reels + Shorts)" else "16:9") + ", slow-mo $slow jagah, zoom $zoom jagah"
    }

    fun doneText(sec: Long, en: Boolean = false): String =
        if (en) "Your edited video is ready, $sec seconds. It is saved in Movies/NOVA. Open it from the Video Studio screen."
        else "Aapki edit ki hui video taiyaar hai, $sec second ki. Movies/NOVA me save hai. Video Studio screen se kholo."

    fun failText(why: String, en: Boolean = false): String =
        if (en) "Video edit failed: $why" else "Video edit nahi ho payi: $why"
}
