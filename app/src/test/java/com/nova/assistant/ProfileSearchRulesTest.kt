package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSearchRulesTest {
    private fun p(s: String) = ProfileSearchRules.parse(s)

    @Test fun usernameOpensTheProfile() {
        val r = p("nova instagram par rahul_sharma search karo")!!
        assertEquals(ProfileSearchRules.Site.INSTAGRAM, r.site)
        assertEquals("rahul_sharma", r.query); assertTrue(r.direct); assertNull(r.refusal)
        assertEquals("https://www.instagram.com/rahul_sharma/", ProfileSearchRules.url(r))
        val t = p("twitter par @elonmusk dikhao")!!
        assertEquals("https://x.com/elonmusk", ProfileSearchRules.url(t))
        assertEquals("priya123", p("snapchat par priya123 search karo")!!.query)
        assertTrue(p("instagram par username rahul99 dhundo")!!.direct)
    }

    @Test fun nameOpensThePeopleSearch() {
        val f = p("facebook par rohit verma dhundo")!!
        assertEquals("rohit verma", f.query); assertTrue(!f.direct)
        assertEquals("https://www.facebook.com/search/people/?q=rohit+verma", ProfileSearchRules.url(f))
        val l = p("linkedin par amit shah profile dhundo")!!
        assertEquals("https://www.linkedin.com/search/results/people/?keywords=amit+shah", ProfileSearchRules.url(l))
        assertEquals(ProfileSearchRules.Site.X, p("x par virat kohli search karo")!!.site)
        assertTrue(!p("instagram par rahul search karo")!!.direct)   // a plain word is a name, not a username
    }

    @Test fun twoSitesGoThroughGoogle() {
        val r = p("rahul sharma ka instagram aur facebook account dhundo")!!
        assertEquals(2, r.sites.size)
        assertTrue(ProfileSearchRules.url(r).startsWith("https://www.google.com/search?q="))
        assertEquals(ProfileSearchRules.Site.WEB, p("rahul ka profile dhundo")!!.site)
    }

    @Test fun faceNumberPasswordAreRefused() {
        assertEquals("face", p("nova is photo ka instagram account dhundo")!!.refusal)
        assertEquals("face", p("selfie lo aur uska account search karo")!!.refusal)
        assertEquals("private", p("instagram par rahul ka number dhundo")!!.refusal)
        assertEquals("hack", p("instagram ka password dhundo")!!.refusal)
        assertEquals("noname", p("instagram par search karo")!!.refusal)
    }

    @Test fun otherFeaturesAreNotTaken() {
        for (s in listOf("instagram kholo", "youtube par gaana search karo", "instagram par reel search karo", "instagram par rahul ko message bhejo",
            "wifi ka password dhundo", "gaana search karo", "bank account search karo", "nova mere camera se dekho", "nova whatsapp par rahul ko search karo",
            "spotify par song search karo", "nova torch jala do", "nova ye kya chal raha hai")) {
            assertNull(s, p(s))
        }
    }

    @Test fun textsAreHonest() {
        assertTrue(ProfileSearchRules.refusalText("face", false).contains("privacy"))
        assertNotNull(ProfileSearchRules.noNameText(true))
        assertTrue(ProfileSearchRules.startedText(p("instagram par rahul_sharma search karo")!!, false).contains("@rahul_sharma"))
    }
}
