package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.116 UI COMPLETION: the example lines that the new SKILLS entries / MADAD topics show must really reach the rule that handles them. */
class UiCompletion116Test {
    @Test fun newHelpTopicsAreFound() {
        assertEquals("reels", HelpGuide.topicFor("kya tum reel scroll kar sakte ho")?.id)
        assertEquals("familynum", HelpGuide.topicFor("kya tum family number save kar sakte ho")?.id)
        assertEquals("webjobs", HelpGuide.topicFor("kya kisi bhi website par kaam kar sakte ho")?.id)
        assertEquals("webjobs", HelpGuide.topicFor("kya tum doosra raasta try kar sakte ho")?.id)
        val ids = HelpGuide.TOPICS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun oldTopicsStillWinTheirOwnQuestions() {
        assertEquals("website", HelpGuide.topicFor("kya tum website bana sakte ho")?.id)
        assertEquals("batterysos", HelpGuide.topicFor("kya tum battery sos kar sakte ho")?.id)
        assertEquals("media", HelpGuide.topicFor("kya tum nova ko share karke bhej sakte ho")?.id)
    }

    @Test fun reelLinesInTheSkillsEntryParse() {
        assertNotNull(ReelRules.parse("nova agli reel"))
        assertNotNull(ReelRules.parse("nova pichli reel"))
        assertNotNull(ReelRules.parse("nova reel scroll karo"))
        val w = ReelRules.parse("nova rahul_sharma jaise hi post kare to like aur comment kar do")
        assertNotNull(w); assertTrue(w!!.watch); assertTrue(w.needsConfirm)
        assertTrue(ReelRules.isWatchOff("watch band karo"))
        val f = ReelRules.parse("nova rahul_sharma ki id pe jao aur uski 5 reels pe like aur comment karo nice")
        assertNotNull(f); assertEquals("rahul_sharma", f!!.friend)
    }

    @Test fun familyLineParses() {
        val r = FamilyContacts.parseSave("nova papa ka number 9876543210 save karo")
        assertNotNull(r); assertEquals("9876543210", r!!.number)
        assertNotNull(FamilyContacts.parseSave("nova rahul ka mobile number 9876543211 save karo"))
    }

    @Test fun websiteOperatorLineParses() {
        val t = WebOperatorRules.parse("amazon.in")
        assertNotNull(t); assertEquals("amazon.in", t!!.host); assertFalse(t.denied)
    }
}
