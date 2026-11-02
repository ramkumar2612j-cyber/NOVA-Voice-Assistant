package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.114: Papa / Mummy / custom saved numbers. Pure rules only (the SharedPreferences store is not touched). */
class FamilyContactsTest {
    private val others = listOf(FamilyContacts.Entry("rahulbhai", "Rahul Bhai", "9811122233", false), FamilyContacts.Entry("didi", "Didi", "+919900011122", false))
    private fun f(s: String, papa: String = "9876543210", mummy: String = "9123456780") = FamilyContacts.find(s, papa, mummy, others)

    @Test fun cleanNumber() {
        assertEquals("9876543210", FamilyContacts.cleanNumber("98765 43210"))
        assertEquals("+919876543210", FamilyContacts.cleanNumber(" +91 98765-43210 "))
        assertEquals("", FamilyContacts.cleanNumber("12345"))
        assertEquals("", FamilyContacts.cleanNumber("1234567890123456"))
        assertEquals("", FamilyContacts.cleanNumber(null))
    }
    @Test fun cleanName() {
        assertEquals("Rahul Bhai", FamilyContacts.cleanName("rahul bhai"))
        assertEquals("", FamilyContacts.cleanName("a"))
        assertEquals("", FamilyContacts.cleanName("a b c"))
        assertEquals("", FamilyContacts.cleanName("rahul123"))
    }
    @Test fun papaMummyWholeNameOnly() {
        assertEquals("9876543210", f("papa")!!.number)
        assertEquals("9876543210", f("Papa ji")!!.number)
        assertEquals("9123456780", f("mummy")!!.number)
        assertEquals("9123456780", f("mom")!!.number)
        assertNull(f("mama ji sweet shop"))
        assertNull(f("papa", papa = ""))
    }
    @Test fun customByWholeName() {
        assertEquals("9811122233", f("rahul bhai")!!.number)
        assertEquals("+919900011122", f("Didi")!!.number)
        assertNull(f("rahul"))
        assertNull(f(""))
    }
    @Test fun saveByVoice() {
        val a = FamilyContacts.parseSave("nova papa ka number 9876543210 save karo")!!
        assertEquals("Papa", a.name); assertEquals("9876543210", a.number)
        val b = FamilyContacts.parseSave("mummy ka no +91 98765 43210 yaad rakho")!!
        assertEquals("Mummy", b.name); assertEquals("+919876543210", b.number)
        val c = FamilyContacts.parseSave("rahul bhai ka mobile number 98111 22233 save kar do")!!
        assertEquals("Rahul Bhai", c.name); assertEquals("9811122233", c.number)
    }
    @Test fun normalSentencesAreNotSaves() {
        assertNull(FamilyContacts.parseSave("papa ko call karo"))
        assertNull(FamilyContacts.parseSave("mera number kya hai"))
        assertNull(FamilyContacts.parseSave("papa ka number save karo"))          // no number said
        assertNull(FamilyContacts.parseSave("alarm 9876543210 save"))             // no number word
        assertNull(FamilyContacts.parseSave(null))
    }
    @Test fun texts() {
        assertTrue(FamilyContacts.savedText("Papa", "9876543210", false).contains("3210"))
        assertFalse(FamilyContacts.savedText("Papa", "9876543210", false).contains("9876543210"))
        assertTrue(FamilyContacts.refusedText(true).isNotEmpty())
    }
}
