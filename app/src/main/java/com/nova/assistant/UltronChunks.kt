package com.nova.assistant

/**
 * v7.126: the Ultron voice used to turn the WHOLE reply into audio before the first word (up to 700 characters = several seconds of silence).
 * Now the reply is cut at sentence ends: the first piece is short, so NOVA starts talking quickly; the rest follows piece by piece.
 * Pure Kotlin (unit tested). Joining the pieces with a space gives back the same words.
 */
object UltronChunks {
    const val FIRST_MAX = 130
    const val NEXT_MAX = 260

    fun split(text: String): List<String> {
        val t = text.trim()
        if (t.length <= FIRST_MAX) return listOf(t)
        val sentences = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in t) {
            sb.append(ch)
            if (ch == '.' || ch == '?' || ch == '!' || ch == '\u0964' || ch == '\n') {
                val s = sb.toString().trim()
                if (s.isNotEmpty()) sentences.add(s)
                sb.setLength(0)
            }
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) sentences.add(tail)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (s0 in sentences) {
            var s = s0
            while (s.length > NEXT_MAX) {
                val cut = s.lastIndexOf(' ', NEXT_MAX)
                val at = if (cut > 40) cut else NEXT_MAX
                if (cur.isNotEmpty()) { out.add(cur.toString()); cur = StringBuilder() }
                out.add(s.substring(0, at).trim())
                s = s.substring(at).trim()
            }
            if (s.isEmpty()) continue
            val limit = if (out.isEmpty()) FIRST_MAX else NEXT_MAX
            if (cur.isEmpty()) cur.append(s)
            else if (cur.length + 1 + s.length <= limit) cur.append(' ').append(s)
            else { out.add(cur.toString()); cur = StringBuilder(s) }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return if (out.isEmpty()) listOf(t) else out
    }
}
