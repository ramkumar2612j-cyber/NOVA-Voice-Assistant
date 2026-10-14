package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentTaskRulesTest {
    @Test fun normalLifecycleAndTerminalStates() {
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.CREATED, AgentTaskRules.State.PLANNING))
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.PLANNING, AgentTaskRules.State.RUNNING))
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.RUNNING, AgentTaskRules.State.VERIFYING))
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.VERIFYING, AgentTaskRules.State.SUCCEEDED))
        assertFalse(AgentTaskRules.canTransition(AgentTaskRules.State.SUCCEEDED, AgentTaskRules.State.RUNNING))
        assertFalse(AgentTaskRules.canTransition(AgentTaskRules.State.FAILED, AgentTaskRules.State.RETRYING))
    }

    @Test fun waitingStatesCanResumeOrCancel() {
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.RUNNING, AgentTaskRules.State.WAITING_FOR_CONFIRMATION))
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.WAITING_FOR_CONFIRMATION, AgentTaskRules.State.RUNNING))
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.PLANNING, AgentTaskRules.State.WAITING_FOR_PERMISSION))
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.WAITING_FOR_PERMISSION, AgentTaskRules.State.PLANNING))
        assertTrue(AgentTaskRules.canTransition(AgentTaskRules.State.WAITING_FOR_CONFIRMATION, AgentTaskRules.State.CANCELLED))
    }

    @Test fun anyLiveStateCanBeMarkedInterruptedButTerminalCannotChange() {
        for (state in listOf(AgentTaskRules.State.CREATED, AgentTaskRules.State.PLANNING, AgentTaskRules.State.RUNNING,
            AgentTaskRules.State.VERIFYING, AgentTaskRules.State.RETRYING, AgentTaskRules.State.WAITING_FOR_CONFIRMATION)) {
            assertTrue("$state should recover as interrupted", AgentTaskRules.canTransition(state, AgentTaskRules.State.INTERRUPTED))
        }
        assertFalse(AgentTaskRules.canTransition(AgentTaskRules.State.INTERRUPTED, AgentTaskRules.State.RUNNING))
    }

    @Test fun retryBudgetAndDeadlineAreBounded() {
        assertTrue(AgentTaskRules.canRetry(0, 1000L, 2000L))
        assertTrue(AgentTaskRules.canRetry(2, 1000L, 2000L))
        assertFalse(AgentTaskRules.canRetry(3, 1000L, 2000L))
        assertFalse(AgentTaskRules.canRetry(0, 1000L, 1000L + AgentTaskRules.MAX_RUNTIME_MS))
        assertFalse(AgentTaskRules.canRetry(0, 2000L, 1000L))
        assertFalse(AgentTaskRules.canRetry(-1, 1000L, 2000L))
    }

    @Test fun onlySafeActionsCanBeResumed() {
        assertTrue(AgentTaskRules.isIdempotentCheckpoint("open_app"))
        assertTrue(AgentTaskRules.isIdempotentCheckpoint("wait"))
        assertFalse(AgentTaskRules.isIdempotentCheckpoint("tap"))
        assertFalse(AgentTaskRules.isIdempotentCheckpoint("type"))
        assertFalse(AgentTaskRules.isIdempotentCheckpoint("scroll"))
        assertFalse(AgentTaskRules.isIdempotentCheckpoint("done"))
        assertTrue(AgentTaskRules.canResumeCheckpoint("open_app", true))
        assertFalse(AgentTaskRules.canResumeCheckpoint("open_app", false))
    }

    @Test fun interruptedTaskResumeRequiresEverySafetyGate() {
        fun eligible(
            state: AgentTaskRules.State = AgentTaskRules.State.INTERRUPTED,
            steps: Int = 2,
            action: String = "open_app",
            completed: Boolean = true,
            safeSoFar: Boolean = true,
            attempted: Boolean = false,
            deadline: Long = 10_000L,
            now: Long = 9_000L
        ) = AgentTaskRules.canResumeInterruptedTask(state, steps, action, completed, safeSoFar, attempted, deadline, now)
        assertTrue(eligible())
        assertFalse(eligible(state = AgentTaskRules.State.FAILED))
        assertFalse(eligible(steps = 0))
        assertFalse(eligible(action = "tap"))
        assertFalse(eligible(action = "open_app", completed = false))
        assertFalse(eligible(safeSoFar = false))
        assertFalse(eligible(attempted = true))
        assertFalse(eligible(deadline = 9_000L))
    }

    @Test fun resumeCommandIsNarrow() {
        assertTrue(AgentTaskRules.isResumeQuery("nova resume last task"))
        assertTrue(AgentTaskRules.isResumeQuery("pichla kaam resume karo"))
        assertFalse(AgentTaskRules.isResumeQuery("mera kaam kar do"))
        assertFalse(AgentTaskRules.isResumeQuery("resume my Instagram account"))
    }

    @Test fun terminalClassificationCoversReportedNotVerified() {
        assertTrue(AgentTaskRules.isTerminal(AgentTaskRules.State.REPORTED_COMPLETE))
        assertFalse(AgentTaskRules.isTerminal(AgentTaskRules.State.RUNNING))
        assertTrue(AgentTaskRules.isActive(AgentTaskRules.State.WAITING_FOR_PERMISSION))
        assertFalse(AgentTaskRules.isActive(AgentTaskRules.State.NEEDS_OWNER))
    }

    @Test fun sensitiveStringsAreNotKeptInSummary() {
        val summary = AgentTaskRules.safeSummary("website banao api_key=supersecretvalue and token abcd_efgh_ijkl_mnop_qrstuvwxyz_1234567890")
        assertTrue(summary.contains("[hidden]"))
        assertFalse(summary.contains("supersecretvalue"))
        assertFalse(summary.contains("abcd_efgh_ijkl_mnop_qrstuvwxyz_1234567890"))
        assertTrue(AgentTaskRules.safeSummary("call me at 98765 43210").contains("[number]"))
        assertTrue(AgentTaskRules.safeSummary("x".repeat(300)).length <= AgentTaskRules.MAX_SUMMARY_CHARS)
    }

    @Test fun statusQueriesAreNarrowAndDoNotStealNormalLanguage() {
        assertTrue(AgentTaskRules.isStatusQuery("nova agent status"))
        assertTrue(AgentTaskRules.isStatusQuery("kaam ka status"))
        assertTrue(AgentTaskRules.isStatusQuery("pichla kaam"))
        assertFalse(AgentTaskRules.isStatusQuery("mera kaam kar do"))
        assertFalse(AgentTaskRules.isStatusQuery("what is the weather"))
    }
}
