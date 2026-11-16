package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class SiteFollowUpTest {
    @Test fun realOwnerSentenceIsAFollowUp() {
        val p = SiteIntent.parseFollowUp("Issko or professional bnao or app ka api ka setup ke baare ma bhi btao")
        assertNotNull(p)
        assertTrue(p!!.followUp)
        assertFalse(p.blocked)
        assertTrue(p.raw.contains("professional"))
    }

    @Test fun otherEnglishAndHinglishFollowUps() {
        assertNotNull(SiteIntent.parseFollowUp("make it more professional"))
        assertNotNull(SiteIntent.parseFollowUp("website ko dark mode ke saath behtar karo"))
    }

    @Test fun ordinaryCommandsAreNotFollowUps() {
        assertNull(SiteIntent.parseFollowUp("isko call karo"))
        assertNull(SiteIntent.parseFollowUp("website publish karo"))
        assertNull(SiteIntent.parseFollowUp("instagram kholo"))
        assertNull(SiteIntent.parseFollowUp("aaj ka mausam batao"))
        assertNull(SiteIntent.parseFollowUp("isko photo bhejo"))
    }

    @Test fun improvePromptCarriesRequestAndCurrentPage() {
        val html = "<!DOCTYPE html><html><body>hello</body></html>"
        val p = SiteGen.improvePrompt("salon", "isko professional banao", html)
        assertTrue(p.contains("isko professional banao"))
        assertTrue(p.contains(html))
    }
}
