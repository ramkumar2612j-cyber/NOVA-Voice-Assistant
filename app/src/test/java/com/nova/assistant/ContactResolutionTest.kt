package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

class ContactResolutionTest {
    private fun row(id: String, name: String, number: String = "9876543210", type: Int = 2, primary: Boolean = false) =
        ContactPhoneRow(id, name, number, type, primary)

    @Test fun exactContactMatch() {
        val result = ContactMatcher.match("Rahul Sharma", listOf(row("1", "Rahul Sharma"), row("2", "Priya")))
        assertEquals(ContactMatchStatus.MATCH, result.status)
        assertEquals("1", result.selected?.contactId)
    }

    @Test fun duplicateExactNamesAreNotAutoSelected() {
        val result = ContactMatcher.match("Rahul Sharma", listOf(row("1", "Rahul Sharma"), row("2", "Rahul Sharma", "9123456780")))
        assertEquals(ContactMatchStatus.AMBIGUOUS_DUPLICATE, result.status)
        assertNull(result.selected)
        assertEquals(2, result.candidates.size)
    }

    @Test fun similarNamesAreAmbiguous() {
        val result = ContactMatcher.match("Rahul", listOf(row("1", "Rahul Sharma"), row("2", "Rahul Verma")))
        assertEquals(ContactMatchStatus.AMBIGUOUS_SIMILAR, result.status)
        assertNull(result.selected)
    }

    @Test fun exactNameDoesNotOverrideAnotherPlausibleContact() {
        val result = ContactMatcher.match("Rahul", listOf(row("1", "Rahul"), row("2", "Rahool")))
        assertTrue(result.status == ContactMatchStatus.AMBIGUOUS_SIMILAR || result.status == ContactMatchStatus.AMBIGUOUS_DUPLICATE)
        assertNull(result.selected)
    }

    @Test fun uniquePartialNameCanResolve() {
        val result = ContactMatcher.match("Sharma", listOf(row("1", "Rahul Sharma"), row("2", "Priya Singh")))
        assertEquals(ContactMatchStatus.MATCH, result.status)
        assertEquals("Rahul Sharma", result.selected?.displayName)
    }

    @Test fun noMatchingContactIsReported() {
        val result = ContactMatcher.match("zzqxyz", listOf(row("1", "Rahul Sharma")))
        assertEquals(ContactMatchStatus.NO_MATCH, result.status)
        assertNull(result.selected)
    }

    @Test fun lowConfidenceMatchIsNotSelected() {
        val result = ContactMatcher.match("rahulx", listOf(row("1", "Rahul")))
        assertEquals(ContactMatchStatus.LOW_CONFIDENCE, result.status)
        assertNull(result.selected)
    }

    @Test fun samePersonWithSeveralNumbersIsOneCandidateAndPrimaryNumberWins() {
        val result = ContactMatcher.match("Rahul Sharma", listOf(
            row("1", "Rahul Sharma", "1111111111", type = 1),
            row("1", "Rahul Sharma", "2222222222", type = 2, primary = true),
            row("1", "Rahul Sharma", "3333333333", type = 2)
        ))
        assertEquals(ContactMatchStatus.MATCH, result.status)
        assertEquals(1, result.candidates.size)
        assertEquals("2222222222", result.selected?.number)
    }

    @Test fun hindiAndHinglishCommandVariationsExtractContact() {
        assertEquals("rahul", ContactRequestParser.parseCommand("Rahul ko call karo")?.requestedName)
        assertEquals("rahul sharma", ContactRequestParser.parseCommand("Call Rahul Sharma")?.requestedName)
        assertEquals("rahul", ContactRequestParser.parseCommand("राहुल को कॉल करो")?.requestedName)
        assertTrue(ContactRequestParser.parseCommand("Isko call karo")?.isReference == true)
        assertTrue(ContactRequestParser.parseCommand("Call this person")?.isReference == true)
        assertEquals("rahul", ContactRequestParser.parseCommand("phone lagao Rahul ko")?.requestedName)
        assertEquals("rahul", ContactRequestParser.parseCommand("dial Rahul")?.requestedName)
        assertEquals("rahul", ContactRequestParser.parseCommand("zara Rahul ko phone lagao")?.requestedName)
        assertEquals("rahul", ContactRequestParser.parseCommand("baat karao Rahul se")?.requestedName)
        assertNull(ContactRequestParser.parseCommand("Rahul ko call mat karo"))
        assertNull(ContactRequestParser.parseCommand("don't call Rahul"))
        assertNull(ContactRequestParser.parseCommand("phone settings"))
        assertNull(ContactRequestParser.parseCommand("phone silent kar do"))
        assertNull(ContactRequestParser.parseCommand("phone unlock kar do"))
        assertEquals("", ContactRequestParser.parseCommand("call karo")?.requestedName)
    }

    @Test fun emptyQueryRequiresName() {
        assertEquals(ContactMatchStatus.NEED_NAME, ContactMatcher.match(" ", listOf(row("1", "Rahul"))).status)
    }

    @Test fun contactPermissionGrantedLookupReturnsRealRowsFromSourceContract() {
        val rows = listOf(row("real-id", "Rahul"))
        val source = object : ContactRowSource {
            override fun queryAll(): ContactLookupResult = ContactLookupResult.Success(rows)
        }
        val result = ContactResolutionPipeline.resolve("Rahul", source)
        assertTrue(result is ContactResolutionOutcome.ResolvedCandidates)
        assertEquals(ContactMatchStatus.MATCH, (result as ContactResolutionOutcome.ResolvedCandidates).result.status)
    }

    @Test fun contactPermissionDeniedLookupIsNotConvertedToFakeData() {
        val source = object : ContactRowSource {
            override fun queryAll(): ContactLookupResult = ContactLookupResult.Failure("Permission denial", permissionDenied = true)
        }
        val result = ContactResolutionPipeline.resolve("Rahul", source)
        assertTrue(result is ContactResolutionOutcome.LookupFailed)
        val failure = (result as ContactResolutionOutcome.LookupFailed).failure
        assertTrue(failure.permissionDenied)
        assertEquals("Permission denial", failure.reason)
    }

    @Test fun sameContactSavedTwiceWithSameNumberIsNotAmbiguous() {
        val result = ContactMatcher.match("Rahul Sharma", listOf(row("1", "Rahul Sharma", "98765 43210"), row("2", "Rahul Sharma", "+91 9876543210")))
        assertEquals(ContactMatchStatus.MATCH, result.status)
        assertEquals(1, result.candidates.size)
    }

    @Test fun sameNameDifferentNumbersStaysAmbiguous() {
        val result = ContactMatcher.match("Rahul Sharma", listOf(row("1", "Rahul Sharma", "9876543210"), row("2", "Rahul Sharma", "9123456780")))
        assertEquals(ContactMatchStatus.AMBIGUOUS_DUPLICATE, result.status)
    }
}
