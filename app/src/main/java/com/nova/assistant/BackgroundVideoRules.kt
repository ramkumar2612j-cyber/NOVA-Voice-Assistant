package com.nova.assistant

/** Pure routing rules for Part 6B. Only explicit background-video recording phrases match;
 * camera-launch/video-camera requests stay on the existing Part 1 camera path. */
object BackgroundVideoRules {
    enum class Action { START, STOP, STATUS }
    data class Request(val action: Action, val facing: String = "back")

    private val VIDEO = setOf("video", "vedio", "videos")
    private val RECORD = setOf("record", "recording", "recordings", "record", "rikording", "rikord")
    private val START = setOf("on", "start", "shuru", "chalu", "chalao", "enable", "begin")
    private val STOP = setOf("stop", "band", "bandh", "off", "rok", "roko", "rokdo", "khatam", "end", "finish")
    private val BACKGROUND = setOf("background", "backgroundme", "backgroundmein", "pichhe", "screenoff", "mobileoff")
    private val CAMERA_LAUNCH = setOf("kholo", "khol", "open", "launch", "camera", "selfie")
    private val QUESTIONS = setOf("kya", "kaise", "how", "what", "why", "kyun", "kyu", "where", "kahan")
    private val NEG = setOf("mat", "nahi", "nahin", "nhi", "dont", "never", "not")

    fun tokens(text: String): List<String> = text.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else ' ' }
        .joinToString("").split(Regex("\\s+")).filter { it.isNotBlank() }

    fun parse(text: String): Request? {
        val all = tokens(text)
        if (all.isEmpty() || all.size > 30) return null
        val s = all.toSet()
        val status = s.contains("status") || (s.contains("chal") && s.contains("rahi") && (s.contains("kya") || s.contains("hai")))
        // Questions are rejected unless they are an explicit recording-status query.
        if (s.any { it in NEG } || (s.any { it in QUESTIONS } && !status)) return null
        if (status) {
            val backStatus = s.any { it in BACKGROUND } || (s.contains("off") && (s.contains("mobile") || s.contains("phone")))
            if (backStatus && s.any { it in VIDEO } && s.any { it in RECORD }) {
                val face = if ("front" in s || "selfie" in s || "aage" in s) "front" else "back"
                return Request(Action.STATUS, face)
            }
        }
        // Critical distinction: existing Part 1 owns "camera kholo ... video on karo".
        if (s.any { it in CAMERA_LAUNCH } && s.any { it in setOf("kholo", "khol", "open", "launch") } && !s.contains("background")) return null
        val back = s.any { it in BACKGROUND } || all.contains("off") && (all.contains("mobile") || all.contains("phone"))
        if (!back || s.none { it in VIDEO } || s.none { it in RECORD }) return null
        val stop = s.any { it in STOP }
        val strongStart = s.any { it in START }
        if (stop && strongStart) return null
        val start = strongStart || (!stop && s.any { it in setOf("karo", "kar", "kardo", "kijiye", "kariye") })
        val facing = when {
            "front" in s || "selfie" in s || "aage" in s -> "front"
            else -> "back"
        }
        return when {
            status -> Request(Action.STATUS, facing)
            stop -> Request(Action.STOP, facing)
            start -> Request(Action.START, facing)
            else -> null
        }
    }
}
