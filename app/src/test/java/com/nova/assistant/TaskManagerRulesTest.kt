package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskManagerRulesTest {
    @Test fun registryIsClean() {
        val ids = TaskManagerRules.ENTRIES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (e in TaskManagerRules.ENTRIES) {
            assertTrue(e.id, e.name.isNotBlank() && e.does.isNotBlank() && e.whenUse.isNotBlank() && e.keywords.isNotEmpty())
            if (e.example.isNotBlank()) assertTrue(e.id, e.example.startsWith("jarvis "))
        }
    }

    @Test fun catalogMentionsEveryToolWithExample() {
        val c = TaskManagerRules.catalogForAi()
        for (e in TaskManagerRules.ENTRIES) if (e.example.isNotBlank()) assertTrue(e.id, c.contains(e.id + " | ") && c.contains(e.example))
        assertTrue(c.contains("denied"))
    }

    @Test fun planIsParsedAndPrefixed() {
        val raw = "{\"mode\":\"agent\",\"understood\":\"leads\",\"agent\":\"leads\",\"say\":\"lucknow me school ke leads dhoondo\"}"
        val p = TaskManagerRules.parsePlan(raw, "mujhe lucknow ke schools ke customers chahiye")!!
        assertEquals("leads", p.agentId); assertFalse(p.denied)
        assertEquals("jarvis lucknow me school ke leads dhoondo", p.say)
    }

    @Test fun noneUnknownAndGenericGiveNull() {
        assertNull(TaskManagerRules.parsePlan("{\"agent\":\"none\",\"say\":\"\"}", "kuch bhi"))
        assertNull(TaskManagerRules.parsePlan("{\"agent\":\"zzz\",\"say\":\"jarvis x\"}", "kuch bhi"))
        assertNull(TaskManagerRules.parsePlan("{\"agent\":\"agent\",\"say\":\"jarvis x\"}", "kuch bhi"))
        assertNull(TaskManagerRules.parsePlan("{\"agent\":\"leads\",\"say\":\"\"}", "kuch bhi"))
        assertNull(TaskManagerRules.parsePlan(null, "kuch bhi"))
        assertNull(TaskManagerRules.parsePlan("not json", "kuch bhi"))
    }

    @Test fun paymentOtpPasswordAreDenied() {
        assertTrue(TaskManagerRules.isDenied("mera otp bata do"))
        assertTrue(TaskManagerRules.isDenied("bank transfer kar do"))
        assertFalse(TaskManagerRules.isDenied("orders dikhao"))
        assertTrue(TaskManagerRules.parsePlan("{\"agent\":\"denied\"}", "kuch bhi")!!.denied)
        assertTrue(TaskManagerRules.parsePlan("{\"agent\":\"leads\",\"say\":\"jarvis password bhejo\"}", "kuch bhi")!!.denied)
        assertTrue(TaskManagerRules.parsePlan("{\"agent\":\"leads\",\"say\":\"jarvis x\"}", "mera cvv likh do")!!.denied)
    }

    @Test fun directoryQuestion() {
        assertTrue(TaskManagerRules.isDirectoryQuestion("jarvis tum kya kya kar sakte ho"))
        assertTrue(TaskManagerRules.isDirectoryQuestion("what can you do"))
        assertFalse(TaskManagerRules.isDirectoryQuestion("delhi me restaurant ke leads dhoondo aur batao tum kya kya kar sakte ho is baare me"))
        val t = TaskManagerRules.directoryText(false)
        assertTrue(t.contains("Leads dhoondhna") && t.contains("Laptop") && t.contains("Business"))
        assertNotNull(TaskManagerRules.byId("site"))
    }
}
