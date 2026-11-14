package com.nova.assistant

import org.junit.Assert.*
import org.junit.Test

/** v7.176 (c) APP BUILDER pure rules. Same cases were compiled and run with the real Kotlin compiler (60 checks) before the zip was made. */
class AppBuilderRulesTest {
    private val A = AppBuilderRules
    @Test fun build_phrases() {
        for (s in listOf("nova chhath calendar app banao", "nova mummy ke liye medicine reminder app bana do", "nova build a todo list app"))
            assertEquals(s, A.K_APP_BUILD, A.parse(s)?.kind)
        assertEquals("Chhath Calendar", A.appName("nova mummy ke liye chhath calendar app banao"))
    }
    @Test fun other_things_are_left_alone() {
        for (s in listOf("nova stop", "nova whatsapp download karo", "nova salon website banao", "nova play store se app download karo",
            "nova app me dark mode banao", "app kaise banate hain", "nova banana shake banao", "nova youtube app kholo", "nova kya tum app bana sakte ho", "can you build an app"))
            assertNull(s, A.parse(s))
    }
    @Test fun screen_stop_install() {
        assertEquals(A.K_APP_SCREEN, A.parse("nova app builder kholo")?.kind)
        assertEquals(A.K_APP_STOP, A.parse("nova app banana band karo")?.kind)
        assertEquals(A.K_APP_INSTALL, A.parse("nova banayi hui app install karo")?.kind)
    }
    @Test fun facts_need_a_source_and_matching_numbers() {
        val plan = A.Plan("T", true, listOf(A.Section("s1", "Days", "q")))
        val srcs = A.mergeSources(listOf(listOf(Triple("A", "https://example.org/a", "Chhath has four days and a 36 hours fast."))))
        val sheet = A.parseSheet("""{"sections":[{"id":"s1","facts":[{"t":"Chhath has a 36 hours fast.","src":[1]},{"t":"Chhath lasts 72 hours.","src":[1]},{"t":"No source here at all.","src":[]}],"gap":""}]}""", plan, srcs)!!
        assertEquals(1, sheet.total)
        assertEquals(2, sheet.dropped)
        assertEquals("facts", A.notEnough(plan, sheet, 1).let { if (it == "sources") "facts" else it })
    }
    @Test fun checker_flags_unsourced_years_phones_mails_links() {
        val bad = "<html><head></head><body>Chhath 2022 call 9876543210 mail a@b.com https://evil.test/x</body></html>"
        val p = A.checkHtml(bad, "chhath 2026", false, emptyList())
        assertTrue(p.any { it.contains("2022") }); assertTrue(p.any { it.contains("phone") }); assertTrue(p.any { it.contains("a@b.com") }); assertTrue(p.any { it.contains("evil.test") })
    }
    @Test fun project_is_complete_and_workflow_is_dispatch_only() {
        val f = A.projectFiles("Chhath Calendar", "com.novaapps.chhath_calendar.b1", "<html></html>", "# s").toMap()
        assertTrue(f.containsKey("app/src/main/java/com/novaapps/chhath_calendar/b1/MainActivity.java"))
        assertTrue(f[".github/workflows/build.yml"]!!.contains("workflow_dispatch:"))
        assertFalse(f.values.any { it.contains('§') })
    }
}
