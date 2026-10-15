package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteQualityTest {
    private val good = """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Rose Cafe</title>
<style>:root{--bg:#fff;--ink:#222;--accent:#c33}body{font-family:Georgia,serif;background:var(--bg);color:var(--ink)}a{color:var(--accent);transition:color .2s}a:hover{color:#000}
@media (max-width:600px){nav{display:block}}@keyframes fade{from{opacity:0}to{opacity:1}}@media (prefers-reduced-motion: reduce){*{animation:none}}</style></head>
<body><header><nav><a href="#menu">Menu</a></nav></header><main><h1>Rose Cafe</h1><section id="menu"><h2>Menu</h2><img src="data:image/png;base64,AAAA" alt="Cake"><p>${"Fresh bakes every morning. ".repeat(140)}</p></section></main><footer>Visit us</footer><script>document.title='Rose Cafe'</script></body></html>"""

    @Test fun goodPagePassesTheGate() {
        assertEquals(emptyList<String>(), SiteQuality.check(good))
    }

    @Test fun stubAndCutOffPagesAreCaught() {
        val g = SiteQuality.check("<!DOCTYPE html><html><head><style>body{}</head><body><h1>x</h1></body></html>")
        assertTrue(g.any { it.contains("stub") })
        assertTrue(g.any { it.contains("not closed") })
        assertTrue(g.any { it.contains("viewport") })
        assertTrue(g.any { it.contains("lang") })
    }

    @Test fun placeholderAltExternalAndDeadLinksAreCaught() {
        val bad = good.replace("Fresh bakes", "Lorem ipsum dolor sit amet").replace(" alt=\"Cake\"", "")
            .replace("<footer>", "<img src=\"https://x.com/a.png\" alt=\"a\"><a href=\"#\">a</a><a href=\"#\">b</a><a href=\"#\">c</a><footer>")
        val g = SiteQuality.check(bad)
        assertTrue(g.any { it.contains("placeholder") })
        assertTrue(g.any { it.contains("without alt") })
        assertTrue(g.any { it.contains("remote http") })
        assertTrue(g.any { it.contains("dead links") })
    }

    @Test fun animationNeedsReducedMotionFallback() {
        val g = SiteQuality.check(good.replace("@media (prefers-reduced-motion: reduce){*{animation:none}}", ""))
        assertTrue(g.any { it.contains("prefers-reduced-motion") })
    }

    @Test fun polishPromptCarriesGapsRequestAndMedia() {
        val p = SiteQuality.polishPrompt("cafe", "red cafe site", good, listOf("missing viewport"), listOf("section Menu missing"), "MEDIA-LINE")
        assertTrue(p.contains("missing viewport") && p.contains("section Menu missing") && p.contains("red cafe site") && p.contains("MEDIA-LINE") && p.contains("__NOVA_IMG_1__"))
    }

    @Test fun polishedPageIsKeptOnlyWhenBetter() {
        assertTrue(SiteQuality.better(5, 1, 2, 1))
        assertTrue(!SiteQuality.better(5, 1, 5, 1))
        assertTrue(!SiteQuality.better(5, 1, 1, 2))   // fewer gaps but breaks an owner requirement: keep pass 1
    }
}
