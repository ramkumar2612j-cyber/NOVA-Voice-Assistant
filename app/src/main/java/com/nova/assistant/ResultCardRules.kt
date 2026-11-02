package com.nova.assistant

/**
 * v7.67 PART 15 - RESULT CARDS (pure Kotlin, no Android): a short card for every analysis report (headline, confidence, counts, up to 3 source sites),
 * one History line (text only), and the voice read-out of the FULL report on "poora padho". Same card idea as the movie scan card (14B-5).
 * Text only: no frame, no file path, no address, nothing about a person's face.
 */
object ResultCardRules {
    private fun t(en: Boolean, hi: String, e: String): String = if (en) e else hi

    /** First sentence of the summary, max [max] chars. */
    fun headline(summary: String, max: Int = 90): String {
        val s = AnalysisRules.clean(summary, 400).replace('\n', ' ').trim()
        if (s.isEmpty()) return ""
        var cut = s.length
        for (i in s.indices) if ((s[i] == '.' || s[i] == '!' || s[i] == '?') && i + 1 < s.length && s[i + 1] == ' ') { cut = i + 1; break }
        val first = s.substring(0, minOf(cut, s.length)).trim()
        return if (first.length <= max) first else first.substring(0, max - 1).trimEnd() + "\u2026"
    }

    /** The card: title line, confidence, verified / not-verified counts, up to 3 source domains (no full links). */
    fun card(r: AnalysisRules.Report, en: Boolean): String {
        val sb = StringBuilder("\uD83D\uDCCB ").append(headline(r.summary).ifEmpty { t(en, "Analysis taiyaar", "Analysis ready") })
        sb.append("\n").append(t(en, "Bharosa: ", "Confidence: ")).append(r.confidence)
        if (r.verified.isNotEmpty() || r.unverified.isNotEmpty())
            sb.append("\n").append(t(en, "Verify hua: ", "Verified: ")).append(r.verified.size).append(" | ").append(t(en, "Verify nahi: ", "Not verified: ")).append(r.unverified.size)
        val hosts = r.sources.map { AnalysisRules.hostOf(it.url) }.filter { it.isNotEmpty() }.distinct().take(3)
        if (hosts.isNotEmpty()) sb.append("\n").append("Sources: ").append(hosts.joinToString(", "))
        return sb.toString()
    }

    /** One History line: mode, confidence, counts. Never the summary text, never a path. */
    fun historyLine(r: AnalysisRules.Report): String =
        "analysis " + r.mode.take(12) + " | " + r.confidence.take(12) + " | v" + r.verified.size + " u" + r.unverified.size + " s" + r.sources.size

    // ------------------------------------------------------------------ "poora padho"

    private val READ_ALL = listOf("poora padho", "pura padho", "poori report padho", "puri report padho", "poora report padho", "poora sunao", "pura sunao", "puri report sunao",
        "poori report sunao", "full report padho", "read full report", "read the full report", "read it all", "read the whole report", "read whole report", "report padh do", "report padh ke sunao")

    /** True for "poora padho" / "read the full report" and its Hinglish variants (checked on the normalised sentence). */
    fun isReadAll(text: String?): Boolean {
        val n = AnalysisRules.norm(text)
        if (n.isEmpty() || n.length > 60) return false
        return READ_ALL.any { n.contains(it) }
    }

    /** Plain speakable text: markdown marks, "[1]" source numbers, bullets and web addresses removed. */
    fun speakable(full: String): String {
        var s = full.replace(Regex("https?://\\S+"), "").replace(Regex("\\[[0-9, ]+\\]"), "")
        s = s.replace(Regex("(?m)^\\s*[-*\u2022]\\s*"), "").replace(Regex("[*_#`>|]"), "")
        return s.replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{2,}"), "\n").trim()
    }

    /** Pieces of at most [max] chars (TTS engines refuse very long text), cut at a line end or sentence end. */
    fun chunks(text: String, max: Int = 700): List<String> {
        val s = speakable(text)
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var rest = s
        while (rest.length > max) {
            var cut = rest.lastIndexOf('\n', max)
            if (cut < max / 3) cut = rest.lastIndexOf(". ", max).let { if (it < 0) -1 else it + 1 }
            if (cut < max / 3) cut = rest.lastIndexOf(' ', max)
            if (cut < 1) cut = max
            out.add(rest.substring(0, cut).trim()); rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out.take(12)
    }

    fun nothingToReadText(en: Boolean): String = t(en, "Abhi padhne ke liye koi report nahi hai. Pehle koi analysis karwao.", "There is no report to read yet. Run an analysis first.")
    fun readingText(en: Boolean): String = t(en, "Poori report padh raha hoon. Rokne ke liye STOP bolo.", "Reading the full report. Say STOP to end it.")
}
