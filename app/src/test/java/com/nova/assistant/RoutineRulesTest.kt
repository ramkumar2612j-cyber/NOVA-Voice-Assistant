package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class RoutineRulesTest {
    @Test fun acceptsBoundedSafeRoutine() {
        val r = RoutineRules.parse("Morning", "good morning", "torch off; volume up")
        assertNotNull(r)
        assertEquals(2, r!!.steps.size)
    }

    @Test fun rejectsRiskyOrUnclearRoutineSteps() {
        assertNull(RoutineRules.parse("Bad", "do bad thing", "tap Pay"))
        assertNull(RoutineRules.parse("Bad", "do other thing", "type hello"))
        assertNull(RoutineRules.parse("Bad", "do other thing", "call mummy"))
        assertNull(RoutineRules.parse("Bad", "good morning", "torch off; volume up; torch off; volume up; torch off; volume up; torch off; volume up; torch off"))
    }
}
