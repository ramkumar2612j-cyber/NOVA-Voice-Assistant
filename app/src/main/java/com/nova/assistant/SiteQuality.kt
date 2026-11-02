package com.nova.assistant

/**
 * Part 8: the PREMIUM GATE for a freshly written website (pure Kotlin, no Android classes, unit-testable).
 * Pass 1 of SiteGen writes the structure and content; this gate then reads the finished HTML and lists real, checkable gaps
 * (cut-off file, no viewport, no responsive CSS, lorem ipsum, images without alt, dead "#" links, no hover / focus states, ...).
 * If the list is not empty SiteGen runs ONE polish pass (pass 2) with exactly these gaps, and keeps the polished page only when it is
 * better (fewer gaps and no more requirement problems). Nothing here removes or disables anything; it only reads text.
 */
object SiteQuality {
    /** Pages shorter than this are a stub, not a site. */
    const val MIN_LEN = 3500

    private fun count(html: String, rx: Regex): Int = rx.findAll(html).count()

    private val VIEWPORT = Regex("<meta[^>]+name\\s*=\\s*[\"']viewport[\"']", RegexOption.IGNORE_CASE)
    private val TITLE = Regex("<title[^>]*>\\s*[^<\\s][^<]*</title>", RegexOption.IGNORE_CASE)
    private val LANG = Regex("<html[^>]+\\blang\\s*=", RegexOption.IGNORE_CASE)
    private val H1 = Regex("<h1[\\s>]", RegexOption.IGNORE_CASE)
    private val LANDMARK = Regex("<(header|nav|main|section|article|footer|aside)[\\s>]", RegexOption.IGNORE_CASE)
    private val IMG = Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ALT = Regex("\\balt\\s*=", RegexOption.IGNORE_CASE)
    private val EXT_IMG = Regex("<img\\b[^>]*\\bsrc\\s*=\\s*[\"']https?://", RegexOption.IGNORE_CASE)
    private val DEAD_LINK = Regex("href\\s*=\\s*[\"']#[\"']", RegexOption.IGNORE_CASE)
    private val LOREM = Regex("lorem ipsum|dolor sit amet|your (company|brand|text|title) (name )?here|placeholder text", RegexOption.IGNORE_CASE)
    private val RESPONSIVE = Regex("@media|auto-fit|auto-fill|clamp\\(|flex-wrap", RegexOption.IGNORE_CASE)
    private val CSS_VAR = Regex("var\\(\\s*--", RegexOption.IGNORE_CASE)
    private val FONT = Regex("font-family\\s*:", RegexOption.IGNORE_CASE)
    private val STATE = Regex(":hover|:focus|:active", RegexOption.IGNORE_CASE)
    private val MOTION = Regex("\\btransition\\s*:|@keyframes|\\banimation\\s*:|transition-", RegexOption.IGNORE_CASE)
    private val KEYFRAMES = Regex("@keyframes", RegexOption.IGNORE_CASE)
    private val REDUCED = Regex("prefers-reduced-motion", RegexOption.IGNORE_CASE)

    /** English gap lines (they go straight into the polish prompt). Empty list = the page passes the gate. */
    fun check(html: String): List<String> {
        val g = ArrayList<String>()
        if (html.length < MIN_LEN) g.add("the page is a stub (under $MIN_LEN characters): write the full page with every section's real content")
        if (count(html, Regex("<style[\\s>]", RegexOption.IGNORE_CASE)) != count(html, Regex("</style>", RegexOption.IGNORE_CASE)) ||
            count(html, Regex("<script[\\s>]", RegexOption.IGNORE_CASE)) != count(html, Regex("</script>", RegexOption.IGNORE_CASE))) {
            g.add("a <style> or <script> block is not closed (the file looks cut off): close every block")
        }
        if (!VIEWPORT.containsMatchIn(html)) g.add("missing <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        if (!TITLE.containsMatchIn(html)) g.add("missing a real <title>")
        if (!LANG.containsMatchIn(html)) g.add("<html> has no lang attribute")
        val h1 = count(html, H1)
        if (h1 == 0) g.add("no <h1> heading") else if (h1 > 2) g.add("too many <h1> headings (use one main heading, then h2/h3)")
        if (count(html, LANDMARK) < 3) g.add("weak structure: use header / main / section / footer landmarks (at least 3)")
        if (!RESPONSIVE.containsMatchIn(html)) g.add("not responsive: add @media rules or fluid grid/clamp() so it fits a 360px phone")
        if (count(html, CSS_VAR) < 3) g.add("no design system: define colours, spacing and fonts as CSS variables on :root and use them")
        if (!FONT.containsMatchIn(html)) g.add("no font-family set (use a chosen font with a system-font fallback)")
        if (!STATE.containsMatchIn(html) || !MOTION.containsMatchIn(html)) g.add("no hover/focus states or transitions: buttons and links need visible, smooth states")
        if (KEYFRAMES.containsMatchIn(html) && !REDUCED.containsMatchIn(html)) g.add("animations without @media (prefers-reduced-motion: reduce) fallback")
        if (LOREM.containsMatchIn(html)) g.add("placeholder text found (lorem ipsum / 'your company here'): replace with specific believable copy for this subject")
        val imgs = IMG.findAll(html).map { it.value }.toList()
        val noAlt = imgs.count { !ALT.containsMatchIn(it) }
        if (noAlt > 0) g.add("$noAlt image(s) without alt text")
        if (EXT_IMG.containsMatchIn(html)) g.add("remote http(s) image found: no external images allowed, use CSS/SVG art or the supplied media tokens")
        if (count(html, DEAD_LINK) > 2) g.add("dead links: several href=\"#\" with no target; point them to real section ids")
        return g
    }

    /** Pass 2 prompt: polish the page that already exists. Keeps all content, requirements and media tokens. */
    fun polishPrompt(subject: String, request: String, html: String, gaps: List<String>, specGaps: List<String>, mediaText: String): String = buildString {
        append("POLISH PASS. Below is a website that already has the right structure and content. Return ONE complete corrected HTML5 document only. ")
        append("Fix every QUALITY GAP listed, then raise the visual finish: consistent type scale, generous spacing, clear hierarchy, strong contrast, smooth hover/focus states, tidy mobile layout. ")
        append("Keep ALL existing sections, copy, names, features, scripts and the exact requested colours; do not remove anything and do not add anything the owner excluded. ")
        append("Tokens like __NOVA_IMG_1__ and __NOVA_VID_1__ are the owner's real media: keep them exactly as written.\n")
        append("QUALITY GAPS TO FIX:\n")
        gaps.forEach { append("- ").append(it).append('\n') }
        if (specGaps.isNotEmpty()) {
            append("REQUIREMENT GAPS (also fix):\n")
            specGaps.forEach { append("- ").append(it).append('\n') }
        }
        append("OWNER REQUEST: ").append(request.take(SiteGen.MAX_REQ)).append("\nTOPIC: ").append(subject.take(300)).append('\n')
        append(mediaText)
        append("\nCURRENT HTML:\n").append(html.take(SiteGen.MAX_HTML))
    }

    /** A candidate replaces the current page only when it is strictly better on the gate and not worse on the owner's requirements. */
    fun better(currentGaps: Int, currentSpec: Int, newGaps: Int, newSpec: Int): Boolean = newGaps < currentGaps && newSpec <= currentSpec
}
