package com.nova.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure rule tests for voice selection safety; Android dialog/device behavior requires instrumentation. */
class ContactChoiceRulesTest {
    private fun choose(raw: String, choices: List<ContactCandidate>): ContactCandidate? {
        val query = ContactMatcher.basic(raw).removePrefix("call ").removePrefix("phone ").trim()
        if (query.isBlank()) return null
        val scored = choices.map { candidate ->
            val name = ContactMatcher.basic(candidate.displayName)
            candidate to when {
                query == name -> 1.0
                name.startsWith(query) || query.startsWith(name) -> 0.85
                name.contains(query) -> 0.80
                else -> 0.0
            }
        }.filter { it.second >= 0.80 }.sortedByDescending { it.second }
        if (scored.isEmpty() || (scored.size > 1 && scored[0].second == scored[1].second)) return null
        return scored.first().first
    }

    private val rahul = ContactCandidate("1", "Rahul Sharma", "9876543210", .9, emptyList())
    private val rahulVerma = ContactCandidate("2", "Rahul Verma", "9876501234", .9, emptyList())

    @Test fun exactDisplayedNameCanBeSelectedByVoice() {
        assertEquals("1", choose("Rahul Sharma", listOf(rahul, rahulVerma))?.contactId)
    }

    @Test fun ambiguousSpokenFirstNameDoesNotSelect() {
        assertNull(choose("Rahul", listOf(rahul, rahulVerma)))
    }

    @Test fun unrelatedSpokenNameDoesNotSelect() {
        assertNull(choose("Amit", listOf(rahul, rahulVerma)))
    }
}
