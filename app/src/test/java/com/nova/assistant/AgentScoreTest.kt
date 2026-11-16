package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentScoreTest {
    private fun r(s: String, st: String, steps: Int = 4) = AgentScore.Row(s, st, steps)

    @Test fun emptyIsHonest() {
        assertTrue(AgentScore.reply(emptyList(), false).contains("koi poora hua agent kaam record nahi"))
        assertTrue(AgentScore.reply(emptyList(), true).contains("No finished agent jobs"))
    }

    @Test fun countsEachKindAndSkipsRunningJobs() {
        val rows = listOf(r("a", "SUCCEEDED"), r("b", "SUCCEEDED"), r("c", "REPORTED_COMPLETE"), r("d", "NEEDS_OWNER"),
            r("e", "FAILED"), r("f", "CANCELLED"), r("g", "RUNNING"))
        val t = AgentScore.tally(rows)
        assertEquals(6, t.total)
        assertEquals(2, t.verified)
        assertEquals(1, t.unverified)
        assertEquals(1, t.needsOwner)
        assertEquals(2, t.failed)
    }

    @Test fun reportedCompleteIsNeverCountedAsVerified() {
        val t = AgentScore.tally(listOf(r("x", "REPORTED_COMPLETE")))
        assertEquals(0, t.verified)
        assertEquals(1, t.unverified)
    }

    @Test fun onlyTheNewestTwentyAreCounted() {
        val rows = (1..30).map { r("t$it", "SUCCEEDED") }
        assertEquals(20, AgentScore.tally(rows).total)
    }

    @Test fun replyNamesTheLatestJobsNewestFirst() {
        val s = AgentScore.reply(listOf(r("pehla", "FAILED"), r("doosra", "SUCCEEDED")), false)
        assertTrue(s.indexOf("doosra") in 0 until s.indexOf("pehla"))
    }
}
