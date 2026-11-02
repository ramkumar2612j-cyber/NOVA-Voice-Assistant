package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SpecContractTest {
    @Test fun threeDBlogMustNotBecomeShop() {
        val s = SpecContract.analyze("nova ek 3D blogging website banao")
        assertEquals("blog", s.kind)
        assertTrue(s.style.contains("3D"))
        assertTrue(s.mustNot.any { it.contains("shop") || it.contains("cart") })
        val wrong = "<!DOCTYPE html><html><head><style>.card{perspective:900px}</style></head><body><h1>Shop</h1><p>Product price</p><button>Add to cart</button><button>Checkout</button></body></html>"
        val problems = SpecContract.check(s, wrong)
        assertTrue(problems.any { it.contains("blog") })
        assertTrue(problems.any { it.contains("shop/cart") })
    }

    @Test fun shopIsExpectedOnlyWhenAsked() {
        assertEquals("shop", SpecContract.analyze("online kapde ki dukaan ki website banao").kind)
        val gym = SpecContract.analyze("gym ki website banao")
        assertNotEquals("shop", gym.kind)
        assertTrue(gym.mustNot.any { it.contains("cart") || it.contains("ecommerce") })
    }

    @Test fun preserveExactColorsQuantitiesAndOriginalRequest() {
        val raw = "Build a dark 3D blog with 6 posts, accent #12abef, in Hindi; no checkout."
        val s = SpecContract.analyze(raw)
        assertTrue(s.exactDetails.contains("6 posts"))
        assertTrue(s.exactDetails.contains("#12abef"))
        val p = SpecContract.promptBlock(s, raw)
        assertTrue(p.contains(raw))
        assertTrue(p.contains("MUST NOT"))
    }

    @Test fun verifierNeverTreatsMalformedOrGappedResponseAsSuccess() {
        assertNull(AgentRules.parseVerification("not json"))
        val missing = AgentRules.parseVerification("{\"satisfied\":true,\"gaps\":[\"cart present\"],\"reason\":\"bad result\"}")!!
        assertFalse(missing.satisfied)
        val good = AgentRules.parseVerification("{\"satisfied\":true,\"gaps\":[],\"reason\":\"blog posts and 3D seen\"}")!!
        assertTrue(good.satisfied)
    }

    @Test fun plannerContractRoundTripsMustMustNotAndResearchQuery() {
        val raw = """{"goal":"Build a niche research dashboard","app":"Chrome","prompt":"Build a research dashboard","result":"dashboard is visible","must":["cite sources","include 3 charts"],"mustnot":["fake statistics"],"confidence":"high","question":"","need_research":"current phytoremediation benchmarks"}"""
        val note = AgentRules.planNote(raw)
        assertTrue(note.contains("must: cite sources; include 3 charts"))
        assertTrue(note.contains("mustnot: fake statistics"))
        assertTrue(note.contains("need-research: current phytoremediation benchmarks"))
        assertTrue(AgentRules.planUser("a".repeat(1000)).length > 400)
    }

    @Test fun requirementsAcrossWebsiteKinds() {
        val cases = listOf(
            "portfolio website banao" to "portfolio",
            "restaurant ki website banao" to "restaurant",
            "app download page banao" to "app-download",
            "admin analytics dashboard banao" to "dashboard",
            "minimal landing page banao" to "landing",
            "interactive calculator web app banao" to "app"
        )
        cases.forEach { (request, kind) -> assertEquals(request, kind, SpecContract.analyze(request).kind) }
    }
}
