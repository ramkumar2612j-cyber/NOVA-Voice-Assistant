package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SiteLuxuryTest {
    @Test fun noStyleNamedMeansRefinedPremiumDirection() {
        val names = (0L..30L).map { SiteDesign.pick("ek website banao", it).name }.toSet()
        assertTrue(names.all { it in setOf("Luxury noir gold", "Editorial warm paper", "Swiss minimal", "Corporate trust blue", "Clinical fresh light") })
        assertTrue(SiteDesign.pool("luxury salon website").containsAll(listOf(3, 0)))
    }

    @Test fun promptCarriesLuxuryAndPerformanceRules() {
        val p = SiteGen.userPrompt("salon", "luxury salon website")
        assertTrue(p.contains("LUXURY / PROFESSIONAL FINISH"))
        assertTrue(p.contains("PHONE PERFORMANCE"))
        assertTrue(p.contains("Google Fonts"))
    }

    @Test fun thinPageFailsPremiumGateButGoodPagePasses() {
        val spec = SpecContract.analyze("salon website")
        val thin = "<!DOCTYPE html><html><body>" + "<p>hello</p>".repeat(700) + "</body></html>"
        val problems = SpecContract.check(spec, thin)
        assertTrue(problems.any { it.contains("viewport") })
        assertTrue(problems.any { it.contains("sections") })
        val sec = "<section><svg></svg><div class=\"x\">text</div></section>".repeat(100)
        val good = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width\"><style>@media(max-width:600px){a{b:c}}.x{transition:all .3s;background:linear-gradient(#000,#111)}</style></head><body>" + sec + "<script>new IntersectionObserver(function(){})</script></body></html>"
        assertTrue(SpecContract.check(spec, good).none { it.contains("viewport") || it.contains("sections") || it.contains("scroll-reveal") })
    }

    @Test fun screenMonitorIsOffByDefaultInSource() {
        val src = java.io.File("src/main/java/com/nova/assistant/Cfg.kt").readText()
        assertTrue(src.contains("getBoolean(\"screenMonitor\", false)"))
    }
}
