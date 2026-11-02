package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v7.4: roundabout commands reach the action model; plain questions do not; several actions in one answer. */
class PhoneActionUnderstandTest {
    @Test fun roundaboutCommandsAreLikelyActions() {
        assertTrue(PhoneActionRules.likelyAction("yaar andhera hai kuch kar na"))
        assertTrue(PhoneActionRules.likelyAction("bahut shor hai thoda dheere kar de"))
        assertTrue(PhoneActionRules.likelyAction("kal subah jaldi uthna hai yaad rakhna"))
        assertTrue(PhoneActionRules.likelyAction("kya tum torch jala sakte ho"))
        assertTrue(PhoneActionRules.likelyAction("mummy ko bol do main late aaunga"))
        assertTrue(PhoneActionRules.likelyAction("\u092e\u0947\u0930\u0940 \u0906\u0935\u093e\u091c\u093c \u0915\u092e \u0915\u0930 \u0926\u094b"))
    }

    @Test fun plainQuestionsAreNotActions() {
        assertFalse(PhoneActionRules.likelyAction("what is the meaning of life"))
        assertFalse(PhoneActionRules.likelyAction("kaun hai bharat ka pradhan mantri"))
        assertFalse(PhoneActionRules.likelyAction("explain recursion with an example"))
        assertFalse(PhoneActionRules.likelyAction(""))
    }

    @Test fun parseAllAcceptsOneOrUpToThreeActions() {
        val one = PhoneActionRules.parseAll("{\"tool\":\"torch\",\"args\":{\"on\":true}}")
        assertEquals(listOf("torch"), one.map { it.tool })
        val many = PhoneActionRules.parseAll("{\"actions\":[{\"tool\":\"torch\",\"args\":{\"on\":true}},{\"tool\":\"volume\",\"args\":{\"action\":\"down\"}}]}")
        assertEquals(listOf("torch", "volume"), many.map { it.tool })
    }

    @Test fun parseAllRejectsBadOrTooManyOrNone() {
        assertTrue(PhoneActionRules.parseAll("{\"tool\":\"none\",\"args\":{}}").isEmpty())
        assertTrue(PhoneActionRules.parseAll("{\"actions\":[]}").isEmpty())
        val four = "{\"tool\":\"torch\",\"args\":{\"on\":true}}"
        assertTrue(PhoneActionRules.parseAll("{\"actions\":[$four,$four,$four,$four]}").isEmpty())
        assertTrue(PhoneActionRules.parseAll("{\"actions\":[$four,{\"tool\":\"tap\",\"args\":{}}]}").isEmpty())
        assertTrue(PhoneActionRules.parseAll("not json").isEmpty())
    }

    @Test fun promptTeachesIndirectPhrasing() {
        for (en in listOf(true, false)) {
            val p = PhoneActionRules.systemPrompt(en)
            assertTrue(p.contains("andhera hai"))
            assertTrue(p.contains("actions"))
            assertFalse(p.lowercase().contains("tap("))
        }
    }
}
