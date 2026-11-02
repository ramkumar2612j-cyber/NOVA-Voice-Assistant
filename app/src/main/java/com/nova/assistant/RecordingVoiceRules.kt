package com.nova.assistant

/** Part 6C: narrowly scoped voice intents for reading the recordings catalogue; never deletes or shares by voice. */
object RecordingVoiceRules {
    enum class Action { COUNT, PLAY_LAST }
    data class Request(val action: Action)

    fun parse(text: String): Request? {
        val t = Logic.norm(text).split(" ").filter { it.isNotBlank() }
        if (t.isEmpty() || t.size > 12) return null
        val s = t.toSet()
        if (s.any { it in setOf("delete", "remove", "share", "send", "bhejo", "hatao", "mitao", "band", "stop", "start", "on", "off") }) return null
        val recording = s.any { it in setOf("recording", "recordings", "record", "rikording") }
        if (!recording) return null
        val count = (s.any { it in setOf("kitni", "kitne", "how", "many", "count", "number") } && s.any { it in setOf("hai", "hain", "are", "have", "total") })
        if (count) return Request(Action.COUNT)
        val last = s.any { it in setOf("last", "latest", "aakhri", "pichhli", "recent") }
        val play = s.any { it in setOf("sunao", "sunao", "play", "chalao", "bajao", "listen", "open", "kholo") }
        if (last && play) return Request(Action.PLAY_LAST)
        return null
    }
}
