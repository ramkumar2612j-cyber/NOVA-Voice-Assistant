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

    @Test fun guideAnswersWhichAgentWhenAndHow() {
        assertTrue(TaskManagerRules.isGuideQuestion("jarvis kaun sa agent kab kaam aayega"))
        assertFalse(TaskManagerRules.isGuideQuestion("jarvis torch on karo"))
        val g = TaskManagerRules.guideText(false)
        for (e in TaskManagerRules.ENTRIES) assertTrue(e.id, g.contains(e.name))
    }

    @Test fun doneLineIsHonest() {
        assertTrue(TaskManagerRules.doneLine("Music", "gaana", "Gaana chala diya", false).startsWith("Kaam poora"))
        assertTrue(TaskManagerRules.doneLine("Music", "gaana", "FAILED: no app", false).startsWith("Poora nahi hua"))
        assertTrue(TaskManagerRules.doneLine("Music", "", "Ye abhi samajh nahi aaya", false).startsWith("Poora nahi hua"))
    }

    @Test fun newAgentsAreInRegistry() {
        for (id in listOf("wa", "gmail", "music")) assertTrue(id, TaskManagerRules.byId(id) != null)
        assertTrue(TaskManagerRules.parsePlan("{\"mode\":\"tools\",\"agent\":\"wa\",\"say\":\"rahul ko whatsapp karo ki late hu\"}", "rahul ko bata dena main late hu")!!.say.startsWith("jarvis "))
    }

    // ---- v7.199 CHAIN ----
    private val CHAIN_RAW = "{\"mode\":\"agent\",\"understood\":\"website banao, publish karo, link rahul ko bhejo\",\"agent\":\"none\",\"say\":\"\",\"steps\":[" +
        "{\"agent\":\"site\",\"say\":\"jarvis ek restaurant ki website banao\"}," +
        "{\"agent\":\"publish\",\"say\":\"jarvis website publish karo\"}," +
        "{\"agent\":\"wa\",\"say\":\"rahul ko whatsapp karo ki website live hai\"}]}"

    @Test fun chainIsParsedInOrderAndPrefixed() {
        val st = TaskManagerRules.parseSteps(CHAIN_RAW, "website banao publish karo aur rahul ko bhej do")!!
        assertEquals(listOf("site", "publish", "wa"), st.map { it.agentId })
        assertTrue(st.all { it.say.startsWith("jarvis ") && !it.denied })
        assertEquals("jarvis rahul ko whatsapp karo ki website live hai", st[2].say)
    }

    @Test fun chainNeedsTwoValidStepsAndKnownTools() {
        assertNull(TaskManagerRules.parseSteps(null, "x"))
        assertNull(TaskManagerRules.parseSteps("{\"agent\":\"leads\",\"say\":\"jarvis x\"}", "x"))
        assertNull(TaskManagerRules.parseSteps("{\"steps\":[{\"agent\":\"site\",\"say\":\"jarvis site banao\"}]}", "x"))
        assertNull(TaskManagerRules.parseSteps("{\"steps\":[{\"agent\":\"site\",\"say\":\"jarvis site banao\"},{\"agent\":\"zzz\",\"say\":\"jarvis x\"}]}", "x"))
        assertNull(TaskManagerRules.parseSteps("{\"steps\":[{\"agent\":\"site\",\"say\":\"jarvis site banao\"},{\"agent\":\"agent\",\"say\":\"jarvis x\"}]}", "x"))
        assertNull(TaskManagerRules.parseSteps("{\"steps\":[{\"agent\":\"site\",\"say\":\"jarvis site banao\"},{\"agent\":\"wa\",\"say\":\"\"}]}", "x"))
    }

    @Test fun chainDeniesPaymentAnywhere() {
        val bad = "{\"steps\":[{\"agent\":\"site\",\"say\":\"jarvis site banao\"},{\"agent\":\"wa\",\"say\":\"rahul ko otp bhejo\"}]}"
        val d = TaskManagerRules.parseSteps(bad, "site banao aur rahul ko bhejo")!!
        assertTrue(d.size == 1 && d[0].denied)
        val d2 = TaskManagerRules.parseSteps(CHAIN_RAW, "mera cvv bhejo aur website banao")!!
        assertTrue(d2.size == 1 && d2[0].denied)
    }

    @Test fun chainTooLongIsRefused() {
        val one = "{\"agent\":\"wa\",\"say\":\"rahul ko whatsapp karo ki hi\"}"
        val raw = "{\"steps\":[" + List(TaskManagerRules.MAX_STEPS + 1) { one }.joinToString(",") + "]}"
        assertNull(TaskManagerRules.parseSteps(raw, "x"))
    }

    @Test fun pendingStepsRoundTripAndKeepDenyRule() {
        val st = TaskManagerRules.parseSteps(CHAIN_RAW, "x")!!
        val back = TaskManagerRules.decodeSteps(TaskManagerRules.encodeSteps(st.subList(1, 3)))
        assertEquals(listOf("publish", "wa"), back.map { it.agentId })
        assertEquals(st[2].say, back[1].say)
        assertTrue(TaskManagerRules.decodeSteps("wa\tjarvis password bhejo").isEmpty())
        assertTrue(TaskManagerRules.decodeSteps("zzz\tjarvis x").isEmpty() && TaskManagerRules.decodeSteps(null).isEmpty())
    }

    @Test fun longToolsPauseTheChainAndShortOnesDoNot() {
        for (id in listOf("site", "publish", "leads", "appbuild", "agent")) assertTrue(id, TaskManagerRules.isBackground(id))
        for (id in listOf("wa", "gmail", "music", "orders", "callagent")) assertFalse(id, TaskManagerRules.isBackground(id))
    }

    @Test fun continuePhraseAndStepFailure() {
        assertTrue(TaskManagerRules.isContinue("jarvis aage badho"))
        assertTrue(TaskManagerRules.isContinue("agla kaam karo"))
        assertFalse(TaskManagerRules.isContinue("gaana aage badha do aur volume kam karo please abhi"))
        assertTrue(TaskManagerRules.stepFailed(null) && TaskManagerRules.stepFailed("FAILED: no app") && TaskManagerRules.stepFailed("ye nahi ho paya"))
        assertFalse(TaskManagerRules.stepFailed("Gaana chala diya"))
    }

    @Test fun chainLineIsHonest() {
        assertTrue(TaskManagerRules.chainLine(3, 3, false, false, false).startsWith("Saare 3"))
        assertTrue(TaskManagerRules.chainLine(1, 3, true, false, false).contains("aage badho"))
        assertTrue(TaskManagerRules.chainLine(1, 3, false, true, false).startsWith("Ruk gaya"))
        assertTrue(TaskManagerRules.chainLine(1, 3, false, true, true).startsWith("Stopped"))
    }

    @Test fun catalogExplainsSteps() {
        val c = TaskManagerRules.catalogForAi()
        assertTrue(c.contains("\"steps\"") && c.contains("TWO OR MORE"))
    }
}
